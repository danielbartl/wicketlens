package dev.wicketlens

import com.intellij.psi.PsiFile
import dev.wicketlens.spring.WicketSpringAnnotationInspection

class WicketSpringAnnotationTest : WicketSpringInspectionTestCase() {
    private val wire = "org.apache.wicket.injection.Injector.get().inject(this);"
    private val autowire = "org.springframework.web.context.support.SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);"
    private fun annotations(file: PsiFile) = problems(file, WicketSpringAnnotationInspection())
    private fun helper(members: String = "@org.apache.wicket.spring.injection.annot.SpringBean Service orders;", body: String = "", base: String = "") =
        source("Helper", "public class Helper $base { $members public Helper() { $body } }")
    private fun autowired(members: String = "", body: String = "") = page("@org.springframework.beans.factory.annotation.Autowired Service orders; $members", body)

    fun testNonComponentSpringBeanExpressesLocalManualInjectionIntent() {
        val problem = annotations(helper()).single()
        assertTrue(problem.psiElement.text.endsWith("SpringBean"))
        assertTrue(problem.descriptionTemplate.contains("No manual Wicket injection path"))
        assertTrue(problem.fixes.orEmpty().isEmpty())
    }

    fun testModelNeedsManualInjection() = assertEquals(1, annotations(helper(base = "extends org.apache.wicket.model.Model<String>")).size)

    fun testSessionNeedsManualInjection() {
        val java = source("ExampleSession", "public class ExampleSession extends org.apache.wicket.protocol.http.WebSession { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; public ExampleSession(org.apache.wicket.request.Request request) { super(request); } }")
        assertEquals(1, annotations(java).size)
    }

    fun testComponentsAlreadyHaveAutomaticInjection() = assertTrue(annotations(page("@SpringBean Service orders;")).isEmpty())
    fun testBehaviorsAlreadyHaveAutomaticInjection() = assertTrue(annotations(page("@SpringBean Service orders;", base = "org.apache.wicket.behavior.Behavior")).isEmpty())
    fun testUnannotatedHelperIsUnaffected() = assertTrue(annotations(helper("Service orders;")).isEmpty())

    fun testStaticSpringBeanCannotBeInjected() {
        assertTrue(annotations(page("@SpringBean static Service orders;")).single().descriptionTemplate.contains("static injection fields"))
    }

    fun testPrimitiveSpringBeanCannotBeInjected() {
        assertTrue(annotations(page("@SpringBean int count;")).single().descriptionTemplate.contains("primitive field"))
    }

    fun testStaticPrimitiveGetsOneSpecificFinding() = assertEquals(1, annotations(page("@SpringBean static int count;")).size)
    fun testBoxedPrimitiveIsAReferenceNotAnUnsupportedTarget() = assertTrue(annotations(page("@SpringBean Integer count;")).isEmpty())
    fun testStaticHelperTargetIsNotHiddenByAManualInjectionCall() = assertTrue(annotations(helper("@org.apache.wicket.spring.injection.annot.SpringBean static Service orders;", wire)).single().descriptionTemplate.contains("static"))
    fun testFinalReferenceFieldsAreNotAssumedUnsupported() = assertTrue(annotations(page("@SpringBean final Service orders = null;")).isEmpty())

    fun testDirectConstructorInjectionIsRecognized() = assertTrue(annotations(helper(body = wire)).isEmpty())
    fun testInstanceInitializerInjectionIsRecognized() = assertTrue(annotations(helper(members = "@org.apache.wicket.spring.injection.annot.SpringBean Service orders; { $wire }")).isEmpty())
    fun testSelfInjectionMethodIsIntentWithoutClaimingEveryCallPathExecutesIt() = assertTrue(annotations(helper(members = "@org.apache.wicket.spring.injection.annot.SpringBean Service orders; void initialize() { $wire }")).isEmpty())
    fun testConditionalManualInjectionIsIntentWithoutClaimingCoverage() = assertTrue(annotations(helper(body = "if (System.getProperty(\"inject\") != null) { $wire }")).isEmpty())

    fun testSourceBaseManualInjectionIsRecognized() {
        source("BaseHelper", "public class BaseHelper { public BaseHelper() { $wire } }")
        assertTrue(annotations(helper(base = "extends BaseHelper")).isEmpty())
    }

