package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.spring.*

class WicketDependencyAnalyzerTest : WicketSpringInspectionTestCase() {
    private fun snapshot(java: PsiJavaFile) = WicketDependencyAnalyzer().analyze(project, WicketPageAnalyzer().analyze(java.classes.single())!!)
    private fun config(methods: String = "@org.springframework.context.annotation.Bean(name={\"orders\",\"alias\"}) Service orders() { return new Service(); }") =
        source("Config", "@org.springframework.context.annotation.Configuration public class Config { $methods }")

    fun testExplicitSpringBeanFieldShowsLocalIntentWithoutAnApplicationAssumption() {
        val result = snapshot(page("@SpringBean Service orders;"))
        val field = result.owners.single().fields.single()
        assertEquals("orders", field.name)
        assertEquals("example.Service", field.type)
        assertEquals("SpringBean", field.annotation)
        assertEquals(DependencyLookup.TYPE, field.lookup)
        assertNull(result.application)
        assertTrue(field.candidates.isEmpty())
    }
    fun testClasspathPresenceAloneDoesNotCreateDependencies() = assertTrue(snapshot(page("Service orders;")).owners.isEmpty())
    fun testGenericInjectWithoutSpringSetupIsNotClassifiedAsSpring() = assertTrue(snapshot(page("@Inject Service orders;")).owners.isEmpty())
    fun testAutowiredWithoutSpringSetupIsNotClassifiedAsWicketSpring() = assertTrue(snapshot(page("@org.springframework.beans.factory.annotation.Autowired Service orders;")).owners.isEmpty())
    fun testRegisteredGenericInjectIsIncluded() {
        application()
        val result = snapshot(page("@Inject Service orders;"))
        assertEquals("example.SpringApplication", result.application)
        assertEquals("Inject", result.owners.single().fields.single().annotation)
    }
    fun testNamedInjectUsesItsLiteralBeanName() {
        application()
        config()
        val field = snapshot(page("@Inject @jakarta.inject.Named(\"alias\") Service orders;")).owners.single().fields.single()
        assertEquals(DependencyLookup.NAME, field.lookup)
        assertEquals("alias", field.beanName)
        assertEquals(listOf("alias", "orders"), field.candidates.single().names)
    }
    fun testSpringBeanPrecedesGenericNamedAnnotation() {
        application()
        val field = snapshot(page("@SpringBean(name=\"orders\") @Inject @jakarta.inject.Named(\"other\") Service orders;")).owners.single().fields.single()
        assertEquals("SpringBean", field.annotation)
        assertEquals("orders", field.beanName)
    }
    fun testAliasesDoNotInventTwoByTypeCandidates() {
        config()
        val field = snapshot(page("@SpringBean Service orders;")).owners.single().fields.single()
        assertEquals(1, field.candidates.size)
        assertEquals(listOf("alias", "orders"), field.candidates.single().names)
    }
    fun testSameNameDeclarationsStaySeparateCandidates() {
        config()
        source("OtherConfig", "public class OtherConfig { @org.springframework.context.annotation.Bean Service orders() { return new Service(); } }")
        assertEquals(2, snapshot(page("@SpringBean(name=\"orders\") Service orders;")).owners.single().fields.single().candidates.size)
    }
    fun testNamedDeclarationWithDifferentTypeIsVisibleWithoutClaimingBinding() {
        config("@org.springframework.context.annotation.Bean String orders() { return \"text\"; }")
        val candidate = snapshot(page("@SpringBean(name=\"orders\") Service orders;")).owners.single().fields.single().candidates.single()
        assertEquals(false, candidate.compatible)
        assertEquals("java.lang.String", candidate.type)
    }
    fun testByTypeOnlyIncludesAssignableDeclaredTypes() {
        config("@org.springframework.context.annotation.Bean String title() { return \"text\"; } @org.springframework.context.annotation.Bean Service orders() { return new Service(); }")
        assertEquals(listOf("orders"), snapshot(page("@SpringBean Service orders;")).owners.single().fields.single().candidates.single().names)
    }
    fun testMissingNameRemainsAnEmptySourceCandidateList() {
        config()
        val field = snapshot(page("@SpringBean(name=\"external\") Service orders;")).owners.single().fields.single()
        assertEquals("external", field.beanName)
        assertTrue(field.candidates.isEmpty())
    }
    fun testEmptyNameMeansByTypeLookup() = assertEquals(DependencyLookup.TYPE, snapshot(page("@SpringBean(name=\"\") Service orders;")).owners.single().fields.single().lookup)
    fun testConstantBeanNameRemainsUnknownRatherThanByType() {
        val field = snapshot(page("static final String NAME=\"orders\"; @SpringBean(name=NAME) Service orders;")).owners.single().fields.single()
        assertEquals(DependencyLookup.UNKNOWN, field.lookup)
        assertTrue(field.candidates.isEmpty())
    }
    fun testOptionalAndRequiredFieldsKeepTheirPolicies() {
        val fields = snapshot(page("@SpringBean(required=false) Service optional; @SpringBean Service required;")).owners.single().fields
        assertEquals(listOf(false, true), fields.map { it.required })
    }
    fun testStaticAndPrimitiveAnnotationsAreVisibleAsUnsupportedTargets() {
        config()
        val fields = snapshot(page("@SpringBean static Service shared; @SpringBean int count;")).owners.single().fields
        assertEquals(2, fields.size)
        assertTrue(fields.all { it.lookup == DependencyLookup.UNSUPPORTED && it.candidates.isEmpty() })
    }
    fun testAutowiredRequiresASpringPathRatherThanBecomingWicketInjection() {
        application()
        config()
        val field = snapshot(page("@org.springframework.beans.factory.annotation.Autowired Service orders;")).owners.single().fields.single()
        assertEquals(DependencyLookup.SPRING_AUTOWIRING, field.lookup)
        assertTrue(field.notes.any { it.contains("does not process") })
        assertTrue(field.candidates.isEmpty())
    }
    fun testExplicitSpringAutowiringIntentIsExplained() {
        application()
        val field = snapshot(page("@org.springframework.beans.factory.annotation.Autowired Service orders;", "org.springframework.web.context.support.SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);")).owners.single().fields.single()
        assertTrue(field.notes.any { it.contains("intent is recognized") })
    }
    fun testInheritedFieldsNavigateToTheirDeclarationAndRemainDistinctWhenHidden() {
        val base = source("BasePage", "public class BasePage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean protected Service orders; }")
        val fields = snapshot(page("@SpringBean Service orders;", base = "BasePage")).owners.single().fields
        assertEquals(2, fields.size)
        assertEquals(2, fields.map { it.id }.distinct().size)
        assertEquals(base.virtualFile.path, fields.last().location.file)
        assertEquals(base.text.indexOf("orders;"), fields.last().location.offset)
    }
    fun testDiscoveredCustomComponentsHaveTheirOwnOwnerGroups() {
        source("OrdersLabel", "public class OrdersLabel extends org.apache.wicket.markup.html.basic.Label { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; public OrdersLabel(String id) { super(id); } }")
        val result = snapshot(page("@SpringBean Service pageService;", "add(new OrdersLabel(\"left\")); add(new OrdersLabel(\"right\"));"))
        assertEquals(listOf("ExamplePage", "ExamplePage:left", "ExamplePage:right"), result.owners.map { it.path })
        assertEquals(3, result.owners.flatMap { it.fields }.map { it.id }.distinct().size)
    }
    fun testUnobservedModelsAreNotInventedAsComponentDependencies() {
        source("OtherModel", "public class OtherModel extends org.apache.wicket.model.Model<String> { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; }")
        assertTrue(snapshot(page("OtherModel model;")).owners.isEmpty())
    }
    fun testGenericSourceFieldDoesNotGetGuessedTypeCandidates() {
        source("BasePage", "public class BasePage<T> extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean T orders; }")
        config()
        val field = snapshot(page("", base = "BasePage<Service>")).owners.single().fields.single()
        assertTrue(field.candidates.isEmpty())
        assertTrue(field.notes.any { it.contains("Generic") })
    }
    fun testFactoryBeanNamedSourceRetainsUnknownProductCompatibility() {
        source("OrdersFactory", "public final class OrdersFactory implements org.springframework.beans.factory.FactoryBean<Service> { public Service getObject() { return null; } public Class<?> getObjectType() { return Service.class; } }")
        config("@org.springframework.context.annotation.Bean OrdersFactory orders() { return new OrdersFactory(); }")
        val candidate = snapshot(page("@SpringBean(name=\"orders\") Service orders;")).owners.single().fields.single().candidates.single()
        assertTrue(candidate.factory)
        assertNull(candidate.compatible)
    }
    fun testTransientAndInitializerNotesAreKept() {
        val field = snapshot(page("@SpringBean transient Service orders=null;")).owners.single().fields.single()
        assertTrue(field.notes.any { it.contains("Transient") })
        assertTrue(field.notes.any { it.contains("initialization") })
    }
    fun testCandidateAndApplicationSourcesAreCapturedAsValues() {
        val config = config()
        val app = application()
        val result = snapshot(page("@SpringBean Service orders;"))
        assertTrue(result.sourceFiles.containsAll(listOf(config.virtualFile.path, app.virtualFile.path)))
        assertTrue(result.locations.any { it.file == config.virtualFile.path })
    }
    fun testFieldLimitIsVisibleRatherThanClaimingCompleteness() {
        val result = snapshot(page((0..256).joinToString(" ") { "@SpringBean Service service$it;" }))
        assertEquals(256, result.fieldCount)
        assertTrue(result.truncated)
    }
    fun testCandidateLimitIsExplicit() {
        config((0..33).joinToString(" ") { "@org.springframework.context.annotation.Bean Service service$it() { return new Service(); }" })
        val field = snapshot(page("@SpringBean Service orders;")).owners.single().fields.single()
        assertEquals(32, field.candidates.size)
        assertTrue(field.candidatesTruncated)
    }
    fun testTheComponentReportAndJsonAreUnchangedByDependencyDiscovery() {
        config()
        val java = page("@SpringBean Service orders;", "add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val before = WicketContextExporter().export(report)
        assertEquals(snapshot(java), snapshot(java))
        assertEquals(before, WicketContextExporter().export(report))
        assertEquals(listOf("title"), report.children().map { it.wicketId })
    }
    fun testDeepGenericFieldParametersRemainUnknown() {
        source("BasePage", "public class BasePage<T> extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean java.util.List<java.util.List<T>> orders; }")
        val field = snapshot(page("", base = "BasePage<Service>")).owners.single().fields.single()
        assertTrue(field.notes.any { it.contains("Generic") })
        assertTrue(field.candidates.isEmpty())
    }
    fun testSourceFieldEditChangesTheNextSnapshotWithoutMutatingTheOldOne() {
        val java = page("@SpringBean Service orders;")
        val before = snapshot(java)
        edit(java, java.text.replace("Service orders", "Service renamed"))
        assertEquals("orders", before.owners.single().fields.single().name)
        assertEquals("renamed", snapshot(java).owners.single().fields.single().name)
    }
    fun testUncommittedFieldDoesNotReturnAStaleSnapshot() {
        val java = page("@SpringBean Service orders;")
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        edit(java, java.text.replace("@SpringBean", "@Deprecated"), commit = false)
        try { assertTrue(WicketDependencyAnalyzer().analyze(project, report).owners.isEmpty()) }
        finally { com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments() }
    }
    fun testDependencyDiscoveryHonorsCancellation() {
        val java = page("@SpringBean Service orders;")
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                WicketDependencyAnalyzer().analyze(project, report)
            }, indicator)
        }
    }
    fun testProfiledCandidatesRemainUnverifiedSourceDeclarations() {
        config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile(\"prod\") Service orders() { return new Service(); }")
        assertTrue(snapshot(page("@SpringBean Service orders;")).owners.single().fields.single().candidates.single().conditional)
    }
    fun testLiteralRequiredExpressionThatIsNotReviewedRemainsUnknown() {
        val field = snapshot(page("static final boolean REQUIRED=false; @SpringBean(required=REQUIRED) Service orders;")).owners.single().fields.single()
        assertNull(field.required)
    }
}
