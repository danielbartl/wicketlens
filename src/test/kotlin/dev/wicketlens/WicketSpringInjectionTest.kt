package dev.wicketlens

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import dev.wicketlens.spring.WicketSpringSupport

class WicketSpringInjectionTest : WicketSpringInspectionTestCase() {
    fun testUsesTheActualWicketSpringAndJakartaInjectionAnnotations() {
        val facade = JavaPsiFacade.getInstance(project)
        assertTrue(facade.findClass(WicketSpringSupport.BEAN, GlobalSearchScope.allScope(project)) is PsiCompiledElement)
        assertTrue(facade.findClass("jakarta.inject.Inject", GlobalSearchScope.allScope(project)) is PsiCompiledElement)
    }

    fun testExplicitNullInitializerWarnsWithoutAssumingAnActiveApplication() {
        val problem = injection(page("@SpringBean private Service orders = null;")).single()
        assertEquals("null", problem.psiElement.text)
        assertTrue(problem.descriptionTemplate.contains("may overwrite"))
        assertTrue(problem.fixes.orEmpty().isEmpty())
    }

    fun testNonNullInitializerAlsoOverwritesConstructionInjection() = assertEquals(1,
        injection(page("@SpringBean private Service orders = new Service();")).size)

    fun testConstructorAssignmentWarnsAtTheAssignedFieldReference() {
        val problem = injection(page("@SpringBean private Service orders;", "this.orders = null;")).single()
        assertEquals("this.orders", problem.psiElement.text)
    }

    fun testInstanceInitializerAssignmentWarns() = assertEquals(1,
        injection(page("@SpringBean private Service orders; { orders = null; }")).size)

    fun testAssignmentInAnotherFieldInitializerWarns() = assertEquals(1,
        injection(page("@SpringBean private Service orders; private Service backup = orders = null;")).size)

    fun testBehaviorFieldsUseTheirAutomaticConstructionInjection() = assertEquals(1,
        injection(page("@SpringBean private Service orders = null;", base = "org.apache.wicket.behavior.Behavior")).size)

    fun testPanelFieldsUseTheSameLocalRule() = assertEquals(1,
        injection(page("@SpringBean private Service orders = null;", "super(\"panel\");", "org.apache.wicket.markup.html.panel.Panel")).size)

    fun testInheritedAnnotatedFieldAssignmentIsRecognized() {
        source("BasePage", "public class BasePage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean protected Service orders; }")
        assertEquals(1, injection(page("", "orders = null;", "BasePage")).size)
    }

    fun testUninitializedSpringBeanFieldHasNoOverwriteFinding() = assertTrue(injection(page("@SpringBean private Service orders;")).isEmpty())
    fun testUnannotatedInitializerDoesNotActivateSpringRules() = assertTrue(injection(page("private Service orders = null;")).isEmpty())
    fun testOrdinaryMethodAssignmentsAreOutsideTheConstructionRule() = assertTrue(injection(page("@SpringBean private Service orders; private void reset() { orders = null; }")).isEmpty())
    fun testDeferredLambdaWritesAreNotConstructorWrites() = assertTrue(injection(page("@SpringBean private Service orders;", "Runnable reset = () -> orders = null;")).isEmpty())
    fun testNestedAnonymousCallbackWritesAreNotConstructorWrites() = assertTrue(injection(page("@SpringBean private Service orders;", "Runnable reset = new Runnable() { public void run() { orders = null; } };" )).isEmpty())
    fun testOtherInstanceAssignmentsDoNotWarnAboutTheCurrentComponent() = assertTrue(injection(page("@SpringBean public Service orders;", "ExamplePage other = null; other.orders = null;")).isEmpty())
    fun testStaticFieldsDoNotAcquireTheInstanceInjectionContract() = assertTrue(injection(page("@SpringBean private static Service orders = null;")).isEmpty())
    fun testStaticInitializerWritesAreNotInstanceConstructionWrites() = assertTrue(injection(page("@SpringBean private Service orders; static { ExamplePage page = null; page.orders = null; }")).isEmpty())
    fun testPrimitiveFieldsAreNotTreatedAsInjectedNullReferences() = assertTrue(injection(page("@SpringBean private int count = 0;")).isEmpty())

    fun testPlainPojoWithSpringBeanDoesNotAcquireAutomaticWicketInjection() {
        assertTrue(injection(source("Plain", "public class Plain { @org.apache.wicket.spring.injection.annot.SpringBean private Service orders = null; }")).isEmpty())
    }

    fun testModelsDoNotAcquireAutomaticComponentInjection() = assertTrue(injection(page("@SpringBean private Service orders = null;", base = "org.apache.wicket.model.Model<String>")).isEmpty())

