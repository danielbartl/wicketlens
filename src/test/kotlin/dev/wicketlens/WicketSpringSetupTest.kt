package dev.wicketlens

import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.spring.WicketSpringSetup
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketSpringSetupTest : WicketSpringInspectionTestCase() {
    private fun setup() = WicketSpringSetup.forFile(page("@SpringBean Service orders;"))
    private val registration = "getComponentInstantiationListeners().add(new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this, new org.springframework.context.support.GenericApplicationContext()));"

    fun testClasspathAloneDoesNotEstablishAnApplication() = assertFalse(setup().registered)

    fun testOneArgumentConstructorUsesDefaultWrapping() {
        application(arguments = "this")
        val result = setup()
        assertTrue(result.registered)
        assertEquals(WicketSpringSetup.Proxies.WRAPPED, result.proxies)
        assertEquals("example.SpringApplication", result.application)
    }

    fun testTwoArgumentConstructorUsesDefaultWrapping() {
        application()
        assertEquals(WicketSpringSetup.Proxies.WRAPPED, setup().proxies)
    }

    fun testFalseThirdArgumentDisablesWrapping() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), false")
        assertEquals(WicketSpringSetup.Proxies.RAW, setup().proxies)
    }

    fun testAbstractSourceBaseCanRegisterAnInheritedInjector() {
        val base = application("BaseApplication")
        edit(base, base.text.replace("public class BaseApplication", "public abstract class BaseApplication"))
        source("SpringApplication", "public class SpringApplication extends BaseApplication {}")
        val result = setup()
        assertTrue(result.registered)
        assertEquals("example.SpringApplication", result.application)
    }

    fun testLiteralFieldInitializationDoesNotObscureDirectSetup() {
        application(extra = "private final String name = \"demo\";")
        assertTrue(setup().registered)
    }

    fun testResolvedThisAndSuperListenerReceiversAreSupported() {
        application(statements = "super.init(); this.$registration")
        assertTrue(setup().registered)
    }

    fun testMissingSuperInitLeavesSetupUnknown() {
        application(statements = registration)
        assertFalse(setup().registered)
    }

    fun testConditionalRegistrationLeavesSetupUnknown() {
        application(statements = "super.init(); if (true) { $registration }")
        assertFalse(setup().registered)
    }

    fun testHelperRegistrationLeavesSetupUnknown() {
        application(statements = "super.init(); install();", extra = "private void install() { $registration }")
        assertFalse(setup().registered)
    }

    fun testConstructingAnInjectorWithoutListenerRegistrationIsInsufficient() {
        application(statements = "super.init(); new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this);")
        assertFalse(setup().registered)
    }

    fun testRegistrationToAnotherApplicationDoesNotEstablishLocalSetup() {
        application(statements = "super.init(); other.$registration", extra = "private org.apache.wicket.protocol.http.WebApplication other;")
        assertFalse(setup().registered)
    }

    fun testInjectorForAnotherApplicationDoesNotEstablishLocalSetup() {
        application(arguments = "other", extra = "private org.apache.wicket.protocol.http.WebApplication other;")
        assertFalse(setup().registered)
    }

    fun testTwoRegistrationsAreAmbiguous() {
        application(statements = "super.init(); $registration $registration")
        assertFalse(setup().registered)
    }

    fun testUnreviewedSecondInjectorCreationLeavesSetupUnknown() {
        application(extra = "void otherSetup() { new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this); }")
        assertFalse(setup().registered)
    }

    fun testInitializerSetupLeavesApplicationUnknown() {
        application(extra = "{ System.getProperty(\"setup\"); }")
        assertFalse(setup().registered)
    }

    fun testNontrivialConstructorSetupLeavesApplicationUnknown() {
        application(extra = "public SpringApplication() { System.getProperty(\"setup\"); }")
        assertFalse(setup().registered)
    }

    fun testUnrelatedInitStatementsAreNotSilentlySkipped() {
        application(statements = "super.init(); $registration System.getProperty(\"setup\");")
        assertFalse(setup().registered)
    }

    fun testUnknownModeStillProvesRegistrationForJakartaInject() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), wrap", extra = "private boolean wrap;")
        val result = setup()
        assertTrue(result.registered)
        assertEquals(WicketSpringSetup.Proxies.UNKNOWN, result.proxies)
    }

    fun testTestApplicationDoesNotEnableProductionChecks() {
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        application(directory = "src/test/java")
        assertFalse(setup().registered)
        val testPage = source("TestPage", "public class TestPage extends org.apache.wicket.markup.html.WebPage { @jakarta.inject.Inject Service orders = null; }", "src/test/java")
        assertTrue(WicketSpringSetup.forFile(testPage).registered)
        assertEquals(1, injection(testPage).size)
    }

    fun testProductionApplicationDoesNotSelectTestSourceSetup() {
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        application()
        val testPage = source("TestPage", "public class TestPage extends org.apache.wicket.markup.html.WebPage {}", "src/test/java")
        assertFalse(WicketSpringSetup.forFile(testPage).registered)
    }
}
