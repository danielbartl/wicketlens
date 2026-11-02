package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.routes.*
import dev.wicketlens.model.SourceLocation
import java.nio.file.Files
import java.nio.file.Path

class WicketRoutesTest : WicketProjectPsiTestCase() {
    fun testDirectMountLinksApplicationPageHtmlAndRegistration() {
        val page = pageFile(); val html = companion()
        val app = application("mountPage(\"/customers\", ExamplePage.class);")
        val snapshot = capture()
        val owner = snapshot.routes.applications.single()
        val route = owner.mounts.single()
        assertEquals("example.App", owner.className)
        assertEquals(module.name, owner.moduleName)
        assertEquals("main", owner.sourceSet)
        assertEquals("/customers", route.path)
        assertEquals("example.ExamplePage", route.pageClass)
        assertEquals(MountStatus.DECLARED, route.status)
        assertEquals(page.virtualFile.path, route.pageSource!!.file)
        assertEquals(html.virtualFile.path, route.markupSource!!.file)
        assertEquals(app.virtualFile.path, route.registration.file)
        assertEquals("mountPage", app.findElementAt(route.registration.offset)!!.text)
        assertTrue(route.analyzablePage)
        assertFalse(snapshot.routes.truncated)
    }

    fun testRequiredAndOptionalPlaceholdersArePreservedLiterally() {
        pageFile()
        application("mountPage(\"/people/${'$'}{id}/#{optional}\", ExamplePage.class);")
        assertEquals("/people/${'$'}{id}/#{optional}", mounts().single().path)
    }

    fun testSourceConstantsAndConcatenationReuseTheReviewedEvaluator() {
        pageFile()
        val constants = source("Paths", "public final class Paths { public static final String PREFIX = \"/people\"; }")
        application("mountPage(Paths.PREFIX + \"/details\", ExamplePage.class);")
        val snapshot = capture()
        assertEquals("/people/details", snapshot.routes.applications.single().mounts.single().path)
        assertTrue(constants.virtualFile.path in snapshot.routes.sourceFiles)
    }

    fun testDynamicPathRetainsThePageAndUncertainty() {
        pageFile()
        application("mountPage(path(), ExamplePage.class);", members = "private String path() { return System.getProperty(\"route\"); }")
        val route = mounts().single()
        assertNull(route.path)
        assertEquals("example.ExamplePage", route.pageClass)
        assertNotNull(route.pageSource)
        assertEquals(MountStatus.UNRESOLVED, route.status)
    }

    fun testDynamicPageTargetRetainsThePathAndRegistration() {
        pageFile()
        application("mountPage(\"/people\", target());", members = "private Class<? extends org.apache.wicket.Page> target() { return ExamplePage.class; }")
        val route = mounts().single()
        assertEquals("/people", route.path)
        assertNull(route.pageClass)
        assertNull(route.pageSource)
        assertEquals(MountStatus.UNRESOLVED, route.status)
    }

