package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.mcp.*
import dev.wicketlens.spring.*
import dev.wicketlens.testing.WicketTestGenerator
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketTestPlanningTest : WicketProjectPsiTestCase() {
    private val query = WicketPageAnalysisRequest()
    private val view = WicketMcpReport.TEST_PLAN
    private fun capture(name: String = "ExamplePage", sourceSet: String = "main") = query.capture(project,"example.$name",sourceSet=sourceSet,view=view)
    private fun render(snapshot: PageAnalysisSnapshot = capture()) = JsonParser.parseString(query.render(snapshot,view)).asJsonObject
    private fun data(snapshot: PageAnalysisSnapshot = capture()) = render(snapshot)["testPlan"].asJsonObject
    private fun failure(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (e: PageAnalysisFailure) { assertEquals(code,e.code) }
    }
    fun testPlainPlanWithoutSpringUsesSharedRendererAndNoMockingOrSpringDependencies() {
        val file = pageFile(); companion()
        val snapshot = capture();val prepared = snapshot.testPlan!!
        assertEquals(WicketTestGenerator().prepare(project,file.virtualFile.url,-1).plan,prepared.plan)
        assertFalse(prepared.plan.spring)
        val result = render(snapshot);val plan = data(snapshot)
        assertEquals(view.tool,result["tool"].asString)
        assertEquals("isolated_wicket_tester_scaffold",plan["kind"].asString)
        assertEquals("ExamplePageTest",plan["suggestedTestName"].asString)
        assertEquals("PAGE",plan["constructorStrategy"].asString)
        assertEquals(WicketTestGenerator.REQUIRED_DEPENDENCIES,plan["requiredDependencies"].asJsonArray.map { it.asString })
        val source = plan["source"].asString
        assertEquals(WicketSpringTestGenerator().render(prepared.plan),source)
        assertFalse(source.contains("org.springframework"));assertFalse(source.contains("org.mockito"));assertFalse(source.contains("SpringComponentInjector"))
        assertTrue(source.contains("tester.assertRenderedPage(example.ExamplePage.class)"))
        assertTrue(source.contains("if (tester != null) tester.destroy()"))
        assertFalse(plan["runtimeValidityVerified"].asBoolean)
        assertFalse(plan["dependencyAvailabilityChecked"].asBoolean)
    }
    fun testPageParametersAndPanelConstructorsUseTheSharedStrategies() {
        myFixture.addFileToProject("src/main/java/example/Parameters.java","package example; public class Parameters extends org.apache.wicket.markup.html.WebPage { public Parameters(org.apache.wicket.request.mapper.parameter.PageParameters p){super(p);} }")
        myFixture.addFileToProject("src/main/java/example/Details.java","package example; public class Details extends org.apache.wicket.markup.html.panel.Panel { public Details(String id){super(id);} }")
        assertEquals("PARAMETERS",data(capture("Parameters"))["constructorStrategy"].asString)
        assertTrue(data(capture("Details"))["source"].asString.contains("tester.startComponentInPage(new example.Details(\"subject\"))"))
    }
    fun testManualPageAndComponentArgumentsRetainThrowingHooks() {
        for ((name,base,args,strategy) in listOf(
            listOf("ManualPage","org.apache.wicket.markup.html.WebPage","int value","MANUAL_PAGE"),
            listOf("ManualPanel","org.apache.wicket.markup.html.panel.Panel","String id, int value","MANUAL_COMPONENT"))) {
            val parent=if(strategy=="MANUAL_COMPONENT") "super(id);" else ""
            myFixture.addFileToProject("src/main/java/example/$name.java","package example; public class $name extends $base { public $name($args){$parent} }")
            val plan=data(capture(name));assertEquals(strategy,plan["constructorStrategy"].asString)
            assertTrue(plan["source"].asString.contains("throw new UnsupportedOperationException(\"Configure the subject constructor\")"))
        }
    }
    fun testUnsupportedSubjectsFailConservatively() {
        myFixture.addFileToProject("src/main/java/example/Generic.java","package example; public class Generic<T> extends org.apache.wicket.markup.html.WebPage {}")
        myFixture.addFileToProject("src/main/java/example/Abstract.java","package example; public abstract class Abstract extends org.apache.wicket.markup.html.WebPage {}")
        for(name in listOf("Generic","Abstract"))failure("TEST_PLAN_UNAVAILABLE"){capture(name)}
    }
    fun testPreviewAndCreationReuseExistingRootsCollisionGuardsAndUndo() {
        pageFile();val root=myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module,root,JavaSourceRootType.TEST_SOURCE)
        val prepared=capture().testPlan!!
        val preview=WicketSpringTestPreview(project,prepared)
        try { assertEquals("Generate Wicket Test",preview.title) } finally { preview.close(1) }
        val file=WicketSpringTestFiles.create(project,prepared,prepared.roots.single(),"ExamplePageTest")
        assertFalse(file.text.contains("org.springframework"))
        try{WicketSpringTestFiles.create(project,prepared,prepared.roots.single(),"ExamplePageTest");fail("Expected collision/staleness")}
        catch(_:dev.wicketlens.analysis.AnalysisUnavailable){}
        UndoManager.getInstance(project).undo(null)
        assertNull(root.findFileByRelativePath("example/ExamplePageTest.java"))
    }
    fun testSourceSetAndRootFreshnessArePreserved() {
        pageFile()
        PsiTestUtil.addSourceRoot(module,myFixture.tempDirFixture.findOrCreateDir("src/test/java"),JavaSourceRootType.TEST_SOURCE)
        myFixture.addFileToProject("src/test/java/example/ExamplePage.java","package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {public ExamplePage(int x){}}")
        assertEquals("MANUAL_PAGE",data(capture(sourceSet="test"))["constructorStrategy"].asString)
        failure("AMBIGUOUS_CLASS"){capture(sourceSet="any")}
        val old=capture();PsiTestUtil.addSourceRoot(module,myFixture.tempDirFixture.findOrCreateDir("other-tests"),JavaSourceRootType.TEST_SOURCE)
        failure("STALE_SNAPSHOT"){query.requireCurrent(project,old)}
    }
    fun testUncommittedChangesAndWholeSourceByteLimitAreEnforced() {
        val file=pageFile();val captured=capture();val prepared=captured.testPlan!!
        failure("RESPONSE_TOO_LARGE"){render(captured.copy(testPlan=prepared.copy(plan=prepared.plan.copy(notes=listOf("界".repeat(180_000))))))}
        WriteCommandAction.runWriteCommandAction(project){PsiDocumentManager.getInstance(project).getDocument(file)!!.insertString(0,"// edit\n")}
        failure("UNCOMMITTED_DOCUMENTS"){capture()};failure("STALE_SNAPSHOT"){query.requireCurrent(project,captured)}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testAsyncRequestIsReadOnlyAndLeavesSidebarStateAlone() {
        val file=pageFile();val html=companion();val before=file.text to html.text
        val state=project.service<WicketLensService>().state
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try{
            val job=scope.async{query.execute(project,"example.ExamplePage",view=view)}
            PlatformTestUtil.waitWithEventsDispatching("Plain test plan",{job.isCompleted},20)
            assertEquals("ok",JsonParser.parseString(runBlocking{job.await()}).asJsonObject["status"].asString)
        }finally{scope.cancel()}
        assertEquals(before,file.text to html.text);assertSame(state,project.service<WicketLensService>().state)
    }
}

class WicketGeneralSpringPlanningTest : WicketSpringInspectionTestCase() {
    fun testSpringClasspathAloneUsesPlainButExplicitIntentReusesTheSpringPlan() {
        val file=page("");val generator=WicketTestGenerator()
        assertFalse(generator.prepare(project,file.virtualFile.url,-1).plan.spring)
        edit(file,file.text.replace("public ExamplePage()","@SpringBean Service orders; public ExamplePage()"))
        assertEquals(WicketSpringTestGenerator().prepare(project,file.virtualFile.url,-1).plan,generator.prepare(project,file.virtualFile.url,-1).plan)
        val query=WicketPageAnalysisRequest()
        val general=query.capture(project,"example.ExamplePage",view=WicketMcpReport.TEST_PLAN)
        val specific=query.capture(project,"example.ExamplePage",view=WicketMcpReport.SPRING_TEST_PLAN)
        val a=JsonParser.parseString(query.render(general,WicketMcpReport.TEST_PLAN)).asJsonObject["testPlan"]
        val b=JsonParser.parseString(query.render(specific,WicketMcpReport.SPRING_TEST_PLAN)).asJsonObject["springTestPlan"]
        assertEquals(b,a)
    }
}
