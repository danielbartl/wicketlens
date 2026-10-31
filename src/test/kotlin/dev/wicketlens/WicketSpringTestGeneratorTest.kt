package dev.wicketlens

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.spring.*
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketSpringTestGeneratorTest : WicketSpringInspectionTestCase() {
    private val generator = WicketSpringTestGenerator()

    private fun orderPage(members: String = "@SpringBean(name = \"orders\") private OrderService orders;", body: String = "") : PsiJavaFile {
        source("OrderService", "public interface OrderService { String title(); }")
        return page(members, body)
    }
    private fun prepare(file: PsiJavaFile) = generator.prepare(project, file.virtualFile.url, -1)

    fun testNamedInterfaceProducesIsolatedMockAndCleanup() {
        val prepared = prepare(orderPage())
        assertEquals(listOf(SpringTestMock("orders", "example.OrderService")), prepared.plan.mocks)
        assertEmpty(prepared.plan.manual)
        val text = generator.render(prepared.plan)
        assertTrue(text.contains("registerSingleton(\"orders\", dependency1)"))
        assertTrue(text.indexOf("new SpringComponentInjector") < text.indexOf("tester.startPage(new example.ExamplePage())"))
        assertTrue(text.contains("if (tester != null) tester.destroy();"))
        assertTrue(text.contains("if (springContext != null) springContext.close();"))
        assertTrue(text.contains("tester.assertRenderedPage(example.ExamplePage.class);"))
        assertTrue(text.indexOf("configureMocks();") < text.indexOf("springContext.refresh();"))
    }

    fun testByTypeFieldsUseFieldNamesWithoutChoosingSourceCandidates() {
        val prepared = prepare(orderPage("@SpringBean private OrderService first; @SpringBean private OrderService second;"))
        assertEquals(listOf("first", "second"), prepared.plan.mocks.map { it.name })
        assertEmpty(prepared.plan.manual)
    }

    fun testRepeatedNamedFieldSharesOneMock() {
        val prepared = prepare(orderPage("@SpringBean(name=\"orders\") private OrderService first; @SpringBean(name=\"orders\") private OrderService second;"))
        assertEquals(1, prepared.plan.mocks.size)
    }

    fun testConflictingNamedTypesRequireManualBinding() {
        source("OtherService", "public interface OtherService {}")
        val prepared = prepare(orderPage("@SpringBean(name=\"orders\") OrderService first; @SpringBean(name=\"orders\") OtherService second;"))
        assertEmpty(prepared.plan.mocks)
        assertEquals(1, prepared.plan.manual.size)
        assertTrue(generator.render(prepared.plan).contains("throw new UnsupportedOperationException"))
    }

    fun testFinalTypeRequiresManualBindingRatherThanInvalidProxy() {
        val plan = prepare(orderPage("@SpringBean String title;")).plan
        assertEmpty(plan.mocks)
        assertEquals(1, plan.manual.size)
    }

    fun testSealedInterfacesAndClassesRequireManualProxyPolicy() {
        source("RestrictedService", "public sealed interface RestrictedService permits PermittedService {}")
        source("PermittedService", "public final class PermittedService implements RestrictedService {}")
        source("RestrictedBase", "public sealed class RestrictedBase permits PermittedBase {}")
        source("PermittedBase", "public final class PermittedBase extends RestrictedBase {}")
        val plan = prepare(orderPage("@SpringBean RestrictedService orders; @SpringBean RestrictedBase audit;")).plan
        assertEmpty(plan.mocks)
        assertEquals(2, plan.manual.size)
        assertTrue(plan.manual.all { it.contains("sealed dependency type") })
    }

    fun testGenericTypesRequireManualBinding() {
        val plan = prepare(orderPage("@SpringBean java.util.List<OrderService> orders;")).plan
        assertEmpty(plan.mocks)
        assertEquals(1, plan.manual.size)
    }

    fun testUnknownExplicitNameDoesNotFallBackToFieldName() {
        val plan = prepare(orderPage("static final String NAME = \"orders\"; @SpringBean(name=NAME) OrderService orders;")).plan
        assertEmpty(plan.mocks)
        assertEquals(1, plan.manual.size)
    }

    fun testInitializedFieldRequiresReview() {
        val plan = prepare(orderPage("@SpringBean OrderService orders = null;")).plan
        assertEmpty(plan.mocks)
        assertEquals(1, plan.manual.size)
    }

    fun testAutowiredIsNeverTurnedIntoAWicketBinding() {
        application()
        val plan = prepare(orderPage("@org.springframework.beans.factory.annotation.Autowired OrderService orders;")).plan
        assertEmpty(plan.mocks)
        assertEquals(1, plan.manual.size)
    }

    fun testPlainWicketAndClasspathAloneDoNotEnableGeneration() {
        val file = pageFile()
        assertThrows(AnalysisUnavailable::class.java) { prepare(file) }
    }

    fun testPageParametersConstructorIsUsed() {
        val file = source("ExamplePage", """
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
              @org.apache.wicket.spring.injection.annot.SpringBean Service service;
              public ExamplePage(org.apache.wicket.request.mapper.parameter.PageParameters parameters) { super(parameters); }
            }
        """.trimIndent())
        val plan = prepare(file).plan
        assertEquals(SpringTestConstructor.PARAMETERS, plan.constructor)
        assertTrue(generator.render(plan).contains("new example.ExamplePage(new org.apache.wicket.request.mapper.parameter.PageParameters())"))
    }

    fun testBusinessConstructorUsesExplicitManualHook() {
        val file = source("ExamplePage", """
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
              @org.apache.wicket.spring.injection.annot.SpringBean Service service;
              public ExamplePage(long account) {}
            }
        """.trimIndent())
        val plan = prepare(file).plan
        assertEquals(SpringTestConstructor.MANUAL_PAGE, plan.constructor)
        assertTrue(generator.render(plan).contains("private example.ExamplePage createSubject()"))
        assertFalse(generator.render(plan).contains("new example.ExamplePage(0"))
    }

    fun testPanelUsesComponentSmokeTest() {
        val file = source("ExamplePanel", """
            public class ExamplePanel extends org.apache.wicket.markup.html.panel.Panel {
              @org.apache.wicket.spring.injection.annot.SpringBean Service service;
              public ExamplePanel(String id) { super(id); }
            }
        """.trimIndent())
        val plan = prepare(file).plan
        assertEquals(SpringTestConstructor.COMPONENT, plan.constructor)
        assertTrue(generator.render(plan).contains("tester.startComponentInPage(new example.ExamplePanel(\"subject\"))"))
        assertTrue(generator.render(plan).contains("tester.assertComponent(\"subject\", example.ExamplePanel.class)"))
    }

    fun testAbstractPageIsRejected() {
        val file = orderPage()
        edit(file, file.text.replace("public class ExamplePage", "public abstract class ExamplePage"))
        assertThrows(AnalysisUnavailable::class.java) { prepare(file) }
    }

    fun testHtmlSelectionResolvesTheSameSubject() {
        val file = orderPage()
        val html = companion()
        assertEquals(prepare(file).plan, generator.prepare(project, html.virtualFile.url, -1).plan)
    }

    fun testLiteralBeanNamesAreEscapedAsJavaStrings() {
        val plan = prepare(orderPage()).plan.copy(mocks = listOf(SpringTestMock("a\"b\\c\n", "example.OrderService")))
        val text = generator.render(plan)
        assertTrue(text.contains("registerSingleton(\"a\\\"b\\\\c\\n\", dependency1)"))
        val parsed = PsiFileFactory.getInstance(project).createFileFromText("ExamplePageSpringTest.java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, text)
        assertEmpty(PsiTreeUtil.findChildrenOfType(parsed, PsiErrorElement::class.java))
    }

    fun testNoConfiguredTestRootStillAllowsCopyablePlan() {
        val prepared = prepare(orderPage())
        assertEmpty(prepared.roots)
        assertTrue(generator.render(prepared.plan).contains("class ExamplePageSpringTest"))
    }

    fun testNativePreviewWithoutTestRootPreservesNameAndDisablesCreation() {
        val prepared = prepare(orderPage())
        val preview = WicketSpringTestPreview(project, prepared)
        try {
            assertFalse(preview.isOKActionEnabled)
            assertEquals(prepared.plan.testName, (preview.getPreferredFocusedComponent() as com.intellij.ui.components.JBTextField).text)
        } finally { preview.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE) }
    }

    fun testNativePreviewUpdatesValidationAndCreateAvailability() {
        val file = orderPage()
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        val preview = WicketSpringTestPreview(project, prepare(file))
        try {
            assertTrue(preview.isOKActionEnabled)
            val field = preview.getPreferredFocusedComponent() as com.intellij.ui.components.JBTextField
            field.text = "WebApplication"
            assertFalse(preview.isOKActionEnabled)
            field.text = "OrdersSmokeTest"
            assertTrue(preview.isOKActionEnabled)
        } finally { preview.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE) }
    }

    fun testPackagePathBlockedByAFileIsReportedBeforeWriting() {
        val file = orderPage()
        val root = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        val obstruction = myFixture.addFileToProject("src/test/java/example", "existing data")
        val prepared = prepare(file)
        assertThrows(AnalysisUnavailable::class.java) { WicketSpringTestFiles.create(project, prepared, prepared.roots.single(), prepared.plan.testName) }
        assertEquals("existing data", obstruction.text)
        assertEquals(listOf("example"), root.children.map { it.name })
    }

    fun testCreationUsesExistingTestRootAndDoesNotOverwrite() {
        val file = orderPage()
        val root = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        val prepared = prepare(file)
        val created = WicketSpringTestFiles.create(project, prepared, prepared.roots.single(), "OrderSmokeTest")
        assertEquals("example", created.packageName)
        assertEquals("OrderSmokeTest", created.classes.single().name)
        assertTrue(created.text.contains("class OrderSmokeTest"))
        assertTrue(created.text.contains("registerSingleton(\"orders\", dependency1)"))
        assertEquals("example.OrderService", created.classes.single().findFieldByName("dependency1", false)!!.type.canonicalText)
        val fresh = prepare(file)
        assertThrows(AnalysisUnavailable::class.java) { WicketSpringTestFiles.create(project, fresh, fresh.roots.single(), "OrderSmokeTest") }
        assertEquals(created.text, root.findFileByRelativePath("example/OrderSmokeTest.java")!!.let { PsiManager.getInstance(project).findFile(it)!!.text })
    }

    fun testStalePlanDoesNotCreateDirectories() {
        val file = orderPage()
        val root = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        val prepared = prepare(file)
        edit(file, file.text + "\n// edited", commit = false)
        assertThrows(AnalysisUnavailable::class.java) { WicketSpringTestFiles.create(project, prepared, prepared.roots.single(), prepared.plan.testName) }
        assertNull(root.findChild("example"))
    }

    fun testPackagePrefixIsRespected() {
        val file = orderPage()
        val root = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.flatMap { it.sourceFolders.toList() }.single { it.file == root }.packagePrefix = "example"
        }
        val prepared = prepare(file)
        val created = WicketSpringTestFiles.create(project, prepared, prepared.roots.single(), prepared.plan.testName)
        assertEquals(root, created.virtualFile.parent)
    }

    fun testGeneratedRootsAreExcluded() {
        val file = orderPage()
        val root = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        ModuleRootModificationUtil.updateModel(module) { model ->
            val folder = model.contentEntries.flatMap { it.sourceFolders.toList() }.single { it.file == root }
            (folder.jpsElement.properties as org.jetbrains.jps.model.java.JavaSourceRootProperties).isForGeneratedSources = true
        }
        assertEmpty(prepare(file).roots)
    }

    fun testInvalidClassNameCannotEscapeTheTestRoot() {
        assertFalse(WicketSpringTestFiles.validName(project, "../Surprise"))
        assertFalse(WicketSpringTestFiles.validName(project, "class"))
        assertFalse(WicketSpringTestFiles.validName(project, "record"))
        assertFalse(WicketSpringTestFiles.validName(project, "WebApplication"))
        assertFalse(WicketSpringTestFiles.validName(project, "Override"))
        assertFalse(WicketSpringTestFiles.validName(project, "Class"))
        assertFalse(WicketSpringTestFiles.validName(project, "ExamplePage", "example.ExamplePage"))
        assertTrue(WicketSpringTestFiles.validName(project, "OrderSmokeTest"))
    }

    fun testNativeUndoRemovesCreatedTestAndPackageDirectory() {
        val file = orderPage()
        val root = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        val prepared = prepare(file)
        WicketSpringTestFiles.create(project, prepared, prepared.roots.single(), prepared.plan.testName)
        assertNotNull(root.findFileByRelativePath("example/${prepared.plan.testName}.java"))
        val undo = UndoManager.getInstance(project)
        assertTrue(undo.isUndoAvailable(null))
        val previous = com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.OK)
        try { undo.undo(null) } finally { com.intellij.openapi.ui.TestDialogManager.setTestDialog(previous) }
        assertNull(root.findChild("example"))
    }

    fun testFormComponentPanelUsesComponentConstructor() {
        val file = source("InputPanel", """
            public class InputPanel extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
                @org.apache.wicket.spring.injection.annot.SpringBean Service service;
                public InputPanel(String id) { super(id); }
            }
        """.trimIndent())
        assertEquals(SpringTestConstructor.COMPONENT, prepare(file).plan.constructor)
    }

    fun testSuperclassInjectionIsIncluded() {
        source("BasePage", """
            public abstract class BasePage extends org.apache.wicket.markup.html.WebPage {
                @org.apache.wicket.spring.injection.annot.SpringBean private Service service;
            }
        """.trimIndent())
        val file = source("ExamplePage", "public class ExamplePage extends BasePage {}")
        assertEquals(listOf(SpringTestMock("service", "example.Service")), prepare(file).plan.mocks)
    }

    fun testSyntaxErrorsAreRejectedBeforeCreatingAPreview() {
        val file = orderPage()
        edit(file, file.text.replace("private OrderService orders;", "private OrderService orders = ;"))
        assertThrows(AnalysisUnavailable::class.java) { prepare(file) }
    }

    fun testGeneratedPageAndPanelTestsCompileAndRunAgainstRealLibraries() {
        val page = orderPage(body = "add(new org.apache.wicket.markup.html.basic.Label(\"title\", orders.title()));")
        val pagePlan = prepare(page).plan
        val panel = source("ExamplePanel", """
            public class ExamplePanel extends org.apache.wicket.markup.html.panel.Panel {
                @org.apache.wicket.spring.injection.annot.SpringBean(name="orders") private OrderService orders;
                public ExamplePanel(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("title", orders.title())); }
            }
        """.trimIndent())
        val panelPlan = prepare(panel).plan
        val directory = Files.createTempDirectory("wicketlens-generated-tests").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "OrderService.java").writeText("package example; public interface OrderService { String title(); }")
            File(example, "ExamplePage.java").writeText(page.text.replace("public ExamplePage()", "public ExamplePage() throws java.io.IOException"))
            File(example, "ExamplePanel.java").writeText(panel.text)
            File(example, "${pagePlan.testName}.java").writeText(generator.render(pagePlan))
            File(example, "${panelPlan.testName}.java").writeText(generator.render(panelPlan))
            File(example, "ManualSpringTest.java").writeText(generator.render(pagePlan.copy(manual = listOf("Review the binding before running.")), "ManualSpringTest"))
            File(example, "ExamplePage.html").writeText("<html><body><span wicket:id=\"title\"></span></body></html>")
            File(example, "ExamplePanel.html").writeText("<wicket:panel><span wicket:id=\"title\"></span></wicket:panel>")
            val libraries = listOf("libraries", "springLibraries", "springTestLibraries").flatMap {
                System.getProperty("wicketlens.test.$it").split(File.pathSeparator)
            }.distinct()
            val classpath = libraries.joinToString(File.pathSeparator)
            fun run(command: List<String>, logName: String) {
                val log = File(directory, logName)
                val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Generated test process timed out: ${log.readText()}") }
                assertEquals(log.readText(), 0, process.exitValue())
            }
            run(listOf(System.getProperty("wicketlens.test.javac"), "--release", "17", "-cp", classpath, "-d", directory.path) +
                example.listFiles()!!.filter { it.extension == "java" }.map { it.path }, "compile.log")
            val java = File(File(System.getProperty("wicketlens.test.javac")).parentFile, "java").path
            val agent = libraries.single { File(it).name.startsWith("mockito-core-") }
            run(listOf(java, "-javaagent:$agent", "-cp", directory.path + File.pathSeparator + classpath,
                "org.junit.platform.console.ConsoleLauncher", "execute", "--select-class", "example.${pagePlan.testName}",
                "--select-class", "example.${panelPlan.testName}", "--fail-if-no-tests", "--disable-banner"), "run.log")
            assertTrue(File(directory, "run.log").readText().contains("2 tests successful"))
        } finally { directory.deleteRecursively() }
    }
}
