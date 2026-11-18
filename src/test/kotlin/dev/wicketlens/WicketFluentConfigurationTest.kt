package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode

class WicketFluentConfigurationTest:WicketProjectPsiTestCase() {
    private fun change(before:String,after:String,file:String="Button.java") {
        val psi=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$file"))!!
        assertTrue(before in psi.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(psi)!!.setText(psi.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(before:String,after:String,file:String="Button.java") {
        val java=WicketFluentConfigurationFixture.install(myFixture);change(before,after,file)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
    }
    private fun matched(java:PsiJavaFile,notes:Int=1) {
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),notes,report.warnings.count {"Reviewed fresh inline component fluent configuration" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }
    fun testFreshAnonymousFluentChainKeepsCallbacksAndPrivateContentsUnknown() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        matched(java)
    }
    fun testAOneOperationChainUsesTheSameStorageProof() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        change(".setStyle(Appearance.Style.GHOST)","","TableShell.java");matched(java)
    }
    fun testExactModelDeclaredFieldsUseTheSameStorageProof() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        change("private IModel<","private Model<","Appearance.java");matched(java)
    }
    fun testComponentConstructorEffectsAreNotSkipped()=withheld("super(id);","super(id);getPage();")
    fun testFluentSetterCannotReturnAnotherReceiver()=withheld("return this;","return new Button(\"other\"){public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target){}};")
    fun testFluentSetterExtraEffectsAreUnreviewed()=withheld("appearance.setStyle(style);","getPage();appearance.setStyle(style);")
    fun testComputedForwardedArgumentIsUnreviewed()=withheld("appearance.setStyle(style);","appearance.setStyle(style==null?Appearance.Style.DEFAULT:style);")
    fun testSynchronizedFluentMethodIsUnreviewed()=withheld("public Button setSize(","public synchronized Button setSize(")
    fun testMutableBehaviorFieldIsUnreviewed()=withheld("private final Appearance appearance;","private Appearance appearance;")
    fun testBorrowedBehaviorFieldIsUnreviewed() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/Shared.java","package example;public class Shared {public static final Appearance VALUE=new Appearance();}")
        change("appearance=new Appearance();","appearance=Shared.VALUE;")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testBehaviorAliasDoesNotBorrowTheStorageProof()=withheld("appearance.setStyle(style);","Appearance alias=appearance;alias.setStyle(style);")
    fun testBehaviorGetterExposureWithholdsTheProof()=withheld("public Button setSize(","public Appearance expose(){return appearance;}public Button setSize(")
    fun testSourceFrameworkAdditionOverrideIsUnreviewed()=withheld("public Button(String id)","public org.apache.wicket.Component add(org.apache.wicket.behavior.Behavior... values){return this;}public Button(String id)")
    fun testBehaviorConstructorEffectsAreUnreviewed()=withheld("public Appearance(){}","public Appearance(){System.setProperty(\"fluent\",\"effect\");}","Appearance.java")
    fun testBehaviorInitializerBlocksAreUnreviewed()=withheld("public Appearance(){}","{System.setProperty(\"fluent\",\"effect\");}public Appearance(){}","Appearance.java")
    fun testBehaviorBindingOverridesCannotRunThroughTheChain()=withheld("public Appearance(){}","public Appearance(){}public void bind(org.apache.wicket.Component component){component.getPage();}","Appearance.java")
    fun testBehaviorSetterExtraEffectsAreUnreviewed()=withheld("styleModel.setObject(style);","System.setProperty(\"fluent\",\"effect\");styleModel.setObject(style);","Appearance.java")
    fun testBehaviorSetterCannotReturnAnotherBehavior()=withheld("styleModel.setObject(style);return this;","styleModel.setObject(style);return new Appearance();","Appearance.java")
    fun testModelFieldReassignmentIsUnreviewed()=withheld("styleModel.setObject(style);","styleModel=Model.of(style);styleModel.setObject(style);","Appearance.java")
    fun testCustomModelImplementationDoesNotAcquireExactModelStorage()=withheld("Model.of(Style.DEFAULT)","new org.apache.wicket.model.LoadableDetachableModel<Style>(){protected Style load(){return Style.DEFAULT;}}","Appearance.java")
    fun testModelGetterExposureWithholdsTheProof()=withheld("public Appearance(){}","public IModel<Style> expose(){return styleModel;}public Appearance(){}","Appearance.java")
    fun testModelAliasDoesNotAcquireTheSetterProof()=withheld("styleModel.setObject(style);","IModel<Style> alias=styleModel;alias.setObject(style);","Appearance.java")
    fun testEnumConstructorCallsAreUnreviewed()=withheld("this.css=css;","this.css=css;System.setProperty(\"enum\",\"effect\");","Appearance.java")
    fun testEnumStaticInitializationIsUnreviewed()=withheld("GHOST(\"ghost\");","GHOST(\"ghost\");static {System.setProperty(\"enum\",\"effect\");}","Appearance.java")
    fun testEnumConstantAnonymousBodiesAreUnreviewed()=withheld("GHOST(\"ghost\")","GHOST(\"ghost\"){public String css(){return \"other\";}}","Appearance.java")
    fun testComputedEnumInputsRemainUnreviewed()=withheld("Appearance.Style.GHOST","Appearance.Style.values()[0]","TableShell.java")
    fun testBorrowedLocalReceiverRemainsUnreviewed() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        change("add(new Button(\"clear\"){","Button other=new Button(\"clear\"){","TableShell.java")
        change("}.setSize(","};add(other.setSize(","TableShell.java")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testMostDerivedFluentOverrideDoesNotBorrowTheBaseMethodProof() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        change("public void onClick(","public Button setStyle(Appearance.Style style){getPage();return this;}public void onClick(","TableShell.java")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testEnumInterfaceInitializationIsAudited() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/Marker.java","package example;interface Marker {int TOKEN=seed();static int seed(){System.setProperty(\"enum\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("public enum Style {","public enum Style implements Marker {","Appearance.java")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testCompletedFluentFactSurvivesTheNextIndependentStop() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        change(".setStyle(Appearance.Style.GHOST));",".setStyle(Appearance.Style.GHOST));getPage();","TableShell.java")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertEquals(1,result.diagnostics.count {"Reviewed fresh inline component fluent configuration" in it.message})
    }
    private fun proof(tick:(PsiElement)->Unit={ProgressManager.checkCanceled()}):WicketFluentComponentConfiguration.Proof {
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        val call=PsiTreeUtil.findChildrenOfType(shell,PsiMethodCallExpression::class.java).single {it.methodExpression.referenceName=="setStyle"}
        return WicketFluentComponentConfiguration(tick,tick,{(it as? PsiLiteralExpression)?.value},{false},{},{_,message->throw IllegalStateException(message)}).inspect(call)
    }
    fun testTwelveFluentOperationsFitTheChainBound() {
        WicketFluentConfigurationFixture.install(myFixture)
        change(".setSize(Appearance.Size.SMALL)",".setSize(Appearance.Size.SMALL)".repeat(11),"TableShell.java")
        assertEquals(12,proof().calls.size)
    }
    fun testThirteenFluentOperationsWithholdTheProof() {
        WicketFluentConfigurationFixture.install(myFixture)
        change(".setSize(Appearance.Size.SMALL)",".setSize(Appearance.Size.SMALL)".repeat(12),"TableShell.java")
        assertThrows(IllegalStateException::class.java) {proof()}
    }
    private fun depth(count:Int):WicketFluentComponentConfiguration.Proof {
        WicketFluentConfigurationFixture.install(myFixture)
        for(i in 0 until count) myFixture.addFileToProject("src/main/java/example/Layer$i.java","package example;public abstract class Layer$i extends "+
            (if(i+1==count) "Button" else "Layer${i+1}")+" {public Layer$i(String id){super(id);}}")
        change("new Button(\"clear\")","new Layer0(\"clear\")","TableShell.java")
        return proof()
    }
    fun testTwelveSourceComponentLevelsFitTheAncestryBound()=assertEquals(12,depth(10).owners.size)
    fun testThirteenSourceComponentLevelsWithholdTheProof() {assertThrows(IllegalStateException::class.java) {depth(11)}}
    fun testOversizedSemanticOwnershipScanWithholdsTheExpansion() {
        val java=WicketFluentConfigurationFixture.install(myFixture)
        change("public Appearance(){}","private void unused(){int count=0;"+"count++;".repeat(6000)+"}public Appearance(){}","Appearance.java")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"budget was exceeded" in it.message})
    }
    fun testCancellationDuringSemanticOwnershipScanPropagates() {
        WicketFluentConfigurationFixture.install(myFixture);val indicator=EmptyProgressIndicator();var reached=false
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            proof {element ->if(element is PsiReferenceExpression && element.referenceName=="styleModel"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        },indicator)}
        assertTrue(reached)
    }
}
