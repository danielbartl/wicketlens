package dev.wicketlens

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.spring.*

class WicketSpringBeanNamesTest : WicketSpringInspectionTestCase() {
    private fun config(methods: String = "@org.springframework.context.annotation.Bean public Service orders() { return new Service(); }", annotations: String = "", name: String = "Config") =
        source(name, "@org.springframework.context.annotation.Configuration $annotations public class $name { $methods }")
    private fun named(name: String = "orders", type: String = "Service") = page("@SpringBean(name = \"$name\") $type orders;")
    private fun catalog() = WicketSpringBeanNames.catalog(named())
    private fun literal(file: PsiFile) = PsiTreeUtil.findChildrenOfType(file, PsiLiteralExpression::class.java).single { it.value is String }
    private fun problems(file: PsiFile) = problems(file, WicketSpringBeanTypeInspection())
    private fun directContext() = application(arguments = "this, new org.springframework.context.annotation.AnnotationConfigApplicationContext(Config.class)")

    fun testExplicitSpringBeanLiteralIsAUseWithoutApplicationProof() = assertNotNull(WicketSpringBeanNames.use(literal(named())))
    fun testConstantNameKeepsItsJavaReference() = assertNull(WicketSpringBeanNames.use(literal(page("static final String NAME = \"orders\"; @SpringBean(name = NAME) Service orders;"))))
    fun testOrdinaryJavaStringIsNotABeanName() = assertNull(WicketSpringBeanNames.use(literal(page("String name = \"orders\";"))))
    fun testStaticAndPrimitiveInjectionFieldsDoNotGetBeanNameReferences() {
        assertNull(WicketSpringBeanNames.use(literal(page("@SpringBean(name = \"orders\") static Service orders;"))))
    }
    fun testNamedAloneDoesNotIdentifyWicketSpring() = assertNull(WicketSpringBeanNames.use(literal(page("@jakarta.inject.Named(\"orders\") @Inject Service orders;"))))
    fun testRegisteredNamedInjectIsSupported() {
        application()
        assertNotNull(WicketSpringBeanNames.use(literal(page("@jakarta.inject.Named(value = \"orders\") @Inject Service orders;"))))
    }
    fun testSpringBeanTakesPrecedenceOverNamed() {
        application()
        val file = page("@SpringBean(name = \"orders\") @jakarta.inject.Named(\"other\") @Inject Service orders;")
        assertEquals(listOf("orders"), PsiTreeUtil.findChildrenOfType(file, PsiLiteralExpression::class.java).mapNotNull { WicketSpringBeanNames.use(it)?.literal?.value })
    }
    fun testBeanMethodDefaultNameAndTypeAreCandidates() {
        config()
        val bean = catalog().beans.single { it.name == "orders" }
        assertEquals("example.Service", bean.type)
        assertEquals("example.Config", bean.owner)
    }
    fun testBeanNameAndAliasesAreCandidates() {
        config("@org.springframework.context.annotation.Bean(name = {\"primary\", \"alias\"}) public Service orders() { return new Service(); }")
        assertEquals(setOf("primary", "alias"), catalog().beans.filter { it.type == "example.Service" }.map { it.name }.toSet())
    }
    fun testBeanValueAttributeIsTheNameAlias() {
        config("@org.springframework.context.annotation.Bean(\"named\") public Service orders() { return new Service(); }")
        assertEquals(listOf("named"), catalog().beans.filter { it.type == "example.Service" }.map { it.name })
    }
    fun testEmptyBeanNameArrayKeepsTheMethodName() {
        config("@org.springframework.context.annotation.Bean(name = {}) public Service orders() { return new Service(); }")
        assertTrue(catalog().beans.any { it.name == "orders" })
    }
    fun testConflictingMirroredNameAttributesAreNotGuessed() {
        config("@org.springframework.context.annotation.Bean(name = \"a\", value = \"b\") public Service orders() { return new Service(); }")
        assertTrue(catalog().beans.none { it.type == "example.Service" })
    }
    fun testConstantDefinitionNamesAreNotGuessed() {
        config("static final String NAME = \"named\"; @org.springframework.context.annotation.Bean(name = NAME) public Service orders() { return new Service(); }")
        assertTrue(catalog().beans.none { it.type == "example.Service" })
    }
    fun testStandardStereotypeDefaultNameAndAcronym() {
        source("URLService", "@org.springframework.stereotype.Service public class URLService extends Service {}")
        assertTrue(catalog().beans.any { it.name == "URLService" })
    }
    fun testStandardStereotypeExplicitName() {
        source("Orders", "@org.springframework.stereotype.Component(\"namedOrders\") public class Orders extends Service {}")
        assertTrue(catalog().beans.any { it.name == "namedOrders" })
    }
    fun testStandardStereotypeDefaultDecapitalization() {
        source("Orders", "@org.springframework.stereotype.Repository public class Orders extends Service {}")
        assertTrue(catalog().beans.any { it.name == "orders" })
    }
    fun testCustomComposedStereotypesAreNotAssignedInventedNames() {
        source("Managed", "@org.springframework.stereotype.Component public @interface Managed {}")
        source("Orders", "@Managed public class Orders extends Service {}")
        assertTrue(catalog().beans.none { it.owner == "example.Orders" })
    }
    fun testGenericFactoryTypeParameterIsNotInterpretedInTheConsumersScope() {
        config("@org.springframework.context.annotation.Bean public <T> T unknown() { return null; }")
        assertTrue(catalog().beans.none { it.name == "unknown" })
    }
    fun testClassLiteralConfigurationIsRecordedWithoutChangingProxyEvidence() {
        config()
        directContext()
        val setup = WicketSpringSetup.forFile(named())
        assertTrue(setup.registered)
        assertEquals(WicketSpringSetup.Proxies.WRAPPED, setup.proxies)
        assertEquals(listOf("example.Config"), setup.configurations)
    }
    fun testContextPackageScanningConstructorDoesNotIdentifyConfigurations() {
        application(arguments = "this, new org.springframework.context.annotation.AnnotationConfigApplicationContext(\"example\")")
        assertTrue(WicketSpringSetup.forFile(named()).configurations.isEmpty())
    }
    fun testIncompatibleFinalBeanTypeInDirectConfigurationGetsAConditionalSourceWarning() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        directContext()
        val finding = problems(named()).single()
        assertEquals("\"orders\"", finding.psiElement.text)
        assertTrue(finding.descriptionTemplate.contains("incompatible"))
        assertTrue(finding.descriptionTemplate.contains("runtime bean replacement"))
        assertTrue(finding.fixes.orEmpty().isEmpty())
    }
    fun testAssignableBeanTypeDoesNotWarn() {
        config()
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
    fun testObjectFieldCanReceiveAStringBean() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        directContext()
        assertTrue(problems(named(type = "Object")).isEmpty())
    }
    fun testUnrelatedContextHasNoTypeWarning() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        application()
        assertTrue(problems(named()).isEmpty())
    }
    fun testAbsentBeanNeverBecomesAMissingBeanWarning() {
        config()
        directContext()
        assertTrue(problems(named("missing")).isEmpty())
    }
    fun testEmptySpringBeanNameMeansByTypeLookupAndDoesNotWarn() {
        config()
        directContext()
        assertTrue(problems(named("")).isEmpty())
    }
    fun testUnknownProfileSuppressesValidation() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }", "@org.springframework.context.annotation.Profile(\"prod\")")
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
    fun testProfileOnFactoryMethodSuppressesValidation() {
        config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile(\"prod\") public String orders() { return \"text\"; }")
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
    fun testComponentScanningSuppressesValidation() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }", "@org.springframework.context.annotation.ComponentScan(\"example\")")
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
    fun testImportSuppressesValidation() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }", "@org.springframework.context.annotation.Import(Other.class)")
        source("Other", "public class Other {}")
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
    fun testNonfinalDeclaredTypeDoesNotProveIncompatibility() {
        config()
        directContext()
        source("Other", "public interface Other {}")
        assertTrue(problems(named(type = "Other")).isEmpty())
    }
    fun testDuplicateNamesStayPolyvariantAndDoNotGetATypeWarning() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        config(name = "OtherConfig")
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
    fun testDefinitionEditsInvalidateTheCatalogAndTypeFinding() {
        val config = config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        directContext()
        val java = named()
        assertEquals(1, problems(java).size)
        edit(config, config.text.replace("String orders() { return \"text\"; }", "Service orders() { return new Service(); }"))
        assertTrue(problems(java).isEmpty())
    }
    fun testUncommittedDefinitionDoesNotReturnAStaleCatalog() {
        val config = config()
        val java = named()
        assertTrue(WicketSpringBeanNames.catalog(java).beans.isNotEmpty())
        edit(config, config.text.replace("orders()", "other()"), commit = false)
        try { assertTrue(WicketSpringBeanNames.catalog(java).beans.isEmpty()) }
        finally { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    }
    fun testTestBeansDoNotLeakIntoProductionSuggestions() {
        com.intellij.testFramework.PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE)
        source("TestConfig", "public class TestConfig { @org.springframework.context.annotation.Bean Service onlyTest() { return null; } }", "src/test/java")
        assertTrue(catalog().beans.isEmpty())
    }
    fun testNativeSuppressionIsHonored() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        directContext()
        assertTrue(problems(page("@SuppressWarnings(\"WicketSpringBeanType\") @SpringBean(name = \"orders\") Service orders;")).isEmpty())
    }
    fun testReadOnlyDeterministicCatalogAndFindings() {
        config()
        val java = named()
        val before = java.text
        assertEquals(WicketSpringBeanNames.catalog(java), WicketSpringBeanNames.catalog(java))
        assertEquals(before, java.text)
    }
    fun testUnresolvedFieldTypeDoesNotReceiveATypeFinding() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        directContext()
        assertTrue(problems(named(type = "MissingType")).isEmpty())
    }
    fun testOneUnknownRootMakesTheContextUncertain() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        config("@org.springframework.context.annotation.Bean public Service differentName() { return new Service(); }", annotations = "@org.springframework.context.annotation.Profile(\"prod\")", name = "OtherConfig")
        application(arguments = "this, new org.springframework.context.annotation.AnnotationConfigApplicationContext(Config.class, OtherConfig.class)")
        assertTrue(problems(named()).isEmpty())
    }
    fun testCatalogHonorsCancellation() {
        config()
        val java = named()
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                WicketSpringBeanNames.catalog(java)
            }, indicator)
        }
    }
    fun testGenericFieldTypeDoesNotReceiveASimpleTypeFinding() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        directContext()
        assertTrue(problems(named(type = "java.util.List<Service>")).isEmpty())
    }
    fun testBlankStereotypeNameUsesTheDefaultClassName() {
        source("Orders", "@org.springframework.stereotype.Component(\"  \") public class Orders extends Service {}")
        assertTrue(catalog().beans.any { it.name == "orders" })
    }
    fun testPrimitiveFieldIsNotANameUse() = assertNull(WicketSpringBeanNames.use(literal(page("@SpringBean(name = \"number\") int number;"))))
    fun testOrdinarySpringNamedPojoDoesNotGetWicketReferences() {
        application()
        val java = source("Pojo", "public class Pojo { @jakarta.inject.Inject @jakarta.inject.Named(\"orders\") Service orders; }")
        assertNull(WicketSpringBeanNames.use(literal(java)))
    }
    fun testManualHelperNamedInjectGetsReferencesWithKnownSpringSetup() {
        application()
        val java = source("Helper", "public class Helper { @jakarta.inject.Inject @jakarta.inject.Named(\"orders\") Service orders; public Helper() { org.apache.wicket.injection.Injector.get().inject(this); } }")
        assertNotNull(WicketSpringBeanNames.use(literal(java)))
    }
    fun testNoApplicationMeansNoPositiveTypeValidation() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        assertTrue(problems(named()).isEmpty())
    }
    fun testMultipleApplicationsDoNotEstablishAValidationContext() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        directContext()
        application("OtherApplication")
        assertTrue(problems(named()).isEmpty())
    }
    fun testInheritedConfigurationDoesNotEstablishAReviewedRoot() {
        source("BaseConfig", "public class BaseConfig {}")
        val config = config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; }")
        edit(config, config.text.replace("class Config", "class Config extends BaseConfig"))
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
    fun testOptionalAbsentBeanDoesNotWarn() {
        config()
        directContext()
        assertTrue(problems(page("@SpringBean(name = \"missing\", required = false) Service orders;")).isEmpty())
    }
    fun testMoreThanTheCatalogBoundSuppressesValidation() {
        config((0..256).joinToString(" ") { index -> "@org.springframework.context.annotation.Bean public String bean$index() { return \"text\"; }" })
        directContext()
        val java = named("bean0")
        assertTrue(WicketSpringBeanNames.catalog(java).truncated)
        assertTrue(problems(java).isEmpty())
    }
    fun testJdkConversionTargetsDoNotGetAnIncompatibleSourceFinding() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"42\"; }")
        directContext()
        assertTrue(problems(named(type = "Integer")).isEmpty())
    }
    fun testEnumConversionTargetsDoNotGetAnIncompatibleSourceFinding() {
        source("State", "public enum State { OPEN }")
        config("@org.springframework.context.annotation.Bean public String orders() { return \"OPEN\"; }")
        directContext()
        assertTrue(problems(named(type = "State")).isEmpty())
    }
    fun testStringConstructorConversionTargetsDoNotGetATypeFinding() {
        source("Value", "public class Value { public Value(String value) {} }")
        config("@org.springframework.context.annotation.Bean public String orders() { return \"42\"; }")
        directContext()
        assertTrue(problems(named(type = "Value")).isEmpty())
    }
    fun testUnknownBeanNameMetadataMakesTheWholeRootUncertain() {
        source("Names", "public class Names { public static final String NAME = \"orders\"; }")
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; } @org.springframework.context.annotation.Bean(name = Names.NAME) public Service other() { return new Service(); }")
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
    fun testKnownPostProcessorMakesTheConfigurationUncertain() {
        config("@org.springframework.context.annotation.Bean public String orders() { return \"text\"; } @org.springframework.context.annotation.Bean public org.springframework.beans.factory.config.BeanFactoryPostProcessor processor() { return factory -> {}; }")
        directContext()
        assertTrue(problems(named()).isEmpty())
    }
}
