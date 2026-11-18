package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.progress.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.editor.*
import dev.wicketlens.model.*

class WicketRegisteredListTest:WicketProjectPsiTestCase() {
    private lateinit var java:PsiJavaFile
    override fun setUp() { super.setUp();java=WicketRegisteredListFixture.install(myFixture) }
    private fun report()=WicketPageAnalyzer().analyze(java.classes.single())!!
    private fun change(from:String,to:String,name:String="DecoratedList") {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(file.text,from in file.text)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(from,to)) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testRegisteredItemDataBridgeRestoresPrimaryAttachmentsAndSymbolicChildren() {
        val report=report()
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count { it.code==DiagnosticCode.MATCHED })
        val host=report.nodes.single { it.wicketId=="sections" }
        assertNotNull(host.registeredListPopulation)
        assertTrue(host.template);assertFalse(host.idRenameSafe)
        assertEquals(1,report.populationContexts.size)
        val context=report.populationContexts.single()
        assertEquals("sections",context.populationTemplate!!.hostComponentPath)
        assertNotNull(context.populationTemplate.registration)
        assertEquals(2,context.matching!!.findings.size)
        assertTrue(context.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertEquals(setOf("title","body"),context.nodes.filter { it.parentNodeId==context.populationTemplate.itemNodeId }.mapNotNull { it.wicketId }.toSet())
        assertEquals(2,report.matching.findings.count { it.code==DiagnosticCode.UNRESOLVED_BRANCH })
    }
    fun testCapturedPageMutationKeepsThePrimaryScopeUnproved() {
        change("item.add(new org.apache.wicket.markup.html.basic.Label(\"title\",model.title()));", "add(new org.apache.wicket.markup.html.basic.Label(\"rogue\")); item.add(new org.apache.wicket.markup.html.basic.Label(\"title\",model.title()));", "CallbackPage")
        assertTrue(report().matching!!.findings.none { it.code==DiagnosticCode.MATCHED })
    }
    fun testChangedItemFactoryIsNotAccepted() {
        change("ListItem<T> item=super.newItem(index,model);", "ListItem<T> item=new ListItem<>(\"fixed\",index,model);")
        assertNull(report().nodes.firstOrNull { it.wicketId=="sections" }?.registeredListPopulation)
    }
    fun testExtraFactoryEffectsKeepTheOldOpaqueGuard() {
        change("java.util.Objects.requireNonNull(callback);", "java.util.Objects.requireNonNull(callback); System.out.println(callback);")
        assertNull(report().nodes.firstOrNull { it.wicketId=="sections" }?.registeredListPopulation)
    }
    fun testHostChildIdsAndCallbackArrowNavigateBothWays() {
        report()
        for(id in listOf("sections","title","body")) {
            val literal=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value==id }
            val uses=literal.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }
            assertEquals(id,1,uses.size)
            assertTrue(id,uses.single().element!!.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element==literal })
        }
        val lambda=PsiTreeUtil.findChildrenOfType(java,PsiLambdaExpression::class.java).single()
        val slot=WicketReplacementNavigation.targets(lambda).single()
        assertTrue(slot.text,slot.text.contains("sections"))
        assertTrue(slot.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element==lambda })
        assertNull(WicketIdRenameTarget.at(slot.containingFile,slot.textOffset+1))
    }
    private fun noRegistration() { assertTrue(report().nodes.none { it.registeredListPopulation!=null }) }
    fun testDifferentDefaultIndexIsNotReviewed() { change("super.newItem(index,model)","super.newItem(index+1,model)");noRegistration() }
    fun testDifferentDefaultModelIsNotReviewed() { change("super.newItem(index,model)","super.newItem(index,org.apache.wicket.model.Model.of((T)null))");noRegistration() }
    fun testWrongBridgeItemIsNotForwarding() { change("callback.accept(item,item.getModelObject())","callback.accept(new ListItem<>(\"other\",0),item.getModelObject())");noRegistration() }
    fun testWrongBridgeModelIsNotForwarding() { change("callback.accept(item,item.getModelObject())","callback.accept(item,null)");noRegistration() }
    fun testCallbackStorageIsNotReviewed() { change("java.util.Objects.requireNonNull(callback);","java.util.function.BiConsumer<ListItem<T>,T> alias=callback; java.util.Objects.requireNonNull(callback);");noRegistration() }
    fun testConditionalCallbackBridgeIsNotFlattened() { change("callback.accept(item,item.getModelObject());","if(item.getIndex()>0) callback.accept(item,item.getModelObject());");noRegistration() }
    fun testDuplicateCallbackBridgeIsNotReviewed() { change("callback.accept(item,item.getModelObject());","callback.accept(item,item.getModelObject()); callback.accept(item,item.getModelObject());");noRegistration() }
    fun testBehaviorBindEffectsStayGuarded() { change("private final boolean exclusive;","private final boolean exclusive; @Override public void bind(org.apache.wicket.Component component) { component.setVisible(false); }","Decoration");noRegistration() }
    fun testBehaviorTemporaryCheckEffectsStayGuarded() { change("private final boolean exclusive;","private final boolean exclusive; @Override public boolean isTemporary(org.apache.wicket.Component component) { component.setVisible(false); return false; }","Decoration");noRegistration() }
    fun testItemFactoryCannotAddChildrenOutsideThePopulation() { change("item.add(behavior);","item.add(new org.apache.wicket.markup.html.basic.Label(\"extra\")); item.add(behavior);");noRegistration() }
    fun testItemFactoryCannotAttachTheItemElsewhere() { change("item.add(behavior);","add(item); item.add(behavior);");noRegistration() }
    fun testItemFactoryCannotReturnABorrowedItem() { change("return item;","return (ListItem<T>)get(\"old\");");noRegistration() }
    fun testListLifecycleOverrideCannotBypassTheDefaultItemContract() { change("public static <T> DecoratedList<T> of(String id,IModel<java.util.List<T>> model,java.util.function", "@Override protected void onInitialize() { super.onInitialize(); } public static <T> DecoratedList<T> of(String id,IModel<java.util.List<T>> model,java.util.function");noRegistration() }
    fun testSourceFactoryCannotComputeAnExtraConstructorArgument() { change("new DecoratedList<T>(id,model,exclusive)","new DecoratedList<T>(id,model,Boolean.getBoolean(\"flag\"))");noRegistration() }
    fun testSourceFactoryMustForwardItsIdRatherThanAnEqualLiteral() { change("new DecoratedList<T>(id,model,exclusive)","new DecoratedList<T>(\"sections\",model,exclusive)");noRegistration() }
    fun testListConstructorCannotAddUnmodeledChildren() { change("add(behavior); }", "add(behavior); add(new org.apache.wicket.markup.html.basic.Label(\"0\")); }");noRegistration() }
    fun testListConstructorCannotEscapeItsReference() { change("add(behavior); }", "add(behavior); System.out.println(this); }");noRegistration() }
    fun testCapturedChildCannotBeReusedAcrossRows() {
        change("add(DecoratedList.of", "org.apache.wicket.markup.html.basic.Label saved=new org.apache.wicket.markup.html.basic.Label(\"saved\"); add(DecoratedList.of", "CallbackPage")
        change("item.add(new org.apache.wicket.markup.html.basic.Label(\"title\",model.title()));", "item.add(saved); item.add(new org.apache.wicket.markup.html.basic.Label(\"title\",model.title()));", "CallbackPage")
        assertTrue(report().matching!!.findings.none { it.code==DiagnosticCode.MATCHED })
    }
    fun testOpaqueAcquisitionGuardForAnUnreviewedFactoryStillStops() {
        change("return of(id,model,true,callback);","return unknown(id,model,callback);")
        change("public abstract class DecoratedList<T> extends ListView<T> {","public abstract class DecoratedList<T> extends ListView<T> { private static <T> DecoratedList<T> unknown(String id,IModel<java.util.List<T>> model,java.util.function.BiConsumer<ListItem<T>,T> callback) { throw new UnsupportedOperationException(); }")
        noRegistration()
    }
    fun testCancellationPropagates() {
        val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();report() },indicator) }
    }
}
