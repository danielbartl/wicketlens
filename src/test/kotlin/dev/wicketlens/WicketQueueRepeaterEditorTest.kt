package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.editor.*
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketLensService

class WicketQueueRepeaterEditorTest : WicketProjectPsiTestCase() {
    private var autoComplete = true
    override fun setUp() {
        super.setUp()
        project.service<WicketLensService>().setEditorFollowing(false)
        autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    }
    override fun tearDown() {
        try {
            project.service<WicketLensService>().cancel()
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testQueuedIdReferencesResolveThroughItsMarkupDerivedParent() {
        val java = page("queue(new WebMarkupContainer(\"form\"), new Label(\"name\"));")
        val html = companion(html = "<div wicket:id=\"form\"><span wicket:id=\"name\"></span></div>")
        assertEquals(html.idValue("name"), java.idValue("name").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(java.idValue("name"), html.idValue("name").references.filterIsInstance<WicketIdReference>().single().resolve())
    }

    fun testNativeQueuedRenameChangesBothSources() {
        val java = page("queue(new WebMarkupContainer(\"form\"), new Label(\"name\"));")
        val html = companion(html = "<div wicket:id=\"form\"><span wicket:id=\"name\"></span></div>")
        rename(java, "name", "givenName")
        assertTrue(java.text, java.text.contains("Label(\"givenName\")"))
        assertTrue(html.text, html.text.contains("wicket:id=\"givenName\""))
        matched(java, 2)
    }

    fun testQueueRenameChecksTheQueueNamespaceAcrossDifferentMarkupParents() {
        val java = page("queue(new WebMarkupContainer(\"left\"), new WebMarkupContainer(\"right\"), new Label(\"name\"), new Label(\"other\"));")
        companion(html = "<div><div wicket:id=\"left\"><span wicket:id=\"name\"></span></div><div wicket:id=\"right\"><span wicket:id=\"other\"></span></div></div>")
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"name\"") + 1)!!
        assertNotNull(target.conflict("other"))
        matched(java, 4)
    }

    fun testNativeQueueCompletionFindsNestedUnoccupiedMarkup() {
        val java = page("queue(new WebMarkupContainer(\"form\"), new Label(\"\"));")
        companion(html = "<div wicket:id=\"form\"><span wicket:id=\"name\"></span></div>")
        complete(java, "name")
        matched(java, 2)
    }

    fun testTemplateIdReferencesResolveInBothDirections() {
        val java = listView()
        val html = companion(html = markup)
        assertEquals(html.idValue("name"), java.idValue("name").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(java.idValue("name"), html.idValue("name").references.filterIsInstance<WicketIdReference>().single().resolve())
    }

    fun testNativeTemplateRenameChangesTheDeclaredTemplatePair() {
        val java = listView()
        val html = companion(html = markup)
        rename(java, "name", "displayName")
        assertTrue(java.text, java.text.contains("Label(\"displayName\", item.getModelObject())"))
        assertTrue(html.text, html.text.contains("wicket:id=\"displayName\""))
        matched(java, 2)
    }

    fun testNativeCompletionUsesTheLogicalItemParent() {
        val java = listView("")
        companion(html = markup)
        complete(java, "name")
        matched(java, 2)
    }

    fun testPossibleUnpairedTemplateIdsAppearInNativeInspections() {
        val java = listView("javaOnly")
        val html = companion(html = "<div wicket:id=\"rows\"><span wicket:id=\"htmlOnly\"></span></div>")
        assertEquals(1, problems(java).size)
        assertEquals(1, problems(html).size)
    }

    fun testHtmlCounterpartCreationIsAvailableForAClosedTemplate() {
        val java = listView()
        companion(html = "<div wicket:id=\"rows\"></div>")
        val fixes = problems(java).single().fixes.orEmpty()
        assertTrue(fixes.any { it.name.startsWith("Create HTML counterpart") })
    }

    fun testJavaCreationInsideAnItemTemplateIsWithheldButOutsideItRemainsAvailable() {
        val java = listView()
        val html = companion(html = "<div><div wicket:id=\"rows\"><span wicket:id=\"name\"></span><span wicket:id=\"innerMissing\"></span></div><span wicket:id=\"footer\">Footer</span></div>")
        val inner = problems(html).single { it.descriptionTemplate.contains("innerMissing") }
        val footer = problems(html).single { it.descriptionTemplate.contains("footer") }
        assertTrue(inner.fixes.orEmpty().none { it.name.startsWith("Create Java") })
        assertTrue(footer.fixes.orEmpty().any { it.name.startsWith("Create Java Label") })
        matched(java, 2)
    }

    fun testChangingMarkupReparentsAQueueAndInvalidatesTheOldSnapshot() {
        val java = page("queue(new WebMarkupContainer(\"left\"), new WebMarkupContainer(\"right\"), new Label(\"name\"));")
        val html = companion(html = "<div><div wicket:id=\"left\"><span wicket:id=\"name\"></span></div><div wicket:id=\"right\"></div></div>")
        val service = project.service<WicketLensService>()
        service.analyze(java.virtualFile.url, -1); await(service)
        fun parent() = service.state.prepared!!.report.let { report -> report.nodes.single { it.nodeId == report.nodes.single { it.wicketId == "name" }.parentNodeId }.wicketId }
        assertEquals("left", parent())
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(html)!!.setText("<div><div wicket:id=\"left\"></div><div wicket:id=\"right\"><span wicket:id=\"name\"></span></div></div>")
        }
        assertTrue(service.state.stale)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await(service)
        assertEquals("right", parent())
    }

    fun testCompositeQueueSetupPreservesItsOuterIdContract() {
        val panel = myFixture.addFileToProject("src/main/java/example/Input.java", """
            package example; public class Input extends org.apache.wicket.markup.html.panel.Panel {
                public Input(String id) { super(id); queue(new org.apache.wicket.markup.html.basic.Label("name")); }
            }
        """.trimIndent())
        companion("Input", "<wicket:panel><span wicket:id=\"name\"></span></wicket:panel>")
        val java = page("add(new Input(\"panel\"));")
        companion(html = "<div wicket:id=\"panel\"></div>")
        matched(java, 1)
        matched(panel, 1)
    }

    private val markup = "<div wicket:id=\"rows\"><span wicket:id=\"name\"></span></div>"
    private fun listView(id: String = "name") = page("""
        add(new ListView<String>("rows", java.util.List.of("Ada")) {
            @Override protected void populateItem(ListItem<String> item) { item.add(new Label("$id", item.getModelObject())); }
        });
    """)
    private fun page(body: String) = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example; import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.markup.html.WebMarkupContainer;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.markup.html.list.*;
        public class ExamplePage extends WebPage { public ExamplePage() { $body } }
    """.trimIndent()) as PsiJavaFile
    private fun rename(file: PsiFile, id: String, replacement: String) {
        val target = WicketIdRenameTarget.at(file, file.text.indexOf("\"$id\"") + 1)!!
        RenameProcessor(project, target, replacement, false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun complete(file: PsiFile, expected: String) {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("\"\"") + 1)
        val choices = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf(expected), choices.map { it.lookupString })
        myFixture.lookup.currentItem = choices.single(); myFixture.finishLookup('\n')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun problems(file: PsiFile): List<com.intellij.codeInspection.ProblemDescriptor> {
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(file)
        return holder.results
    }
    private fun matched(file: PsiFile, count: Int) = assertEquals(count, WicketEditorAnalysis.pages(file).single().report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
    private fun await(service: WicketLensService) {
        PlatformTestUtil.waitWithEventsDispatching("Queue report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
}
