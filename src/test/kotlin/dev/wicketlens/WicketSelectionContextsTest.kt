package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.model.*

class WicketSelectionContextsTest : WicketProjectPsiTestCase() {
    private fun setup() = WicketSelectionFixture.install(myFixture)
    private fun result(java: PsiJavaFile, limits: WicketReplacementContexts.Limits = WicketReplacementContexts.Limits()) =
        WicketReplacementContexts(limits).analyze(java.classes.single(), WicketPageAnalyzer().analyze(java.classes.single())!!)
    private fun change(path: String, before: String, after: String) {
        val file = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(java: PsiJavaFile) { val r=result(java); assertTrue(r.toString(),r.reports.isEmpty()); assertTrue(r.toString(),r.diagnostics.isNotEmpty()) }
    fun testRegistrationsHaveSeparateSourceSelectionScopesAndUnknownRuntimeIndex() {
        val java=setup(); val primaryBefore=WicketPageAnalyzer().analyze(java.classes.single())!!; val r=result(java); assertTrue(r.diagnostics.toString(),r.complete)
        assertEquals(2,r.reports.size)
        assertEquals(listOf(0,1),r.reports.map { it.lifecycle!!.selection!!.registrationIndex })
        assertEquals(listOf("First","Second"),r.reports.map { it.lifecycle!!.selection!!.title })
        r.reports.forEach { report ->
            assertEquals("flow:form:body",report.lifecycle!!.componentPath)
            assertEquals(2,report.lifecycle.selection!!.registrationCount)
            assertNotNull(report.lifecycle.selection.initializationLocation)
            assertEquals(4,report.matching!!.findings.count { it.code==DiagnosticCode.MATCHED })
            assertTrue(report.matching.findings.all { it.confidence==Confidence.INFERRED })
            assertTrue(report.nodes.filter { it.wicketId!=null }.all { it.template && !it.idRenameSafe })
        }
        assertEquals(setOf("name"),r.reports[0].nodes.filter { it.parentNodeId==r.reports[0].lifecycle!!.factoryNodeId }.mapNotNull { it.wicketId }.toSet())
        assertEquals(setOf("total"),r.reports[1].nodes.filter { it.parentNodeId==r.reports[1].lifecycle!!.factoryNodeId }.mapNotNull { it.wicketId }.toSet())
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNull(primary.lifecycle); assertEquals(primaryBefore,primary)
    }
    fun testLambdaArrowAndSharedCoreSlotNavigateWithoutEditingAuthority() {
        val java=setup(); val lambda=PsiTreeUtil.findChildrenOfType(java,PsiLambdaExpression::class.java).first()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val arrow=lambda.node.findChildByType(JavaTokenType.ARROW)!!.textRange.startOffset
        val targets=WicketReplacementDeclarationHandler().getGotoDeclarationTargets(java.findElementAt(arrow),arrow,myFixture.editor)!!
        assertEquals(1,targets.size)
        val attribute=targets.single() as XmlAttributeValue
        val reverse=attribute.references.filterIsInstance<WicketIdReference>().single().multiResolve(false)
        assertTrue(reverse.any { it.element==lambda }); assertEquals(2,reverse.count { it.element is PsiLambdaExpression })
        assertNull(WicketIdRenameTarget.at(attribute.containingFile,attribute.textOffset+1))
        val name=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="name" }
        assertTrue(name.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).isNotEmpty())
    }
    fun testReorderedSourceRegistrationsKeepTheirOwnDefinitions() {
        val java=setup()
        val lines=java.text.lines(); val first=lines.single { "flow.add(\"First\"" in it }; val second=lines.single { "flow.add(\"Second\"" in it }
        change("src/main/java/example/ExamplePage.java",first,"SWAP"); change("src/main/java/example/ExamplePage.java",second,first); change("src/main/java/example/ExamplePage.java","SWAP",second)
        val r=result(java); assertEquals(listOf("second","first"),r.reports.map { it.lifecycle!!.selection!!.definitionId })
        assertEquals(listOf("Second","First"),r.reports.map { it.lifecycle!!.selection!!.title })
    }
    fun testUnknownRegisteredFactoryDoesNotInventOrdinals() { val java=setup(); change("src/main/java/example/ExamplePage.java", "flow.add(\"First\"", "flow.add(\"Other\", (java.util.function.Function<String,org.apache.wicket.Component>) null); flow.add(\"First\""); withheld(java) }
    fun testOverriddenRegistrationDoesNotUseTheBaseAppendContract() { val java=setup(); change("src/main/java/example/ExamplePage.java", "new Flow(\"flow\")", "new Flow(\"flow\") { @Override public Flow add(org.apache.wicket.model.IModel<String> title, java.util.function.Function<String,? extends org.apache.wicket.Component> factory) { return this; } }"); withheld(java) }
    fun testFluentHelperOverrideMustKeepTheAllocatedReceiver() {
        val java=setup()
        myFixture.addFileToProject("src/main/java/example/Shifted.java", """
            package example; class Base extends org.apache.wicket.markup.html.basic.Label {
                Base(String id) { super(id); } Base configure() { return self(); } Base self() { return this; }
            }
            public class Shifted extends Base { public Shifted(String id) { super(id); } @Override Base self() { return new Shifted("foreign"); } }
        """)
        change("src/main/java/example/ExamplePage.java", "new org.apache.wicket.markup.html.basic.Label(\"name\")", "new Shifted(\"name\").configure()")
        withheld(java)
    }
    fun testCustomRecordAccessorDoesNotPreserveTheFactory() { val java=setup(); change("src/main/java/example/Flow.java", "factory) {}", "factory) { public java.util.function.Function<String,? extends org.apache.wicket.Component> factory() { return null; } }"); assertTrue(result(java).reports.isEmpty()) }
    fun testOtherRecordLookupCannotEscapeTheSelector() { val java=setup(); change("src/main/java/example/Flow.java", "selected=value;", "Object escaped=entries.get(0); selected=value;"); withheld(java) }
    fun testListClearingDoesNotKeepTheDeclaredOrder() { val java=setup(); change("src/main/java/example/Flow.java","selected=value;","entries.clear(); selected=value;"); withheld(java) }
    fun testListEscapeDoesNotKeepTheDeclaredOrder() { val java=setup(); change("src/main/java/example/Flow.java","selected=value;","java.util.Collections.reverse(entries); selected=value;"); withheld(java) }
    fun testConditionalRegistrationDoesNotInventAnOrdinal() { val java=setup(); change("src/main/java/example/ExamplePage.java","flow.add(\"First\"","if (System.nanoTime()>0) flow.add(\"First\""); withheld(java) }
    fun testFragmentMustUseTheSuppliedId() { val java=setup(); change("src/main/java/example/ExamplePage.java","Fragment(id,","Fragment(\"wrong\","); withheld(java) }
    fun testReassignedFactoryIdCannotBeTreatedAsForwarding() { val java=setup(); change("src/main/java/example/ExamplePage.java","id -> {","id -> { id=\"wrong\";"); withheld(java) }
    fun testReassignedFragmentCannotKeepTheFirstDeclaration() { val java=setup(); change("src/main/java/example/ExamplePage.java","return step;","step=null; return step;"); withheld(java) }
    fun testFragmentEscapeDoesNotDeclareAnOwnedScope() { val java=setup(); change("src/main/java/example/ExamplePage.java","return step;","java.util.Objects.requireNonNull(step); return step;"); withheld(java) }
    fun testMutationOutsideTheFragmentWithholdsTheStep() { val java=setup(); change("src/main/java/example/ExamplePage.java","return step;","removeAll(); return step;"); withheld(java) }
    fun testCompetingBodyParentsWithholdSelection() { val java=setup(); change("src/main/java/example/Flow.java","form.addOrReplace(content);","form.addOrReplace(content); addOrReplace(content);"); withheld(java) }
    fun testUnknownIndexReceiverIsNotTheControllerIndex() { val java=setup(); change("src/main/java/example/Flow.java","entries.get(selected)","entries.get(System.identityHashCode(this))"); withheld(java) }
    fun testChangedResourceDoesNotInventMatchingFields() { val java=setup(); change("src/main/resources/example/Flow.html","wicket:id='body'","wicket:id='other'"); withheld(java) }
    fun testContextLimitPublishesNoPartialRegistrationSet() { val java=setup(); val r=result(java,WicketReplacementContexts.Limits(contexts=1)); assertFalse(r.complete); assertTrue(r.reports.isEmpty()) }
    fun testCancellationPropagates() {
        val java=setup(); val primary=WicketPageAnalyzer().analyze(java.classes.single())!!; val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel(); WicketReplacementContexts().analyze(java.classes.single(),primary) },indicator) }
    }
}
