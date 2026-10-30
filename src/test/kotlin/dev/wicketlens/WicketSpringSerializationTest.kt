package dev.wicketlens

import com.intellij.psi.PsiDocumentManager
import dev.wicketlens.spring.WicketSpringSetup

class WicketSpringSerializationTest : WicketSpringInspectionTestCase() {
    fun testTransientSpringBeanWarnsFromExplicitLocalIntent() {
        val finding = serialization(page("@SpringBean private transient Service orders;")).single()
        assertEquals("orders", finding.psiElement.text)
        assertTrue(finding.descriptionTemplate.contains("may be null"))
        assertTrue(finding.fixes.orEmpty().isEmpty())
    }

    fun testUnannotatedTransientFieldDoesNotActivateSpring() = assertTrue(serialization(page("private transient Service orders;")).isEmpty())
    fun testNonTransientReferenceWithoutKnownProxyPolicyHasNoFinding() = assertTrue(serialization(page("@SpringBean private Service orders;")).isEmpty())

    fun testFinalFieldTypeWithoutKnownProxyPolicyDoesNotWarn() {
        finalService()
        assertTrue(serialization(page("@SpringBean private FinalService orders;")).isEmpty())
    }

    fun testDefaultWrappingRejectsAFinalConcreteDeclaredType() {
        application()
        finalService()
        val finding = serialization(page("@SpringBean private FinalService orders;")).single()
        assertEquals("FinalService", finding.psiElement.text)
        assertTrue(finding.descriptionTemplate.contains("cannot be subclass-proxied"))
    }

