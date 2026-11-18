package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketReportFormatter

class WicketBaseLayoutTest : WicketProjectPsiTestCase() {
    private fun setup()=WicketBaseLayoutFixture.install(myFixture)
    private fun report(java:PsiJavaFile)=WicketPageAnalyzer().analyze(java.classes.single())!!
    private fun change(java:PsiFile,before:String,after:String) {
        assertTrue(before,java.text.contains(before))
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text.replace(before,after));PsiDocumentManager.getInstance(project).commitAllDocuments() }
    }
    fun testAllConstructorEntriesShareAnInferredShellAndOpenExtensionBoundary() {
        val java=setup();val r=report(java)
        assertNotNull(r.warnings.toString(),r.baseLayoutTemplate)
        assertEquals(3,r.baseLayoutTemplate!!.constructors.size);assertEquals(1,r.baseLayoutTemplate.extensionSlots.size)
        assertEquals(5,r.matching!!.findings.size);assertTrue(r.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertEquals(setOf("html","title","nav","name","extra"),r.nodes.mapNotNull { it.wicketId }.toSet())
        assertFalse(r.scopeComplete);assertFalse(r.markup!!.scopeComplete);assertTrue(r.nodes.all { it.template && !it.idRenameSafe })
        assertEquals(2,r.nodes.single { it.wicketId=="html" }.idLocations().size)
        assertTrue(r.markup.documentDiagnostics.isEmpty());assertTrue(r.markup.diagnostics.any { "extension slot" in it.message })
        val text=WicketReportFormatter().format(r);assertTrue(text.contains("base-layout declaration template"));assertTrue(text.contains("MATCHED / INFERRED"));assertFalse(text.contains("UNRESOLVED_CONSTRUCTOR"))
    }
    fun testDifferentConstructorPathsAreWithheld() { val j=setup();change(j,"super(model); add(new Html(\"html\"))","super(model); add(new Html(\"other\"))");assertNull(report(j).baseLayoutTemplate) }
    fun testUnsafeAlternateConstructorIsWithheld() { val j=setup();change(j,"super(model); add(new Html(\"html\"));","super(model); add(new Html(\"html\")); removeAll();");assertNull(report(j).baseLayoutTemplate) }
    fun testOpaqueConstructorArgumentsCannotBecomeKnownModels() { val j=setup();change(j,"super(model); add(new Html(\"html\"));","super(model); if(model!=null) add(new Html(\"html\"));");assertNull(report(j).baseLayoutTemplate) }
    fun testSamePathWithDifferentConstructorTypesIsWithheld() { val j=setup();change(j,"super(model); add(new Html(\"html\"));","super(model); add(new WebMarkupContainer(\"html\"));");assertNull(report(j).baseLayoutTemplate) }
    fun testOpaqueConstructorModelCallbackIsNotEvaluated() { val j=setup();change(j,"super(model); add(new Html(\"html\"));","super(model); model.getObject(); add(new Html(\"html\"));");assertNull(report(j).baseLayoutTemplate) }
    fun testOpaqueConstructorInputCannotEscapeIntoDataHelpers() { val j=setup();change(j,"super(model); add(new Html(\"html\"));","super(model); java.util.Objects.requireNonNull(model); add(new Html(\"html\"));");assertNull(report(j).baseLayoutTemplate) }
    fun testUnknownEntryParameterTypesAreWithheld() { val j=setup();change(j,"Shell(IModel<?> model)","Shell(Object model)");assertNull(report(j).baseLayoutTemplate) }
    fun testUnavailableAbstractHookIsWithheld() { val j=setup();change(j,"protected org.apache.wicket.Component extra(String id) { return new WebMarkupContainer(id).setVisible(false); }","protected abstract org.apache.wicket.Component extra(String id);");assertNull(report(j).baseLayoutTemplate) }
    fun testAbstractModelHookWithholdsTheBaseButASelectedConcreteOverrideResolves() {
        val base=setup()
        change(base,"protected IModel<String> title() { return Model.of(\"App\"); }","protected abstract IModel<String> title();")
        val abstractReport=report(base)
        assertNull(abstractReport.baseLayoutTemplate)
        assertTrue(abstractReport.warnings.any { "Base-layout template withheld" in it.message && "helper body is unavailable" in it.message })
        val concrete=myFixture.addFileToProject("src/main/java/example/ExamplePage.java","package example; public class ExamplePage extends Shell { @Override protected org.apache.wicket.model.IModel<String> title() { return org.apache.wicket.model.Model.of(\"Concrete\"); } }") as PsiJavaFile
        companion(html="<wicket:extend></wicket:extend>")
        val resolved=report(concrete)
        assertEquals(5,resolved.matching!!.findings.count { it.code==DiagnosticCode.MATCHED })
        assertTrue(resolved.nodes.any { it.wicketId=="title" })
    }
    fun testMultipleOpenSlotsDoNotAcquireACommonShell() { val j=setup();val html=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/resources/example/Shell.html"))!!;change(html,"<wicket:child/>","<wicket:child/><wicket:child/>");assertNull(report(j).baseLayoutTemplate) }
    fun testChildSlotWithContentDoesNotAcquireACommonShell() { val j=setup();val html=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/resources/example/Shell.html"))!!;change(html,"<wicket:child/>","<wicket:child>unreviewed</wicket:child>");assertNull(report(j).baseLayoutTemplate) }
    fun testExtensionSlotInsideHeadIsNotAccepted() { val j=setup();val html=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/resources/example/Shell.html"))!!;change(html,"</head>","<wicket:child/></head>");change(html,"<main><wicket:child/></main>","<main></main>");assertNull(report(j).baseLayoutTemplate) }
    fun testConcreteSubclassKeepsItsOrdinarySelectedOverrideGraph() {
        setup();val java=myFixture.addFileToProject("src/main/java/example/ExamplePage.java","package example; public class ExamplePage extends Shell { @Override protected org.apache.wicket.Component extra(String id) { return new org.apache.wicket.markup.html.basic.Label(id,\"Concrete\"); } }") as PsiJavaFile
        companion(html="<wicket:extend></wicket:extend>");val r=report(java)
        assertNull(r.baseLayoutTemplate);assertEquals(5,r.matching!!.findings.count { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.CONFIRMED })
        assertEquals("org.apache.wicket.markup.html.basic.Label",r.nodes.single { it.wicketId=="extra" }.componentType)
    }
    fun testConstructorBoundPublishesNoTemplate()=bound(WicketBaseLayoutAnalyzer.Limits(constructors=2))
    fun testStepBoundPublishesNoTemplate()=bound(WicketBaseLayoutAnalyzer.Limits(steps=2))
    fun testNodeBoundPublishesNoTemplate()=bound(WicketBaseLayoutAnalyzer.Limits(nodes=2))
    fun testSourceBoundPublishesNoTemplate()=bound(WicketBaseLayoutAnalyzer.Limits(sources=1))
    private fun bound(limits:WicketBaseLayoutAnalyzer.Limits) { val j=setup();val type=j.classes.single();val r=WicketBaseLayoutAnalyzer(limits).analyze(type,WicketJavaAnalyzer().analyze(type)!!);assertNull(r.baseLayoutTemplate);assertTrue(r.warnings.any { "withheld" in it.message }) }
    fun testCancellationPropagates() { val j=setup();val type=j.classes.single();val initial=WicketJavaAnalyzer().analyze(type)!!;val indicator=EmptyProgressIndicator();assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketBaseLayoutAnalyzer().analyze(type,initial) },indicator) } }
}