    fun testExternalCallerInjectionIsRecognizedThroughResolvedTargetType() {
        val java = helper()
        source("Factory", "public class Factory { Helper make() { Helper target = new Helper(); org.apache.wicket.injection.Injector.get().inject(target); return target; } }")
        assertTrue(annotations(java).isEmpty())
    }

    fun testExternalSubtypeInjectionIsIntentForInheritedFields() {
        val java = helper()
        source("Child", "public class Child extends Helper { public Child() { $wire } }")
        assertTrue(annotations(java).isEmpty())
    }

    fun testInjectingAnUnrelatedTypeDoesNotHideTheMissingPath() {
        assertEquals(1, annotations(helper(body = "org.apache.wicket.injection.Injector.get().inject(new Service());")).size)
    }

    fun testUnrelatedMethodNamedInjectIsNotWicketInjection() = assertEquals(1, annotations(helper(members = "@org.apache.wicket.spring.injection.annot.SpringBean Service orders; void inject(Object value) {}", body = "inject(this);")).size)

    fun testSpringAutowiringDoesNotProcessSpringBean() = assertEquals(1, annotations(helper(body = autowire)).size)

    fun testSpringManagedTypeDoesNotByItselfProcessSpringBean() {
        val java = source("Managed", "@org.springframework.stereotype.Component public class Managed { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; }")
        assertEquals(1, annotations(java).size)
    }

    fun testJakartaInjectAloneDoesNotImplySpringManualInjection() = assertTrue(annotations(helper("@jakarta.inject.Inject Service orders;")).isEmpty())
    fun testRegisteredSpringJakartaInjectOnModelNeedsManualInjection() {
        application()
        assertEquals(1, annotations(helper("@jakarta.inject.Inject Service orders;", base = "extends org.apache.wicket.model.Model<String>")).size)
    }

    fun testGenericInjectOnOrdinaryPojoDoesNotImplyWicketOwnership() {
        application()
        assertTrue(annotations(helper("@jakarta.inject.Inject Service orders;")).isEmpty())
    }

    fun testGenericInjectOnManagedSpringModelDoesNotRequireWicketInjection() {
        application()
        val java = helper("@jakarta.inject.Inject Service orders;", base = "extends org.apache.wicket.model.Model<String>")
        edit(java, java.text.replace("public class", "@org.springframework.stereotype.Component public class"))
        assertTrue(annotations(java).isEmpty())
    }

    fun testPrimitiveGenericInjectOnOrdinarySpringServiceIsNotAWicketFinding() {
        application()
        assertTrue(annotations(source("PlainService", "@org.springframework.stereotype.Service public class PlainService { @jakarta.inject.Inject int count; }")).isEmpty())
    }

    fun testRegisteredSpringJakartaInjectRespectsManualInjection() {
        application()
        assertTrue(annotations(helper("@jakarta.inject.Inject Service orders;", wire)).isEmpty())
    }

    fun testRegisteredSpringJakartaInjectRejectsPrimitiveTarget() {
        application()
        assertTrue(annotations(page("@Inject int count;")).single().descriptionTemplate.contains("primitive"))
    }

    fun testAutowiredWithoutRecognizedWicketSpringSetupIsSilent() = assertTrue(annotations(autowired()).isEmpty())
    fun testAutowiredOnPlainSpringObjectsIsOutsideWicketChecks() {
        application()
        assertTrue(annotations(helper("@org.springframework.beans.factory.annotation.Autowired Service orders;")).isEmpty())
    }

    fun testAutowiredComponentWithKnownSetupWarnsAboutTheAnnotation() {
        application()
        val finding = annotations(autowired()).single()
        assertTrue(finding.psiElement.text.endsWith("Autowired"))
        assertTrue(finding.descriptionTemplate.contains("does not process @Autowired"))
    }

    fun testAutowiredBehaviorWithKnownSetupWarns() {
        application()
        assertEquals(1, annotations(page("@org.springframework.beans.factory.annotation.Autowired Service orders;", base = "org.apache.wicket.behavior.Behavior")).size)
    }

    fun testDualSupportedAnnotationDoesNotGetAnAutowiredWarning() {
        application()
        assertTrue(annotations(page("@org.springframework.beans.factory.annotation.Autowired @SpringBean Service orders;")).isEmpty())
    }