    fun testExplicitWrappingRejectsAFinalConcreteDeclaredType() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), true")
        finalService()
        assertTrue(serialization(page("@SpringBean private FinalService orders;")).single().descriptionTemplate.contains("subclass-proxied"))
    }

    fun testRawFinalNonSerializableBeanHasAConditionalSerializationFinding() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), false")
        finalService()
        val finding = serialization(page("@SpringBean private FinalService orders;")).single()
        assertTrue(finding.descriptionTemplate.contains("disables proxy wrapping"))
        assertTrue(finding.descriptionTemplate.contains("non-null instance"))
    }

    fun testRawFinalSerializableTypeDoesNotWarn() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), false")
        source("FinalService", "public final class FinalService implements java.io.Serializable {}")
        assertTrue(serialization(page("@SpringBean private FinalService orders;")).isEmpty())
    }

    fun testRawInterfaceDoesNotProveThatItsRuntimeBeanIsNonSerializable() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), false")
        source("Orders", "public interface Orders {}")
        assertTrue(serialization(page("@SpringBean private Orders orders;")).isEmpty())
    }

    fun testRawNonfinalClassMayHaveASerializableSubtype() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), false")
        assertTrue(serialization(page("@SpringBean private Service orders;")).isEmpty())
    }

    fun testTransientRawFieldDoesNotAlsoGetANonSerializableStorageWarning() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), false")
        finalService()
        val finding = serialization(page("@SpringBean private transient FinalService orders;")).single()
        assertTrue(finding.descriptionTemplate.contains("transient"))
    }

    fun testStringWrappersAndEnumsKeepWicketsDirectValueExceptions() {
        application()
        source("State", "public enum State { OPEN }")
        val java = page("@SpringBean String title; @SpringBean Integer count; @SpringBean Boolean enabled; @SpringBean State state;")
        assertTrue(serialization(java).isEmpty())
    }

    fun testNormalInterfaceAndNonfinalClassDoNotHaveFinalProxyFindings() {
        application()
        source("Orders", "public interface Orders {}")
        assertTrue(serialization(page("@SpringBean Orders orders; @SpringBean Service service;")).isEmpty())
    }

    fun testRecordIsAFinalConcreteProxyType() {
        application()
        source("RecordService", "public record RecordService(String name) {}")
        assertTrue(serialization(page("@SpringBean RecordService orders;")).single().descriptionTemplate.contains("subclass-proxied"))
    }

    fun testPlainPojoDoesNotAcquireWicketSerializationRules() {
        assertTrue(serialization(source("Plain", "public class Plain { @org.apache.wicket.spring.injection.annot.SpringBean transient Service orders; }")).isEmpty())
    }

    fun testCustomReadObjectMakesTransientRestorationUnknown() {
        assertTrue(serialization(page("@SpringBean transient Service orders; private void readObject(java.io.ObjectInputStream in) throws Exception { in.defaultReadObject(); org.apache.wicket.injection.Injector.get().inject(this); }")).isEmpty())
    }

    fun testCustomReadResolveMakesTransientRestorationUnknown() {
        assertTrue(serialization(page("@SpringBean transient Service orders; private Object readResolve() { return this; }")).isEmpty())
    }

    fun testInheritedCustomSerializationHookIsRespected() {
        source("BasePage", "public class BasePage extends org.apache.wicket.markup.html.WebPage { private Object readResolve() { return this; } }")
        assertTrue(serialization(page("@SpringBean transient Service orders;", base = "BasePage")).isEmpty())
    }

    fun testAutowiredDoesNotActivateWicketSpringSerializationRules() = assertTrue(serialization(page("@org.springframework.beans.factory.annotation.Autowired transient Service orders;")).isEmpty())
    fun testInjectAloneDoesNotActivateWicketSpringSerializationRules() = assertTrue(serialization(page("@Inject transient Service orders;")).isEmpty())

    fun testInjectWithKnownSpringSetupReceivesTheTransientCheck() {
        application()
        assertEquals(1, serialization(page("@Inject transient Service orders;")).size)
    }

    fun testUnknownWrappingFlagDoesNotImplyAProxyPolicy() {
        application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), wrap", extra = "private boolean wrap = true;")
        finalService()
        val java = page("@SpringBean FinalService orders;")
        assertTrue(WicketSpringSetup.forFile(java).registered)
        assertEquals(WicketSpringSetup.Proxies.UNKNOWN, WicketSpringSetup.forFile(java).proxies)
        assertTrue(serialization(java).isEmpty())
    }

    fun testMultipleApplicationsDoNotImplyAProxyPolicy() {
        application()
        application("OtherApplication", "this, new org.springframework.context.support.GenericApplicationContext(), false")
        finalService()
        assertTrue(serialization(page("@SpringBean FinalService orders;")).isEmpty())
    }

    fun testNativeSerializationSuppressionIsHonored() = assertTrue(serialization(page("@SuppressWarnings(\"WicketSpringSerialization\") @SpringBean transient Service orders;")).isEmpty())

    fun testApplicationEditsInvalidateCachedProxyEvidence() {
        val app = application(arguments = "this, new org.springframework.context.support.GenericApplicationContext(), true")
        finalService()
        val java = page("@SpringBean FinalService orders;")
        assertTrue(serialization(java).single().descriptionTemplate.contains("subclass-proxied"))
        edit(app, app.text.replace(", true", ", false"))
        assertTrue(serialization(java).single().descriptionTemplate.contains("disables proxy wrapping"))
    }

    fun testNewApplicationInvalidatesCachedSingleApplicationEvidence() {
        application()
        finalService()
        val java = page("@SpringBean FinalService orders;")
        assertEquals(1, serialization(java).size)
        application("OtherApplication")
        assertTrue(serialization(java).isEmpty())
    }

    fun testUncommittedApplicationCannotYieldAStaleProxyFinding() {
        val app = application()
        finalService()
        val java = page("@SpringBean FinalService orders;")
        assertEquals(1, serialization(java).size)
        edit(app, app.text.replace("super.init();", "super.init(); if (false) return;"), commit = false)
        try { assertTrue(serialization(java).isEmpty()) }
        finally { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    }

    fun testInspectionsAreReadOnlyAndDeterministic() {
        application()
        finalService()
        val java = page("@SpringBean transient FinalService orders = null;")
        val before = java.text
        val first = serialization(java).map { it.psiElement.text to it.descriptionTemplate }
        assertEquals(first, serialization(java).map { it.psiElement.text to it.descriptionTemplate })
        assertEquals(1, injection(java).size)
        assertEquals(before, java.text)
    }

    private fun finalService() = source("FinalService", "public final class FinalService {}")
}