    fun testConditionalMountDoesNotBecomeAnActiveRouteClaim() {
        pageFile()
        application("if (System.getProperty(\"route\") != null) mountPage(\"/people\", ExamplePage.class);")
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testKnownFalseConditionIsStillReportedAsAConditionalDeclaration() {
        pageFile(); application("if (false) mountPage(\"/people\", ExamplePage.class);")
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testEarlierGuardReturnMakesLaterMountConditional() {
        pageFile(); application("if (System.getProperty(\"route\") != null) return; mountPage(\"/people\", ExamplePage.class);")
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testLoopMountsAreConditionalAndNotExpandedIntoRuntimeInstances() {
        pageFile(); application("for (int i = 0; i < 3; i++) mountPage(\"/people\", ExamplePage.class);")
        assertEquals(1, mounts().size)
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testDeferredLambdasAreNotAttributedAsDirectMounts() {
        pageFile(); application("Runnable later = () -> mountPage(\"/people\", ExamplePage.class);")
        val snapshot = capture()
        assertTrue(snapshot.routes.applications.single().mounts.isEmpty())
        assertTrue(snapshot.routes.applications.single().notes.any { it.contains("lambda") })
    }

    fun testAnonymousBodiesAreNotAttributedAsDirectMounts() {
        pageFile(); application("new Object() { void later() { mountPage(\"/people\", ExamplePage.class); } };")
        assertTrue(mounts().isEmpty())
        assertTrue(capture().routes.applications.single().notes.any { it.contains("anonymous") })
    }

    fun testOtherApplicationReceiverIsNotMisattributed() {
        pageFile(); application("other().mountPage(\"/people\", ExamplePage.class);", members = "private App other() { return this; }")
        assertTrue(mounts().isEmpty())
        assertTrue(capture().routes.applications.single().notes.any { it.contains("receiver") })
    }

    fun testUnexpandedHelperDoesNotEraseDirectMountObservations() {
        pageFile(); application("configure(); mountPage(\"/people\", ExamplePage.class);",
            members = "private void configure() { mountPage(\"/hidden\", ExamplePage.class); }")
        assertEquals(listOf("/people"), mounts().map { it.path })
        assertTrue(capture().routes.applications.single().notes.any { it.contains("helpers") })
    }

    fun testCustomMapperOperationsKeepAnExplicitCoverageNote() {
        pageFile(); application("mount(new org.apache.wicket.core.request.mapper.MountedMapper(\"/hidden\", ExamplePage.class)); mountPage(\"/people\", ExamplePage.class);")
        assertEquals(listOf("/people"), mounts().map { it.path })
        assertTrue(capture().routes.applications.single().notes.any { it.contains("mapper") })
    }

    fun testLaterUnmountDoesNotClaimTheDeclaredMountIsActive() {
        pageFile(); application("mountPage(\"/people\", ExamplePage.class); unmount(\"/people\");")
        assertEquals(1, mounts().size)
        val json = JsonParser.parseString(WicketRoutesRequest().render(capture())).asJsonObject
        assertFalse(json["routes"].asJsonObject["runtimeRoutesComplete"].asBoolean)
        assertTrue(capture().routes.applications.single().notes.any { it.contains("runtime") })
    }

    fun testDuplicatePathDeclarationsAreRetainedWithoutInventedConflictDiagnostics() {
        pageFile(); application("mountPage(\"/people\", ExamplePage.class); mountPage(\"/people\", ExamplePage.class);")
        assertEquals(2, mounts().size)
        assertEquals(2, mounts().map { it.id }.distinct().size)
    }

    fun testTwoApplicationsStaySeparatelyGrouped() {
        pageFile(); application("mountPage(\"/one\", ExamplePage.class);", name = "FirstApp")
        application("mountPage(\"/two\", ExamplePage.class);", name = "SecondApp")
        assertEquals(listOf("example.FirstApp", "example.SecondApp"), capture().routes.applications.map { it.className })
        assertEquals(listOf("/one"), capture(application = "example.FirstApp").routes.applications.single().mounts.map { it.path })
    }

    fun testInheritedSetupHasCoverageRatherThanCopiedDirectRegistrations() {
        pageFile()
        source("BaseApp", """
            public abstract class BaseApp extends org.apache.wicket.protocol.http.WebApplication {
                @Override protected void init() { super.init(); mountPage("/base", ExamplePage.class); }
            }
        """)
        source("DerivedApp", """
            public class DerivedApp extends BaseApp {
                @Override public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            }
        """)
        val app = capture().routes.applications.single()
        assertTrue(app.mounts.isEmpty())
        assertTrue(app.notes.any { it.contains("Inherited") })
    }

    fun testNoApplicationIsAnEmptySuccessfulSnapshot() {
        pageFile(); assertTrue(capture().routes.applications.isEmpty())
    }

    fun testMissingApplicationFilterHasAnExplicitError() {
        failure("APPLICATION_NOT_FOUND") { capture(application = "example.Missing") }
    }

    fun testMissingModuleFilterHasAnExplicitError() {
        failure("MODULE_NOT_FOUND") { WicketRoutesRequest().capture(project, "missing") }
    }

    fun testInvalidScopeFiltersAreRejected() {
        for (set in listOf("", "production", "MAIN")) failure("INVALID_REQUEST") { WicketRoutesRequest().capture(project, sourceSet = set) }
        failure("INVALID_REQUEST") { capture(application = "../App") }
        failure("INVALID_REQUEST") { WicketRoutesRequest().capture(project, " ") }
    }

    fun testPlainWicketNeedsNoSpringForDiscovery() {
        pageFile(); application("mountPage(\"/plain\", ExamplePage.class);")
        assertEquals("/plain", mounts().single().path)
        assertFalse(mounts().single().notes.any { it.contains("Spring") })
    }

    fun testSourceSetFilterExcludesTestsByDefault() {
        com.intellij.testFramework.PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE)
        pageFile(); application("mountPage(\"/main\", ExamplePage.class);")
        myFixture.addFileToProject("src/test/java/example/TestApp.java", appText("TestApp", "mountPage(\"/test\", ExamplePage.class);"))
        assertEquals(listOf("example.App"), capture().routes.applications.map { it.className })
        assertEquals(listOf("example.TestApp"), WicketRoutesRequest().capture(project, sourceSet = "test").routes.applications.map { it.className })
        assertEquals(2, WicketRoutesRequest().capture(project, sourceSet = "any").routes.applications.size)
    }

    fun testApplicationLimitIsExplicitAndDeterministic() {
        pageFile()
        for (i in 0..64) application("", name = "App$i")
        val snapshot = capture()
        assertEquals(64, snapshot.routes.applications.size)
        assertTrue(snapshot.routes.truncated)
    }

    fun testTraversalOrMountLimitPreservesBoundedObservationsAndSignalsTruncation() {
        pageFile(); application((0..1000).joinToString("\n") { "mountPage(\"/p$it\", ExamplePage.class);" })
        val snapshot = capture()
        val mounts = snapshot.routes.applications.single().mounts
        assertTrue(mounts.size in 1..1000)
        assertEquals((0 until mounts.size).map { "/p$it" }, mounts.map { it.path })
        assertTrue(snapshot.routes.truncated)
    }

    fun testParenthesizedSelfReceiverAndPageLiteralKeepDirectNavigation() {
        pageFile(); application("(this).mountPage(\"/people\", (ExamplePage.class));")
        assertEquals(MountStatus.DECLARED, mounts().single().status)
        assertNotNull(mounts().single().pageSource)
    }

    fun testConditionalExpressionMountsKeepTheirReachabilityUncertainty() {
        pageFile(); application("Object mapper = System.getProperty(\"route\") != null ? mountPage(\"/people\", ExamplePage.class) : null;")
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testShortCircuitExpressionMountIsNotAnUnconditionalDeclaration() {
        pageFile(); application("boolean mounted = System.getProperty(\"route\") != null && mountPage(\"/people\", ExamplePage.class) != null;")
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testOversizedSyntheticApplicationAndMountSnapshotsAreRejected() {
        pageFile(); application("mountPage(\"/people\", ExamplePage.class);")
        val snapshot = capture(); val app = snapshot.routes.applications.single()
        failure("RESPONSE_TOO_LARGE") { WicketRoutesRequest().render(snapshot.copy(routes = snapshot.routes.copy(applications = List(65) { app }))) }
        val oversized = snapshot.routes.copy(applications = listOf(app.copy(mounts = List(1001) { app.mounts.single() })))
        failure("RESPONSE_TOO_LARGE") { WicketRoutesRequest().render(snapshot.copy(routes = oversized)) }
    }

    fun testMcpProjectionIsDeterministicAndReadOnly() {
        pageFile(); companion(); val app = application("mountPage(\"/people\", ExamplePage.class);")
        val before = app.text
        val request = WicketRoutesRequest()
        val snapshot = capture()
        val json = JsonParser.parseString(request.render(snapshot)).asJsonObject
        assertEquals("wicket_get_routes", json["tool"].asString)
        assertEquals("ok", json["status"].asString)
        assertFalse(json["truncated"].asBoolean)
        assertFalse(json["routes"].asJsonObject["runtimeRoutesComplete"].asBoolean)
        assertTrue(json["routes"].asJsonObject["applicationRelativePaths"].asBoolean)
        val mount = json["routes"].asJsonObject["applications"].asJsonArray.single().asJsonObject["mounts"].asJsonArray.single().asJsonObject
        assertEquals("/people", mount["path"].asString)
        assertEquals("DECLARED", mount["status"].asString)
        assertEquals(before, app.text)
        assertEquals(request.render(snapshot), request.render(capture()))
    }

    fun testUtf8ResponseBoundRejectsTheWholeJson() {
        pageFile(); application("mountPage(\"/people\", ExamplePage.class);")
        val snapshot = capture()
        val huge = snapshot.copy(routes = snapshot.routes.copy(notes = listOf("界".repeat(180_000))))
        failure("RESPONSE_TOO_LARGE") { WicketRoutesRequest().render(huge) }
    }

    fun testPendingEditorEditInvalidatesTheCapturedRouteSnapshot() {
        pageFile(); val app = application("mountPage(\"/old\", ExamplePage.class);")
        val request = WicketRoutesRequest(); val old = capture()
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(app)!!.setText(app.text.replace("/old", "/new")) }
        failure("STALE_SNAPSHOT") { request.requireCurrent(project, old) }
        failure("UNCOMMITTED_DOCUMENTS") { capture() }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("/new", mounts().single().path)
    }

    fun testExternalConstantDependencyEditInvalidatesTheSnapshot() {
        pageFile()
        val paths = source("Paths", "public final class Paths { public static final String PATH = \"/old\"; }")
        application("mountPage(Paths.PATH, ExamplePage.class);")
        val old = capture()
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(paths)!!.setText(paths.text.replace("/old", "/new")) }
        failure("STALE_SNAPSHOT") { WicketRoutesRequest().requireCurrent(project, old) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("/new", mounts().single().path)
    }

    fun testNewApplicationInvalidatesAnEmptyDiscoverySnapshot() {
        pageFile(); val old = capture()
        application("mountPage(\"/new\", ExamplePage.class);")
        failure("STALE_SNAPSHOT") { WicketRoutesRequest().requireCurrent(project, old) }
        assertEquals(1, capture().routes.applications.size)
    }

    fun testCancellationIsNotConvertedIntoPartialRouteSuccess() {
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable { indicator.cancel(); capture() }, indicator)
        }
    }

    fun testPortableRoutesDemoHasTwoMountsAndPreservesParameterPattern() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        pageFile("CustomerPage"); companion("CustomerPage")
        sourceFile("RoutesApplication", Files.readString(root.resolve("main/java/example/RoutesApplication.java")))
        val routes = mounts()
        assertEquals(listOf("/customers", "/customers/${'$'}{id}"), routes.map { it.path })
        assertTrue(routes.all { it.status == MountStatus.DECLARED })
    }

    private fun capture(application: String? = null) = WicketRoutesRequest().capture(project, applicationClass = application)
    private fun mounts() = capture().routes.applications.flatMap { it.mounts }
    private fun source(name: String, declaration: String) = sourceFile(name, "package example; $declaration")
    private fun sourceFile(name: String, text: String) = myFixture.addFileToProject("src/main/java/example/$name.java", text) as PsiJavaFile
    private fun application(body: String, name: String = "App", members: String = "") = sourceFile(name, appText(name, body, members))
    private fun appText(name: String, body: String, members: String = "") = """
        package example;
        public class $name extends org.apache.wicket.protocol.http.WebApplication {
            @Override public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            @Override protected void init() { super.init(); $body }
            $members
        }
    """.trimIndent()
    private fun failure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") }
        catch (failure: PageAnalysisFailure) { assertEquals(failure.message, code, failure.code) }
    }
}
