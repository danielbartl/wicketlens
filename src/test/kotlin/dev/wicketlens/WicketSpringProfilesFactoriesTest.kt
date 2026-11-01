package dev.wicketlens

import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.spring.*

class WicketSpringProfilesFactoriesTest : WicketSpringInspectionTestCase() {
    private fun config(methods: String, annotations: String = "") = source("Config",
        "@org.springframework.context.annotation.Configuration $annotations public class Config { $methods }")
    private fun factory(type: String = "Service", name: String = "OrdersFactory") = source(name,
        "public final class $name implements org.springframework.beans.factory.FactoryBean<$type> { public $type getObject() { return null; } public Class<?> getObjectType() { return null; } }")
    private fun declaration(type: String = "OrdersFactory") = config("@org.springframework.context.annotation.Bean({\"orders\",\"alias\"}) $type orders() { return null; }")
    private var pageNumber = 0
    private fun beans() = WicketSpringBeanNames.catalog(page("@SpringBean Service orders;", name = "BeanPage${pageNumber++}")).beans.filter { it.owner == "example.Config" && it.name != "config" }
    private fun fields(members: String) = page(members, name = "DependencyPage${pageNumber++}").let {
        WicketDependencyAnalyzer().analyze(project, WicketPageAnalyzer().analyze(it.classes.single())!!).owners.single().fields
    }
    private fun findings() = problems(page("@SpringBean(name=\"orders\") Service orders;"), WicketSpringBeanTypeInspection())
    private fun directContext() = application(arguments = "this, new org.springframework.context.annotation.AnnotationConfigApplicationContext(Config.class)")

