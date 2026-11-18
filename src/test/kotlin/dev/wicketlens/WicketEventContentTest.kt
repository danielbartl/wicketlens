package dev.wicketlens

import dev.wicketlens.analysis.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketReportFormatter

class WicketEventContentTest:WicketProjectPsiTestCase() {
    fun testEventFragmentHasItsOwnMatchedGraphAndKeepsTheInitialPlaceholder() {
        val java=WicketEventContentFixture.install(myFixture);val type=java.classes.single();val primary=WicketPageAnalyzer().analyze(type)!!
        assertNotNull(WicketReportFormatter().format(primary),primary.initialContentSetup)
        val before=WicketContextExporter().export(primary)
        val result=WicketEventContentContexts().analyze(type,primary)
        assertTrue(result.complete);assertEquals(result.diagnostics.toString(),1,result.reports.size)
        val event=result.reports.single();val metadata=event.lifecycle!!.directContent!!
        assertEquals("detailsDrawer:dialog:form:content",event.lifecycle.componentPath)
        assertEquals("rows:details",metadata.triggerComponentPath)
        assertEquals("rows",metadata.triggerHostComponentPath)
        assertNotNull(metadata.triggerPopulationContextId)
        assertEquals(WicketReportFormatter().format(event),5,event.matching!!.findings.size)
        assertTrue(event.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertEquals("personDetails",event.nodes.single { it.nodeId==event.lifecycle.factoryNodeId }.fragment!!.definitionId)
        assertTrue(primary.fragmentUses.single { it.definitionId=="personDetails" }.instanceNodeIds.isEmpty())
        assertEquals(before,WicketContextExporter().export(primary))
    }

    private fun withheld(page:String=WicketEventContentFixture.page,host:String=WicketEventContentFixture.host,
        drawer:String=WicketInitialContentFixture.drawer,list:String=WicketEventContentFixture.list,markup:String=WicketEventContentFixture.markup) {
        val java=WicketEventContentFixture.install(myFixture,page,host,drawer,list,markup);val type=java.classes.single()
        val primary=WicketPageAnalyzer().analyze(type)!!
        val result=WicketEventContentContexts().analyze(type,primary)
        assertTrue(result.reports.toString(),result.reports.isEmpty())
    }
    fun testWrongSlotIdIsRejected()=withheld(page=WicketEventContentFixture.page.replace("new Fragment(DialogBox.CONTENT_ID", "new Fragment(\"wrong\""))
    fun testForeignFragmentProviderIsRejected()=withheld(page=WicketEventContentFixture.page.replace("EventPage.this)","new WebPage())"))
    fun testConditionalTransferIsRejected()=withheld(page=WicketEventContentFixture.page.replace("details.open(content,target);","if(target!=null) details.open(content,target);"))
    fun testDifferentAjaxTargetIsRejected()=withheld(page=WicketEventContentFixture.page.replace("details.open(content,target);","details.open(content,null);"))
    fun testContentEscapeIsRejected()=withheld(page=WicketEventContentFixture.page.replace("details.setFooterVisible(false);","System.out.println(content);"))
    fun testBorrowedContentIsRejected()=withheld(page=WicketEventContentFixture.page.replace("Fragment content=new Fragment(DialogBox.CONTENT_ID,\"personDetails\",EventPage.this);","Fragment content=(Fragment)EventPage.this.get(\"borrowed\");"))
    fun testReassignedHostIsRejected()=withheld(page=WicketEventContentFixture.page.replace("details.open(content,target);","details=new DrawerBox(\"other\",\"Other\");details.open(content,target);"))
    fun testHostAliasIsNotExpanded()=withheld(page=WicketEventContentFixture.page.replace("details.open(content,target);","DrawerBox alias=details;alias.open(content,target);"))
    fun testAnonymousEventStateIsRejected()=withheld(page=WicketEventContentFixture.page.replace("@Override public void onClick", "private int state=1;@Override public void onClick"))
    fun testMissingDefinitionIsRejected()=withheld(markup=WicketEventContentFixture.markup.replace("'personDetails'","'other'"))
    fun testDuplicateContentBindingsAreRejected()=withheld(page=WicketEventContentFixture.page.replace("details.setFooterVisible(false);","content.add(new DetailList(\"list\"));"))
    fun testChangedFluentIdentityIsRejected()=withheld(list=WicketEventContentFixture.list.replace("return addItem(property);","return new DetailList(\"other\");"))
    fun testOverriddenSetterCannotDiscardContent()=withheld(drawer=WicketInitialContentFixture.drawer.replace("super.setContent(content);",""))
    fun testTransferCannotUseAnotherContent()=withheld(host=WicketEventContentFixture.host.replace("setContent(content);open=true;", "setContent(new WebMarkupContainer(CONTENT_ID));open=true;"))
    fun testOpenHelperCannotMutateChildren()=withheld(host=WicketEventContentFixture.host.replace("open=true;target.add(this);", "add(new WebMarkupContainer(\"later\"));open=true;target.add(this);"))
    fun testConfigurationCannotReplaceChildren()=withheld(drawer=WicketInitialContentFixture.drawer.replace("@Override public DrawerBox setContent", "@Override public DrawerBox setFooterVisible(boolean visible) { add(new org.apache.wicket.markup.html.WebMarkupContainer(\"other\"));return this; } @Override public DrawerBox setContent"))
    fun testEventDiscoveryBoundsWithholdAllGraphs() {
        val java=WicketEventContentFixture.install(myFixture);val type=java.classes.single();val primary=WicketPageAnalyzer().analyze(type)!!
        for(limit in listOf(WicketEventContentContexts.Limits(steps=0),WicketEventContentContexts.Limits(sources=0),
            WicketEventContentContexts.Limits(contexts=0),WicketEventContentContexts.Limits(nodes=4),WicketEventContentContexts.Limits(depth=1))) {
            val result=WicketEventContentContexts(limit).analyze(type,primary)
            assertFalse(result.toString(),result.complete);assertTrue(result.reports.isEmpty());assertTrue(result.diagnostics.isNotEmpty())
        }
    }
    fun testCancellationPropagates() {
        val java=WicketEventContentFixture.install(myFixture);val type=java.classes.single();val primary=WicketPageAnalyzer().analyze(type)!!
        val indicator=com.intellij.openapi.progress.EmptyProgressIndicator()
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel();WicketEventContentContexts().analyze(type,primary)
            },indicator)
        }
    }

    fun testNativeContentAndChildNavigationProtectsEventDeclarations() {
        val java=WicketEventContentFixture.install(myFixture)
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val report=WicketEventContentContexts().analyze(java.classes.single(),primary).reports.single()
        val creation=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,com.intellij.psi.PsiNewExpression::class.java).single { it.textOffset==report.lifecycle!!.factoryLocation.offset }
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val handler=dev.wicketlens.editor.WicketReplacementDeclarationHandler()
        val targets=handler.getGotoDeclarationTargets(java.findElementAt(creation.textOffset),creation.textOffset+1,myFixture.editor)!!
        assertEquals(1,targets.size)
        assertTrue(dev.wicketlens.editor.WicketReplacementNavigation.targets(targets.single()).contains(creation))
        val reverse=targets.single().references.filterIsInstance<com.intellij.psi.PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }
        assertTrue(reverse.toString(),reverse.any { it.element==creation })
        assertNull(handler.getGotoDeclarationTargets(java.findElementAt(creation.textOffset+5),creation.textOffset+5,myFixture.editor))
        val literal=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,com.intellij.psi.PsiLiteralExpression::class.java).single { it.value=="list" }
        val childTargets=literal.references.filterIsInstance<com.intellij.psi.PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.mapNotNull { it.element }.distinct()
        assertEquals(1,childTargets.size)
        assertTrue(childTargets.single().references.filterIsInstance<com.intellij.psi.PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element==literal })
        assertNull(dev.wicketlens.editor.WicketIdRenameTarget.at(java,literal.textOffset))
    }
}
