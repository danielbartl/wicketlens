package dev.wicketlens.spring

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.model.PageReport
import dev.wicketlens.ui.ReportVersion
import dev.wicketlens.testing.WicketTestSubjects

data class SpringTestMock(val name: String, val type: String)
enum class SpringTestConstructor { PAGE, PARAMETERS, COMPONENT, MANUAL_PAGE, MANUAL_COMPONENT }
data class SpringTestPlan(val packageName: String, val target: String, val testName: String,
    val constructor: SpringTestConstructor, val mocks: List<SpringTestMock>, val manual: List<String>, val notes: List<String>,
    val spring: Boolean = true)
data class PreparedSpringTest(val plan: SpringTestPlan, val sourceUrl: String, val version: ReportVersion,
    val roots: List<SpringTestRoot>)

/** Deterministic test recipe. Source bean candidates never choose the bindings in this isolated context. */
class WicketSpringTestGenerator {
    fun prepare(project: Project, editorUrl: String, offset: Int): PreparedSpringTest {
        val target = WicketEditorTargetResolver().resolve(project, editorUrl, offset)
        val report = WicketPageSelection().analyze(project, target.sourceUrl, target.caretOffset, target.qualifiedClassName, target.markupSuffix)
        return prepare(project, report, target)
    }

    /** Reuse an already selected, committed page snapshot without repeating editor/class selection. */
    fun prepare(project: Project, report: PageReport, target: EditorPageTarget,
        dependencies: WicketDependencySnapshot? = null): PreparedSpringTest {
        val subject = WicketTestSubjects.select(project, report, target)
        val file = subject.file
        val type = subject.type
        val snapshot = dependencies ?: WicketDependencyAnalyzer().analyze(project, report)
        if (snapshot.fieldCount == 0 && !WicketSpringSetup.forFile(file).registered)
            throw AnalysisUnavailable("No Wicket Spring injection intent was found for this class. Select a page/component with Spring dependencies.")
        val facade = JavaPsiFacade.getInstance(project)
        if (facade.findClass(WicketSpringSupport.INJECTOR, file.resolveScope) == null)
            throw AnalysisUnavailable("The module needs wicket-spring to generate a Wicket Spring test.")
        val manual = mutableListOf<String>()
        val proposed = mutableListOf<SpringTestMock>()
        val typeSources = linkedSetOf<String>()
        for (field in snapshot.owners.flatMap { it.fields }.distinctBy { it.id }) {
            com.intellij.openapi.progress.ProgressManager.checkCanceled()
            // Keep the analyzer's exact source identity when main/test or modules share a class name.
            val declaringFile = WicketSourceFiles.find(project, field.location.file)?.let { PsiManager.getInstance(project).findFile(it) }
            val declaration = declaringFile?.let { PsiTreeUtil.findChildrenOfType(it, PsiClass::class.java) }
                ?.singleOrNull { it.qualifiedName == field.declaringClass }?.findFieldByName(field.name, false)
            val resolved = (declaration?.type as? PsiClassType)?.resolve()
            resolved?.containingFile?.virtualFile?.path?.let(typeSources::add)
            val sealed = resolved?.hasModifierProperty(PsiModifier.SEALED) == true
            val mockable = resolved != null && resolved !is PsiTypeParameter && resolved.qualifiedName != null &&
                !resolved.hasModifierProperty(PsiModifier.FINAL) && !sealed && !resolved.isEnum && !resolved.isAnnotationType &&
                (declaration.type as PsiClassType).parameters.isEmpty() &&
                accessible(resolved, file.packageName) && (resolved.isInterface || resolved.constructors.isEmpty() ||
                    resolved.constructors.any { it.parameterList.isEmpty && !it.hasModifierProperty(PsiModifier.PRIVATE) &&
                        (it.hasModifierProperty(PsiModifier.PUBLIC) || it.hasModifierProperty(PsiModifier.PROTECTED) ||
                            (it.containingFile as? PsiJavaFile)?.packageName == file.packageName) })
            val name = when (field.lookup) { DependencyLookup.TYPE -> field.name; DependencyLookup.NAME -> field.beanName; else -> null }
            if (name == null || !mockable || declaration.initializer != null) {
                manual += if (sealed) "${field.declaringClass}.${field.name}: sealed dependency type; supply a permitted implementation and review Wicket's proxy policy."
                    else "${field.declaringClass}.${field.name}: review ${field.lookup.name.lowercase()} injection, field initialization, and the declared type before supplying a test bean."
            } else proposed += SpringTestMock(name, resolved.qualifiedName!!)
        }
        val mocks = proposed.groupBy { it.name }.mapNotNull { (_, entries) ->
            if (entries.map { it.type }.distinct().size == 1) entries.first() else {
                manual += "A bean name is requested with different declared types; provide a compatible shared test binding."
                null
            }
        }
        if (snapshot.truncated || snapshot.owners.any { it.partial }) manual += "Dependency discovery is incomplete; review inherited and dynamically created components."
        val strategy = subject.constructor
        val notes = mutableListOf("Uses a separate test WebApplication with default Wicket proxy wrapping. Review production routes, sessions, resources, and mock behavior.",
            "Required test dependencies: ${REQUIRED_DEPENDENCIES.joinToString()}. Use the project's framework versions and a JUnit Jupiter runner; dependency availability is not checked and build files are not changed.")
        if (target.markupSuffix.isNotEmpty()) notes += "A markup variant was selected. Configure locale/style in the test to select that variant."
        if (strategy in setOf(SpringTestConstructor.MANUAL_PAGE, SpringTestConstructor.MANUAL_COMPONENT)) notes += "Implement createSubject() with the required constructor arguments."
        val plan = SpringTestPlan(file.packageName, report.qualifiedClassName, "${type.name}SpringTest", strategy, mocks,
            manual.distinct(), notes)
        return PreparedSpringTest(plan, target.sourceUrl, ReportVersion.capture(project, report, target.sourceUrl, 0, snapshot.sourceFiles + typeSources),
            WicketSpringTestFiles.roots(project, target.sourceUrl, file.packageName))
    }