    fun testFactoryProductsAndDereferencesRetainAliasesAndOneSourceDeclaration() {
        factory(); declaration()
        val beans = beans()
        assertEquals(setOf("orders", "alias", "&orders", "&alias"), beans.map { it.name }.toSet())
        assertEquals(1, beans.map { it.offset }.distinct().size)
        assertTrue(beans.all { it.productType == "example.Service" && it.factory })
        assertTrue(beans.single { it.name == "&orders" }.dereference)
    }
    fun testInheritedGenericFactoryProductIsSubstituted() {
        source("BaseFactory", "public abstract class BaseFactory<T> implements org.springframework.beans.factory.FactoryBean<T> {}")
        source("OrdersFactory", "public abstract class OrdersFactory extends BaseFactory<Service> {}")
        declaration()
        assertEquals("example.Service", beans().first().productType)
    }
    fun testParameterizedFactoryReturnUsesItsActualProductArgument() {
        declaration("org.springframework.beans.factory.FactoryBean<java.util.List<Service>>")
        assertEquals("java.util.List<example.Service>", beans().first().productType)
    }
    fun testRawFactoryProductRemainsUnknownButItsFactoryCanBeNavigated() {
        declaration("org.springframework.beans.factory.FactoryBean")
        assertNull(beans().first().productType)
        val field = fields("@SpringBean(name=\"orders\") Service orders;").single()
        assertNull(field.candidates.single().compatible)
        assertEquals("<unknown product type>", field.candidates.single().type)
        assertTrue(fields("@SpringBean Service orders;").single().candidates.isEmpty())
    }
    fun testWildcardFactoryProductRemainsUnknown() {
        declaration("org.springframework.beans.factory.FactoryBean<? extends Service>")
        assertNull(beans().first().productType)
    }
    fun testRawGenericProductDoesNotBecomeAnExactType() {
        declaration("org.springframework.beans.factory.FactoryBean<java.util.List>")
        assertNull(beans().first().productType)
    }
    fun testUnresolvedProductIsNotOfferedAsAKnownType() {
        declaration("org.springframework.beans.factory.FactoryBean<Missing>")
        assertTrue(beans().isEmpty())
    }
    fun testFactoryStereotypeProvidesProductAndFactoryNames() {
        val type = source("ConcreteFactory", "@org.springframework.stereotype.Component(\"concrete\") public final class ConcreteFactory implements org.springframework.beans.factory.FactoryBean<Service> { public Service getObject(){return null;} public Class<?> getObjectType(){return null;} }")
        val catalog = WicketSpringBeanNames.catalog(page("@SpringBean Service orders;"))
        assertEquals(setOf("concrete", "&concrete"), catalog.beans.filter { it.url == type.virtualFile.url }.map { it.name }.toSet())
    }
    fun testByTypeDistinguishesProductsAndFactoriesAndKeepsAliasesGrouped() {
        factory(); declaration()
        val fields = fields("@SpringBean Service product; @SpringBean OrdersFactory factory;")
        assertEquals(listOf("alias", "orders"), fields[0].candidates.single().names)
        assertFalse(fields[0].candidates.single().factoryLookup)
        assertEquals(listOf("&alias", "&orders"), fields[1].candidates.single().names)
        assertTrue(fields[1].candidates.single().factoryLookup)
    }
    fun testNamedFactoryDoesNotAccidentallyIncludeProductAliases() {
        factory(); declaration()
        val candidate = fields("@SpringBean(name=\"&alias\") OrdersFactory orders;").single().candidates.single()
        assertEquals(listOf("&alias", "&orders"), candidate.names)
        assertEquals("example.OrdersFactory", candidate.type)
        assertEquals(true, candidate.compatible)
    }
    fun testObjectTypeDoesNotDuplicateProductWithItsFactoryFallback() {
        factory(); declaration()
        assertEquals(1, fields("@SpringBean Object orders;").single().candidates.count { it.factory })
    }
    fun testProductWithFinalIncompatibleTypeDoesNotEnablePositiveInspection() {
        factory("String"); declaration(); directContext()
        assertTrue(findings().isEmpty())
    }
    fun testMethodAndConfigurationProfilesRetainAndOrStructure() {
        config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile({\"prod & eu\", \"staging\"}) Service orders(){return null;}",
            "@org.springframework.context.annotation.Profile(\"!test\")")
        val bean = beans().single()
        assertEquals(listOf("(!test)", "((prod & eu) OR (staging))"), bean.profiles)
        assertTrue(bean.conditional)
        assertFalse(bean.profilesUnknown)
        val candidate = fields("@SpringBean Service orders;").single().candidates.single()
        assertEquals(bean.profiles, candidate.profiles)
    }
    fun testProfileConstantExpressionsAreReadWithoutExecutingCode() {
        source("Profiles", "public class Profiles { public static final String LIVE = \"prod\"; }")
        config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile(Profiles.LIVE) Service orders(){return null;}")
        assertEquals(listOf("(prod)"), beans().single().profiles)
    }
    fun testUnresolvedProfileDoesNotTurnIntoAnUnconditionalCandidate() {
        config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile(Missing.VALUE) Service orders(){return null;}")
        val bean = beans().single()
        assertTrue(bean.profilesUnknown)
        assertTrue(bean.conditional)
    }
    fun testEmptyProfileArrayRetainsUnknownCondition() {
        config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile({}) Service orders(){return null;}")
        assertTrue(beans().single().profilesUnknown)
    }
    fun testComposedProfileConditionOnDirectBeanIsVisible() {
        source("Production", "@org.springframework.context.annotation.Profile(\"prod\") public @interface Production {}")
        config("@org.springframework.context.annotation.Bean @Production Service orders(){return null;}")
        assertEquals(listOf("(prod)"), beans().single().profiles)
    }
    fun testMultipleMetaProfileConditionsOnOneOwnerAreAlternatives() {
        source("Production", "@org.springframework.context.annotation.Profile(\"prod\") public @interface Production {}")
        source("Staging", "@org.springframework.context.annotation.Profile(\"staging\") public @interface Staging {}")
        config("@org.springframework.context.annotation.Bean @Production @Staging Service orders(){return null;}")
        assertEquals(listOf("((prod) OR (staging))"), beans().single().profiles)
    }
    fun testOrdinaryNamedBeanHasNoInventedUnknownProfileCondition() {
        declaration("Service")
        assertTrue(beans().all { it.profiles.isEmpty() && !it.profilesUnknown })
    }
    fun testRawInheritedFactoryDoesNotInventObjectProduct() {
        source("BaseFactory", "public abstract class BaseFactory<T> implements org.springframework.beans.factory.FactoryBean<T> {}")
        source("OrdersFactory", "public abstract class OrdersFactory extends BaseFactory {}")
        declaration()
        assertNull(beans().first().productType)
    }
    fun testMutuallyExclusiveProfileDefinitionsRemainSeparateCandidates() {
        config("@org.springframework.context.annotation.Bean(\"orders\") @org.springframework.context.annotation.Profile(\"prod\") Service production(){return null;} " +
            "@org.springframework.context.annotation.Bean(\"orders\") @org.springframework.context.annotation.Profile(\"!prod\") Service development(){return null;}")
        val candidates = fields("@SpringBean(name=\"orders\") Service orders;").single().candidates
        assertEquals(2, candidates.size)
        assertEquals(setOf(listOf("(prod)"), listOf("(!prod)")), candidates.map { it.profiles }.toSet())
    }
    fun testProfileAndGenericEditsInvalidateCachedCandidates() {
        val config = config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile(\"prod\") org.springframework.beans.factory.FactoryBean<Service> orders(){return null;}")
        val java = page("@SpringBean Service orders;")
        assertEquals("example.Service", WicketSpringBeanNames.catalog(java).beans.first().productType)
        edit(config, config.text.replace("\"prod\"", "\"dev\"").replace("FactoryBean<Service>", "FactoryBean<String>"))
        val bean = WicketSpringBeanNames.catalog(java).beans.first()
        assertEquals("java.lang.String", bean.productType)
        assertEquals(listOf("(dev)"), bean.profiles)
    }
    fun testFactoryAndProfileSupportDoesNotAlterTheComponentGraph() {
        factory(); declaration()
        val java = page("@SpringBean Service orders;", "add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val before = dev.wicketlens.export.WicketContextExporter().export(report)
        WicketDependencyAnalyzer().analyze(project, report)
        assertEquals(before, dev.wicketlens.export.WicketContextExporter().export(report))
    }
}
