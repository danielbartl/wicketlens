package dev.wicketlens

import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketReportFormatter
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.editor.*

class WicketInitialContentTest : WicketProjectPsiTestCase() {
    private fun change(name:String, before:String, after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(file.text,before in file.text)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun noSetup(name:String,before:String,after:String) {
        val java=WicketInitialContentFixture.install(myFixture);change(name,before,after)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNull(WicketReportFormatter().format(report),report.initialContentSetup)
    }
    fun testInitialOwnedContentAndSourceHooksHaveAnExplicitDeclarationPhase() {
        val java=WicketInitialContentFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNotNull(WicketReportFormatter().format(report),report.initialContentSetup)
        assertEquals(2,report.initialContentSetup!!.hosts.size)
        assertTrue(WicketReportFormatter().format(report),report.matching!!.findings.all { it.code==DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.all { it.confidence==Confidence.INFERRED })
        assertTrue(report.nodes.filter { it.nodeId!=report.rootNodeId }.all { it.template && !it.idRenameSafe })
    }
    fun testDeferredHookChildrenAreNotInitialChildren() {
        val java=WicketInitialContentFixture.install(myFixture)
        change("ContentPage","success(\"Saved\");","add(new Label(\"later\",\"After confirm\"));")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNotNull(report.initialContentSetup);assertTrue(report.nodes.none { it.wicketId=="later" })
        assertTrue(report.initialContentSetup!!.callbackLocations.any { it.file==java.virtualFile.path })
    }
    fun testWrongContentIdWithholdsDeclarations()=noSetup("ContentPage","new Label(DialogBox.CONTENT_ID,\"Initial\")","new Label(\"wrong\",\"Initial\")")
    fun testGuardRejectingTheExpectedIdWithholdsDeclarations()=noSetup("DialogBox","if(!CONTENT_ID.equals","if(CONTENT_ID.equals")
    fun testContentParameterCannotEscapeItsSlot()=noSetup("DialogBox","form.addOrReplace(content);","System.out.println(content);form.addOrReplace(content);")
    fun testTheOwnedFormFieldMustRemainFinal()=noSetup("DialogBox","private final Form<Void> form;","private Form<Void> form;")
    fun testTheOwnedFormFieldMustRemainPrivate()=noSetup("DialogBox","private final Form<Void> form;","public final Form<Void> form;")
    fun testAContentSetterCannotReturnAnotherComponent()=noSetup("DialogBox","form.addOrReplace(content);open=false;return this;","form.addOrReplace(content);open=false;return new DialogBox(\"other\",\"Other\");")
    fun testAnEagerSourceHookCallWithholdsDeclarations()=noSetup("DialogBox","form.add(confirm);form.setDefaultButton(confirm);","form.add(confirm);form.setDefaultButton(confirm);onConfirm(null);")
    fun testAnonymousHostInitializerRemainsUnsupported()=noSetup("ContentPage","@Override protected void onConfirm","{ setVisible(false); } @Override protected void onConfirm")
    fun testUnreviewedAnonymousMethodsRemainUnsupported()=noSetup("ContentPage","@Override protected void onConfirm","public void escape() {} @Override protected void onConfirm")
    fun testHostInitializationOverrideRemainsUnsupported()=noSetup("DialogBox","protected void onConfirm(AjaxRequestTarget target) {}","@Override protected void onInitialize() { super.onInitialize(); } protected void onConfirm(AjaxRequestTarget target) {}")
    fun testCompetingInitialPlacementsRemainUnsupported()=noSetup("ContentPage","drawer.setContent(fields).setFooterVisible(false);","drawer.setContent(fields);drawer.setContent(new Label(DialogBox.CONTENT_ID,\"Again\")).setFooterVisible(false);")
    fun testStructuralConfigurationWithholdsDeclarations()=noSetup("DialogBox","confirm.setVisible(visible);return this;","form.removeAll();confirm.setVisible(visible);return this;")
    fun testForeignAccessorCannotSupplyTheDrawerElement()=noSetup("DialogBox","return dialog;","return new WebMarkupContainer(\"foreign\");")
    fun testAConditionalIdCannotBeTreatedAsUnchangedForwarding()=noSetup("DrawerBox","super(id,title);","super(id!=null?id:\"other\",title);")
    fun testUnreviewedDelegationDataRemainsUnsupported()=noSetup("DrawerBox","super(id,title);","super(id,String.valueOf(title));")
    fun testTransparentOwnedFooterKeepsButtonsInTheirFormScope() {
        val java=WicketInitialContentFixture.install(myFixture)
        change("DialogBox","form.add(confirm);","form.add(new TransparentWebMarkupContainer(\"footer\"));form.add(confirm);")
        val html=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/resources/example/DialogBox.html"))!!
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(html)!!.setText(html.text.replace("<button wicket:id='confirm'></button>","<footer wicket:id='footer'><button wicket:id='confirm'></button></footer>")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(WicketReportFormatter().format(report),report.matching!!.findings.all { it.code==DiagnosticCode.MATCHED })
    }
    private fun row(java:PsiJavaFile, expression:String) {
        change("ContentPage","add(drawer);","""add(drawer);add(new org.apache.wicket.markup.html.list.ListView<String>("rows",java.util.List.of("x")) {
            @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) { item.add($expression); }
        });""")
        val html=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/resources/example/ContentPage.html"))!!
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(html)!!.setText(html.text+"<div wicket:id='rows'><a wicket:id='edit'></a></div>") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testFreshInlineBehaviorDecorationPreservesTheRowDeclaration() {
        val java=WicketInitialContentFixture.install(myFixture)
        row(java,"""new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("edit") {
            @Override public void onClick(AjaxRequestTarget target) {}
        }.add(new org.apache.wicket.behavior.Behavior())""")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.populationContexts.size)
        assertEquals(1,report.populationContexts.single().matching!!.findings.count { it.code==DiagnosticCode.MATCHED })
    }
    fun testBehaviorDecorationThroughAFactoryDoesNotGuessDynamicDispatch() {
        val java=WicketInitialContentFixture.install(myFixture)
        change("ContentPage","public ContentPage()", """private org.apache.wicket.Component rowLink() {
            return new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("edit") {
                @Override public void onClick(AjaxRequestTarget target) {}
                @Override public org.apache.wicket.Component add(org.apache.wicket.behavior.Behavior... behaviors) { return new Label("changed","Changed"); }
            };
        } public ContentPage()""")
        row(java,"rowLink().add(new org.apache.wicket.behavior.Behavior())")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNotNull(report.initialContentSetup)
        assertTrue(report.populationContexts.isEmpty())
        assertTrue(report.matching!!.findings.none { it.code==DiagnosticCode.MATCHED && report.nodes.single { n->n.nodeId==it.javaNodeId }.wicketId=="edit" })
    }
    fun testFragmentFieldsNavigateBothWaysAndStayProtectedFromRename() {
        val java=WicketInitialContentFixture.install(myFixture)
        for(id in listOf("name","email")) {
            val literal=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value==id }
            val targets=literal.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.mapNotNull { it.element }.distinct()
            assertEquals(id,1,targets.size)
            assertTrue(targets.single().references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element==literal })
            assertNull(WicketIdRenameTarget.at(java,literal.textOffset+1))
        }
    }
    fun testDeclarationNodeBudgetWithholdsTheWholeSetup() {
        val java=WicketInitialContentFixture.install(myFixture)
        change("DialogBox","form.add(confirm);",(1..130).joinToString("") { "form.add(new Label(\"extra$it\",\"x\"));" }+"form.add(confirm);")
        assertNull(WicketPageAnalyzer().analyze(java.classes.single())!!.initialContentSetup)
    }
    fun testCancellationPropagates() {
        val java=WicketInitialContentFixture.install(myFixture);val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketPageAnalyzer().analyze(java.classes.single()) },indicator) }
    }
}