    private fun accessible(type: PsiClass, packageName: String): Boolean {
        if (type.hasModifierProperty(PsiModifier.PRIVATE) || type.containingClass != null && !type.hasModifierProperty(PsiModifier.STATIC)) return false
        return (type.hasModifierProperty(PsiModifier.PUBLIC) || (type.containingFile as? PsiJavaFile)?.packageName == packageName) &&
            (type.containingClass?.let { accessible(it, packageName) } ?: true)
    }

    fun render(plan: SpringTestPlan, testName: String = plan.testName): String {
        if (!plan.spring) return renderPlain(plan, testName)
        val component = plan.constructor in setOf(SpringTestConstructor.COMPONENT, SpringTestConstructor.MANUAL_COMPONENT)
        return buildString {
            if (plan.packageName.isNotEmpty()) append("package ${plan.packageName};\n\n")
            append("""
                import org.apache.wicket.Page;
                import org.apache.wicket.markup.html.WebPage;
                import org.apache.wicket.protocol.http.WebApplication;
                import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
                import org.apache.wicket.util.tester.WicketTester;
                import org.junit.jupiter.api.AfterEach;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.springframework.context.support.StaticApplicationContext;
                import static org.mockito.Mockito.mock;

                // Isolated smoke test: adapt application setup and stub mock behavior before constructing the subject.
                class $testName {
                    private StaticApplicationContext springContext;
                    private WicketTester tester;
            """.trimIndent()).append('\n')
            plan.mocks.forEachIndexed { index, mock -> append("    private ${mock.type} dependency${index + 1};\n") }
            append("\n    @BeforeEach\n    void setUp() {\n        springContext = new StaticApplicationContext();\n")
            plan.mocks.forEachIndexed { index, mock ->
                append("        dependency${index + 1} = mock(${mock.type}.class);\n")
                append("        springContext.getBeanFactory().registerSingleton(${javaString(mock.name)}, dependency${index + 1});\n")
            }
            append("""
                        configureMocks();
                        registerAdditionalBeans();
                        springContext.refresh();
                        tester = new WicketTester(new WebApplication() {
                            @Override
                            public Class<? extends Page> getHomePage() {
                                return ${if (component) "WebPage" else plan.target}.class;
                            }

                            @Override
                            protected void init() {
                                super.init();
                                // Customize sessions/routes/resources here if the subject needs them.
                                getComponentInstantiationListeners().add(new SpringComponentInjector(this, springContext));
                            }
                        });
                    }

                    private void configureMocks() {
                        // TODO: stub dependency methods used by the subject before it is constructed.
                    }

                    private void registerAdditionalBeans() {
            """.trimIndent().prependIndent("    ")).append('\n')
            if (plan.manual.isNotEmpty()) {
                // String literals keep arbitrary source names out of Java comments.
                append("        // TODO: supply the following bindings; remove this guard after reviewing them.\n")
                append("        throw new UnsupportedOperationException(${javaString(plan.manual.joinToString("\n"))});\n")
            } else append("        // Add any dependencies outside the bounded source analysis here.\n")
            append("    }\n\n    @AfterEach\n    void tearDown() {\n        try {\n            if (tester != null) tester.destroy();\n        } finally {\n            if (springContext != null) springContext.close();\n        }\n    }\n\n")
            append(testBody(plan))
        }
    }