    fun testDualJakartaAnnotationDoesNotGetAnAutowiredWarning() {
        application()
        assertTrue(annotations(page("@org.springframework.beans.factory.annotation.Autowired @Inject Service orders;")).isEmpty())
    }

    fun testWicketManualInjectionDoesNotProcessAutowired() {
        application()
        assertEquals(1, annotations(autowired(body = wire)).size)
    }

    fun testSpringAutowiringSupportIsRecognized() {
        application()
        assertTrue(annotations(autowired(body = autowire)).isEmpty())
    }

    fun testAutowireCapableFactoryIsRecognized() {
        application()
        assertTrue(annotations(autowired("void wire(org.springframework.beans.factory.config.AutowireCapableBeanFactory factory) { factory.autowireBean(this); }")).isEmpty())
    }

    fun testConcreteAutowireFactoryImplementationIsRecognized() {
        application()
        assertTrue(annotations(autowired("void wire(org.springframework.beans.factory.support.DefaultListableBeanFactory factory) { factory.autowireBean(this); }")).isEmpty())
    }

    fun testExplicitSpringConfigureBeanIsRecognized() {
        application()
        assertTrue(annotations(autowired("void wire(org.springframework.beans.factory.config.AutowireCapableBeanFactory factory) { factory.configureBean(this, \"page\"); }")).isEmpty())
    }

    fun testAutowiredPostProcessorIsRecognized() {
        application()
        assertTrue(annotations(autowired("void wire(org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor processor) { processor.processInjection(this); }")).isEmpty())
    }

    fun testExternalSpringAutowiringIsRecognized() {
        application()
        val java = autowired()
        source("Factory", "public class Factory { void wire(ExamplePage target, org.springframework.beans.factory.config.AutowireCapableBeanFactory factory) { factory.autowireBean(target); } }")
        assertTrue(annotations(java).isEmpty())
    }

    fun testSpringStereotypeIsManagementIntent() {
        application()
        val java = autowired()
        edit(java, java.text.replace("public class", "@org.springframework.stereotype.Component public class"))
        assertTrue(annotations(java).isEmpty())
    }

    fun testComposedSpringStereotypeIsManagementIntent() {
        application()
        source("ManagedPage", "@org.springframework.stereotype.Component public @interface ManagedPage {}")
        val java = autowired()
        edit(java, java.text.replace("public class", "@ManagedPage public class"))
        assertTrue(annotations(java).isEmpty())
    }

    fun testConfigurableIsManagementIntent() {
        application()
        val java = autowired()
        edit(java, java.text.replace("public class", "@org.springframework.beans.factory.annotation.Configurable public class"))
        assertTrue(annotations(java).isEmpty())
    }

    fun testBeanFactoryMethodIsManagementIntent() {
        application()
        val java = autowired()
        source("Config", "@org.springframework.context.annotation.Configuration public class Config { @org.springframework.context.annotation.Bean ExamplePage page() { return new ExamplePage(); } }")
        assertTrue(annotations(java).isEmpty())
    }

    fun testCreateBeanFactoryIsManagementIntent() {
        application()
        val java = autowired()
        source("Factory", "public class Factory { Object page(org.springframework.beans.factory.config.AutowireCapableBeanFactory factory) { return factory.createBean(ExamplePage.class); } }")
        assertTrue(annotations(java).isEmpty())
    }

    fun testUnrelatedMethodNamedAutowireBeanDoesNotSuppress() {
        application()
        assertEquals(1, annotations(autowired("void autowireBean(Object target) {}", "autowireBean(this);")).size)
    }

    fun testAutowiredForOtherTypeDoesNotSuppress() {
        application()
        assertEquals(1, annotations(autowired(body = autowire.replace("(this)", "(new Service())"))).size)
    }

    fun testUnresolvedSuperclassLeavesTheManualPathUnknown() = assertTrue(annotations(helper(base = "extends MissingBase")).isEmpty())

    fun testInspectionSuppressionIsHonored() {
        assertTrue(annotations(helper("@SuppressWarnings(\"WicketSpringAnnotation\") @org.apache.wicket.spring.injection.annot.SpringBean Service orders;")).isEmpty())
    }

