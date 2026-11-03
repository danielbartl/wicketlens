package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.routes.*
import java.nio.file.Files
import java.nio.file.Path

class WicketMountedMapperTest : WicketProjectPsiTestCase() {
    private val mapper = "org.apache.wicket.core.request.mapper.MountedMapper"
    private fun source(name: String, body: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $body")
    private fun app(body: String, members: String = "", name: String = "App") = source(name, """
        public class $name extends org.apache.wicket.protocol.http.WebApplication {
            public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            protected void init() { super.init(); $body }
            $members
        }
    """)
    private fun capture() = WicketRoutesRequest().capture(project)
    private fun mounts() = capture().routes.applications.flatMap { it.mounts }

    fun testInlineMapperRetainsPageHtmlAndActualRegistration() {
        val page = pageFile(); val html = companion()
        val application = app("mount(new $mapper(\"/people\", ExamplePage.class));")
        val mount = mounts().single()
        assertEquals("/people", mount.path)
        assertEquals("example.ExamplePage", mount.pageClass)
        assertEquals(MountStatus.DECLARED, mount.status)
        assertEquals(page.virtualFile.path, mount.pageSource!!.file)
        assertEquals(html.virtualFile.path, mount.markupSource!!.file)
        assertTrue(mount.analyzablePage)
        assertEquals(application.virtualFile.path, mount.registration.file)
        assertEquals("mount", application.findElementAt(mount.registration.offset)!!.text)
        assertTrue(mount.notes.any { it.contains("Inline MountedMapper") })
    }

    fun testExplicitEncoderPreservesPathAndPageWithoutClaimingParameterSemantics() {
        pageFile(); app("mount(new $mapper(\"/people\", ExamplePage.class, new org.apache.wicket.request.mapper.parameter.PageParametersEncoder()));")
        val mount = mounts().single()
        assertEquals(MountStatus.DECLARED, mount.status)
        assertEquals("/people", mount.path)
        assertTrue(mount.notes.any { it.contains("encoder is not analyzed") })
    }

    fun testSupplierIsNotExecutedOrTreatedAsAClassLiteral() {
        pageFile(); app("mount(new $mapper(\"/people\", () -> ExamplePage.class));")
        val mount = mounts().single()
        assertEquals("/people", mount.path)
        assertNull(mount.pageClass)
        assertNull(mount.pageSource)
        assertFalse(mount.analyzablePage)
        assertEquals(MountStatus.UNRESOLVED, mount.status)
        assertTrue(mount.notes.any { it.contains("class literal") })
    }

    fun testSupplierWithEncoderRetainsBothUncertaintyReasons() {
        pageFile(); app("mount(new $mapper(\"/people\", () -> ExamplePage.class, new org.apache.wicket.request.mapper.parameter.PageParametersEncoder()));")
        val mount = mounts().single()
        assertEquals(MountStatus.UNRESOLVED, mount.status)
        assertTrue(mount.notes.any { it.contains("encoder") })
        assertNull(mount.pageClass)
    }

    fun testDynamicPathKeepsPageNavigation() {
        pageFile(); app("mount(new $mapper(System.getProperty(\"route\"), ExamplePage.class));")
        val mount = mounts().single()
        assertNull(mount.path)
        assertNotNull(mount.pageSource)
        assertEquals(MountStatus.UNRESOLVED, mount.status)
    }

    fun testDynamicPageKeepsKnownPath() {
        pageFile(); app("mount(new $mapper(\"/people\", target()));", "private Class<ExamplePage> target() { return ExamplePage.class; }")
        val mount = mounts().single()
        assertEquals("/people", mount.path)
        assertNull(mount.pageClass)
        assertEquals(MountStatus.UNRESOLVED, mount.status)
    }

    fun testAnonymousOverrideKeepsConstructorFactsButIsUnresolved() {
        pageFile(); app("""
            mount(new $mapper("/people", ExamplePage.class) {
                @Override public int getCompatibilityScore(org.apache.wicket.request.Request request) { return 0; }
            });
        """)
        val mount = mounts().single()
        assertEquals("/people", mount.path)
        assertEquals("example.ExamplePage", mount.pageClass)
        assertEquals(MountStatus.UNRESOLVED, mount.status)
        assertTrue(mount.notes.any { it.contains("Anonymous MountedMapper") })
    }

    fun testAnonymousInitializerDoesNotInventNestedRegistrations() {
        pageFile(); app("mount(new $mapper(\"/outer\", ExamplePage.class) {{ mountPage(\"/hidden\", ExamplePage.class); }});")
        assertEquals(listOf("/outer"), mounts().map { it.path })
        assertEquals(MountStatus.UNRESOLVED, mounts().single().status)
    }

    fun testInvalidConstructorCannotConfirmKnownArguments() {
        pageFile(); app("mount(new $mapper(\"/people\", ExamplePage.class, 42));")
        val mount = mounts().single()
        assertEquals("/people", mount.path)
        assertEquals(MountStatus.UNRESOLVED, mount.status)
        assertTrue(mount.notes.any { it.contains("constructor is not fully resolved") })
    }

    fun testParenthesizedThisArgumentAndClassLiteral() {
        pageFile(); app("(this).mount((new $mapper(\"/people\", (ExamplePage.class))));")
        assertEquals(MountStatus.DECLARED, mounts().single().status)
    }

    fun testConditionalAndHelperCallerStatusPropagate() {
        pageFile(); app("if (System.getProperty(\"route\") != null) routes();", "private void routes() { mount(new $mapper(\"/people\", ExamplePage.class)); }")
        val mount = mounts().single()
        assertEquals(MountStatus.CONDITIONAL, mount.status)
        assertTrue(mount.notes.any { it.contains("example.App.routes") })
    }

    fun testRepeatedHelperCallsKeepDistinctObservationsAndSourceOrder() {
        pageFile(); app("routes(); mountPage(\"/direct\", ExamplePage.class); routes();", "private void routes() { mount(new $mapper(\"/mapper\", ExamplePage.class)); }")
        val routes = mounts()
        assertEquals(listOf("/mapper", "/direct", "/mapper"), routes.map { it.path })
        assertEquals(3, routes.map { it.id }.distinct().size)
        assertEquals(routes[0].registration, routes[2].registration)
    }

    fun testConstructingWithoutMountingDoesNotRegisterARoute() {
        pageFile(); app("new $mapper(\"/unused\", ExamplePage.class);")
        assertTrue(mounts().isEmpty())
    }

    fun testMapperVariablesAndFactoriesRemainOutsideReviewedSubset() {
        pageFile(); app("$mapper mapper = new $mapper(\"/local\", ExamplePage.class); mount(mapper); mount(create());",
            "private $mapper create() { return new $mapper(\"/factory\", ExamplePage.class); }")
        assertTrue(mounts().isEmpty())
        assertTrue(capture().routes.applications.single().notes.any { it.contains("mapper operations") })
    }

    fun testRootMapperMutationIsNotMistakenForApplicationMount() {
        pageFile(); app("getRootRequestMapperAsCompound().add(new $mapper(\"/root\", ExamplePage.class));")
        assertTrue(mounts().isEmpty())
        assertTrue(capture().routes.applications.single().notes.any { it.contains("mapper operations") })
    }

    fun testNamedSubclassDoesNotInheritAssumedMapperSemantics() {
        pageFile(); source("CustomMapper", "public class CustomMapper extends $mapper { public CustomMapper(String path, Class<ExamplePage> page) { super(path, page); } }")
        app("mount(new CustomMapper(\"/people\", ExamplePage.class));")
        assertTrue(mounts().isEmpty())
    }

    fun testAnotherReceiverDoesNotRegisterOnCurrentApplication() {
        pageFile(); app("other().mount(new $mapper(\"/people\", ExamplePage.class));", "private App other() { return this; }")
        assertTrue(mounts().isEmpty())
        assertTrue(capture().routes.applications.single().notes.any { it.contains("receiver") })
    }

    fun testOverriddenMountDoesNotAcquireFrameworkSemantics() {
        pageFile(); app("mount(new $mapper(\"/people\", ExamplePage.class));", "public void mount(org.apache.wicket.request.IRequestMapper mapper) {}")
        assertTrue(mounts().isEmpty())
    }

    fun testDeferredLambdaAndMethodReferenceCannotMountFromTheirBody() {
        pageFile(); app("Runnable later = () -> mount(new $mapper(\"/later\", ExamplePage.class)); java.util.function.Consumer<org.apache.wicket.request.IRequestMapper> ref = this::mount;")
        assertTrue(mounts().isEmpty())
    }

    fun testConstantDependencyEditsInvalidateAndUpdateMapperPaths() {
        pageFile(); val paths = source("Paths", "public final class Paths { public static final String ROOT = \"/old\"; }")
        app("mount(new $mapper(Paths.ROOT + \"/${'$'}{id}/#{optional}\", ExamplePage.class));")
        val request = WicketRoutesRequest(); val before = capture()
        assertTrue(paths.virtualFile.path in before.routes.sourceFiles)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(paths)!!.setText(paths.text.replace("/old", "/new")) }
        try { request.requireCurrent(project, before); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("/new/${'$'}{id}/#{optional}", mounts().single().path)
    }

    fun testMcpUsesExistingSchemaReadOnlyAndKeepsApplicationIsolation() {
        pageFile(); val first = app("mount(new $mapper(\"/one\", ExamplePage.class));", name = "FirstApp")
        app("mount(new $mapper(\"/two\", ExamplePage.class));", name = "SecondApp")
        val text = first.text
        val request = WicketRoutesRequest()
        val snapshot = request.capture(project, applicationClass = "example.FirstApp")
        val json = JsonParser.parseString(request.render(snapshot)).asJsonObject
        assertEquals(1, json["schemaVersion"].asInt)
        val routes = json["routes"].asJsonObject
        assertEquals(1, routes["schemaVersion"].asInt)
        assertFalse(routes["runtimeRoutesComplete"].asBoolean)
        val mount = routes["applications"].asJsonArray.single().asJsonObject["mounts"].asJsonArray.single().asJsonObject
        assertEquals("/one", mount["path"].asString)
        assertEquals(text, first.text)
        assertEquals(request.render(snapshot), request.render(request.capture(project, applicationClass = "example.FirstApp")))
    }

    fun testPortableDemoPreservesPlaceholdersAndEncoderNotes() {
        pageFile("CustomerPage"); companion("CustomerPage")
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        myFixture.addFileToProject("src/main/java/example/MountedRoutesApplication.java", Files.readString(root.resolve("main/java/example/MountedRoutesApplication.java")))
        val routes = mounts()
        assertEquals(listOf("/mapped-customers/${'$'}{id}", "/encoded-customers"), routes.map { it.path })
        assertTrue(routes.all { it.status == MountStatus.DECLARED })
        assertTrue(routes.last().notes.any { it.contains("encoder") })
    }
}
