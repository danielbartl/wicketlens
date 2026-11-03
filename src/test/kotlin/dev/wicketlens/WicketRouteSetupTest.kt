package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.routes.*

class WicketRouteSetupTest : WicketProjectPsiTestCase() {
    private fun source(name: String, body: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $body")
    private fun app(init: String? = "super.init();", members: String = "", parent: String = "org.apache.wicket.protocol.http.WebApplication", name: String = "App") = source(name, """
        public class $name extends $parent {
            public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            ${init?.let { "protected void init() { $it }" } ?: ""}
            $members
        }
    """)
    private fun base(init: String = "super.init(); mountPage(\"/base\", ExamplePage.class);", members: String = "") = source("BaseApp", """
        public abstract class BaseApp extends org.apache.wicket.protocol.http.WebApplication {
            protected void init() { $init }
            $members
        }
    """)
    private fun capture(name: String? = null) = WicketRoutesRequest().capture(project, applicationClass = name)
    private fun mounts() = capture().routes.applications.flatMap { it.mounts }
    private fun paths() = mounts().map { it.path }
    private fun notes() = capture().routes.applications.flatMap { it.notes }

    fun testInheritedInitOpensTheActualAncestorRegistrationAndPage() {
        val page = pageFile(); companion(); val parent = base(); app(init = null, parent = "BaseApp")
        val snapshot = capture(); val mount = snapshot.routes.applications.single().mounts.single()
        assertEquals("/base", mount.path); assertEquals(MountStatus.DECLARED, mount.status)
        assertEquals(parent.virtualFile.path, mount.registration.file)
        assertEquals(page.virtualFile.path, mount.pageSource!!.file)
        assertTrue(mount.notes.any { it.contains("example.BaseApp.init") })
        assertTrue(parent.virtualFile.path in snapshot.routes.sourceFiles)
    }

    fun testOverrideWithoutSuperDoesNotInventBaseRegistrations() {
        pageFile(); base(); app("mountPage(\"/own\", ExamplePage.class);", parent = "BaseApp")
        assertEquals(listOf("/own"), paths())
    }

    fun testExplicitSuperIsExpandedAtItsCallSiteInDeclarationOrder() {
        pageFile(); base()
        app("mountPage(\"/before\", ExamplePage.class); super.init(); mountPage(\"/after\", ExamplePage.class);", parent = "BaseApp")
        assertEquals(listOf("/before", "/base", "/after"), paths())
    }

    fun testGrandparentInitIsReachedThroughAnIntermediateClassWithoutAnOverride() {
        pageFile(); base()
        source("MiddleApp", "public abstract class MiddleApp extends BaseApp {}")
        app(parent = "MiddleApp")
        assertEquals(listOf("/base"), paths())
    }

    fun testSeveralSourceSuperCallsPreserveTheirOwnOrdering() {
        pageFile(); base()
        source("MiddleApp", """public abstract class MiddleApp extends BaseApp {
            protected void init() { mountPage("/middle", ExamplePage.class); super.init(); }
        }""")
        app("super.init(); mountPage(\"/leaf\", ExamplePage.class);", parent = "MiddleApp")
        assertEquals(listOf("/middle", "/base", "/leaf"), paths())
    }

    fun testPrivateNoArgumentHelperIsExpandedWithItsSetupPath() {
        pageFile(); app("super.init(); routes();", "private void routes() { mountPage(\"/helper\", ExamplePage.class); }")
        val mount = mounts().single()
        assertEquals("/helper", mount.path)
        assertTrue(mount.notes.any { it.contains("example.App.init") && it.contains("example.App.routes") })
    }

    fun testFinalInheritedHelperCanBeCalledFromTheLeaf() {
        pageFile(); base(init = "super.init();", members = "protected final void routes() { mountPage(\"/final\", ExamplePage.class); }")
        app("super.init(); routes();", parent = "BaseApp")
        assertEquals(listOf("/final"), paths())
    }

    fun testPrivateBaseHelperDoesNotDispatchToASameNamedLeafMethod() {
        pageFile(); base("super.init(); routes();", "private void routes() { mountPage(\"/base\", ExamplePage.class); }")
        app(parent = "BaseApp", members = "private void routes() { mountPage(\"/leaf\", ExamplePage.class); }")
        assertEquals(listOf("/base"), paths())
    }

    fun testExplicitSuperHelperBypassesTheLeafOverride() {
        pageFile(); base("super.init();", "protected void routes() { mountPage(\"/base\", ExamplePage.class); }")
        app("super.init(); super.routes();", "protected void routes() { mountPage(\"/leaf\", ExamplePage.class); }", "BaseApp")
        assertEquals(listOf("/base"), paths())
    }

    fun testOpenVirtualHelperIsNotMistakenForItsBaseImplementation() {
        pageFile(); base("super.init(); routes();", "protected void routes() { mountPage(\"/base\", ExamplePage.class); }")
        app(parent = "BaseApp", members = "protected void routes() { mountPage(\"/leaf\", ExamplePage.class); }")
        assertTrue(mounts().isEmpty()); assertTrue(notes().any { it.contains("open/parameterized") })
    }

    fun testThisQualifiedPrivateHelpersAndClosedChainsAreExpanded() {
        pageFile(); app("super.init(); (this).routes();", """
            private void routes() { finalRoutes(); }
            private final void finalRoutes() { mountPage("/chain", ExamplePage.class); }
        """)
        assertEquals(listOf("/chain"), paths())
    }

    fun testUncalledHelperContributesNoRegistrations() {
        pageFile(); app(members = "private void routes() { mountPage(\"/hidden\", ExamplePage.class); }")
        assertTrue(mounts().isEmpty())
    }

    fun testParameterizedHelpersRemainOutsideTheReviewedContract() {
        pageFile(); app("super.init(); routes(\"/hidden\"); mountPage(\"/known\", ExamplePage.class);", "private void routes(String path) { mountPage(path, ExamplePage.class); }")
        assertEquals(listOf("/known"), paths()); assertTrue(notes().any { it.contains("parameterized") })
    }

    fun testHelpersOnOtherReceiversAreNotAttributedToThisApplication() {
        pageFile(); app("super.init(); other().routes();", """
            private App other() { return this; }
            private void routes() { mountPage("/hidden", ExamplePage.class); }
        """)
        assertTrue(mounts().isEmpty()); assertTrue(notes().any { it.contains("not expanded") })
    }

    fun testStaticAndValueReturningHelpersAreNotExpanded() {
        pageFile(); app("super.init(); configure(); value();", """
            private static void configure() { }
            private Object value() { mountPage("/hidden", ExamplePage.class); return null; }
        """)
        assertTrue(mounts().isEmpty()); assertTrue(notes().any { it.contains("not expanded") })
    }

    fun testMethodReferencesKeepDeferredCoverageInsteadOfCallingTheHelper() {
        pageFile(); app("super.init(); Runnable later = this::routes;", "private void routes() { mountPage(\"/hidden\", ExamplePage.class); }")
        assertTrue(mounts().isEmpty()); assertTrue(notes().any { it.contains("method-reference") })
    }

    fun testLambdasDoNotEagerlyInvokeClosedHelpers() {
        pageFile(); app("super.init(); Runnable later = () -> routes();", "private void routes() { mountPage(\"/hidden\", ExamplePage.class); }")
        assertTrue(mounts().isEmpty()); assertTrue(notes().any { it.contains("lambda") })
    }

    fun testConditionalCallerPropagatesReachabilityThroughEveryHelper() {
        pageFile(); app("super.init(); if (System.getProperty(\"routes\") != null) routes();", """
            private void routes() { inner(); }
            private void inner() { mountPage("/conditional", ExamplePage.class); }
        """)
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testConditionalSuperInitKeepsInheritedMountsConditional() {
        pageFile(); base(); app("if (System.getProperty(\"routes\") != null) super.init();", parent = "BaseApp")
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testHelperOwnGuardPreservesConditionalReachability() {
        pageFile(); app("super.init(); routes();", "private void routes() { if (false) mountPage(\"/guarded\", ExamplePage.class); }")
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testEarlierReturnGuardAlsoPropagatesIntoTheHelper() {
        pageFile(); app("super.init(); if (System.getProperty(\"routes\") != null) return; routes();", "private void routes() { mountPage(\"/guarded\", ExamplePage.class); }")
        assertEquals(MountStatus.CONDITIONAL, mounts().single().status)
    }

    fun testRepeatedHelperCallsHaveDistinctDeterministicObservationIds() {
        pageFile(); app("super.init(); routes(); routes();", "private void routes() { mountPage(\"/same\", ExamplePage.class); }")
        val rows = mounts()
        assertEquals(listOf("/same", "/same"), rows.map { it.path })
        assertEquals(2, rows.map { it.id }.distinct().size)
        assertEquals(rows, mounts())
        assertEquals(1, rows.map { it.registration }.distinct().size)
    }

    fun testEachHelperInvocationKeepsItsOwnConditionalContext() {
        pageFile(); app("super.init(); routes(); if (false) routes(); routes();", "private void routes() { mountPage(\"/same\", ExamplePage.class); }")
        assertEquals(listOf(MountStatus.DECLARED, MountStatus.CONDITIONAL, MountStatus.CONDITIONAL), mounts().map { it.status })
    }

    fun testSetupInvocationBudgetIsPerApplication() {
        pageFile()
        app("super.init(); " + "routes();".repeat(140), "private void routes() { mountPage(\"/bounded\", ExamplePage.class); }", name = "FirstApp")
        app("super.init(); routes();", "private void routes() { mountPage(\"/other\", ExamplePage.class); }", name = "OtherApp")
        val snapshot = capture(); val other = snapshot.routes.applications.single { it.className == "example.OtherApp" }
        assertTrue(snapshot.routes.truncated); assertEquals("/other", other.mounts.single().path)
        assertFalse(other.notes.any { it.contains("invocation limit") })
    }

    fun testSiblingApplicationsInheritingOneInitStaySeparatelyGrouped() {
        pageFile(); base(); app(init = null, parent = "BaseApp", name = "FirstApp"); app(init = null, parent = "BaseApp", name = "OtherApp")
        val applications = capture().routes.applications
        assertEquals(listOf("example.FirstApp", "example.OtherApp"), applications.map { it.className })
        assertTrue(applications.all { it.mounts.single().path == "/base" })
        assertEquals(2, applications.flatMap { it.mounts }.map { it.id }.distinct().size)
        assertEquals(1, capture("example.FirstApp").routes.applications.size)
    }

    fun testRecursiveHelpersKeepBoundedObservationsAndCoverage() {
        pageFile(); app("super.init(); routes(); mountPage(\"/after\", ExamplePage.class);", "private void routes() { mountPage(\"/before\", ExamplePage.class); routes(); }")
        assertEquals(listOf("/before", "/after"), paths()); assertTrue(notes().any { it.contains("Recursive") })
    }

    fun testMutualRecursionIsNotExpandedIndefinitely() {
        pageFile(); app("super.init(); first();", """
            private void first() { second(); }
            private void second() { mountPage("/once", ExamplePage.class); first(); }
        """)
        assertEquals(listOf("/once"), paths()); assertTrue(notes().any { it.contains("Recursive") })
    }

    fun testHelperDepthLimitIsExplicitAndPreservesOtherDirectDeclarations() {
        pageFile(); val helpers = (0..9).joinToString("\n") { n -> "private void helper$n() { ${if (n < 9) "helper${n+1}();" else "mountPage(\"/deep\", ExamplePage.class);"} }" }
        app("super.init(); helper0(); mountPage(\"/known\", ExamplePage.class);", helpers)
        assertEquals(listOf("/known"), paths()); assertTrue(capture().routes.truncated)
        assertTrue(notes().any { it.contains("depth or invocation limit") })
    }

    fun testRepeatedInvocationBudgetDoesNotExpandAnExponentialCallTree() {
        pageFile(); app("super.init(); " + "routes();".repeat(140), "private void routes() { mountPage(\"/same\", ExamplePage.class); }")
        val snapshot = capture(); val rows = snapshot.routes.applications.single().mounts
        assertTrue(snapshot.routes.truncated); assertTrue(rows.size in 1..128)
        assertEquals(rows.size, rows.map { it.id }.distinct().size)
    }

    fun testAncestorHelperEditInvalidatesTheSnapshotAndUpdatesMcpFacts() {
        pageFile(); val parent = base("super.init(); routes();", "private void routes() { mountPage(\"/old\", ExamplePage.class); }")
        app(init = null, parent = "BaseApp"); val request = WicketRoutesRequest(); val old = capture()
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(parent)!!.setText(parent.text.replace("/old", "/new")) }
        try { request.requireCurrent(project, old); fail("Expected stale ancestor") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val json = JsonParser.parseString(request.render(capture())).asJsonObject
        assertEquals(1, json["schemaVersion"].asInt); assertFalse(json["routes"].asJsonObject["runtimeRoutesComplete"].asBoolean)
        val row = json["routes"].asJsonObject["applications"].asJsonArray.single().asJsonObject["mounts"].asJsonArray.single().asJsonObject
        assertEquals("/new", row["path"].asString)
        assertEquals(parent.virtualFile.path, row["registration"].asJsonObject["file"].asString)
        assertTrue(row["notes"].asJsonArray.any { it.asString.contains("BaseApp.routes") })
    }

    fun testSetupExpansionRemainsReadOnlyAndRetainsUtf8ResponseBounds() {
        pageFile(); val parent = base(); val leaf = app(init = null, parent = "BaseApp")
        val before = listOf(parent.text, leaf.text); val request = WicketRoutesRequest(); val snapshot = capture()
        assertEquals(request.render(snapshot), request.render(capture()))
        assertEquals(before, listOf(parent.text, leaf.text))
        val oversized = snapshot.copy(routes = snapshot.routes.copy(notes = listOf("界".repeat(180_000))))
        try { request.render(oversized); fail("Expected byte bound") }
        catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
    }

    fun testCancellationIsNotPublishedAsPartialInheritedDiscovery() {
        pageFile(); base(); app(init = null, parent = "BaseApp")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable { indicator.cancel(); capture() }, indicator)
        }
    }

    fun testSourceHierarchyLimitDoesNotEraseTheLeafDeclaration() {
        pageFile()
        for (n in 0..13) source("Base$n", """public abstract class Base$n extends ${if (n == 0) "org.apache.wicket.protocol.http.WebApplication" else "Base${n-1}"} {
            ${if (n == 0) "protected void init() { mountPage(\"/deep\", ExamplePage.class); }" else ""}
        }""")
        app("super.init(); mountPage(\"/leaf\", ExamplePage.class);", parent = "Base13")
        assertEquals(listOf("/leaf"), paths()); assertTrue(capture().routes.truncated)
        assertTrue(notes().any { it.contains("hierarchy limit") })
    }

    fun testHelperSourceErrorsDoNotConfirmItsMountOrEraseASeparateLeafDeclaration() {
        pageFile(); base("super.init();", """
            protected final void routes() { mountPage("/uncertain", ExamplePage.class); }
            private int broken = ;
        """)
        app("super.init(); routes(); mountPage(\"/leaf\", ExamplePage.class);", parent = "BaseApp")
        val rows = mounts()
        assertEquals(MountStatus.UNRESOLVED, rows.single { it.path == "/uncertain" }.status)
        assertEquals(MountStatus.DECLARED, rows.single { it.path == "/leaf" }.status)
    }

    fun testDynamicHelperMountRetainsItsPageAndRegistrationWithoutGuessingAPath() {
        pageFile(); app("super.init(); routes();", "private void routes() { mountPage(System.getProperty(\"path\"), ExamplePage.class); }")
        val row = mounts().single()
        assertNull(row.path); assertEquals(MountStatus.UNRESOLVED, row.status)
        assertEquals("example.ExamplePage", row.pageClass); assertNotNull(row.pageSource)
    }

    fun testConstantsUsedByInheritedHelpersJoinTheSnapshotDependencies() {
        pageFile(); val constants = source("Paths", "public class Paths { public static final String ROOT = \"/known\"; }")
        base("super.init(); routes();", "private void routes() { mountPage(Paths.ROOT + \"/child\", ExamplePage.class); }")
        app(init = null, parent = "BaseApp")
        val snapshot = capture()
        assertEquals("/known/child", snapshot.routes.applications.single().mounts.single().path)
        assertTrue(constants.virtualFile.path in snapshot.routes.sourceFiles)
    }

    fun testTestApplicationCanInheritProductionSetupWithoutEnteringTheDefaultScan() {
        pageFile(); base()
        com.intellij.testFramework.PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE)
        myFixture.addFileToProject("src/test/java/example/TestApp.java", """
            package example; public class TestApp extends BaseApp {
                public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            }
        """)
        assertTrue(capture().routes.applications.isEmpty())
        val selected = WicketRoutesRequest().capture(project, sourceSet = "test").routes.applications.single()
        assertEquals("test", selected.sourceSet); assertEquals("/base", selected.mounts.single().path)
    }

    fun testPortableInheritedRouteDemoUsesTheSharedDiscovery() {
        pageFile("CustomerPage"); companion("CustomerPage")
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        myFixture.addFileToProject("src/main/java/example/InheritedRoutesApplication.java", java.nio.file.Files.readString(root.resolve("main/java/example/InheritedRoutesApplication.java")))
        val row = capture("example.InheritedRoutesApplication").routes.applications.single().mounts.single()
        assertEquals("/inherited-customers", row.path); assertEquals(MountStatus.DECLARED, row.status)
        assertTrue(row.notes.any { it.contains("BaseRoutesApplication.mountCustomers") })
    }
}
