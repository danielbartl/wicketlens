package dev.wicketlens

import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketReportFormatter
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketComponentIdentity

class WicketConfirmContentTest : WicketProjectPsiTestCase() {
    private fun change(name:String,before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(file.text,before in file.text)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun report(name:String="ConfirmBox",before:String,after:String):PageReport {
        val java=WicketConfirmContentFixture.install(myFixture);change(name,before,after)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun withheld(name:String="ConfirmBox",before:String,after:String) {
        val report=report(name,before,after)
        assertNull(WicketReportFormatter().format(report),report.initialContentSetup)
        assertTrue(report.matching!!.findings.none { it.code==DiagnosticCode.MATCHED })
    }
    fun testConstructorOwnedMessageReplacesTheInheritedPlaceholderAsInferredDeclarations() {
        val java=WicketConfirmContentFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNotNull(WicketReportFormatter().format(report),report.initialContentSetup)
        val host=report.initialContentSetup!!.hosts.single()
        val content=report.nodes.single { it.nodeId==host.contentNodeId }
        assertEquals("org.apache.wicket.markup.html.basic.Label",content.componentType)
        assertTrue(content.javaLocation.file.endsWith("ConfirmBox.java"))
        assertEquals(1,host.placementLocations.size)
        assertTrue(host.placementLocations.single().file.endsWith("ConfirmBox.java"))
        assertTrue(report.matching!!.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertTrue(report.nodes.filter { it.nodeId!=report.rootNodeId }.all { !it.idRenameSafe && it.template })
        assertTrue(report.initialContentSetup.callbackLocations.any { it.file.endsWith("ConfirmBox.java") })
    }
    fun testEmptyModelIdentityIsOptedIntoDeclarationsWithoutStrictEditingAuthority() {
        val java=WicketConfirmContentFixture.install(myFixture)
        val creation=PsiTreeUtil.findChildOfType(java,PsiNewExpression::class.java)!!
        assertNull(WicketComponentIdentity.trace(creation))
        assertNotNull(WicketComponentIdentity.trace(creation,conditionalData=true))
        assertNull(dev.wicketlens.editor.WicketIdRenameTarget.at(java,java.text.indexOf("\"confirmBox\"")))
    }
    fun testWrongMessageIdWithholdsTheGraph()=withheld(before="new Label(CONTENT_ID",after="new Label(\"wrong\"")
    fun testMutableMessageStorageWithholdsTheGraph()=withheld(before="private final Label message",after="private Label message")
    fun testPublicMessageStorageWithholdsTheGraph()=withheld(before="private final Label message",after="public final Label message")
    fun testMessageAliasesCannotSupplyTheConstructorPlacement()=withheld(before="setContent(message);",after="Label alias=message;setContent(alias);")
    fun testBorrowedConstructorContentCannotSupplyThePlacement()=withheld(before="setContent(message);",after="setContent(new Label(CONTENT_ID));")
    fun testRepeatedConstructorPlacementsRemainUnresolved()=withheld(before="setContent(message);",after="setContent(message);setContent(message);")
    fun testPagePlacementCannotSilentlyOverrideConstructorContent()=withheld("ConfirmPage",before="add(new ConfirmBox(\"confirmBox\"));",after="ConfirmBox box=new ConfirmBox(\"confirmBox\");box.setContent(new org.apache.wicket.markup.html.basic.Label(\"content\"));add(box);")
    fun testNonemptyModelDelegationDoesNotAcquireTheEmptyModelContract()=withheld(before="super(id,new Model<>());",after="super(id,new Model<>(\"header\"));")
    fun testAnonymousModelDelegationRemainsUnsupported()=withheld(before="super(id,new Model<>());",after="super(id,new Model<String>() {});")
    fun testTransformedIdsRemainUnproved()=withheld(before="super(id,new Model<>());",after="super(id+\"suffix\",new Model<>());")
    fun testEagerConfirmationIsNotConfiguration()=withheld(before="setFooterVisible(true);",after="setFooterVisible(true);onConfirm(null);")
    fun testMessageModelArgumentsCannotRunAnEagerSourceHook() {
        val java=WicketConfirmContentFixture.install(myFixture)
        change("ConfirmBox","public ConfirmBox(String id)","private org.apache.wicket.model.IModel<String> eager(){onConfirm(null);return new Model<>();}public ConfirmBox(String id)")
        change("ConfirmBox","message=new Label(CONTENT_ID,new Model<String>())","message=new Label(CONTENT_ID,eager())")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNull(WicketReportFormatter().format(report),report.initialContentSetup)
    }
    fun testDeferredActionChildrenDoNotBecomeInitialBindings() {
        val report=report(before="var pending=action;",after="add(new Label(\"later\"));var pending=action;")
        assertNotNull(WicketReportFormatter().format(report),report.initialContentSetup)
        assertTrue(report.nodes.none { it.wicketId=="later" })
    }
    fun testAnonymousMessageInitializationIsNotADeclaration()=withheld(before="@Override protected void onConfigure()",after="private final Object state=new Object();@Override protected void onConfigure()")
    fun testSourceSetterForwardingRetainsTheGuardedPlacement() {
        val report=report(before="public ConfirmBox(String id)",after="@Override public ConfirmBox setContent(org.apache.wicket.Component content){super.setContent(content);return this;}public ConfirmBox(String id)")
        assertNotNull(WicketReportFormatter().format(report),report.initialContentSetup)
        assertEquals("org.apache.wicket.markup.html.basic.Label",report.nodes.single { it.nodeId==report.initialContentSetup!!.hosts.single().contentNodeId }.componentType)
    }
    fun testOverriddenSetterCannotBypassTheInheritedGuard()=withheld(before="public ConfirmBox(String id)",after="@Override public ConfirmBox setContent(org.apache.wicket.Component content){return this;}public ConfirmBox(String id)")
    fun testNonfirstAttachmentIdsStillForwardThroughSourceWrappers() {
        WicketConfirmContentFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/WrappedBox.java","package example;public class WrappedBox extends ConfirmBox {public WrappedBox(String display,String id){super(id);}}")
        change("ConfirmPage","new ConfirmBox(\"confirmBox\")","new WrappedBox(\"display\",\"confirmBox\")")
        val java=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/ConfirmPage.java")) as PsiJavaFile
        assertNotNull(WicketReportFormatter().format(WicketPageAnalyzer().analyze(java.classes.single())!!),WicketPageAnalyzer().analyze(java.classes.single())!!.initialContentSetup)
    }
    fun testRuntimeSubclassSetterDispatchIsCheckedDuringSuperclassConstruction() {
        val java=WicketConfirmContentFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/WrappedBox.java","package example;public class WrappedBox extends ConfirmBox {public WrappedBox(String id){super(id);}@Override public WrappedBox setContent(org.apache.wicket.Component content){return this;}}")
        change("ConfirmPage","new ConfirmBox(\"confirmBox\")","new WrappedBox(\"confirmBox\")")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNull(WicketReportFormatter().format(report),report.initialContentSetup)
    }
    fun testConstructorDeclarationNoteSurvivesLaterUnsupportedPageSetup() {
        val report=report("ConfirmPage",before="add(new ConfirmBox(\"confirmBox\"));",after="add(new ConfirmBox(\"confirmBox\"));removeAll();")
        assertNull(report.initialContentSetup)
        assertTrue(report.matching!!.findings.none {it.code==DiagnosticCode.MATCHED})
        assertTrue(report.warnings.toString(),report.warnings.any {"Constructor-owned initial content declared" in it.message})
    }
    fun testCancellationDuringConstructorFieldInspectionPropagates() {
        val java=WicketConfirmContentFixture.install(myFixture)
        val creation=PsiTreeUtil.findChildOfType(java,PsiNewExpression::class.java)!!
        val indicator=EmptyProgressIndicator();var reached=false
        val tick:(PsiElement)->Unit={at -> if(at is PsiField && at.name=="message"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            dev.wicketlens.analysis.WicketInitialContentDeclarations(java.classes.single(),tick,tick,mutableSetOf(),{_,message->throw AssertionError(message)}).inspect(creation,"host")
        },indicator)}
        assertTrue(reached)
    }
    fun testWholeSourceFieldScanHonorsTheSharedDiscoveryBudget() {
        val java=WicketConfirmContentFixture.install(myFixture)
        change("ConfirmBox","public ConfirmBox(String id)","private void unused(){"+(0..5500).joinToString(";") {"int value$it=0"}+";}public ConfirmBox(String id)")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNull(report.initialContentSetup)
        assertTrue(report.warnings.toString(),report.warnings.any {"budget" in it.message})
        assertTrue(report.matching!!.findings.none {it.code==DiagnosticCode.MATCHED})
    }
    fun testStoredOldPlaceholderDoesNotLeaveDanglingFieldIdentities() {
        val java=WicketConfirmContentFixture.install(myFixture)
        change("DialogBox","private boolean open;","private final org.apache.wicket.markup.html.WebMarkupContainer placeholder;private boolean open;")
        change("DialogBox","form.add(new WebMarkupContainer(CONTENT_ID));","placeholder=new WebMarkupContainer(CONTENT_ID);form.add(placeholder);")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNotNull(WicketReportFormatter().format(report),report.initialContentSetup)
        assertTrue(report.matching!!.findings.all {it.code==DiagnosticCode.MATCHED})
        val creation=PsiTreeUtil.findChildOfType(java,PsiNewExpression::class.java)!!
        val host=dev.wicketlens.analysis.WicketInitialContentDeclarations(java.classes.single(),{ProgressManager.checkCanceled()},{},mutableSetOf(),{_,message->throw AssertionError(message)}).inspect(creation,"host")
        val declared=host.nodes.map {it.nodeId}.toSet()
        assertTrue(host.fields.values.all {it in declared})
        assertTrue(host.fields.keys.none {it.name=="placeholder"})
    }
    fun testAnalysisRemainsReadOnlyAndCancellationPropagates() {
        val java=WicketConfirmContentFixture.install(myFixture);val before=java.text to java.modificationStamp
        assertNotNull(WicketPageAnalyzer().analyze(java.classes.single())!!.initialContentSetup)
        assertEquals(before,java.text to java.modificationStamp)
        val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable {indicator.cancel();WicketPageAnalyzer().analyze(java.classes.single())},indicator) }
    }
}
