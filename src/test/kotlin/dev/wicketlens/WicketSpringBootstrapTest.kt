package dev.wicketlens

import dev.wicketlens.spring.*

class WicketSpringBootstrapTest : WicketSpringInspectionTestCase() {
    private val creation = "new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this, new org.springframework.context.support.GenericApplicationContext())"
    private fun setup() = WicketSpringSetup.forFile(page("@SpringBean Service orders;"))
    private fun inspect(file: com.intellij.psi.PsiFile) = problems(file, WicketSpringBootstrapInspection())

    fun testLocalContextVariablePreservesRegistrationAndConfigurationIdentity() {
        source("Config", "@org.springframework.context.annotation.Configuration public class Config {}")
        application(statements = "super.init(); var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext(Config.class); getComponentInstantiationListeners().add(new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this, context));")
        val result = setup()
        assertTrue(result.registered)
        assertEquals(listOf("example.Config"), result.configurations)
    }
    fun testLocalInjectorVariableCanBeRegistered() {
        application(statements = "super.init(); var injector = $creation; getComponentInstantiationListeners().add(injector);")
        assertTrue(setup().registered)
    }
    fun testLiteralBooleanLocalDeterminesProxyMode() {
        application(statements = "super.init(); final boolean wrap = false; getComponentInstantiationListeners().add(new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this, new org.springframework.context.support.GenericApplicationContext(), wrap));")
        assertEquals(WicketSpringSetup.Proxies.RAW, setup().proxies)
    }
    fun testFinalBooleanLiteralFieldDeterminesProxyMode() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), WRAP", extra = "private static final boolean WRAP = false;")
        assertEquals(WicketSpringSetup.Proxies.RAW, setup().proxies)
    }
    fun testPrivateHelperChainIsReviewed() {
        application(statements = "super.init(); setupSpring();", extra = "private void setupSpring() { install(); } private void install() { getComponentInstantiationListeners().add($creation); }")
        assertTrue(setup().registered)
    }
    fun testFinalHelperIsReviewed() {
        application(statements = "super.init(); install();", extra = "protected final void install() { getComponentInstantiationListeners().add($creation); }")
        assertTrue(setup().registered)
    }
    fun testVirtualHelperDoesNotEstablishSetup() {
        application(statements = "super.init(); install();", extra = "protected void install() { getComponentInstantiationListeners().add($creation); }")
        assertFalse(setup().registered)
    }
    fun testHelperOnAnotherReceiverDoesNotEstablishSetup() {
        application(statements = "super.init(); other.install();", extra = "private SpringApplication other; private void install() { getComponentInstantiationListeners().add($creation); }")
        assertFalse(setup().registered)
    }
    fun testRecursiveHelperDoesNotEstablishSetup() {
        application(statements = "super.init(); install();", extra = "private void install() { install(); }")
        assertNull(setup().registrationCount)
    }
    fun testMountPageDoesNotHideARecognizedRegistration() {
        application(statements = "super.init(); mountPage(\"/home\", org.apache.wicket.markup.html.WebPage.class); getComponentInstantiationListeners().add($creation);")
        assertTrue(setup().registered)
    }
    fun testEffectfulMountArgumentsAreNotSilentlySkipped() {
        application(statements = "super.init(); mountPage(route(), org.apache.wicket.markup.html.WebPage.class); getComponentInstantiationListeners().add($creation);", extra = "private String route() { return \"/home\"; }")
        assertFalse(setup().registered)
    }
    fun testLocalContextRefreshIsReviewed() {
        application(statements = "super.init(); var context = new org.springframework.context.support.GenericApplicationContext(); context.refresh(); getComponentInstantiationListeners().add(new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this, context));")
        assertTrue(setup().registered)
    }
    fun testReassignmentLeavesSetupUnknown() {
        application(statements = "super.init(); var injector = $creation; injector = $creation; getComponentInstantiationListeners().add(injector);")
        assertFalse(setup().registered)
    }
    fun testConditionalPrivateHelperRemainsUnknown() {
        val app = application(statements = "super.init(); install();", extra = "private void install() { if (true) getComponentInstantiationListeners().add($creation); }")
        assertNull(setup().registrationCount)
        assertTrue(inspect(app).isEmpty())
    }
    fun testTwoExecutedHelperCallsAreNotOneRegistration() {
        val app = application(statements = "super.init(); install(); install();", extra = "private void install() { getComponentInstantiationListeners().add($creation); }")
        assertEquals(2, setup().registrationCount)
        assertFalse(WicketSpringSetup.forFile(app).registered)
        assertTrue(inspect(app).single().descriptionTemplate.contains("multiple"))
    }
    fun testNoRegistrationWithComponentInjectionIntentIsAConditionalFinding() {
        page("@SpringBean Service orders;")
        val app = application(statements = "super.init();")
        val finding = inspect(app).single()
        assertTrue(finding.descriptionTemplate.contains("Verify external wiring"))
        assertTrue(finding.fixes.orEmpty().isEmpty())
    }
    fun testPlainApplicationWithSpringLibrariesHasNoBootstrapFinding() {
        assertTrue(inspect(application(statements = "super.init();")).isEmpty())
    }
    fun testConstructingButDiscardingAnInjectorExplainsMissingComponentRegistration() {
        val app = application(statements = "super.init(); $creation;")
        assertTrue(inspect(app).single().descriptionTemplate.contains("constructing it alone"))
    }
    fun testUnusedInjectorInUncalledHelperStillLeavesSetupUnknown() {
        val app = application(extra = "private void unused() { $creation; }")
        assertTrue(inspect(app).isEmpty())
        assertFalse(setup().registered)
    }
    fun testUnknownStatementsSuppressMissingRegistrationFindings() {
        page("@SpringBean Service orders;")
        assertTrue(inspect(application(statements = "super.init(); System.getProperty(\"setup\");")).isEmpty())
    }
    fun testNativeBootstrapSuppressionIsHonored() {
        val app = application(statements = "super.init(); $creation;")
        edit(app, app.text.replace("public class", "@SuppressWarnings(\"WicketSpringBootstrap\") public class"))
        assertTrue(inspect(app).isEmpty())
    }
}