    private fun renderPlain(plan: SpringTestPlan, testName: String): String = buildString {
        val component = plan.constructor in setOf(SpringTestConstructor.COMPONENT, SpringTestConstructor.MANUAL_COMPONENT)
        if (plan.packageName.isNotEmpty()) append("package ${plan.packageName};\n\n")
        append("""
            import org.apache.wicket.Page;
            import org.apache.wicket.markup.html.WebPage;
            import org.apache.wicket.protocol.http.WebApplication;
            import org.apache.wicket.util.tester.WicketTester;
            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;

            // Isolated smoke test: adapt sessions, routes, resources and subject construction before running.
            class $testName {
                private WicketTester tester;

                @BeforeEach
                void setUp() {
                    tester = new WicketTester(new WebApplication() {
                        @Override
                        public Class<? extends Page> getHomePage() {
                            return ${if (component) "WebPage" else plan.target}.class;
                        }

                        @Override
                        protected void init() {
                            super.init();
                            // Customize sessions/routes/resources and any application-specific setup here.
                        }
                    });
                }

                @AfterEach
                void tearDown() {
                    if (tester != null) tester.destroy();
                }

        """.trimIndent()).append("\n")
        append(testBody(plan))
    }

    private fun testBody(plan: SpringTestPlan): String = buildString {
        val component = plan.constructor in setOf(SpringTestConstructor.COMPONENT, SpringTestConstructor.MANUAL_COMPONENT)
        val subject = when (plan.constructor) {
            SpringTestConstructor.PAGE -> "new ${plan.target}()"
            SpringTestConstructor.PARAMETERS -> "new ${plan.target}(new org.apache.wicket.request.mapper.parameter.PageParameters())"
            SpringTestConstructor.COMPONENT -> "new ${plan.target}(\"subject\")"
            else -> "createSubject()"
        }
        append("    @Test\n    void rendersSubject() throws Throwable {\n")
        if (component) append("        tester.startComponentInPage($subject);\n        tester.assertComponent(\"subject\", ${plan.target}.class);\n")
        else append("        tester.startPage($subject);\n        tester.assertRenderedPage(${plan.target}.class);\n")
        append("    }\n")
        if (plan.constructor in setOf(SpringTestConstructor.MANUAL_COMPONENT, SpringTestConstructor.MANUAL_PAGE))
            append("\n    private ${plan.target} createSubject() throws Throwable {\n        // TODO: supply the subject's constructor arguments.\n        throw new UnsupportedOperationException(\"Configure the subject constructor\");\n    }\n")
        append("}\n")
    }

    private fun javaString(value: String): String = "\"" + buildString {
        value.forEach { char -> when (char) {
            '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (char.code < 32 || char.code > 126) append("\\u%04x".format(char.code)) else append(char)
        } }
    } + "\""

    companion object {
        val REQUIRED_DEPENDENCIES = listOf("org.apache.wicket:wicket-tester", "org.apache.wicket:wicket-spring",
            "org.springframework:spring-context", "jakarta.servlet:jakarta.servlet-api",
            "org.junit.jupiter:junit-jupiter", "org.mockito:mockito-core")
    }
}
