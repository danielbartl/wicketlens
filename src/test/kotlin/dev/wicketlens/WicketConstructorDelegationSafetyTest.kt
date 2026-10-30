package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketIdConstructors
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.editor.WicketIdRenameTarget
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode

class WicketConstructorDelegationSafetyTest : WicketProjectPsiTestCase() {
    fun testEarlierAssignmentCannotChangeALaterForwardedIdSilently() = unresolved("""
        public CustomLabel(String id) { this(id = "actual", id); }
        public CustomLabel(String ignored, String actualId) { super(actualId); }
    """)

    fun testNestedAssignmentInANonIdArgumentIsRejected() = unresolved("""
        public CustomLabel(String id) { this((Object) (id = "actual"), id); }
        public CustomLabel(Object ignored, String actualId) { super(actualId); }
    """)

    fun testAssignmentInsideACallArgumentIsRejected() = unresolved("""
        public CustomLabel(String id) { this(value(id = "actual"), id); }
        public CustomLabel(Object ignored, String actualId) { super(actualId); }
        private static Object value(String value) { return value; }
    """)

    fun testAssignmentsInConditionalArgumentsAreRejected() = unresolved("""
        public CustomLabel(String id) { this(id.isEmpty() ? (id = "other") : (id = "actual"), id); }
        public CustomLabel(String ignored, String actualId) { super(actualId); }
    """)

    fun testLaterArgumentEffectsAreRejectedEvenIfTheIdWasAlreadyRead() = unresolved("""
        public CustomLabel(String id) { this(id, id = "actual"); }
        public CustomLabel(String actualId, String ignored) { super(actualId); }
    """)

    fun testFixedTargetLiteralDoesNotBypassArgumentValidation() = unresolved("""
        public CustomLabel(String id) { this(id = "actual", id); }
        public CustomLabel(String ignored, String actualId) { super("title"); }
    """)

    fun testUnknownNonIdCallsAreNotAssumedToBeSafe() = unresolved("""
        public CustomLabel(String id) { super(id, caption()); }
        private static String caption() { return "caption"; }
    """)

    fun testUpdatesInNonIdArgumentsAreRejected() = unresolved("""
        public CustomLabel(String id) { this(id, 0); }
        public CustomLabel(String id, int index) { this(index++, id); }
        public CustomLabel(int ignored, String id) { super(id); }
    """)

    fun testParenthesizedForwardingWithLiteralAndClassArgumentsRemainsSupported() {
        wrapper("""
            public CustomLabel(String id) { this("caption", (id), CustomLabel.class); }
            public CustomLabel(String caption, String id, Class<?> type) { super((id), (caption)); }
        """)
        val java = pageFile(body = "add(new CustomLabel(\"title\"));")
        companion()
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(DiagnosticCode.MATCHED, report.matching!!.findings.single().code)
        assertEquals("title", report.nodes.single { it.parentNodeId != null }.wicketId)
        assertNotNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"")))
    }

    private fun unresolved(constructors: String) {
        wrapper(constructors)
        val java = pageFile(body = "add(new CustomLabel(\"title\"));")
        companion()
        val creation = PsiTreeUtil.findChildOfType(java, PsiNewExpression::class.java)!!
        assertTrue("Fixture constructor must resolve", creation.resolveMethodGenerics().isValidResult)
        assertNull(WicketIdConstructors.trace(creation))
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val node = report.nodes.single { it.parentNodeId != null }
        assertNull(node.wicketId)
        assertEquals(Confidence.UNRESOLVED, node.confidence)
        assertTrue(node.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_CONSTRUCTOR })
        assertTrue(report.matching!!.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
        assertNull(WicketIdPsi.pair(java.idValue("title")))
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"")))
    }

    private fun wrapper(constructors: String) = myFixture.addFileToProject("src/main/java/example/CustomLabel.java", """
        package example; public class CustomLabel extends org.apache.wicket.markup.html.basic.Label {
            ${constructors.trimIndent()}
        }
    """.trimIndent()) as PsiJavaFile
}
