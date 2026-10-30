package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerEx
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.PreparedReport
import dev.wicketlens.ui.SourceSide
import dev.wicketlens.ui.WicketLensService
import dev.wicketlens.ui.WicketReportPanel
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.awt.event.ActionEvent

class WicketNavigationTest : WicketProjectPsiTestCase() {
    private val service get() = project.service<WicketLensService>()

    override fun setUp() { super.setUp(); service.setEditorFollowing(false); service.cancel() }

    override fun tearDown() {
        try {
            service.cancel()
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testMatchedComponentButtonsOpenBothFilesAtSnapshotOffsets() {
        val java = pageFile()
        val html = companion()
        val prepared = analyze(java)
        val panel = panel()
        select(panel, "title : Label")
        assertTrue(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        performReportAction(panel.javaAction)
        assertEditor(SourceLocation(java.virtualFile.path, java.text.indexOf("new Label")))
        assertFalse(service.state.stale)
        performReportAction(panel.markupAction)
        assertEditor(SourceLocation(html.virtualFile.path, html.text.indexOf("<span")))
        assertEquals(prepared.json, service.state.prepared?.json)
    }

    fun testRootOffersItsCompanionWhileJavaOnlyChildrenKeepOnlyJavaNavigation() {
        val java = pageFile()
        val html = companion(html = "<html><body><div>No IDs</div></body></html>")
        val prepared = analyze(java)
        val panel = panel()
        val root = prepared.report.nodes.single { it.nodeId == prepared.report.rootNodeId }
        panel.reportText.caretPosition = prepared.presentation.links.first { it.javaLocation == root.javaLocation }.start
        assertTrue(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        performReportAction(panel.javaAction)
        assertEditor(root.javaLocation)
        performReportAction(panel.markupAction)
        assertEditor(SourceLocation(html.virtualFile.path, 0))
        assertNull(root.markupLocation) // File navigation does not invent a matched root component.
        select(panel, "title : Label")
        assertTrue(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
    }

    fun testPanelClassRowOpensItsCompanionAtTheBeginning() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePanel.java", """
            package example;
            public class ExamplePanel extends org.apache.wicket.markup.html.panel.Panel {
                public ExamplePanel(String id) { super(id); }
            }
        """.trimIndent())
        val html = companion("ExamplePanel", "<!-- Panel file -->\n<wicket:panel><div></div></wicket:panel>")
        analyze(java)
        val panel = panel()
        panel.hierarchyTree.setSelectionRow(0)
        assertTrue(panel.markupAction.isEnabled)
        performReportAction(panel.markupAction)
        assertEditor(SourceLocation(html.virtualFile.path, 0))
    }

    fun testClassRowUsesTheSelectedDerivedOrInheritedCompanion() {
        for (ownMarkup in listOf(true, false)) {
            val suffix = if (ownMarkup) "Extended" else "Fallback"
            val base = "Base$suffix"
            val child = "Child$suffix"
            myFixture.addFileToProject("src/main/java/example/$base.java", """
                package example; public class $base extends org.apache.wicket.markup.html.WebPage {}
            """.trimIndent())
            val baseHtml = companion(base, if (ownMarkup) "<wicket:child/>" else "<span wicket:id=\"title\"></span>")
            val java = myFixture.addFileToProject("src/main/java/example/$child.java", """
                package example; public class $child extends $base {
                    public $child() { add(new org.apache.wicket.markup.html.basic.Label("title")); }
                }
            """.trimIndent())
            val html = if (ownMarkup) companion(child, "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>") else baseHtml
            val prepared = analyze(java)
            val panel = panel()
            panel.hierarchyTree.setSelectionRow(0)
            assertEquals(SourceLocation(html.virtualFile.path, 0), prepared.hierarchy.single { it.id == "report" }.markupLocation)
            performReportAction(panel.markupAction)
            assertEditor(SourceLocation(html.virtualFile.path, 0))
        }
    }

    fun testClassRowKeepsHtmlDisabledForMissingAndAmbiguousCompanions() {
        for (name in listOf("MissingPage", "AmbiguousPage")) {
            val java = pageFile(name)
            if (name == "AmbiguousPage") {
                companion(name)
                myFixture.addFileToProject("src/main/java/example/$name.html", "<span wicket:id=\"title\"></span>")
            }
            val prepared = analyze(java)
            val panel = panel()
            panel.hierarchyTree.setSelectionRow(0)
            assertTrue(panel.javaAction.isEnabled)
            assertFalse(panel.markupAction.isEnabled)
            assertNull(prepared.hierarchy.single { it.id == "report" }.markupLocation)
            val root = prepared.report.nodes.single { it.nodeId == prepared.report.rootNodeId }
            assertNull(prepared.presentation.links.first { it.javaLocation == root.javaLocation }.markupLocation)
        }
    }

    fun testClassFileNavigationWorksWithUncertainMarkupAndStopsWhenStale() {
        val java = pageFile()
        val html = companion(html = "<div wicket:id=\"title\"")
        val prepared = analyze(java)
        assertFalse(prepared.report.markup!!.scopeComplete)
        val panel = panel()
        panel.hierarchyTree.setSelectionRow(0)
        assertTrue(panel.markupAction.isEnabled)
        performReportAction(panel.markupAction)
        val target = SourceLocation(html.virtualFile.path, 0)
        assertEditor(target)
        edit(html) { "\n$it" }
        assertTrue(service.state.stale)
        assertFalse(panel.markupAction.isEnabled)
        assertFalse(service.navigate(target))
    }

    fun testMarkupOnlyRowEnterOpensHtmlWithoutInventingAJavaTarget() {
        val java = pageFile(body = "")
        val html = companion()
        analyze(java)
        val panel = panel()
        select(panel, "title : <span>")
        assertFalse(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        panel.reportText.actionMap.get("wicketlens.navigate").actionPerformed(ActionEvent(panel.reportText, 0, ""))
        assertEditor(SourceLocation(html.virtualFile.path, html.text.indexOf("<span")))
    }

    fun testUnresolvedComponentsKeepOnlyObservedSourceTargets() {
        val java = pageFile(body = "add(new Label(System.getProperty(\"id\")));")
        companion()
        analyze(java)
        val panel = panel()
        select(panel, "<unresolved ID> : Label")
        assertTrue(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
        select(panel, "title : <span>")
        assertFalse(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
    }

    fun testRepeatedIdsNavigateToTheirOwnJavaAndMarkupNodes() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> left = new org.apache.wicket.markup.html.form.Form<>("left");
            org.apache.wicket.markup.html.form.Form<?> right = new org.apache.wicket.markup.html.form.Form<>("right");
            left.add(new Label("name")); right.add(new Label("name")); add(left, right);
        """.trimIndent())
        companion(html = "<html><body><form wicket:id=\"right\"><span wicket:id=\"name\"></span></form><form wicket:id=\"left\"><b wicket:id=\"name\"></b></form></body></html>")
        val prepared = analyze(java)
        val panel = panel()
        val links = prepared.presentation.links.filter { prepared.text.substring(it.start, it.end).contains("name : Label") }
        assertEquals(2, links.size)
        assertEquals(2, links.map { it.markupLocation }.distinct().size)
        links.forEach { link ->
            panel.reportText.caretPosition = link.start
            performReportAction(panel.javaAction)
            assertEditor(requireNotNull(link.javaLocation))
            performReportAction(panel.markupAction)
            assertEditor(requireNotNull(link.markupLocation))
        }
    }

    fun testUncommittedJavaEditImmediatelyBlocksNavigationAndCopy() = assertEditedSourceIsStale(editJava = true)

    fun testUncommittedHtmlEditImmediatelyBlocksNavigationAndCopy() = assertEditedSourceIsStale(editJava = false)

    fun testDeletedOrRenamedCompanionInvalidatesTheSnapshot() {
        for (rename in listOf(true, false)) {
            val name = if (rename) "RenamedPage" else "DeletedPage"
            val java = pageFile(name)
            val html = companion(name)
            val prepared = analyze(java)
            val location = requireNotNull(prepared.report.children().single().markupLocation)
            WriteCommandAction.runWriteCommandAction(project) {
                if (rename) html.virtualFile.rename(this, "$name-renamed.html") else html.virtualFile.delete(this)
            }
            assertFalse(service.navigate(location))
            assertTrue(service.state.stale)
            assertFalse(service.copyContext())
        }
    }

    fun testNewCompanionInvalidatesAMissingMarkupReportAndReanalyzeFindsIt() {
        val java = pageFile()
        val prepared = analyze(java)
        assertNull(prepared.report.markup)
        companion()
        assertFalse(service.refreshFreshness())
        assertTrue(service.state.stale)
        val panel = panel()
        assertTrue(panel.reanalyzeAction.isEnabled)
        performReportAction(panel.reanalyzeAction)
        awaitCompletion()
        assertFalse(service.state.stale)
        assertEquals("MATCHED", service.state.prepared?.report?.matching?.findings?.single()?.code?.name)
    }

    fun testReanalyzeFromHtmlUsesOriginalJavaClassAndRestoresNavigation() {
        val java = pageFile()
        val html = companion()
        analyze(java)
        val panel = panel()
        select(panel, "title : Label")
        performReportAction(panel.markupAction)
        edit(java) { it.replace("\"title\"", "\"updated\"") }
        edit(html) { it.replace("\"title\"", "\"updated\"") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        performReportAction(panel.reanalyzeAction)
        assertTrue(service.state.running)
        assertFalse(panel.javaAction.isEnabled)
        awaitCompletion()
        val prepared = requireNotNull(service.state.prepared)
        assertFalse(service.state.stale)
        assertEquals("updated", prepared.report.children().single().wicketId)
        assertTrue(JsonParser.parseString(prepared.json).asJsonObject.getAsJsonArray("components")
            .any { it.asJsonObject["wicketId"].isJsonPrimitive && it.asJsonObject["wicketId"].asString == "updated" })
        select(panel, "updated : Label")
        assertTrue(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        performReportAction(panel.javaAction)
        assertEditor(prepared.report.children().single().javaLocation)
    }

    fun testReanalyzePreservesSelectedClassAfterOffsetsShift() {
        val java = twoPages()
        analyze(java, java.text.indexOf("Second"))
        edit(java) { it.replace("class First", "/*" + "padding ".repeat(100) + "*/ class First") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze()
        awaitCompletion()
        assertEquals("example.Second", service.state.prepared?.report?.qualifiedClassName)
    }

    fun testReanalyzeDoesNotSilentlyChooseAnotherClassAfterRename() {
        val java = twoPages()
        analyze(java, java.text.indexOf("Second"))
        edit(java) { it.replace("class Second", "class Third") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze()
        awaitCompletion()
        assertNull(service.state.prepared)
        assertTrue(service.state.message.contains("previously analyzed class"))
    }

    fun testRootConfigurationChangesInvalidateTheSnapshot() {
        val java = pageFile()
        companion()
        analyze(java)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("extra-resources"), JavaResourceRootType.RESOURCE)
        assertFalse(service.refreshFreshness())
        assertTrue(service.state.stale)
    }

    fun testExternalVfsContentChangeBlocksOldOffsets() {
        val java = pageFile()
        val html = companion()
        val prepared = analyze(java)
        val target = requireNotNull(prepared.report.children().single().markupLocation)
        WriteCommandAction.runWriteCommandAction(project) {
            html.virtualFile.setBinaryContent(("<!-- external edit -->\n" + html.text).toByteArray())
        }
        assertFalse(service.navigate(target))
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
    }

    fun testEditBeforeQueuedNavigationCannotApplyTheOldOffset() {
        val java = pageFile()
        val html = companion()
        val prepared = analyze(java)
        val target = requireNotNull(prepared.report.children().single().markupLocation)
        assertTrue(FileEditorManager.getInstance(project).openFiles.isEmpty())
        assertTrue(service.navigate(target))
        edit(html) { "\n\n$it" }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue(service.state.stale)
        assertTrue("An obsolete queued navigation must not open an editor", FileEditorManager.getInstance(project).openFiles.isEmpty())
    }

    fun testNavigationRecreatesAnUnopenedCleanCompanionDocument() {
        val java = pageFile()
        val html = companion()
        val prepared = analyze(java)
        val manager = FileDocumentManager.getInstance()
        val document = manager.getCachedDocument(html.virtualFile)!!
        assertFalse(manager.isDocumentUnsaved(document))
        // Deterministically simulate weak-cache collection; fixture VFS files also keep a hard reference.
        val base = com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase::class.java
        val cache = base.getDeclaredField("myDocumentCache").apply { isAccessible = true }.get(manager) as MutableMap<*, *>
        cache.remove(html.virtualFile)
        html.virtualFile.putUserData(com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase.HARD_REF_TO_DOCUMENT_KEY, null)
        assertNull(manager.getCachedDocument(html.virtualFile))
        assertTrue(service.refreshFreshness())
        val target = prepared.report.children().single().markupLocation!!
        assertTrue(service.navigate(target))
        assertEditor(target)
    }

    fun testOtherJavaSourceEditsConservativelyInvalidateResolutionContext() {
        val helper = myFixture.addFileToProject("src/main/java/example/Helper.java", "package example; class Helper {}")
        val java = pageFile()
        companion()
        analyze(java)
        edit(helper) { "\n$it" }
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
    }

    fun testSavingAnalyzedEditorContentsDoesNotMakeReportStale() {
        val java = pageFile()
        companion()
        edit(java) { it.replace("\"title\"", "\"updated\"") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        analyze(java)
        FileDocumentManager.getInstance().saveDocument(requireNotNull(PsiDocumentManager.getInstance(project).getDocument(java)))
        assertTrue(service.refreshFreshness())
        assertFalse(service.state.stale)
    }

    fun testOutOfDateStateIsStickyEvenIfTheEditIsUndone() {
        val java = pageFile()
        companion()
        analyze(java)
        val original = java.text
        edit(java) { "\n$it" }
        edit(java) { original }
        assertFalse(service.refreshFreshness())
        assertTrue(service.state.stale)
    }

    fun testUnlinkedReportHeadingsCannotNavigate() {
        val java = pageFile()
        companion()
        val prepared = analyze(java)
        val panel = panel()
        panel.reportText.caretPosition = 0
        assertFalse(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
        assertFalse(service.navigate(SourceLocation(java.virtualFile.path, 1)))
        val ranges = prepared.presentation.links
        assertTrue(ranges.zipWithNext().all { (left, right) -> left.end <= right.start })
        assertTrue(ranges.all { it.start >= 0 && it.end <= prepared.text.length && it.start < it.end })
        val markupLine = ranges.first { it.preferredSide == SourceSide.MARKUP }
        assertTrue(prepared.text.substring(markupLine.start, markupLine.end).contains("Markup:"))
    }

    private fun assertEditedSourceIsStale(editJava: Boolean) {
        val java = pageFile()
        val html = companion()
        val prepared = analyze(java)
        val panel = panel()
        select(panel, "title : Label")
        val manager = PsiDocumentManager.getInstance(project)
        (manager as PsiDocumentManagerEx).disableBackgroundCommit(testRootDisposable)
        try {
            val file = if (editJava) java else html
            edit(file) { "\n$it" }
            assertFalse(manager.isCommitted(requireNotNull(manager.getDocument(file))))
            assertTrue(service.state.stale)
            assertTrue(service.state.message.contains("out of date"))
            assertEquals(prepared.text, panel.reportText.text)
            assertFalse(panel.javaAction.isEnabled)
            assertFalse(panel.markupAction.isEnabled)
            assertFalse(panel.copyAction.isEnabled)
            assertFalse(service.navigate(prepared.report.children().single().javaLocation))
            assertFalse(service.copyContext())
            assertTrue(FileEditorManager.getInstance(project).openFiles.isEmpty())
        } finally { manager.commitAllDocuments() }
    }

    private fun analyze(file: PsiFile, offset: Int = -1): PreparedReport {
        service.analyze(file.virtualFile.url, offset)
        awaitCompletion()
        assertFalse(service.state.message, service.state.stale)
        return requireNotNull(service.state.prepared) { service.state.message }
    }

    private fun panel() = WicketReportPanel(service).also { Disposer.register(testRootDisposable, it) }
    private fun select(panel: WicketReportPanel, text: String) {
        val offset = panel.reportText.text.indexOf(text)
        assertTrue("Missing report text: $text", offset >= 0)
        panel.reportText.caretPosition = offset
    }
    private fun assertEditor(location: SourceLocation) {
        PlatformTestUtil.waitWithEventsDispatching({ "Navigation did not reach $location" }, {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            editor != null && FileDocumentManager.getInstance().getFile(editor.document)?.path == location.file && editor.caretModel.offset == location.offset
        }, 10)
        val editor = requireNotNull(FileEditorManager.getInstance(project).selectedTextEditor)
        assertEquals(location.file, FileDocumentManager.getInstance().getFile(editor.document)?.path)
        assertEquals(location.offset, editor.caretModel.offset)
    }
    private fun edit(file: PsiFile, change: (String) -> String) {
        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
        WriteCommandAction.runWriteCommandAction(project) { document.setText(change(document.text)) }
    }
    private fun twoPages() = myFixture.addFileToProject("src/main/java/example/First.java", """
        package example;
        class First extends org.apache.wicket.markup.html.WebPage {}
        class Second extends org.apache.wicket.markup.html.WebPage {}
    """.trimIndent())
    private fun awaitCompletion() {
        PlatformTestUtil.waitWithEventsDispatching({ "Analysis did not finish: ${service.state.message}" }, { !service.state.running }, 20)
    }
}