    fun testSessionsDoNotAcquireAutomaticComponentInjection() {
        val java = source("Session", "public abstract class Session extends org.apache.wicket.Session { @org.apache.wicket.spring.injection.annot.SpringBean Service orders = null; protected Session(org.apache.wicket.request.Request request) { super(request); } }")
        assertTrue(injection(java).isEmpty())
    }

    fun testAutowiredIsNotSilentlyTreatedAsSpringBean() = assertTrue(injection(page("@org.springframework.beans.factory.annotation.Autowired private Service orders = null;")).isEmpty())
    fun testInjectAloneDoesNotIdentifySpring() = assertTrue(injection(page("@Inject private Service orders = null;")).isEmpty())

    fun testInjectIsRecognizedWithASingleRegisteredSpringApplication() {
        application()
        assertEquals(1, injection(page("@Inject private Service orders = null;")).size)
    }

    fun testUnrelatedAnnotationWithTheSameSimpleNameDoesNotActivateRules() {
        source("SpringBean", "public @interface SpringBean {}")
        val java = source("Page", "public class Page extends org.apache.wicket.markup.html.WebPage { @SpringBean Service orders = null; }")
        assertTrue(injection(java).isEmpty())
    }

    fun testNullInitializerImmediatelyRepairedByExplicitInjectionIsExcluded() {
        assertTrue(injection(page("@SpringBean private Service orders = null;", "org.apache.wicket.injection.Injector.get().inject(this);")).isEmpty())
    }

    fun testNullAssignmentImmediatelyRepairedByExplicitInjectionIsExcluded() {
        assertTrue(injection(page("@SpringBean private Service orders;", "orders = null; org.apache.wicket.injection.Injector.get().inject(this);")).isEmpty())
    }

    fun testNonNullInitializerIsNotRepairedByAnInjectorThatSkipsNonNullFields() {
        assertEquals(1, injection(page("@SpringBean private Service orders = new Service();", "org.apache.wicket.injection.Injector.get().inject(this);")).size)
    }

    fun testReinjectionAfterAFieldReadDoesNotHideTheInitializerRisk() {
        assertEquals(1, injection(page("@SpringBean private Service orders = null;", "orders.toString(); org.apache.wicket.injection.Injector.get().inject(this);")).size)
    }

    fun testAnotherFieldInitializerMayReadTheResetValueBeforeReinjection() {
        assertEquals(1, injection(page("@SpringBean private Service orders = null; private String name = orders.toString();", "org.apache.wicket.injection.Injector.get().inject(this);")).size)
    }

    fun testOneRepairedConstructorDoesNotHideAnotherUnrepairedConstructor() {
        val java = page("@SpringBean private Service orders = null; public ExamplePage(int value) {}", "org.apache.wicket.injection.Injector.get().inject(this);")
        assertEquals(1, injection(java).size)
    }

    fun testLiteralFalseConstructorBranchIsExcluded() = assertTrue(injection(page("@SpringBean private Service orders;", "if (false) orders = null;")).isEmpty())
    fun testPossibleConditionalConstructorWriteIsReported() = assertEquals(1, injection(page("@SpringBean private Service orders;", "if (System.getProperty(\"reset\") != null) orders = null;")).size)

    fun testLocalVariableShadowDoesNotBecomeAnInjectedFieldWrite() = assertTrue(injection(page("@SpringBean private Service orders;", "Service orders = null; orders = new Service();")).isEmpty())

    fun testNativeSuppressionAnnotationIsHonored() {
        assertTrue(injection(page("@SuppressWarnings(\"WicketSpringInjection\") @SpringBean private Service orders = null;")).isEmpty())
    }

    fun testNativeSuppressionCommentIsHonored() {
        assertTrue(injection(page("@SpringBean private Service orders;", "\n//noinspection WicketSpringInjection\n orders = null;")).isEmpty())
    }

    fun testUncommittedSourceDoesNotProduceAStaleFinding() {
        val java = page("@SpringBean private Service orders = null;")
        assertEquals(1, injection(java).size)
        edit(java, java.text.replace(" = null", ""), commit = false)
        try { assertTrue(injection(java).isEmpty()) }
        finally { PsiDocumentManager.getInstance(project).commitAllDocuments() }
        assertTrue(injection(java).isEmpty())
    }

    fun testRemovingTheAnnotationUpdatesFindingsWithoutTheSidebar() {
        val java = page("@SpringBean private Service orders = null;")
        assertEquals(1, injection(java).size)
        edit(java, java.text.replace("@SpringBean", "@Deprecated"))
        assertTrue(injection(java).isEmpty())
    }
}
