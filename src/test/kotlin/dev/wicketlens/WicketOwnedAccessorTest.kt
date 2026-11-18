package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode

class WicketOwnedAccessorTest:WicketProjectPsiTestCase() {
    private fun change(before:String,after:String,file:String="Popover.java") {
        val psi=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$file"))!!
        assertTrue(before in psi.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(psi)!!.setText(psi.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(before:String,after:String,file:String="Popover.java") {
        val java=WicketOwnedAccessorFixture.install(myFixture);change(before,after,file)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
    }
    private fun matched(java:PsiJavaFile,notes:Int=1) {
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),notes,report.warnings.count {"Reviewed owned component accessor behavior decoration" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }
    fun testFreshOwnedGetterAllowsLiteralAttributeBehaviorWithoutPublishingPrivateChildren() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        matched(java)
    }
    fun testRepeatedDirectDecorationsKeepTheLocalOwnerProof() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        change("tools.add(chooser);","chooser.getTrigger().add(AttributeModifier.replace(\"title\",\"Open chooser\"));tools.add(chooser);","TableShell.java")
        matched(java,2)
    }
    fun testFieldInitializerCanEstablishTheChildIdentity() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        change("trigger=new WebMarkupContainer(\"trigger\");\n","")
        change("private final WebMarkupContainer trigger;","private final WebMarkupContainer trigger=new WebMarkupContainer(\"trigger\");")
        matched(java)
    }
    fun testExplicitThisFieldReturnKeepsTheIdentity() {
        val java=WicketOwnedAccessorFixture.install(myFixture);change("return trigger;","return this.trigger;");matched(java)
    }
    fun testSourceSubclassUsesTheInheritedPureGetter() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/Derived.java","package example;public class Derived extends Popover {public Derived(String id,java.util.function.Function<String,org.apache.wicket.Component> content){super(id,content);}}")
        change("new Popover(\"chooser\"","new Derived(\"chooser\"","TableShell.java");matched(java)
    }
    fun testAllocatedSubtypeSelectsItsOwnPureFieldGetter() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/Derived.java","package example;public class Derived extends Popover {private final org.apache.wicket.markup.html.WebMarkupContainer own;public Derived(String id,java.util.function.Function<String,org.apache.wicket.Component> content){super(id,content);own=new org.apache.wicket.markup.html.WebMarkupContainer(\"own\");add(own);}public org.apache.wicket.markup.html.WebMarkupContainer getTrigger(){return own;}}")
        change("new Popover(\"chooser\"","new Derived(\"chooser\"","TableShell.java");matched(java)
    }
    fun testAllocatedSubtypeCannotReuseTheBaseGetterProofForAnImpureOverride() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/Derived.java","package example;public class Derived extends Popover {public Derived(String id,java.util.function.Function<String,org.apache.wicket.Component> content){super(id,content);}public org.apache.wicket.markup.html.WebMarkupContainer getTrigger(){getPage();return super.getTrigger();}}")
        change("new Popover(\"chooser\"","new Derived(\"chooser\"","TableShell.java")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testThisConstructorDelegationReachesTheUniqueFieldAssignment() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        change("super(id);","this(id,content,1);}\npublic Popover(String id,java.util.function.Function<String,Component> content,int ignored){super(id);")
        matched(java)
    }
    fun testMutableChildIsNotAnOwnedAccessor()=withheld("private final WebMarkupContainer trigger;","private WebMarkupContainer trigger;")
    fun testProtectedChildIsNotAnOwnedAccessor()=withheld("private final WebMarkupContainer trigger;","protected final WebMarkupContainer trigger;")
    fun testStaticChildIsNotAnOwnedAccessor()=withheld("private final WebMarkupContainer trigger;","private static WebMarkupContainer trigger;")
    fun testAccessorExtraEffectsAreNotSkipped()=withheld("return trigger;","getPage();return trigger;")
    fun testSynchronizedAccessorIsUnreviewed()=withheld("public WebMarkupContainer getTrigger()","public synchronized WebMarkupContainer getTrigger()")
    fun testAccessorCannotReturnAnOuterField()=withheld("return trigger;","return getPage();")
    fun testSourceChildOverrideCannotBorrowTheFrameworkAdditionContract()=withheld("new WebMarkupContainer(\"trigger\")","new WebMarkupContainer(\"trigger\"){public Component add(org.apache.wicket.behavior.Behavior... behaviors){getPage();return this;}}")
    fun testConditionalFieldAssignmentIsUnreviewed()=withheld("trigger=new WebMarkupContainer(\"trigger\");","if(id!=null)trigger=new WebMarkupContainer(\"trigger\");else trigger=new WebMarkupContainer(\"other\");")
    fun testBorrowedFieldAllocationIsUnreviewed()=withheld("trigger=new WebMarkupContainer(\"trigger\");","trigger=(WebMarkupContainer)content.apply(\"trigger\");")
    fun testOtherGetterDoesNotAcquireTheSelectedFieldProof()=withheld("public WebMarkupContainer getTrigger()","public WebMarkupContainer expose(){return trigger;}public WebMarkupContainer getTrigger()")
    fun testChildCannotEscapeThroughConstructorAliases()=withheld("add(trigger);","WebMarkupContainer alias=trigger;add(alias);")
    fun testChildCannotEscapeToAConsumer()=withheld("add(trigger);","System.out.println(trigger);add(trigger);")
    fun testPriorOwnerAttachmentWithholdsTheProof()=withheld("chooser.getTrigger().add","add(chooser);chooser.getTrigger().add","TableShell.java")
    fun testPriorOwnerEscapeWithholdsTheProof()=withheld("chooser.getTrigger().add","System.out.println(chooser);chooser.getTrigger().add","TableShell.java")
    fun testReassignedOwnerIsNotFresh()=withheld("chooser.getTrigger().add","chooser=new Popover(\"other\",x -> new Label(x));chooser.getTrigger().add","TableShell.java")
    fun testOwnerAliasDoesNotBorrowTheAllocationProof()=withheld("chooser.getTrigger().add","Popover alias=chooser;alias.getTrigger().add","TableShell.java")
    fun testComputedReplacementInputIsUnreviewed()=withheld("\"outline small\"","\"outline\"+\" small\"","TableShell.java")
    fun testSourceBehaviorBindingCannotRunThroughTheAccessor()=withheld("AttributeModifier.replace(\"class\",\"outline small\")","new org.apache.wicket.behavior.Behavior(){public void bind(Component component){component.getPage();}}","TableShell.java")
    fun testComponentAdditionsDoNotAcquireTheBehaviorOnlyContract()=withheld("AttributeModifier.replace(\"class\",\"outline small\")","new Label(\"extra\")","TableShell.java")
    fun testOwnerConstructorEffectsRemainAudited()=withheld("super(id);","super(id);getPage();")
    fun testOwnerInterfaceInitializationRemainsAudited() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/Marker.java","package example;interface Marker {int TOKEN=seed();static int seed(){System.setProperty(\"owner\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("extends org.apache.wicket.markup.html.panel.Panel {","extends org.apache.wicket.markup.html.panel.Panel implements Marker {")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testUnrelatedFluentConfigurationKeepsItsExistingUnresolvedScope() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        change("tools.add(chooser);","new WebMarkupContainer(\"other\").setOutputMarkupId(true).add(AttributeModifier.replace(\"class\",\"other\"));tools.add(chooser);","TableShell.java")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertEquals(1,result.diagnostics.count {"Reviewed owned component accessor behavior decoration" in it.message})
    }
    fun testCompletedAccessorFactSurvivesALaterIndependentStop() {
        val java=WicketOwnedAccessorFixture.install(myFixture)
        change("tools.add(chooser);","tools.add(chooser);getPage();","TableShell.java")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertEquals(result.diagnostics.toString(),1,result.diagnostics.count {"Reviewed owned component accessor behavior decoration" in it.message})
    }
    private fun proof(record:(PsiElement)->Unit={}):WicketOwnedComponentAccessor.Proof {
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        val call=PsiTreeUtil.findChildrenOfType(shell,PsiMethodCallExpression::class.java).single {it.methodExpression.qualifierExpression is PsiMethodCallExpression && it.methodExpression.referenceName=="add"}
        return WicketOwnedComponentAccessor({ProgressManager.checkCanceled()},record,{}, {_,message->throw IllegalStateException(message)}).inspect(call)
    }
    private fun depth(count:Int):WicketOwnedComponentAccessor.Proof {
        WicketOwnedAccessorFixture.install(myFixture)
        for(i in 0 until count) myFixture.addFileToProject("src/main/java/example/OwnerBase$i.java","package example;public class OwnerBase$i extends "+
            (if(i+1==count) "Popover" else "OwnerBase${i+1}")+" {public OwnerBase$i(String id,java.util.function.Function<String,org.apache.wicket.Component> content){super(id,content);}}")
        change("new Popover(\"chooser\"","new OwnerBase0(\"chooser\"","TableShell.java")
        return proof()
    }
    fun testTwelveSourceOwnerLevelsRetainTheAccessorProof()=assertEquals("trigger",depth(10).field.name)
    fun testThirteenSourceOwnerLevelsWithholdTheAccessorProof() {assertThrows(IllegalStateException::class.java) {depth(11)}}
    fun testCancellationDuringSelectedGetterRecordingPropagates() {
        WicketOwnedAccessorFixture.install(myFixture);val indicator=EmptyProgressIndicator();var reached=false
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            proof {at ->if(at is PsiMethod && at.name=="getTrigger"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        },indicator)}
        assertTrue(reached)
    }
}
