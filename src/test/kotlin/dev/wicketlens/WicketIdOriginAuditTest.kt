package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.util.IncorrectOperationException
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.model.*

class WicketIdOriginAuditTest : WicketProjectPsiTestCase() {
    override fun tearDown() {
        try { FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) } }
        finally { super.tearDown() }
    }
    private fun fixture(body: String = "add(new Decorated(\"title\"));", effects: String = "", members: String = ""): Triple<PsiJavaFile, PsiFile, PsiFile> {
        val helper = myFixture.addFileToProject("src/main/java/example/Decorated.java", """
            package example; public class Decorated extends org.apache.wicket.markup.html.WebMarkupContainer {
                public Decorated(String id) { super(id); add(new org.apache.wicket.behavior.Behavior()); $effects }
            }
        """)
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() { $body }
                $members
            }
        """) as PsiJavaFile
        val html = companion(html = "<div wicket:id='title'></div>")
        return Triple(java, html, helper)
    }
    private fun node(java: PsiJavaFile) = WicketPageAnalyzer().analyze(java.classes.single())!!.nodes.single { it.wicketId == "title" }
    private fun value(file: PsiFile, id: String) = WicketIdPsi.valueAt(file, file.text.indexOf(id))!!
    private fun target(file: PsiFile) = WicketIdRenameTarget.at(file, file.text.indexOf("title"))
    private fun edit(file: PsiFile, text: String) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun guarded(body: String = "add(new Decorated(\"title\"));", effects: String = "", members: String = "") {
        val (java, html) = fixture(body, effects, members)
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(report.toString(), report.matching!!.findings.any { it.code == DiagnosticCode.MATCHED })
        assertFalse(node(java).idRenameSafe)
        assertNull(target(java)); assertNull(target(html))
    }
    fun testDecoratedLiteralNativePreviewRenameAndUndo() {
        val (java, html, helper) = fixture()
        assertTrue(node(java).idRenameSafe)
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val before = listOf(java.text, html.text, helper.text)
        val processor = RenameProcessor(project, target(java)!!, "heading", false, false)
        assertEquals(listOf(value(html, "title")), processor.findUsages().map { it.element })
        processor.run(); PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("Decorated(\"heading\")")); assertNotNull(value(html, "heading"))
        assertEquals(before[2], helper.text)
        val old = com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.OK)
        try { myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO) }
        finally { com.intellij.openapi.ui.TestDialogManager.setTestDialog(old) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(before, listOf(java.text, html.text, helper.text))
    }
    fun testLocalAliasUsedOnlyForIdentityCanRenameFromMarkup() {
        val (java, html) = fixture("String id = \"title\"; String alias = id; add(new Decorated(alias));")
        RenameProcessor(project, target(html)!!, "heading", false, false).run()
        assertTrue(java.text.contains("String id = \"heading\"")); assertTrue(java.text.contains("Decorated(alias)"))
    }
    fun testPrivateHelperForwardsOnlyTheInvocationLiteral() {
        val (java, html, helper) = fixture("add(build(\"title\"));", members = "private Decorated build(String id) { return new Decorated(id); }")
        RenameProcessor(project, target(java)!!, "heading", false, false).run()
        assertTrue(java.text.contains("build(\"heading\")")); assertTrue(java.text.contains("new Decorated(id)"))
        assertTrue(helper.text.contains("super(id)")); assertNotNull(value(html, "heading"))
    }
    fun testPrivateFinalComponentInitializerCanRename() {
        val (java, html) = fixture("add(field);", members = "private final Decorated field = new Decorated(\"title\");")
        RenameProcessor(project, target(html)!!, "heading", false, false).run()
        assertTrue(java.text.contains("field = new Decorated(\"heading\")"))
    }
    fun testReviewedInitializationLiteralCanRename() {
        val (java, html) = fixture(body = "", members = "@Override protected void onInitialize() { super.onInitialize(); add(new Decorated(\"title\")); }")
        RenameProcessor(project, target(html)!!, "heading", false, false).run()
        assertTrue(java.text.contains("Decorated(\"heading\")"))
    }
    fun testReadOnlyCounterpartPreventsBothWrites() {
        val (java, html) = fixture(); val prepared = target(java)!!
        val before = java.text to html.text
        WriteCommandAction.runWriteCommandAction(project) { html.virtualFile.isWritable = false }
        try {
            assertThrows(IncorrectOperationException::class.java) { WriteCommandAction.runWriteCommandAction(project) { prepared.setName("heading") } }
            assertEquals(before, java.text to html.text)
        } finally { WriteCommandAction.runWriteCommandAction(project) { html.virtualFile.isWritable = true } }
    }
    fun testLocalIdAlsoUsedAsModelDataIsGuarded() = guarded("String id = \"title\"; add(new Decorated(id)); new org.apache.wicket.markup.html.basic.Label(\"other\", id);")
    fun testConstructorIdAlsoUsedAsResourceKeyIsGuarded() = guarded(effects = "new org.apache.wicket.model.StringResourceModel(id, this);")
    fun testConstructorIdAlsoUsedAsDefaultTextIsGuarded() = guarded(effects = "org.apache.wicket.model.Model.of(id);")
    fun testIdUsedInComputationIsGuardedEvenWhenTheResultIsUnused() = guarded("String id = \"title\"; String other = id + \"suffix\"; add(new Decorated(id));")
    fun testIdUsedAsAMethodReceiverIsGuarded() = guarded("String id = \"title\"; String text = id.intern(); add(new Decorated(id));")
    fun testIdCapturedForDeferredDisplayIsGuarded() = guarded(effects = "java.util.function.Supplier<String> display = () -> id;")
    fun testSharedFragmentChildrenStayGuardedWhileOneAttachmentCanRename() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.panel.Fragment first = new org.apache.wicket.markup.html.panel.Fragment("first", "shared", this);
            first.add(new Label("title")); add(first);
            org.apache.wicket.markup.html.panel.Fragment second = new org.apache.wicket.markup.html.panel.Fragment("second", "shared", this);
            second.add(new Label("title")); add(second);
        """)
        val html = companion(html = "<div wicket:id='first'></div><div wicket:id='second'></div><wicket:fragment wicket:id='shared'><b wicket:id='title'></b></wicket:fragment>")
        assertNull(target(java)); assertNull(target(html))
        val first = WicketIdRenameTarget.at(java, java.text.indexOf("\"first\"") + 1)!!
        RenameProcessor(project, first, "renamed", false, false).run()
        assertTrue(html.text.contains("renamed")); assertTrue(java.text.contains("Fragment(\"renamed\", \"shared\", this)"))
        assertEquals(2, "Label(\"title\")".toRegex(RegexOption.LITERAL).findAll(java.text).count())
    }
    fun testMemberEnumerationLimitDoesNotDisableExistingTopLevelRename() {
        val members = (0..257).joinToString("\n") { "private static class Helper$it {}" }
        val (java) = fixture(members = members)
        assertNotNull(target(java))
    }
    fun testIdStoredInAFieldIsGuarded() = guarded("String id = \"title\"; stored = id; add(new Decorated(id));", members = "private String stored;")
    fun testHelperImplementationLiteralIsGuarded() = guarded("add(build());", members = "private Decorated build() { return new Decorated(\"title\"); }")
    fun testOneOriginFeedingTwoComponentsIsGuarded() {
        val (java, html) = fixture("String id = \"title\"; Decorated first = new Decorated(\"first\"); Decorated second = new Decorated(\"second\"); first.add(new Decorated(id)); second.add(new Decorated(id)); add(first, second);")
        edit(html, "<div wicket:id='first'><div wicket:id='title'></div></div><div wicket:id='second'><div wicket:id='title'></div></div>")
        val nodes = WicketPageAnalyzer().analyze(java.classes.single())!!.nodes.filter { it.wicketId == "title" }
        assertEquals(2, nodes.size); assertTrue(nodes.none { it.idRenameSafe }); assertNull(target(java)); assertNull(target(html))
    }
    fun testAChangedHelperRevokesAPreparedRenameBeforeEitherWrite() {
        val (java, html, helper) = fixture(); val prepared = target(java)!!
        edit(helper, helper.text.replace("super(id);", "super(id); org.apache.wicket.model.Model.of(id);"))
        val before = java.text to html.text
        assertNotNull(prepared.conflict("heading"))
        assertThrows(IncorrectOperationException::class.java) { WriteCommandAction.runWriteCommandAction(project) { prepared.setName("heading") } }
        assertEquals(before, java.text to html.text)
    }
    fun testNewVariantRevokesAPreparedRename() {
        val (java, html) = fixture(); val prepared = target(java)!!
        companion("ExamplePage_fr", html.text)
        assertNotNull(prepared.conflict("heading")); assertNotNull(target(java))
    }
    fun testNewInheritorRevokesAPreparedRename() {
        val (java) = fixture(); val prepared = target(java)!!
        myFixture.addFileToProject("src/main/java/example/Child.java", "package example; class Child extends ExamplePage {}")
        assertNotNull(prepared.conflict("heading")); assertNotNull(target(java))
    }
    fun testNewSiblingCollisionIsCheckedBeforeWriting() {
        val (java, html) = fixture(); val prepared = target(java)!!
        edit(html, html.text + "<div wicket:id='heading'></div>")
        assertNotNull(prepared.conflict("heading"))
    }
    fun testEffectDepthBudgetWithholdsAllPromotions() {
        val (java) = fixture(); val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val result = WicketIdOriginAudit(WicketIdOriginAudit.Limits(depth = 1)).analyze(java.classes.single(), report.nodes, promote = true)
        assertTrue(result.nodes.filter { it.wicketId != null }.none { it.idRenameSafe })
        assertTrue(result.diagnostics.single().message.contains("bounded traversal"))
    }
    fun testAuditCancellationPropagates() {
        val (java) = fixture(); val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable {
            indicator.cancel(); WicketIdOriginAudit().analyze(java.classes.single(), report.nodes, promote = true)
        }, indicator) }
    }
    private fun nested(): Pair<PsiJavaFile, PsiFile> {
        val java = myFixture.addFileToProject("src/main/java/example/Outer.java", """
            package example; public class Outer {
                public static final class Fields extends org.apache.wicket.markup.html.panel.Panel {
                    public Fields(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("title", "display")); }
                }
            }
        """) as PsiJavaFile
        return java to companion("Outer\$Fields", "<wicket:panel><b wicket:id='title'></b></wicket:panel>")
    }
    fun testIsolatedNamedMemberRenamesItsOwnPair() {
        val (java, html) = nested()
        RenameProcessor(project, target(html)!!, "heading", false, false).run()
        assertTrue(java.text.contains("Label(\"heading\", \"display\")")); assertTrue(html.text.contains("heading"))
    }
    fun testExternalMemberUseDoesNotAuthorizeAMultiContextRename() {
        val (java, html) = nested(); val prepared = target(html)!!
        myFixture.addFileToProject("src/main/java/example/Caller.java", "package example; class Caller extends org.apache.wicket.markup.html.WebPage { Caller() { add(new Outer.Fields(\"slot\")); } }")
        assertNotNull(prepared.conflict("heading")); assertNull(target(java)); assertNull(target(html))
    }
    fun testNamedMemberSearchBoundWithholdsEditing() {
        val (java, html) = nested()
        val uses = (0..257).joinToString(",") { "Fields.class" }
        edit(java, java.text.replace("public Fields(String id)", "private void references() { Class<?>[] types = {$uses}; } public Fields(String id)"))
        val report = WicketPageAnalyzer().analyze(java.classes.single().innerClasses.single())!!
        assertTrue(report.nodes.single { it.wicketId == "title" }.idRenameSafe)
        assertTrue(report.matching!!.findings.all { it.code == DiagnosticCode.MATCHED && it.causes.isEmpty() })
        assertNull(target(java)); assertNull(target(html))
    }
}