    fun testCallerEditsInvalidatePathEvidence() {
        val java = helper()
        val factory = source("Factory", "public class Factory { void wire(Helper target) { org.apache.wicket.injection.Injector.get().inject(target); } }")
        assertTrue(annotations(java).isEmpty())
        edit(factory, factory.text.replace("org.apache.wicket.injection.Injector.get().inject(target);", ""))
        assertEquals(1, annotations(java).size)
    }

    fun testNewCallerInvalidatesMissingPathEvidence() {
        val java = helper()
        assertEquals(1, annotations(java).size)
        source("Factory", "public class Factory { void wire(Helper target) { org.apache.wicket.injection.Injector.get().inject(target); } }")
        assertTrue(annotations(java).isEmpty())
    }

    fun testUncommittedCallerDoesNotReturnStaleEvidence() {
        val java = helper()
        assertEquals(1, annotations(java).size)
        val caller = source("Factory", "public class Factory { void wire(Helper target) {} }")
        edit(caller, caller.text.replace("{}", "{ org.apache.wicket.injection.Injector.get().inject(target); }"), commit = false)
        try { assertTrue(annotations(java).isEmpty()) }
        finally { com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments() }
    }

    fun testAnalysisIsReadOnlyAndRepeatable() {
        val java = helper()
        val before = java.text
        assertEquals(annotations(java).map { it.descriptionTemplate }, annotations(java).map { it.descriptionTemplate })
        assertEquals(before, java.text)
    }

    fun testCallerFileBoundLeavesPathsUnknown() {
        val java = helper()
        repeat(33) { index -> source("Caller$index", "public class Caller$index { Helper helper; }") }
        assertTrue(annotations(java).isEmpty())
    }

    fun testTestCallerDoesNotHideMissingProductionInjection() {
        com.intellij.testFramework.PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE)
        val java = helper()
        source("TestFactory", "public class TestFactory { void wire(Helper target) { org.apache.wicket.injection.Injector.get().inject(target); } }", "src/test/java")
        assertEquals(1, annotations(java).size)
    }

    fun testFakeSpringBeanAnnotationDoesNotActivateTheInspection() {
        source("SpringBean", "public @interface SpringBean {}")
        assertTrue(annotations(helper("@SpringBean Service orders;")).isEmpty())
    }

    fun testInheritedSpringAutowiringIsRecognized() {
        application()
        source("BasePage", "public class BasePage extends org.apache.wicket.markup.html.WebPage { protected BasePage() { $autowire } }")
        assertTrue(annotations(page("@org.springframework.beans.factory.annotation.Autowired Service orders;", base = "BasePage")).isEmpty())
    }

    fun testManualCallDeletionInvalidatesPreviouslyRecognizedPath() {
        val java = helper(body = wire)
        assertTrue(annotations(java).isEmpty())
        edit(java, java.text.replace(wire, ""))
        assertEquals(1, annotations(java).size)
    }

    fun testUnknownCompiledAncestrySuppressesMissingPathFindings() = assertTrue(annotations(helper(base = "extends java.util.ArrayList<String>")).isEmpty())

    fun testSpringServletContextAutowiringIsRecognized() {
        application()
        assertTrue(annotations(autowired("void wire(jakarta.servlet.ServletContext context) { org.springframework.web.context.support.SpringBeanAutowiringSupport.processInjectionBasedOnServletContext(this, context); }")).isEmpty())
    }

    fun testAutowireBeanPropertiesIsRecognized() {
        application()
        assertTrue(annotations(autowired("void wire(org.springframework.beans.factory.config.AutowireCapableBeanFactory factory) { factory.autowireBeanProperties(this, 0, false); }")).isEmpty())
    }

    fun testSpringInitializeBeanAloneIsNotPropertyAutowiring() {
        application()
        assertEquals(1, annotations(autowired("void wire(org.springframework.beans.factory.config.AutowireCapableBeanFactory factory) { factory.initializeBean(this, \"page\"); }")).size)
    }

    fun testCustomWicketInjectorImplementationCountsAsInjectionIntent() {
        source("CustomInjector", "public class CustomInjector extends org.apache.wicket.injection.Injector { public void inject(Object target) {} }")
        assertTrue(annotations(helper(body = "new CustomInjector().inject(this);")).isEmpty())
    }

    fun testInspectionHonorsCancellation() {
        val java = helper()
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                annotations(java)
            }, indicator)
        }
    }
}
