package dev.wicketlens

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.WicketMcpReport
import dev.wicketlens.mcp.WicketPageAnalysisRequest
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.routes.MountStatus
import dev.wicketlens.routes.WicketRoutesRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

/** Opt-in corpus runner. Source is read from the supplied checkout and analyzed only in fixture copies. */
class WicketOatCorpusTest : WicketProjectPsiTestCase() {
    private var navigationRetrievalEvidence:Map<String,Any> = emptyMap()
    private fun boundedDataTableNavigation(query:WicketPageAnalysisRequest,snapshot:dev.wicketlens.mcp.PageAnalysisSnapshot):com.google.gson.JsonObject {
        try {query.render(snapshot);fail("The complete primary page plus all navigation contexts must retain the response bound.")}
        catch(failure:dev.wicketlens.mcp.PageAnalysisFailure) {assertEquals("RESPONSE_TOO_LARGE",failure.code)}
        val indexText=query.render(snapshot,WicketMcpReport.NAVIGATION)
        val index=JsonParser.parseString(indexText).asJsonObject["navigationContexts"].asJsonObject
        val descriptors=index["contexts"].asJsonArray
        val assembled=com.google.gson.JsonArray();var maxBytes=0
        descriptors.forEachIndexed { i,item ->
            val id=item.asJsonObject["contextId"].asString
            val fresh=query.capture(project,snapshot.report.qualifiedClassName!!,snapshot.moduleName,snapshot.sourceSet,WicketMcpReport.NAVIGATION)
            assertEquals(snapshot.report,fresh.report)
            val text=query.render(fresh,WicketMcpReport.NAVIGATION,contextId=id)
            val bytes=text.toByteArray(Charsets.UTF_8).size;maxBytes=maxOf(maxBytes,bytes)
            assertTrue(bytes<=WicketPageAnalysisRequest.MAX_RESPONSE_BYTES)
            val selected=JsonParser.parseString(text).asJsonObject["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
            assertEquals(id,selected.remove("contextId").asString)
            assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(snapshot.navigationContexts!!.contexts[i]),selected)
            query.requireCurrent(project,fresh);assembled.add(selected)
        }
        assertEquals(snapshot.navigationContexts!!.contexts.size,assembled.size())
        navigationRetrievalEvidence=mapOf("tool" to "wicket_get_navigation_contexts","contextCount" to assembled.size(),"indexResponseBytes" to indexText.toByteArray(Charsets.UTF_8).size,
            "largestSelectedResponseBytes" to maxBytes,"wholeResponseByteLimit" to WicketPageAnalysisRequest.MAX_RESPONSE_BYTES,"everySelectedProjectionEqualsNative" to true,
            "freshCapturePerSelection" to true,"legacyCombinedResponse" to "RESPONSE_TOO_LARGE","primaryReportUnchanged" to true)
        // Client-side reassembly of separate complete responses, never one oversized wire response.
        return com.google.gson.JsonObject().apply {add("navigationContexts",com.google.gson.JsonObject().apply {add("contexts",assembled)})}
    }
    private fun storedActionEvidence(repeated:Boolean=false,chained:Boolean=false,initialized:Boolean=false):Map<String,Any> {
        val expected=if(repeated || chained || initialized) 2 else 1
        val namespace=if(initialized) "dev.jbaby.wicket.oat.app.initializedstorageprobe" else if(chained) "dev.jbaby.wicket.oat.app.chainedstorageprobe" else if(repeated) "dev.jbaby.wicket.oat.app.repeatedstorageprobe" else "dev.jbaby.wicket.oat.app.storageprobe"
        val folder="wicket-oat-examples/src/main/"
        val path=namespace.replace('.','/')
        val java=myFixture.addFileToProject(folder+"java/$path/ConfirmationPage.java",(if(initialized) WicketRecordActionFixture.initializedPage else if(chained) WicketRecordActionFixture.chainedPage else if(repeated) WicketRecordActionFixture.repeatedPage else WicketRecordActionFixture.plainPage).replace("package example;","package $namespace;")) as PsiJavaFile
        myFixture.addFileToProject(folder+"java/$path/Actions.java",WicketConfirmationFixture.actions.replace("package example;","package $namespace;"))
        myFixture.addFileToProject(folder+"resources/$path/ConfirmationPage.html","<html><body><div wicket:id='rows'></div></body></html>")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(expected,report.storedActionRegistrations.size)
        assertEquals(1,report.matching!!.findings.size)
        assertTrue(report.matching.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED})
        assertTrue(report.nodes.filter {it.nodeId!=report.rootNodeId}.all {it.template && !it.idRenameSafe})
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,java.classes.single().qualifiedName!!,view=WicketMcpReport.NAVIGATION)
        assertEquals(report,snapshot.report)
        val rendered=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(report)),rendered)
        val index=JsonParser.parseString(query.render(snapshot,WicketMcpReport.NAVIGATION)).asJsonObject["navigationContexts"].asJsonObject["contexts"].asJsonArray
        val descriptors=index.filter {it.asJsonObject["relationship"].asString=="ACTION_TEMPLATE"}
        val nativeActions=snapshot.navigationContexts!!.contexts.filter {it.report.actionTemplate!=null}
        assertEquals(expected,descriptors.size)
        for((i,descriptor) in descriptors.withIndex()) {
            val selected=JsonParser.parseString(query.render(snapshot,WicketMcpReport.NAVIGATION,contextId=descriptor.asJsonObject["contextId"].asString)).asJsonObject["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
            selected.remove("contextId");assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(nativeActions[i]),selected)
            assertEquals(1,selected["bindings"].asJsonArray.size())
        }
        query.requireCurrent(project,snapshot)
        if(repeated || chained || initialized) {
            assertEquals(1,report.storedActionRegistrations.map {it.slotLocation}.distinct().size)
            assertEquals(1,report.storedActionRegistrations.map {it.allocationLocation}.distinct().size)
            if(chained || initialized) {
                val offsets=report.storedActionRegistrations.map {it.registrationLocation.offset}
                assertEquals(2,offsets.distinct().size);assertTrue(offsets.all {java.text.substring(it).startsWith("addAction")})
                if(initialized) return mapOf("probeClass" to java.classes.single().qualifiedName!!,"probeKind" to "minimal_plain_page_with_local_registrar_initializer_chain",
                    "initializerLinks" to 2,"initialMatchedBindings" to 1,"actionContexts" to 2,"actionMatchedBindings" to 2,
                    "allocationKind" to "DIRECT_NEW_IN_LOCAL_INITIALIZER","registrationLocationKind" to "METHOD_NAME_TOKEN",
                    "bindingConfidence" to "INFERRED","renameAuthority" to false,"activeRegistration" to "unknown",
                    "callbackExecution" to "not_evaluated","nativeAndMcpValuesEqual" to true)
                return mapOf("probeClass" to java.classes.single().qualifiedName!!,"probeKind" to "minimal_plain_page_with_fluent_source_registrar_chain",
                    "registrationLinks" to 2,"initialMatchedBindings" to 1,"actionContexts" to 2,"actionMatchedBindings" to 2,
                    "registrationLocationKind" to "METHOD_NAME_TOKEN","bindingConfidence" to "INFERRED","renameAuthority" to false,
                    "activeRegistration" to "unknown","callbackExecution" to "not_evaluated","nativeAndMcpValuesEqual" to true)
            }
            return mapOf("probeClass" to java.classes.single().qualifiedName!!,"probeKind" to "minimal_plain_page_with_repeated_source_registrar_uses",
                "registrationSites" to 2,"initialMatchedBindings" to 1,"actionContexts" to 2,"actionMatchedBindings" to 2,
                "bindingConfidence" to "INFERRED","renameAuthority" to false,"activeRegistration" to "unknown","callbackExecution" to "not_evaluated","nativeAndMcpValuesEqual" to true)
        }
        return mapOf("probeClass" to java.classes.single().qualifiedName!!,"probeKind" to "minimal_plain_page_with_fresh_source_storage_registrar",
            "initialMatchedBindings" to 1,"actionMatchedBindings" to 1,"bindingConfidence" to "INFERRED","renameAuthority" to false,
            "callbackExecution" to "not_evaluated","activeRegistrationDispatchAndLaterTreeState" to "unknown","nativeAndMcpValuesEqual" to true)
    }
    private fun confirmationEvidence(type:com.intellij.psi.PsiClass,primary:dev.wicketlens.model.PageReport,snapshot:dev.wicketlens.mcp.PageAnalysisSnapshot):Map<String,Any> {
        val result=dev.wicketlens.analysis.WicketConfirmationContexts().analyze(type,primary)
        assertTrue(result.complete)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
        val report=result.reports.single {it.submissionTemplate!!.confirmation!!.selectionClears.isEmpty()};val metadata=report.submissionTemplate!!;val route=metadata.confirmation!!
        assertEquals("confirm:dialog:form:confirm",metadata.triggerComponentPath)
        assertEquals(listOf("SOURCE_DATA_CALL","FEEDBACK","AJAX_REFRESH"),metadata.effects.map {it.kind})
        assertEquals("dataTable",metadata.effects.last().targetComponentPath)
        assertEquals(5,report.matching!!.findings.size)
        assertTrue(report.matching.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED})
        assertTrue(result.diagnostics.toString(),result.diagnostics.isEmpty())
        assertEquals(result.reports,snapshot.navigationContexts!!.contexts.filter {it.relationship=="CONFIRMATION_TEMPLATE"}.map {it.report})
        val output=Path.of(System.getProperty("wicketlens.test.oatOutput")).parent
        Files.writeString(output.resolve("DataTableConfirmation.txt"),dev.wicketlens.ui.WicketReportFormatter().format(report))
        return mapOf("target" to report.qualifiedClassName!!,"phase" to "CONFIRMATION_TEMPLATE","matchedBindings" to 5,
            "triggerComponentPath" to metadata.triggerComponentPath,"dataMutationMethod" to route.dataCalls.single().mutationMethod,
            "effectKinds" to metadata.effects.map {it.kind},"setterLocations" to route.setterLocations,"askLocation" to route.registrationLocation,
            "bulkSelectionContext" to "source_declarations","diagnostics" to result.diagnostics,"nativeAndMcpValuesEqual" to true,
            "initiatingDispatchCurrentSlotDataOutcomesAndCleanupReachability" to "unknown","callbackExecution" to "not_evaluated")
    }
    private fun bulkSelectionEvidence(type:com.intellij.psi.PsiClass,primary:dev.wicketlens.model.PageReport,snapshot:dev.wicketlens.mcp.PageAnalysisSnapshot):Map<String,Any> {
        val result=dev.wicketlens.analysis.WicketConfirmationContexts().analyze(type,primary)
        val bulk=result.reports.single {it.submissionTemplate!!.confirmation!!.selectionClears.isNotEmpty()}
        val metadata=bulk.submissionTemplate!!;val operation=metadata.confirmation!!.selectionClears.single()
        assertEquals("dataTable",operation.targetComponentPath)
        assertEquals(listOf("SOURCE_DATA_CALL","FEEDBACK","SOURCE_SELECTION_CALL","AJAX_REFRESH"),metadata.effects.map {it.kind})
        assertEquals(5,bulk.matching!!.findings.size)
        assertTrue(bulk.matching.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED})
        assertTrue(bulk.nodes.all {it.template && !it.idRenameSafe})
        assertEquals(bulk,snapshot.navigationContexts!!.contexts.single {it.report.submissionTemplate?.confirmation?.selectionClears?.isNotEmpty()==true}.report)
        val native=dev.wicketlens.editor.WicketEditorAnalysis.navigationPages(type.containingFile).single {it.report.submissionTemplate?.confirmation?.selectionClears?.isNotEmpty()==true}.report
        assertEquals(WicketContextExporter().export(bulk),WicketContextExporter().export(native))
        val output=Path.of(System.getProperty("wicketlens.test.oatOutput")).parent
        Files.writeString(output.resolve("DataTableBulkConfirmation.txt"),dev.wicketlens.ui.WicketReportFormatter().format(bulk))
        return mapOf("target" to bulk.qualifiedClassName!!,"phase" to "CONFIRMATION_TEMPLATE","matchedBindings" to 5,
            "effectKinds" to metadata.effects.map {it.kind},"targetComponentPath" to operation.targetComponentPath,
            "operation" to operation,"modelRead" to "org.apache.wicket.model.IModel.getObject","collectionClear" to "java.util.Collection.clear",
            "nativeAndMcpValuesEqual" to true,"callbackAndModelExecution" to "not_evaluated","columnDispatchModelCollectionAndOutcomes" to "unknown")
    }
    private fun recordActionEvidence(type:com.intellij.psi.PsiClass,primary:dev.wicketlens.model.PageReport,snapshot:dev.wicketlens.mcp.PageAnalysisSnapshot):List<Map<String,Any>> {
        val result=dev.wicketlens.analysis.WicketActionContexts().analyze(type,primary)
        assertTrue(result.complete);assertEquals(result.diagnostics.toString(),2,result.reports.size)
        val contexts=snapshot.navigationContexts!!.contexts.filter {it.relationship=="ACTION_TEMPLATE"}
        assertEquals(result.reports,contexts.map {it.report})
        val native=dev.wicketlens.editor.WicketEditorAnalysis.navigationPages(type.containingFile).filter {it.report.actionTemplate!=null}.map {it.report}
        assertEquals(result.reports.map {WicketContextExporter().export(it)},native.map {WicketContextExporter().export(it)})
        return result.reports.map {report ->
            val action=report.actionTemplate!!;val update=action.updates.single();val bulk=action.selectionClears.isNotEmpty()
            assertEquals(if(bulk) listOf("SOURCE_RECORD_UPDATE_CALL","FEEDBACK","SOURCE_SELECTION_CALL","AJAX_REFRESH") else listOf("SOURCE_RECORD_UPDATE_CALL","FEEDBACK","AJAX_REFRESH"),action.effects.map {it.kind})
            assertEquals("dataTable",action.effects.last().targetComponentPath)
            assertEquals(1,report.matching!!.findings.size)
            assertTrue(report.matching.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED})
            assertTrue(report.nodes.all {it.template && !it.idRenameSafe})
            assertEquals(8,update.componentArguments.size)
            assertEquals(listOf("status"),update.componentArguments.filter {it.kind=="SOURCE_ENUM_CONSTANT"}.map {it.componentName})
            assertEquals("dev.jbaby.wicket.oat.app.DataTablePage.Status.PAID",update.componentArguments.single {it.kind=="SOURCE_ENUM_CONSTANT"}.declarationName)
            val output=Path.of(System.getProperty("wicketlens.test.oatOutput")).parent
            Files.writeString(output.resolve(if(bulk) "DataTableBulkAction.txt" else "DataTableRowAction.txt"),dev.wicketlens.ui.WicketReportFormatter().format(report))
            mapOf("target" to report.qualifiedClassName!!,"phase" to "ACTION_TEMPLATE","kind" to if(bulk) "bulk" else "row",
                "matchedBindings" to 1,"effectKinds" to action.effects.map {it.kind},"update" to update,
                "selectionClears" to action.selectionClears,"nativeAndMcpValuesEqual" to true,"uiTrigger" to "not_reconstructed",
                "callbackDataPredicateAndConstructorExecution" to "not_evaluated","registrationRowsPredicateEffectsAndOutcomes" to "unknown")
        }
    }
    private fun completedDataTableReport(report:dev.wicketlens.model.PageReport,seedLoop:com.intellij.psi.PsiForStatement):Map<String,Any> {
        val dataTableReport=report
        assertFalse(report.warnings.toString(),report.warnings.any {it.message.startsWith("Source layout setup stopped:")})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed source null-guarded owned toolbar cache" in it.message && it.location?.offset==8214})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed owned DataTable columns getter" in it.message && it.location?.offset==8600})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed owned DataTable reference forwarding" in it.message && it.location?.offset==8234})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed fresh inline component fluent configuration" in it.message && it.location?.offset==6999})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed owned component accessor behavior decoration" in it.message && it.location?.offset==15146})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed fresh-table navigation-toolbar construction" in it.message})
        assertEquals(dataTableReport.warnings.toString(),2,dataTableReport.warnings.count {"Reviewed owned DataTable toolbar registration" in it.message})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed DataTable constructor" in it.message && it.location?.offset==19041})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed owned DataTable toolbar registration" in it.message && it.location?.offset==4624})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed fresh-table toolbar construction" in it.message})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed source data-provider construction" in it.message})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed JDK FormatStyle enum storage" in it.message})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed LambdaColumn constructor storage" in it.message})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Reviewed AbstractColumn constructor storage" in it.message})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {"Constructor-owned initial content declared for dev.jbaby.wicket.oat.components.OatConfirmDialog" in it.message})
        assertTrue(dataTableReport.warnings.toString(),dataTableReport.warnings.any {
            "Constructor data loop audited" in it.message && it.location?.offset==seedLoop.textOffset })
        assertEquals(mapOf(DiagnosticCode.MATCHED to 26,DiagnosticCode.UNRESOLVED_BRANCH to 9),report.matching!!.findings.groupingBy {it.code}.eachCount())
        assertTrue(report.matching.findings.filter {it.code==DiagnosticCode.MATCHED}.all {it.confidence==dev.wicketlens.model.Confidence.INFERRED})
        assertNotNull(report.initialContentSetup)
        assertEquals(1,report.initialContentSetup!!.hosts.size)
        assertTrue(report.nodes.filter {it.nodeId!=report.rootNodeId}.all {it.template && !it.idRenameSafe})
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,report.qualifiedClassName!!)
        val native=JsonParser.parseString(WicketContextExporter().export(report))
        assertEquals(native,JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"])
        assertTrue(query.render(snapshot,WicketMcpReport.VALIDATION).contains("SOURCE_DECLARED_INITIAL_CONTENT"))
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"dataTable")).asJsonObject["context"].asJsonObject
        assertEquals("dataTable",context["component"].asJsonObject["wicketId"].asString)
        query.requireCurrent(project,snapshot)
        val output=Path.of(System.getProperty("wicketlens.test.oatOutput")).parent
        Files.createDirectories(output)
        Files.writeString(output.resolve("DataTablePage.txt"),dev.wicketlens.ui.WicketReportFormatter().format(report))
        return mapOf("constructorDataLoopAudited" to true,"loopSourceOffset" to seedLoop.textOffset,
                "constructorOwnedConfirmationContentDeclared" to true,"selectionColumnConstructionAudited" to true,"lambdaColumnConstructionAudited" to true,"signedColumnConstantsAudited" to true,"jdkFormatStyleStorageAudited" to true,"providerConstructionAudited" to true,"dataTableConstructionAudited" to true,"headerToolbarConstructionAndRegistrationAudited" to true,"navigationToolbarConstructionAndEagerFactoryAudited" to true,"ownedComponentAccessorDecorationAudited" to true,"ownedToolbarCacheAudited" to true,"ownedTableColumnsReadAudited" to true,"ownedTableReferenceForwardingAudited" to true,"freshInlineFluentConfigurationAudited" to true,"semanticDeclarationSnapshotsReused" to true,"initialSourceSetupComplete" to true,"matchedBindings" to 26,"unresolvedBranches" to 9,"sourceLayoutStepLimit" to 20000,"sourceDependencies" to report.javaSourceFiles.size,"nativeAndMcpAssertionsPassed" to true)
    }
    fun testMilestoneBindingsNavigationAndMcpOnApplicationCopies() {
        val path = requireNotNull(System.getProperty("wicketlens.test.oatProject")) {
            "Supply -PwicketOatProject=/path/to/wicket-oat to run the application corpus."
        }
        val origin = Path.of(path)
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.orderEntries.filterIsInstance<LibraryOrderEntry>().forEach(model::removeOrderEntry)
        }
        System.getProperty("wicketlens.test.oatLibraries").split(File.pathSeparator).forEachIndexed { index, entry ->
            val jar = Path.of(entry)
            PsiTestUtil.addLibrary(testRootDisposable, module, "Oat corpus $index", jar.parent.toString(), jar.fileName.toString())
        }
        val pages = mutableListOf<PsiJavaFile>()
        val sourceHashes = linkedMapOf<Path, String>()
        for (name in listOf("wicket-oat-core", "wicket-oat-examples")) {
            for ((folder, type) in listOf("java" to JavaSourceRootType.SOURCE, "resources" to JavaResourceRootType.RESOURCE)) {
                val target = "$name/src/main/$folder"
                PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir(target), type)
                val root = origin.resolve(target)
                Files.walk(root).use { paths ->
                    paths.filter { Files.isRegularFile(it) && (it.toString().endsWith(".java") || it.toString().endsWith(".html")) }.sorted().forEach { source ->
                        sourceHashes[source] = hash(source)
                        val file = myFixture.addFileToProject("$target/${root.relativize(source)}", Files.readString(source))
                        if (name == "wicket-oat-examples" && file is PsiJavaFile && file.name.endsWith("Page.java")) pages += file
                    }
                }
            }
        }
        val appSelector = "dev.jbaby.wicket.oat.WicketOatApplication#wicketApp"
        val routes = WicketRoutesRequest()
        val snapshot = routes.capture(project, applicationClass = appSelector)
        val oatApp = snapshot.routes.applications.single()
        val expectedMounts = mapOf("DialogPage" to "/dialog", "InboxPage" to "/inbox",
            "DataTablePage" to "/data-table", "MobileCapacityPage" to "/mobile/capacity")
        for ((name, path) in expectedMounts) {
            val pageClass = "dev.jbaby.wicket.oat.app.$name"
            val mount = oatApp.mounts.single { it.pageClass == pageClass }
            val page = pages.single { it.classes.single().qualifiedName == pageClass }
            assertEquals(path, mount.path)
            assertEquals(MountStatus.DECLARED, mount.status)
            assertEquals(page.virtualFile.path, mount.pageSource?.file)
        }
        assertTrue(routes.render(snapshot).toByteArray(Charsets.UTF_8).size <= WicketPageAnalysisRequest.MAX_RESPONSE_BYTES)
        routes.requireCurrent(project, snapshot)

        val projectTreeMarkers=linkedMapOf(
            "dev.jbaby.wicket.oat.app.MobileCapacityPage" to true,
            "dev.jbaby.wicket.oat.app.InboxPage" to true,
            "dev.jbaby.wicket.oat.app.DataTablePage" to true,
            "dev.jbaby.wicket.oat.components.OatMobileLayout" to true,
            "dev.jbaby.wicket.oat.components.OatDialog" to true,
            "dev.jbaby.wicket.oat.components.OatMeter" to true,
        )
        projectTreeMarkers.forEach { (name,expected) ->
            val type=com.intellij.psi.JavaPsiFacade.getInstance(project).findClass(name,com.intellij.psi.search.GlobalSearchScope.projectScope(project))!!
            val node=com.intellij.ide.projectView.impl.nodes.ClassTreeNode(project,type,com.intellij.ide.projectView.ViewSettings.DEFAULT)
            node.update()
            assertEquals(name,expected,dev.wicketlens.ui.WicketProjectViewDecorator.hasMarker(node.presentation.getIcon(false)))
            assertFalse(node.presentation.coloredText.any { "Wicket " in it.text })
        }
        val mobileLayoutType=com.intellij.psi.JavaPsiFacade.getInstance(project).findClass("dev.jbaby.wicket.oat.components.OatMobileLayout",com.intellij.psi.search.GlobalSearchScope.projectScope(project))!!
        val mobileLayoutReport=WicketPageAnalyzer().analyze(mobileLayoutType)!!
        assertNull(mobileLayoutReport.baseLayoutTemplate)
        assertTrue(mobileLayoutReport.warnings.any { "Base-layout template withheld" in it.message && "helper body is unavailable" in it.message })
        assertNull(mobileLayoutType.findMethodsByName("bottomNavItemsModel",false).single().body)
        val mobileType=pages.single { it.name=="MobileCapacityPage.java" }.classes.single()
        val mobileReport=WicketPageAnalyzer().analyze(mobileType)!!
        assertEquals(4,mobileReport.matching!!.findings.count { it.code==DiagnosticCode.MATCHED })
        assertEquals(3,mobileReport.matching.findings.count { it.code==DiagnosticCode.UNRESOLVED_BRANCH })
        val mobileRow=mobileReport.populationContexts.single { it.populationTemplate!!.hostComponentPath=="days" }
        assertEquals(3,mobileRow.matching!!.findings.count { it.code==DiagnosticCode.MATCHED })
        assertEquals(setOf("day","count","meter"),mobileRow.matching.findings.filter { it.code==DiagnosticCode.MATCHED }
            .map { finding -> mobileRow.nodes.single { it.nodeId==finding.javaNodeId }.wicketId }.toSet())
        val mobileLinkIssue=mobileReport.markup!!.diagnostics.single { it.message=="This Wicket tag's component scope is not reconstructed." }
        val mobileLinkSource=dev.wicketlens.editor.WicketIdPsi.source(project,mobileLinkIssue.location!!.file)!!
        assertTrue(mobileLinkSource.text.substring(mobileLinkIssue.location.offset).startsWith("<wicket:link>"))
        val dataTableType=pages.single { it.name=="DataTablePage.java" }.classes.single()
        val dataTableReport=WicketPageAnalyzer().analyze(dataTableType)!!
        val seedLoop=dataTableType.constructors.single().body!!.statements.filterIsInstance<com.intellij.psi.PsiForStatement>().single()
        assertTrue(seedLoop.text.contains("invoices.add(new Invoice("))
        val dataTableReview=completedDataTableReport(dataTableReport,seedLoop)
        val confirmationProbe=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/ConfirmContentProbe.java",
            "package dev.jbaby.wicket.oat.app;public class ConfirmContentProbe extends BasePage {public ConfirmContentProbe(){add(new dev.jbaby.wicket.oat.components.OatConfirmDialog(\"confirmation\"));}}") as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/ConfirmContentProbe.html","<wicket:extend><div wicket:id='confirmation'></div></wicket:extend>")
        val confirmationReport=WicketPageAnalyzer().analyze(confirmationProbe.classes.single())!!
        assertNotNull(dev.wicketlens.ui.WicketReportFormatter().format(confirmationReport),confirmationReport.initialContentSetup)
        val confirmationHost=confirmationReport.initialContentSetup!!.hosts.single()
        val confirmationMessage=confirmationReport.nodes.single {it.nodeId==confirmationHost.contentNodeId}
        assertEquals("org.apache.wicket.markup.html.basic.Label",confirmationMessage.componentType)
        assertTrue(confirmationMessage.javaLocation.file.endsWith("/OatConfirmDialog.java"))
        assertEquals(1,confirmationHost.placementLocations.size)
        assertTrue(confirmationHost.placementLocations.single().file.endsWith("/OatConfirmDialog.java"))
        assertEquals(24,confirmationReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertTrue(confirmationReport.matching.findings.filter {it.code==DiagnosticCode.MATCHED}.all {it.confidence==dev.wicketlens.model.Confidence.INFERRED})
        val confirmationQuery=WicketPageAnalysisRequest()
        val confirmationSnapshot=confirmationQuery.capture(project,confirmationProbe.classes.single().qualifiedName!!)
        assertTrue(confirmationQuery.render(confirmationSnapshot).contains("SOURCE_DECLARED_INITIAL_CONTENT"))
        assertTrue(confirmationQuery.render(confirmationSnapshot,WicketMcpReport.VALIDATION).contains("INITIAL_DECLARATIONS"))
        assertTrue(confirmationQuery.render(confirmationSnapshot,WicketMcpReport.CONTEXT,"confirmation:dialog:form:content").contains("org.apache.wicket.markup.html.basic.Label"))
        val columnProbe=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/ColumnConstructionProbe.java",
            "package dev.jbaby.wicket.oat.app;public class ColumnConstructionProbe extends BasePage {public ColumnConstructionProbe(){java.util.List<org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn<String,String>> columns=new java.util.ArrayList<>();columns.add(new dev.jbaby.wicket.oat.components.table.OatSelectionColumn<>(org.apache.wicket.model.Model.ofList(new java.util.ArrayList<String>())));add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Column storage\"));}}") as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/ColumnConstructionProbe.html","<wicket:extend><b wicket:id='title'></b></wicket:extend>")
        val columnReport=WicketPageAnalyzer().analyze(columnProbe.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(columnReport),11,columnReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertTrue(columnReport.warnings.toString(),columnReport.warnings.any {"Reviewed AbstractColumn constructor storage" in it.message})
        assertTrue(columnReport.nodes.none {it.componentType.endsWith("CheckCell") || it.wicketId=="cell"})
        val columnSnapshot=confirmationQuery.capture(project,columnProbe.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(columnReport)),JsonParser.parseString(confirmationQuery.render(columnSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(columnSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed AbstractColumn constructor storage"))
        assertTrue(confirmationQuery.render(columnSnapshot,WicketMcpReport.CONTEXT,"title").contains("Reviewed AbstractColumn constructor storage"))
        val lambdaProbe=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/LambdaColumnProbe.java",
            "package dev.jbaby.wicket.oat.app;public class LambdaColumnProbe extends BasePage {public LambdaColumnProbe(){java.util.List<org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn<String,String>> columns=new java.util.ArrayList<>();columns.add(new dev.jbaby.wicket.oat.components.table.OatBadgeColumn<>(org.apache.wicket.model.Model.of(\"Status\"),\"status\",value->value,value->dev.jbaby.wicket.oat.OatVariant.SUCCESS));columns.add(new dev.jbaby.wicket.oat.components.table.OatNumberColumn<>(org.apache.wicket.model.Model.of(\"Total\"),\"total\",String::length));add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Lambda storage\"));}}") as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/LambdaColumnProbe.html","<wicket:extend><b wicket:id='title'></b></wicket:extend>")
        val lambdaReport=WicketPageAnalyzer().analyze(lambdaProbe.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(lambdaReport),11,lambdaReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(lambdaReport.warnings.toString(),2,lambdaReport.warnings.count {"Reviewed LambdaColumn constructor storage" in it.message})
        assertTrue(lambdaReport.nodes.none {it.wicketId=="cell" || it.componentType.endsWith("OatBadge") || it.componentType.endsWith("OatNumberColumn")})
        val lambdaSnapshot=confirmationQuery.capture(project,lambdaProbe.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(lambdaReport)),JsonParser.parseString(confirmationQuery.render(lambdaSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(lambdaSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed LambdaColumn constructor storage"))
        assertTrue(confirmationQuery.render(lambdaSnapshot,WicketMcpReport.CONTEXT,"title").contains("Reviewed LambdaColumn constructor storage"))
        val dateProbe=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/DateColumnProbe.java",
            "package dev.jbaby.wicket.oat.app;public class DateColumnProbe extends BasePage {public DateColumnProbe(){java.util.List<org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn<java.time.LocalDate,String>> columns=new java.util.ArrayList<>();columns.add(new dev.jbaby.wicket.oat.components.table.OatDateColumn<>(org.apache.wicket.model.Model.of(\"Due\"),\"due\",value->value));add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Date storage\"));}}") as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/DateColumnProbe.html","<wicket:extend><b wicket:id='title'></b></wicket:extend>")
        val dateReport=WicketPageAnalyzer().analyze(dateProbe.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(dateReport),11,dateReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(dateReport.warnings.toString(),2,dateReport.warnings.count {"Reviewed JDK FormatStyle enum storage" in it.message})
        assertTrue(dateReport.nodes.none {it.wicketId=="cell" || it.componentType.endsWith("OatDateColumn")})
        val dateSnapshot=confirmationQuery.capture(project,dateProbe.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(dateReport)),JsonParser.parseString(confirmationQuery.render(dateSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(dateSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed JDK FormatStyle enum storage"))
        assertTrue(confirmationQuery.render(dateSnapshot,WicketMcpReport.CONTEXT,"title").contains("Reviewed JDK FormatStyle enum storage"))
        val providerSeed=dataTableType.constructors.single().body!!.statements.filter {statement ->
            statement is com.intellij.psi.PsiForStatement || statement is com.intellij.psi.PsiDeclarationStatement &&
                statement.declaredElements.filterIsInstance<com.intellij.psi.PsiLocalVariable>().any {it.name in setOf("customers","currencies")}
        }.joinToString("\n") {it.text}
        val providerConfirmation=dataTableType.constructors.single().body!!.statements.filter {statement ->
            statement is com.intellij.psi.PsiDeclarationStatement && statement.declaredElements.filterIsInstance<com.intellij.psi.PsiLocalVariable>().any {it.name=="confirm"} || statement.text=="add(confirm);"
        }.joinToString("\n") {it.text}
        val providerProbeText=dataTableType.containingFile.text
            .replace(dataTableType.constructors.single().text,"public DataTablePage(){"+providerSeed+providerConfirmation+"InvoiceProvider rows=new InvoiceProvider();add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Provider storage\"));}")
            .replace("public class DataTablePage extends BasePage","public class ProviderConstructionProbe extends BasePage")
            .replace("public DataTablePage()","public ProviderConstructionProbe()")
        val providerProbe=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/ProviderConstructionProbe.java",providerProbeText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/ProviderConstructionProbe.html","<wicket:extend><div wicket:id='confirm'></div><b wicket:id='title'></b></wicket:extend>")
        assertEquals(dataTableType.innerClasses.single {it.name=="InvoiceProvider"}.text,providerProbe.classes.single().innerClasses.single {it.name=="InvoiceProvider"}.text)
        assertEquals(dataTableType.findMethodsByName("filtered",false).single().text,providerProbe.classes.single().findMethodsByName("filtered",false).single().text)
        assertTrue(seedLoop.text in providerProbe.classes.single().constructors.single().text)
        val providerReport=WicketPageAnalyzer().analyze(providerProbe.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(providerReport),25,providerReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(providerReport.warnings.toString(),1,providerReport.warnings.count {"Reviewed source data-provider construction" in it.message})
        val providerSnapshot=confirmationQuery.capture(project,providerProbe.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(providerReport)),JsonParser.parseString(confirmationQuery.render(providerSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(providerSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed source data-provider construction"))
        assertTrue(confirmationQuery.render(providerSnapshot,WicketMcpReport.CONTEXT,"title").contains("Reviewed source data-provider construction"))
        val originalTable=(com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("wicket-oat-core/src/main/java/dev/jbaby/wicket/oat/components/OatDataTable.java")) as PsiJavaFile).classes.single()
        val tableConstructor=originalTable.constructors.single()
        val tablePrefix=tableConstructor.body!!.statements.takeWhile {!it.text.startsWith("table.addTopToolbar(")}.joinToString("\n") {it.text}
        assertTrue(tablePrefix.contains("table = new RowsTable(\"table\", columns, dataProvider, rowsPerPage);"))
        val tableProbeText=originalTable.containingFile.text.replace(tableConstructor.text,
            "public OatDataTable(String id,List<? extends IColumn<T,S>> columns,ISortableDataProvider<T,S> dataProvider,long rowsPerPage){"+tablePrefix+"selectionColumn=null;bulkBar=null;add(table);}")
            .replace("OatDataTable","DataTableConstructionProbe")
        val tableProbe=myFixture.addFileToProject("wicket-oat-core/src/main/java/dev/jbaby/wicket/oat/components/DataTableConstructionProbe.java",tableProbeText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-core/src/main/resources/dev/jbaby/wicket/oat/components/DataTableConstructionProbe.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
        assertEquals(originalTable.innerClasses.single {it.name=="RowsTable"}.text.replace("OatDataTable","DataTableConstructionProbe"),tableProbe.classes.single().innerClasses.single {it.name=="RowsTable"}.text)
        assertTrue(tablePrefix.replace("OatDataTable","DataTableConstructionProbe") in tableProbe.classes.single().constructors.single().text)
        val tablePageText=providerProbeText.replace("ProviderConstructionProbe","TableAllocationProbe")
            .replace("add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Provider storage\"));",
                "add(new dev.jbaby.wicket.oat.components.DataTableConstructionProbe<Invoice,String>(\"table\",java.util.List.of(),rows,10));add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Table shell\"));")
        val tablePage=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/TableAllocationProbe.java",tablePageText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/TableAllocationProbe.html","<wicket:extend><div wicket:id='confirm'></div><div wicket:id='table'></div><b wicket:id='title'></b></wicket:extend>")
        assertEquals(dataTableType.innerClasses.single {it.name=="InvoiceProvider"}.text,tablePage.classes.single().innerClasses.single {it.name=="InvoiceProvider"}.text)
        assertEquals(dataTableType.findMethodsByName("filtered",false).single().text,tablePage.classes.single().findMethodsByName("filtered",false).single().text)
        val tableReport=WicketPageAnalyzer().analyze(tablePage.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(tableReport),26,tableReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(tableReport.warnings.toString(),1,tableReport.warnings.count {"Reviewed DataTable constructor" in it.message})
        val tableSnapshot=confirmationQuery.capture(project,tablePage.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(tableReport)),JsonParser.parseString(confirmationQuery.render(tableSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(tableSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed DataTable constructor"))
        assertTrue(confirmationQuery.render(tableSnapshot,WicketMcpReport.CONTEXT,"table").contains("Reviewed DataTable constructor"))
        val headerPrefix=tableConstructor.body!!.statements.takeWhile {!it.text.startsWith("table.addBottomToolbar(")}.joinToString("\n") {it.text}
        val headerProbeText=originalTable.containingFile.text.replace(tableConstructor.text,
            "public OatDataTable(String id,List<? extends IColumn<T,S>> columns,ISortableDataProvider<T,S> dataProvider,long rowsPerPage){"+headerPrefix+"selectionColumn=null;bulkBar=null;add(table);}")
            .replace("OatDataTable","HeaderRegistrationProbe")
        val headerProbe=myFixture.addFileToProject("wicket-oat-core/src/main/java/dev/jbaby/wicket/oat/components/HeaderRegistrationProbe.java",headerProbeText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-core/src/main/resources/dev/jbaby/wicket/oat/components/HeaderRegistrationProbe.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
        val originalTop=tableConstructor.body!!.statements.single {it.text.startsWith("table.addTopToolbar(")}
        assertEquals(originalTop.text.replace("OatDataTable","HeaderRegistrationProbe"),headerProbe.classes.single().constructors.single().body!!.statements.single {it.text.startsWith("table.addTopToolbar(")}.text)
        assertEquals(originalTable.innerClasses.single {it.name=="RowsTable"}.text.replace("OatDataTable","HeaderRegistrationProbe"),headerProbe.classes.single().innerClasses.single {it.name=="RowsTable"}.text)
        val headerPageText=tablePageText.replace("TableAllocationProbe","HeaderRegistrationPage").replace("components.DataTableConstructionProbe<Invoice,String>","components.HeaderRegistrationProbe<Invoice,String>")
        val headerPage=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/HeaderRegistrationPage.java",headerPageText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/HeaderRegistrationPage.html","<wicket:extend><div wicket:id='confirm'></div><div wicket:id='table'></div><b wicket:id='title'></b></wicket:extend>")
        val headerReport=WicketPageAnalyzer().analyze(headerPage.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(headerReport),26,headerReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(headerReport.warnings.toString(),1,headerReport.warnings.count {"Reviewed fresh-table toolbar construction" in it.message})
        assertEquals(headerReport.warnings.toString(),1,headerReport.warnings.count {"Reviewed owned DataTable toolbar registration" in it.message})
        val headerSnapshot=confirmationQuery.capture(project,headerPage.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(headerReport)),JsonParser.parseString(confirmationQuery.render(headerSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(headerSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed owned DataTable toolbar registration"))
        assertTrue(confirmationQuery.render(headerSnapshot,WicketMcpReport.CONTEXT,"table").contains("Reviewed owned DataTable toolbar registration"))
        val navigationPrefix=tableConstructor.body!!.statements.takeWhile {it.text!="add(table);"}.joinToString("\n") {it.text}
        val navigationProbeText=originalTable.containingFile.text.replace(tableConstructor.text,
            "public OatDataTable(String id,List<? extends IColumn<T,S>> columns,ISortableDataProvider<T,S> dataProvider,long rowsPerPage){"+navigationPrefix+"selectionColumn=null;bulkBar=null;add(table);}")
            .replace("OatDataTable","NavigationRegistrationProbe")
        val navigationProbe=myFixture.addFileToProject("wicket-oat-core/src/main/java/dev/jbaby/wicket/oat/components/NavigationRegistrationProbe.java",navigationProbeText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-core/src/main/resources/dev/jbaby/wicket/oat/components/NavigationRegistrationProbe.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
        val originalBottom=tableConstructor.body!!.statements.single {it.text.startsWith("table.addBottomToolbar(")}
        for(original in listOf(originalTop,originalBottom)) assertEquals(original.text.replace("OatDataTable","NavigationRegistrationProbe"),navigationProbe.classes.single().constructors.single().body!!.statements.single {it.text.startsWith(if(original==originalTop) "table.addTopToolbar(" else "table.addBottomToolbar(")}.text)
        assertEquals(originalTable.innerClasses.single {it.name=="RowsTable"}.text.replace("OatDataTable","NavigationRegistrationProbe"),navigationProbe.classes.single().innerClasses.single {it.name=="RowsTable"}.text)
        val navigationPageText=tablePageText.replace("TableAllocationProbe","NavigationRegistrationPage").replace("components.DataTableConstructionProbe<Invoice,String>","components.NavigationRegistrationProbe<Invoice,String>")
        val navigationPage=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/NavigationRegistrationPage.java",navigationPageText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/NavigationRegistrationPage.html","<wicket:extend><div wicket:id='confirm'></div><div wicket:id='table'></div><b wicket:id='title'></b></wicket:extend>")
        val navigationReport=WicketPageAnalyzer().analyze(navigationPage.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(navigationReport),26,navigationReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(navigationReport.warnings.toString(),1,navigationReport.warnings.count {"Reviewed fresh-table toolbar construction" in it.message})
        assertEquals(navigationReport.warnings.toString(),1,navigationReport.warnings.count {"Reviewed fresh-table navigation-toolbar construction" in it.message})
        assertEquals(navigationReport.warnings.toString(),2,navigationReport.warnings.count {"Reviewed owned DataTable toolbar registration" in it.message})
        val navigationSnapshot=confirmationQuery.capture(project,navigationPage.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(navigationReport)),JsonParser.parseString(confirmationQuery.render(navigationSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(navigationSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed fresh-table navigation-toolbar construction"))
        assertTrue(confirmationQuery.render(navigationSnapshot,WicketMcpReport.CONTEXT,"table").contains("Reviewed fresh-table navigation-toolbar construction"))
        val accessorPrefix=tableConstructor.body!!.statements.takeWhile {!it.text.startsWith("bulkBar =")}.joinToString("\n") {it.text}
        assertTrue(accessorPrefix.contains("add(newTools(\"tools\"));"))
        val accessorProbeText=originalTable.containingFile.text.replace(tableConstructor.text,
            "public OatDataTable(String id,List<? extends IColumn<T,S>> columns,ISortableDataProvider<T,S> dataProvider,long rowsPerPage){"+accessorPrefix+"bulkBar=null;}")
            .replace("OatDataTable","OwnedAccessorProbe")
        val accessorProbe=myFixture.addFileToProject("wicket-oat-core/src/main/java/dev/jbaby/wicket/oat/components/OwnedAccessorProbe.java",accessorProbeText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-core/src/main/resources/dev/jbaby/wicket/oat/components/OwnedAccessorProbe.html","<wicket:panel><table wicket:id='table'></table><div wicket:id='toolbar'></div><div wicket:id='tools'></div></wicket:panel>")
        assertEquals(originalTable.findMethodsByName("newTools",false).single().text.replace("OatDataTable","OwnedAccessorProbe"),accessorProbe.classes.single().findMethodsByName("newTools",false).single().text)
        assertEquals(originalTable.innerClasses.single {it.name=="RowsTable"}.text.replace("OatDataTable","OwnedAccessorProbe"),accessorProbe.classes.single().innerClasses.single {it.name=="RowsTable"}.text)
        val accessorPageText=tablePageText.replace("TableAllocationProbe","OwnedAccessorPage").replace("components.DataTableConstructionProbe<Invoice,String>","components.OwnedAccessorProbe<Invoice,String>")
        val accessorPage=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/OwnedAccessorPage.java",accessorPageText) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/OwnedAccessorPage.html","<wicket:extend><div wicket:id='confirm'></div><div wicket:id='table'></div><b wicket:id='title'></b></wicket:extend>")
        val accessorReport=WicketPageAnalyzer().analyze(accessorPage.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(accessorReport),26,accessorReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(accessorReport.warnings.toString(),1,accessorReport.warnings.count {"Reviewed owned component accessor behavior decoration" in it.message})
        val accessorSnapshot=confirmationQuery.capture(project,accessorPage.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(accessorReport)),JsonParser.parseString(confirmationQuery.render(accessorSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(accessorSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed owned component accessor behavior decoration"))
        assertTrue(confirmationQuery.render(accessorSnapshot,WicketMcpReport.CONTEXT,"table").contains("Reviewed owned component accessor behavior decoration"))
        val fluentPage=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/FluentConfigurationProbe.java","""
            package dev.jbaby.wicket.oat.app;
            public class FluentConfigurationProbe extends org.apache.wicket.markup.html.WebPage {
                public FluentConfigurationProbe(){
                    var rows=new org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider<String,String>() {
                        public java.util.Iterator<? extends String> iterator(long first,long count){return java.util.List.<String>of().iterator();}
                        public long size(){return 0;}
                        public org.apache.wicket.model.IModel<String> model(String row){return org.apache.wicket.model.Model.of(row);}
                    };
                    add(new dev.jbaby.wicket.oat.components.OatDataTable<String,String>("table",java.util.List.of(),rows,10));
                    add(new org.apache.wicket.markup.html.basic.Label("title","Fluent storage"));
                }
            }
        """.trimIndent()) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/FluentConfigurationProbe.html","<html><body><div wicket:id='table'></div><b wicket:id='title'></b></body></html>")
        val actualTableAllocation=PsiTreeUtil.findChildrenOfType(fluentPage,com.intellij.psi.PsiNewExpression::class.java).single {it.classReference?.resolve()==originalTable}
        assertEquals(tableConstructor,actualTableAllocation.resolveConstructor())
        val fluentReport=WicketPageAnalyzer().analyze(fluentPage.classes.single())!!
        assertEquals(dev.wicketlens.ui.WicketReportFormatter().format(fluentReport),2,fluentReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(fluentReport.warnings.toString(),1,fluentReport.warnings.count {"Reviewed fresh inline component fluent configuration" in it.message})
        val fluentSnapshot=confirmationQuery.capture(project,fluentPage.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(fluentReport)),JsonParser.parseString(confirmationQuery.render(fluentSnapshot)).asJsonObject["analysis"])
        assertTrue(confirmationQuery.render(fluentSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed fresh inline component fluent configuration"))
        assertTrue(confirmationQuery.render(fluentSnapshot,WicketMcpReport.CONTEXT,"table").contains("Reviewed fresh inline component fluent configuration"))
        val forwardingEvidence=ownedTableForwardingProbe(fluentPage,confirmationQuery)
        val required = mapOf(
            "HomePage" to setOf("message", "now", "timeForm", "refreshTime", "theme", "user"),
            "BadgePage" to setOf("defaultBadge", "secondaryBadge", "outlineBadge", "successBadge", "warningBadge", "dangerBadge", "customBehaviorBadge", "theme", "user"),
            "CardPage" to setOf("simpleCard", "title", "description", "content", "cancel", "save", "theme", "user"),
            "MobileCapacityPage" to setOf("html", "appTitle", "bottomNav", "days"),
            "InboxPage" to setOf("inbox", "commands", "cookies", "askAgain", "message", "body", "empty", "open", "icon", "subject", "from"),
        )
        val selections = mapOf("HomePage" to "timeForm:refreshTime", "BadgePage" to "defaultBadge", "CardPage" to "simpleCard:title", "MobileCapacityPage" to "bottomNav", "InboxPage" to "inbox:second:message:subject")
        val summaries = pages.sortedBy { it.name }.map { java ->
            val page = java.classes.single()
            val report = requireNotNull(WicketPageAnalyzer().analyze(page))
            val matches = report.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }
            val matchedIds = matches.map { finding -> report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }
            val name = java.virtualFile.nameWithoutExtension
            val expected = required[name]
            if (expected != null) {
                assertTrue("$name: ${report.warnings}", matchedIds.containsAll(expected))
                assertFalse(report.scopeComplete)
                // Native references on the page's own IDs must agree with the report in both directions.
                expected.filter { id -> report.nodes.singleOrNull { it.wicketId == id }?.idLocation?.file == java.virtualFile.path }.forEach { id ->
                    val pair = WicketIdPsi.pair(java.idValue(id))
                    assertNotNull("$name:$id must navigate", pair)
                    assertEquals(pair!!.literal, WicketIdPsi.pair(pair.attribute)?.literal)
                }
                val query = WicketPageAnalysisRequest()
                val snapshot = query.capture(project, page.qualifiedName!!)
                val structure = JsonParser.parseString(query.render(snapshot, WicketMcpReport.STRUCTURE)).asJsonObject
                assertEquals(JsonParser.parseString(WicketContextExporter().export(report)), structure["analysis"])
                val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
                assertEquals(matches.size, validation["validation"].asJsonObject["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
                val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, selections.getValue(name))).asJsonObject["context"].asJsonObject
                assertTrue(context["hierarchyResolved"].asBoolean)
                assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
                query.requireCurrent(project, snapshot)
            }
            val requiredDefinitions = when (name) {
                "InboxPage" -> setOf("detailFragment", "rowFragment")
                "DialogPage" -> setOf("editFields", "filterFields", "personDetails")
                "TabsPage" -> setOf("overviewTab", "activityTab", "settingsTab")
                else -> emptySet()
            }
            for (id in requiredDefinitions) {
                val use = report.fragmentUses.single { it.definitionId == id }
                assertNotNull("$name:$id definition lookup", use.definitionLocation)
                val literal = PsiTreeUtil.findChildrenOfType(java, PsiLiteralExpression::class.java).single { it.textOffset == use.definitionIdOrigin!!.offset }
                val reference = literal.references.filterIsInstance<PsiPolyVariantReference>().single()
                val target = reference.resolve()
                assertNotNull("$name:$id definition navigation", target)
                val reverse = target!!.references.filterIsInstance<PsiPolyVariantReference>().single()
                assertTrue(reverse.multiResolve(false).any { it.element == literal })
                if (name == "InboxPage" || name == "DialogPage" && id != "personDetails") assertTrue("$name:$id requires a captured instance", use.instanceNodeIds.isNotEmpty())
                else assertTrue("$name:$id remains outside the supported factory subset", use.instanceNodeIds.isEmpty())
            }
            if (expected != null && name != "MobileCapacityPage") {
                val query = WicketPageAnalysisRequest()
                val snapshot = query.capture(project, page.qualifiedName!!)
                val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, "topNav:topNavExtra:theme")).asJsonObject["context"].asJsonObject
                assertTrue(context["hierarchyResolved"].asBoolean)
                assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
                assertEquals("topBar", context["parent"].asJsonObject["fragment"].asJsonObject["definitionId"].asString)
            }
            if (name == "InboxPage") {
                val query = WicketPageAnalysisRequest()
                val snapshot = query.capture(project, page.qualifiedName!!)
                for (path in listOf("inbox:firstPane:first", "inbox:second", "inbox:second:message:subject",
                    "inbox:firstPane:first:list:batch:item:open:subject")) {
                    val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, path)).asJsonObject["context"].asJsonObject
                    assertTrue("Inbox path $path", context["hierarchyResolved"].asBoolean)
                    assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
                    if (path.contains(":item:")) assertEquals("INFERRED", context["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
                }
                assertEquals(2, report.nodes.count { it.sourceSetupExpanded && it.ownedMarkupPath != null })
                assertEquals(2, report.nodes.count { it.templateItem && it.wicketId == null })
                assertTrue(report.markup!!.sourceFiles.any { it.endsWith("/OatSplitLayout.html") })
                assertTrue(report.markup.sourceFiles.any { it.endsWith("/OatLoadMoreList.html") })
            }
            linkedMapOf<String, Any?>(
                "page" to page.qualifiedName, "markupPaired" to (report.markupPath != null),
                "javaSourceDependencies" to report.javaSourceFiles.size,
                "findings" to report.matching.findings.groupingBy { it.code.name }.eachCount(),
                "matchedIds" to matchedIds,
                "matchesByConfidence" to matches.groupingBy { it.confidence.name }.eachCount(),
                "ownedMarkup" to report.nodes.filter { it.ownedMarkupPath != null }.map { it.ownedMarkupPath },
                "fragmentUses" to report.fragmentUses.map { mapOf("definitionId" to it.definitionId,
                    "definitionLocation" to it.definitionLocation, "instanceNodeIds" to it.instanceNodeIds,
                    "diagnostics" to it.diagnostics) },
                "warnings" to report.warnings.map { mapOf("code" to it.code.name, "message" to it.message,
                    "offset" to it.location?.offset, "file" to it.location?.file) }.distinct(),
            )
        }
        assertTrue("The corpus must contain every milestone page.", required.keys.all { name -> pages.any { it.name == "$name.java" } })
        assertTrue(summaries.all { it["markupPaired"] == true })
        val baseType=com.intellij.psi.JavaPsiFacade.getInstance(project).findClass("dev.jbaby.wicket.oat.components.OatAppLayout",com.intellij.psi.search.GlobalSearchScope.projectScope(project))!!
        val baseReport=WicketPageAnalyzer().analyze(baseType)!!
        assertNotNull(baseReport.warnings.toString(),baseReport.baseLayoutTemplate)
        assertEquals(3,baseReport.baseLayoutTemplate!!.constructors.size);assertEquals(1,baseReport.baseLayoutTemplate.extensionSlots.size)
        val baseMatches=baseReport.matching!!.findings.filter { it.code==DiagnosticCode.MATCHED }
        assertEquals(baseReport.warnings.toString(),8,baseMatches.size)
        assertTrue(baseMatches.all { it.confidence==dev.wicketlens.model.Confidence.INFERRED })
        assertEquals(setOf("html","appTitle","topNav","appName","topNavExtra","sidebar","menuItems","footer"),baseMatches.map { f -> baseReport.nodes.single { it.nodeId==f.javaNodeId }.wicketId }.toSet())
        assertEquals(9,baseReport.matching.findings.count { it.code==DiagnosticCode.UNRESOLVED_BRANCH })
        assertEquals(baseReport.warnings.toString(),2,baseReport.populationContexts.size)
        val populations=baseReport.populationContexts
        assertEquals(listOf(false,true),populations.map { it.populationTemplate!!.conditions.single().result })
        assertEquals(listOf(4,7),populations.map { context -> context.matching!!.findings.count { it.code==DiagnosticCode.MATCHED && it.javaNodeId in context.populationTemplate!!.ownedNodeIds } })
        assertTrue(populations.all { it.matching!!.findings.all { finding -> finding.code==DiagnosticCode.MATCHED && finding.confidence==dev.wicketlens.model.Confidence.INFERRED } })
        assertEquals(9,populations.flatMap { context -> context.matching!!.findings.filter { it.javaNodeId in context.populationTemplate!!.ownedNodeIds }.mapNotNull { it.markupLocation } }.distinct().size)
        val nativePopulations=dev.wicketlens.editor.WicketEditorAnalysis.navigationPages(baseType.containingFile).filter { it.report.populationTemplate!=null }.map { it.report }
        assertEquals(populations.map { WicketContextExporter().export(it) },nativePopulations.map { WicketContextExporter().export(it) })
        val badgeLiteral=PsiTreeUtil.findChildrenOfType(baseType,PsiLiteralExpression::class.java).single { it.value=="menuBadge" }
        val badgeTargets=badgeLiteral.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false)
        assertEquals(2,badgeTargets.size)
        assertTrue(badgeTargets.all { target -> target.element!!.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).any { it.element==badgeLiteral } })
        assertNull(dev.wicketlens.editor.WicketIdRenameTarget.at(badgeLiteral.containingFile,badgeLiteral.textOffset))
        val populationText=dev.wicketlens.ui.WicketReportFormatter().format(baseReport)
        assertTrue(populationText.contains("Population templates: 2"));assertTrue(populationText.contains("mi.isGroup() = false"));assertTrue(populationText.contains("mi.isGroup() = true"))
        assertTrue(baseReport.nodes.all { it.template && !it.idRenameSafe });assertTrue(baseReport.markup!!.documentDiagnostics.isEmpty())
        val baseQuery=WicketPageAnalysisRequest();val baseSnapshot=baseQuery.capture(project,baseType.qualifiedName!!)
        val baseJson=JsonParser.parseString(baseQuery.render(baseSnapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(baseReport)),baseJson["analysis"])
        val baseValidation=JsonParser.parseString(baseQuery.render(baseSnapshot,WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(8,baseValidation["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
        assertTrue(baseValidation.has("baseLayoutTemplate"))
        val baseContext=JsonParser.parseString(baseQuery.render(baseSnapshot,WicketMcpReport.CONTEXT,"topNav:appName")).asJsonObject["context"].asJsonObject
        assertEquals("BASE_RELATIVE",baseContext["baseLayoutTemplate"].asJsonObject["pathScope"].asString)
        assertEquals("INFERRED",baseContext["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        val htmlOrigins=PsiTreeUtil.findChildrenOfType(baseType,PsiLiteralExpression::class.java).filter { it.value=="html" }
        assertEquals(2,htmlOrigins.size)
        for(literal in htmlOrigins) {
            val targets=literal.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false)
            assertEquals(1,targets.size)
            val reverse=targets.single().element!!.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false)
            assertTrue(htmlOrigins.all { origin -> reverse.any { it.element==origin } })
            assertNull(dev.wicketlens.editor.WicketIdRenameTarget.at(literal.containingFile,literal.textOffset))
        }
        baseQuery.requireCurrent(project,baseSnapshot)
        val formType = com.intellij.psi.JavaPsiFacade.getInstance(project).findClass("dev.jbaby.wicket.oat.components.form.OatDateRangeField",com.intellij.psi.search.GlobalSearchScope.projectScope(project))!!
        val formPrimary = WicketPageAnalyzer().analyze(formType)!!
        val formResult = dev.wicketlens.analysis.WicketFormContexts().analyze(formType,formPrimary)
        val form = formResult.reports.single()
        assertEquals(formResult.diagnostics.toString(),1,formResult.reports.size)
        assertEquals(6,form.matching!!.findings.size)
        assertTrue(form.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED })
        assertEquals(setOf("CONVERSION","REQUIRED_CHECK","VALIDATOR_DECLARATION"),form.formTemplate!!.hooks.map { it.kind }.toSet())
        assertEquals(setOf("from","to"),form.nodes.filter { it.parentNodeId==form.formTemplate.inputNodeId }.mapNotNull { it.wicketId }.toSet())
        assertTrue(form.nodes.all { it.template && !it.idRenameSafe })
        val formQuery = WicketPageAnalysisRequest()
        val formSnapshot = formQuery.capture(project,formType.qualifiedName!!,includeNavigationContexts=true)
        val formJson = JsonParser.parseString(formQuery.render(formSnapshot)).asJsonObject
        assertEquals(formPrimary,formSnapshot.report)
        val exportedForm = formJson["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString=="FORM_TEMPLATE" }.asJsonObject
        assertEquals(6,exportedForm["bindings"].asJsonArray.size())
        assertEquals("unknown",exportedForm["formTemplate"].asJsonObject["validationResult"].asString)
        val nativeForm = dev.wicketlens.editor.WicketEditorAnalysis.navigationPages(formType.containingFile).single { it.report.formTemplate!=null }.report
        assertEquals(WicketContextExporter().export(form),WicketContextExporter().export(nativeForm))
        for(id in listOf("from","to")) {
            val literal = PsiTreeUtil.findChildrenOfType(formType,PsiLiteralExpression::class.java).single { it.value==id }
            val targets = literal.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false)
            assertEquals(1,targets.size)
            assertTrue(targets.single().element!!.containingFile.name.endsWith("OatDateRangeField\$RangeInput.html"))
            assertTrue(targets.single().element!!.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).any { it.element==literal })
        }
        val formFactory = formType.methods.single { it.name=="newInput" }
        val formSlot = dev.wicketlens.editor.WicketFormNavigation.targets(formFactory).single()
        assertTrue(formSlot.containingFile.name=="OatDateRangeField.html")
        assertTrue(formSlot.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).any { it.element==formFactory.nameIdentifier })
        formQuery.requireCurrent(project,formSnapshot)
        val invoice = pages.single { it.name=="InvoicePage.java" }.classes.single()
        val invoicePrimary = WicketPageAnalyzer().analyze(invoice)!!
        val registeredResult = dev.wicketlens.analysis.WicketFormContexts().analyze(invoice,invoicePrimary)
        assertEquals(registeredResult.diagnostics.toString(),1,registeredResult.reports.size)
        val registered = registeredResult.reports.single()
        assertEquals(6,registered.matching!!.findings.size)
        assertTrue(registered.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED })
        assertEquals("dev.jbaby.wicket.oat.app.PhoneInput",registered.formTemplate!!.inputClassName)
        assertEquals(setOf("code","number"),registered.nodes.filter { it.parentNodeId==registered.formTemplate.inputNodeId }.mapNotNull { it.wicketId }.toSet())
        assertEquals(invoice.qualifiedName,registered.formTemplate.registration!!.ownerClassName)
        val registeredSnapshot = formQuery.capture(project,invoice.qualifiedName!!,includeNavigationContexts=true)
        assertEquals(invoicePrimary,registeredSnapshot.report)
        val registeredNative = dev.wicketlens.editor.WicketEditorAnalysis.navigationPages(invoice.containingFile).single { it.report.formTemplate?.registration!=null }.report
        assertEquals(WicketContextExporter().export(registered),WicketContextExporter().export(registeredNative))
        assertEquals(WicketContextExporter().export(registered),WicketContextExporter().export(registeredSnapshot.navigationContexts!!.contexts.single { it.report.formTemplate?.registration!=null }.report))
        val invoiceSubmissions=registeredSnapshot.navigationContexts!!.contexts.filter { it.report.submissionTemplate?.formCallback!=null && it.report.qualifiedClassName==invoice.qualifiedName }.map { it.report }
        assertEquals(registeredSnapshot.navigationContexts.lifecycleDiagnostics.toString(),2,invoiceSubmissions.size)
        assertEquals(setOf("onSubmit","onError"),invoiceSubmissions.map { it.submissionTemplate!!.callbackName }.toSet())
        val invoiceNative=dev.wicketlens.editor.WicketEditorAnalysis.navigationPages(invoice.containingFile).map { it.report }.filter { it.submissionTemplate?.formCallback!=null && it.qualifiedClassName==invoice.qualifiedName }
        assertEquals(invoiceSubmissions.map { WicketContextExporter().export(it) },invoiceNative.map { WicketContextExporter().export(it) })
        invoiceSubmissions.forEach { report ->
            assertEquals(2,report.matching!!.findings.size)
            assertTrue(report.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED })
            assertEquals("invoiceForm:submit",report.submissionTemplate!!.triggerComponentPath)
            assertEquals(listOf("invoiceForm:submit","invoiceForm"),report.submissionTemplate.effects.map { it.targetComponentPath })
            val callback=PsiTreeUtil.findChildrenOfType(invoice,com.intellij.psi.PsiMethod::class.java).single { it.textOffset==report.submissionTemplate.callbackLocation.offset }
            val trigger=dev.wicketlens.editor.WicketSubmissionNavigation.targets(callback).single()
            assertEquals("InvoicePage.html",trigger.containingFile.name)
            assertTrue(dev.wicketlens.editor.WicketSubmissionNavigation.targets(trigger).any { it==callback.nameIdentifier })
        }
        val phoneSource = com.intellij.psi.JavaPsiFacade.getInstance(project).findClass(registered.formTemplate.inputClassName,com.intellij.psi.search.GlobalSearchScope.projectScope(project))!!
        for(id in listOf("code","number")) {
            val literal=PsiTreeUtil.findChildrenOfType(phoneSource,PsiLiteralExpression::class.java).single { it.value==id }
            val targets=literal.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false)
            assertEquals(1,targets.size);assertEquals("PhoneInput.html",targets.single().element!!.containingFile.name)
            assertTrue(targets.single().element!!.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).any { it.element==literal })
        }
        val phoneReference=PsiTreeUtil.findChildrenOfType(invoice,com.intellij.psi.PsiMethodReferenceExpression::class.java).single { it.text=="PhoneInput::new" }
        val phoneSlot=dev.wicketlens.editor.WicketFormNavigation.targets(phoneReference).single()
        assertEquals("OatCustomField.html",phoneSlot.containingFile.name)
        assertTrue(phoneSlot.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).any { it.element==phoneReference })
        formQuery.requireCurrent(project,registeredSnapshot)
        val treeSource = pages.single { it.name == "AccountsPage.java" }
        val treePrimary = WicketPageAnalyzer().analyze(treeSource.classes.single())!!
        val treeResult = dev.wicketlens.analysis.WicketTreeContexts().analyze(treeSource.classes.single(),treePrimary)
        val treeTemplates = treeResult.reports
        assertEquals(treeResult.diagnostics.toString(),4,treeTemplates.size)
        assertEquals(listOf("folders","folders","accounts","accounts"),treeTemplates.map { it.treeTemplate!!.hostComponentPath })
        assertEquals(listOf("NULL","NON_NULL","NULL","NON_NULL"),treeTemplates.map { it.treeTemplate!!.conditions.single().nullness })
        treeTemplates.forEach { report ->
            assertEquals(8,report.matching!!.findings.size)
            assertTrue(report.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED })
            assertTrue(report.nodes.all { it.template && !it.idRenameSafe })
            assertNull(report.nodes.single { it.nodeId==report.rootNodeId }.wicketId)
            assertEquals(1,report.nodes.count { it.possibleComponentTypes.size==2 })
        }
        val treeQuery = WicketPageAnalysisRequest()
        val treeSnapshot = treeQuery.capture(project,treeSource.classes.single().qualifiedName!!,includeNavigationContexts=true)
        val treeStructure = JsonParser.parseString(treeQuery.render(treeSnapshot)).asJsonObject
        assertEquals(treePrimary,treeSnapshot.report)
        val treeContexts = treeSnapshot.navigationContexts!!.contexts.filter { it.relationship=="TREE_TEMPLATE" }
        assertEquals(treeTemplates.map { WicketContextExporter().export(it) },treeContexts.map { WicketContextExporter().export(it.report) })
        assertEquals(4,treeStructure["navigationContexts"].asJsonObject["contexts"].asJsonArray.count { it.asJsonObject["relationship"].asString=="TREE_TEMPLATE" })
        val contentJava = dev.wicketlens.analysis.WicketSourceFiles.find(project,treeTemplates.first().nodes.single { it.wicketId=="link" }.javaLocation.file)!!.let { com.intellij.psi.PsiManager.getInstance(project).findFile(it)!! }
        val label = PsiTreeUtil.findChildrenOfType(contentJava,PsiLiteralExpression::class.java).single { it.value=="label" && (it.parent.parent as? com.intellij.psi.PsiNewExpression)?.classReference?.referenceName=="Label" }
        val treeTargets = label.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false)
        assertEquals(2,treeTargets.size)
        treeTargets.forEach { target -> assertTrue(target.element!!.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).any { it.element==label }) }
        treeQuery.requireCurrent(project,treeSnapshot)
        val dialogSource = pages.single { it.name == "DialogPage.java" }
        val dialogQuery = WicketPageAnalysisRequest()
        val dialogSnapshot = dialogQuery.capture(project, "dev.jbaby.wicket.oat.app.DialogPage")
        val dialogReport = dialogSnapshot.report
        assertNotNull(dev.wicketlens.ui.WicketReportFormatter().format(dialogReport),dialogReport.initialContentSetup)
        assertEquals(4,dialogReport.initialContentSetup!!.hosts.size)
        assertEquals(3,dialogReport.initialContentSetup.hosts.sumOf { it.placementLocations.size })
        assertEquals(mapOf(DiagnosticCode.MATCHED to 72,DiagnosticCode.UNRESOLVED_BRANCH to 13),dialogReport.matching!!.findings.groupingBy { it.code }.eachCount())
        assertEquals(listOf(4,4,7),dialogReport.populationContexts.map { it.matching!!.findings.count { finding -> finding.code==DiagnosticCode.MATCHED } }.sorted())
        val dialogJson = JsonParser.parseString(dialogQuery.render(dialogSnapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(dialogReport)),dialogJson)
        val dialogValidation=JsonParser.parseString(dialogQuery.render(dialogSnapshot,WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(dialogJson["initialContentSetup"],dialogValidation["initialContentSetup"])
        for(path in listOf("deleteDialog:dialog:form:content","editDialog:dialog:form:content:name","editDialog:dialog:form:content:email",
            "filtersDrawer:dialog:form:content:status","filtersDrawer:dialog:form:content:overdueOnly","detailsDrawer:dialog:form:content")) {
            val context=JsonParser.parseString(dialogQuery.render(dialogSnapshot,WicketMcpReport.CONTEXT,path)).asJsonObject["context"].asJsonObject
            assertEquals(path,"MATCHED",context["findings"].asJsonArray.single().asJsonObject["code"].asString)
            assertEquals(path,"INFERRED",context["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
            assertEquals(dialogJson["initialContentSetup"],context["initialContentSetup"])
        }
        for(type in listOf("OatTextField","OatEmailField","OatDropdownChoice","OatSwitch")) {
            val node=dialogReport.nodes.single { it.componentType.endsWith(".$type") }
            val literal=PsiTreeUtil.findChildrenOfType(dialogSource,PsiLiteralExpression::class.java).single { it.textOffset==node.idLocation!!.offset }
            val targets=literal.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.mapNotNull { it.element }.distinct()
            assertEquals(type,1,targets.size)
            assertTrue(targets.single().references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element==literal })
        }
        assertTrue(dialogReport.fragmentUses.single { it.definitionId=="personDetails" }.instanceNodeIds.isEmpty())
        assertTrue(dialogReport.matching!!.findings.filter { it.code==DiagnosticCode.MATCHED }.all { it.confidence==dev.wicketlens.model.Confidence.INFERRED })
        dialogQuery.requireCurrent(project,dialogSnapshot)
        val detailsDiscovered=dev.wicketlens.analysis.WicketEventContentContexts().analyze(dialogSource.classes.single(),dialogReport)
        assertEquals(detailsDiscovered.diagnostics.toString(),1,detailsDiscovered.reports.size)
        val detailsSnapshot=dialogQuery.capture(project,dialogReport.qualifiedClassName,includeNavigationContexts=true)
        assertEquals(dialogJson,JsonParser.parseString(WicketContextExporter().export(detailsSnapshot.report)))
        val detailsEvent=detailsSnapshot.navigationContexts!!.contexts.single { it.report.lifecycle?.directContent!=null }.report
        assertEquals("detailsDrawer:dialog:form:content",detailsEvent.lifecycle!!.componentPath)
        assertEquals("people:rows:details",detailsEvent.lifecycle.directContent!!.triggerComponentPath)
        assertEquals("people:rows",detailsEvent.lifecycle.directContent.triggerHostComponentPath)
        assertNotNull(detailsEvent.lifecycle.directContent.triggerPopulationContextId)
        assertEquals(5,detailsEvent.matching!!.findings.size)
        assertTrue(detailsEvent.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED })
        val nativeDetails=dev.wicketlens.editor.WicketEditorAnalysis.navigationPages(dialogSource).single { it.report.lifecycle?.directContent!=null }.report
        assertEquals(WicketContextExporter().export(detailsEvent),WicketContextExporter().export(nativeDetails))
        val allocation=PsiTreeUtil.findChildrenOfType(dialogSource,com.intellij.psi.PsiNewExpression::class.java).single { it.textOffset==detailsEvent.lifecycle.factoryLocation.offset }
        val target=dev.wicketlens.editor.WicketReplacementNavigation.targets(allocation).single()
        assertEquals("OatDialog.html",target.containingFile.name)
        assertTrue(dev.wicketlens.editor.WicketReplacementNavigation.targets(target).contains(allocation))
        val detailsList=detailsEvent.nodes.single { it.wicketId=="list" }
        val listLiteral=PsiTreeUtil.findChildrenOfType(dialogSource,PsiLiteralExpression::class.java).single { it.textOffset==detailsList.idLocation!!.offset }
        val listTargets=listLiteral.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.mapNotNull { it.element }.distinct()
        assertEquals(1,listTargets.size)
        assertTrue(listTargets.single().references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element==listLiteral })
        assertNull(dev.wicketlens.editor.WicketIdRenameTarget.at(listLiteral.containingFile,listLiteral.textOffset))
        dialogQuery.requireCurrent(project,detailsSnapshot)
        val submissions=detailsSnapshot.navigationContexts.contexts.filter { it.relationship=="SUBMISSION_TEMPLATE" }.map { it.report }
        assertEquals(detailsSnapshot.navigationContexts.diagnostics.toString(),3,submissions.size)
        assertEquals(setOf("deleteDialog","editDialog","filtersDrawer"),submissions.map { it.submissionTemplate!!.hostComponentPath }.toSet())
        val nativeSubmissions=dev.wicketlens.editor.WicketEditorAnalysis.navigationPages(dialogSource).map { it.report }.filter { it.submissionTemplate!=null }
        assertEquals(submissions.map { WicketContextExporter().export(it) },nativeSubmissions.map { WicketContextExporter().export(it) })
        for(submission in submissions) {
            val metadata=submission.submissionTemplate!!
            assertEquals("onConfirm",metadata.callbackName)
            assertEquals("${metadata.hostComponentPath}:dialog:form:confirm",metadata.triggerComponentPath)
            assertEquals(if(metadata.hostComponentPath=="editDialog") 5 else 4,submission.matching!!.findings.size)
            assertTrue(submission.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==dev.wicketlens.model.Confidence.INFERRED })
            assertEquals(1,metadata.effects.count { it.kind=="FEEDBACK" })
            assertEquals(if(metadata.hostComponentPath=="editDialog") listOf("people") else emptyList(),metadata.effects.filter { it.kind=="AJAX_REFRESH" }.map { it.targetComponentPath })
            assertNull(metadata.contentNodeId)
            val callback=PsiTreeUtil.findChildrenOfType(dialogSource,com.intellij.psi.PsiMethod::class.java).single { it.textOffset==metadata.callbackLocation.offset }
            val trigger=dev.wicketlens.editor.WicketSubmissionNavigation.targets(callback).single()
            assertEquals("OatDialog.html",trigger.containingFile.name)
            assertEquals("confirm",trigger.text.trim('"','\''))
            assertTrue(dev.wicketlens.editor.WicketSubmissionNavigation.targets(trigger).any { it==callback.nameIdentifier })
            assertNull(dev.wicketlens.editor.WicketIdRenameTarget.at(trigger.containingFile,trigger.textOffset))
        }
        val accordionSource = pages.single { it.name == "AccordionPage.java" }
        val accordionReport = WicketPageAnalyzer().analyze(accordionSource.classes.single())!!
        val accordionHost = accordionReport.nodes.single { it.wicketId == "accordion" }
        assertNotNull(accordionHost.registeredListPopulation)
        assertEquals(11, accordionReport.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        val accordionPopulation = accordionReport.populationContexts.single { it.populationTemplate!!.registration != null }
        assertEquals("accordion", accordionPopulation.populationTemplate!!.hostComponentPath)
        assertEquals(2, accordionPopulation.matching!!.findings.size)
        assertTrue(accordionPopulation.matching.findings.all { it.code == DiagnosticCode.MATCHED && it.confidence == dev.wicketlens.model.Confidence.INFERRED })
        assertEquals(3, accordionReport.populationContexts.size)
        assertEquals(2, accordionHost.registeredListPopulation!!.factoryLocations.size)
        assertEquals(1, accordionHost.registeredListPopulation.itemFactoryLocations.size)
        for (id in listOf("accordion", "title", "content")) {
            val literal = PsiTreeUtil.findChildrenOfType(accordionSource, PsiLiteralExpression::class.java).single { it.value == id }
            val uses = literal.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }
            assertEquals(id, 1, uses.size)
            assertTrue(id, uses.single().element!!.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element == literal })
        }
        val accordionCallback = PsiTreeUtil.findChildrenOfType(accordionSource, com.intellij.psi.PsiLambdaExpression::class.java).single()
        assertEquals("accordion", dev.wicketlens.editor.WicketReplacementNavigation.targets(accordionCallback).single().text.trim('"', '\''))
        val accordionQuery = WicketPageAnalysisRequest()
        val accordionSnapshot = accordionQuery.capture(project, accordionSource.classes.single().qualifiedName!!, includeNavigationContexts=true)
        assertEquals(accordionReport, accordionSnapshot.report)
        val accordionJson = JsonParser.parseString(accordionQuery.render(accordionSnapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(accordionReport)), accordionJson["analysis"])
        val accordionValidation = JsonParser.parseString(accordionQuery.render(accordionSnapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(3, accordionValidation["validation"].asJsonObject["populationTemplates"].asJsonArray.size())
        val accordionContext = JsonParser.parseString(accordionQuery.render(accordionSnapshot, WicketMcpReport.CONTEXT, "accordion")).asJsonObject
        assertEquals(3, accordionContext["context"].asJsonObject["populationTemplates"].asJsonArray.size())
        accordionQuery.requireCurrent(project, accordionSnapshot)
        val tableSource = pages.single { it.name == "DataTablePage.java" }
        val tablePrimary = WicketPageAnalyzer().analyze(tableSource.classes.single())!!
        val contentResult = dev.wicketlens.analysis.WicketReplacementContexts().analyze(tableSource.classes.single(), tablePrimary)
        val contentTemplates = contentResult.reports.filter { it.lifecycle?.contentFactory != null }
        assertEquals(contentResult.diagnostics.toString(), 1, contentTemplates.size)
        val toolbar = contentTemplates.single()
        assertEquals("dataTable:toolbar", toolbar.lifecycle!!.componentPath)
        assertEquals(setOf("dataTable", "toolbar", "searchForm", "search"), toolbar.nodes.mapNotNull { it.wicketId }.toSet())
        assertEquals(4, toolbar.matching!!.findings.size)
        assertTrue(toolbar.matching.findings.all { it.code == DiagnosticCode.MATCHED && it.confidence == dev.wicketlens.model.Confidence.INFERRED })
        assertTrue(toolbar.nodes.filter { it.wicketId != null }.all { it.template && !it.idRenameSafe })
        assertEquals(2, toolbar.lifecycle.contentFactory!!.callbackLocations.size)
        val toolbarQuery = WicketPageAnalysisRequest()
        val toolbarSnapshot = toolbarQuery.capture(project, tableSource.classes.single().qualifiedName!!, includeNavigationContexts=true)
        val toolbarContexts = toolbarSnapshot.navigationContexts!!.contexts.filter { it.relationship == "FACTORY_TEMPLATE" }
        assertEquals(listOf(WicketContextExporter().export(toolbar)), toolbarContexts.map { WicketContextExporter().export(it.report) })
        assertEquals(tablePrimary, toolbarSnapshot.report)
        val confirmationReview=confirmationEvidence(tableSource.classes.single(),tablePrimary,toolbarSnapshot)
        val bulkSelectionReview=bulkSelectionEvidence(tableSource.classes.single(),tablePrimary,toolbarSnapshot)
        val recordActionsReview=recordActionEvidence(tableSource.classes.single(),tablePrimary,toolbarSnapshot)
        val toolbarJson = boundedDataTableNavigation(toolbarQuery,toolbarSnapshot)
        val toolbarContext = toolbarJson["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString == "FACTORY_TEMPLATE" }.asJsonObject
        assertEquals("not_evaluated", toolbarContext["lifecycle"].asJsonObject["contentFactory"].asJsonObject["callbackExecution"].asString)
        assertTrue(toolbarContext["bindings"].asJsonArray.all { it.asJsonObject.has("ancestors") && it.asJsonObject.has("children") })
        val toolbarFactory = PsiTreeUtil.findChildrenOfType(tableSource, com.intellij.psi.PsiLambdaExpression::class.java).single {
            (it.parent.parent as? com.intellij.psi.PsiMethodCallExpression)?.methodExpression?.referenceName == "setToolbar"
        }
        val toolbarTargets = dev.wicketlens.editor.WicketReplacementNavigation.targets(toolbarFactory)
        assertEquals(1, toolbarTargets.size); assertEquals("OatDataTable.html", toolbarTargets.single().containingFile.name)
        assertTrue(toolbarTargets.single().references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element == toolbarFactory })
        for (id in listOf("searchForm", "search", "toolbarFragment")) {
            val literal = PsiTreeUtil.findChildrenOfType(tableSource, PsiLiteralExpression::class.java).single { it.value == id }
            val targets = literal.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }
            assertEquals(id, 1, targets.size)
            assertTrue(id, targets.single().element!!.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element == literal })
        }
        toolbarQuery.requireCurrent(project, toolbarSnapshot)
        val toolbarText = dev.wicketlens.ui.WicketReportFormatter().formatWithLinks(toolbar)
        assertTrue(toolbarText.text, "source content-factory template at dataTable:toolbar" in toolbarText.text)
        assertEquals(4, Regex("MATCHED / INFERRED").findAll(toolbarText.text).count())
        val emptyResult = dev.wicketlens.analysis.WicketTableToolbarContexts().analyze(tableSource.classes.single(), tablePrimary)
        assertEquals(emptyResult.diagnostics.toString(), 1, emptyResult.reports.size)
        val emptyToolbar = emptyResult.reports.single()
        assertEquals("emptyDataTable", emptyToolbar.toolbarTemplate!!.hostComponentPath)
        assertEquals("dev.jbaby.wicket.oat.components.OatDataTable.EmptyStateToolbar", emptyToolbar.toolbarTemplate.toolbarClassName)
        assertEquals("dev.jbaby.wicket.oat.components.OatEmptyState", emptyToolbar.toolbarTemplate.contentClassName)
        assertTrue(emptyToolbar.markupPath.orEmpty().endsWith("/OatDataTable\$EmptyStateToolbar.html"))
        assertNull(emptyToolbar.nodes.single { it.nodeId == emptyToolbar.rootNodeId }.wicketId)
        assertEquals(setOf("cell", "content", "title", "message"), emptyToolbar.nodes.mapNotNull { it.wicketId }.toSet())
        assertEquals(4, emptyToolbar.matching!!.findings.size)
        assertTrue(emptyToolbar.matching.findings.all { it.code == DiagnosticCode.MATCHED && it.confidence == dev.wicketlens.model.Confidence.INFERRED })
        assertTrue(emptyToolbar.nodes.all { it.template && !it.idRenameSafe })
        assertEquals(2, emptyToolbar.toolbarTemplate.callbackLocations.size)
        val emptyContexts = toolbarSnapshot.navigationContexts!!.contexts.filter { it.relationship == "TABLE_TOOLBAR_TEMPLATE" }
        assertEquals(listOf(WicketContextExporter().export(emptyToolbar)), emptyContexts.map { WicketContextExporter().export(it.report) })
        val emptyJson = toolbarJson["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString == "TABLE_TOOLBAR_TEMPLATE" }.asJsonObject
        assertEquals("TOOLBAR_RELATIVE", emptyJson["toolbarTemplate"].asJsonObject["pathScope"].asString)
        assertEquals("unknown", emptyJson["toolbarTemplate"].asJsonObject["runtimeToolbarId"].asString)
        assertEquals("unknown", emptyJson["toolbarTemplate"].asJsonObject["rowCount"].asString)
        val emptyFactory = PsiTreeUtil.findChildrenOfType(tableSource, com.intellij.psi.PsiLambdaExpression::class.java).single {
            (it.parent.parent as? com.intellij.psi.PsiMethodCallExpression)?.methodExpression?.referenceName == "setEmptyState"
        }
        val emptyTarget = dev.wicketlens.editor.WicketReplacementNavigation.targets(emptyFactory).single()
        assertEquals("OatDataTable\$EmptyStateToolbar.html", emptyTarget.containingFile.name)
        assertTrue(emptyTarget.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element == emptyFactory })
        for (id in listOf("cell", "title", "message")) {
            val node = emptyToolbar.nodes.single { it.wicketId == id }
            val sourceFile = dev.wicketlens.analysis.WicketSourceFiles.find(project, node.idLocation!!.file)!!.let { com.intellij.psi.PsiManager.getInstance(project).findFile(it)!! }
            val literal = generateSequence(sourceFile.findElementAt(node.idLocation.offset)) { it.parent }.filterIsInstance<PsiLiteralExpression>().first()
            val targets = literal.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }
            assertTrue(id, targets.isNotEmpty())
            assertTrue(id, targets.any { target -> target.element!!.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element == literal } })
        }
        val emptyText = dev.wicketlens.ui.WicketReportFormatter().formatWithLinks(emptyToolbar)
        assertTrue(emptyText.text, "toolbar-relative source content template for emptyDataTable" in emptyText.text)
        assertEquals(4, Regex("MATCHED / INFERRED").findAll(emptyText.text).count())
        val wizardSource = pages.single { it.name == "OrderWizardPage.java" }
        val wizardPrimary = WicketPageAnalyzer().analyze(wizardSource.classes.single())!!
        val wizardResult = dev.wicketlens.analysis.WicketReplacementContexts().analyze(wizardSource.classes.single(), wizardPrimary)
        val wizardSelections = wizardResult.reports.filter { it.lifecycle?.selection != null }
        assertEquals(wizardResult.diagnostics.toString(), 3, wizardSelections.size)
        assertEquals(listOf("Customer", "Items", "Review"), wizardSelections.map { it.lifecycle!!.selection!!.title })
        assertEquals(listOf("customerStep", "itemsStep", "reviewStep"), wizardSelections.map { it.lifecycle!!.selection!!.definitionId })
        assertEquals(listOf(6, 5, 4), wizardSelections.map { it.matching!!.findings.count { it.code == DiagnosticCode.MATCHED } })
        val expectedFields = listOf(setOf("customer", "notify", "delivery"), setOf("quantity", "unitPrice"), setOf("review"))
        wizardSelections.forEachIndexed { index, report ->
            assertEquals("wizard:form:body", report.lifecycle!!.componentPath)
            assertEquals(index, report.lifecycle.selection!!.registrationIndex)
            assertEquals(3, report.lifecycle.selection.registrationCount)
            assertNotNull(report.lifecycle.selection.initializationLocation)
            assertTrue(report.matching!!.findings.all { it.code == DiagnosticCode.MATCHED && it.confidence == dev.wicketlens.model.Confidence.INFERRED })
            assertTrue(report.nodes.filter { it.wicketId != null }.all { it.template && !it.idRenameSafe })
            assertEquals(expectedFields[index], report.nodes.filter { it.parentNodeId == report.lifecycle.factoryNodeId }.mapNotNull { it.wicketId }.toSet())
        }
        val wizardQuery = WicketPageAnalysisRequest()
        val wizardSnapshot = wizardQuery.capture(project, wizardSource.classes.single().qualifiedName!!, includeNavigationContexts = true)
        val wizardContexts = wizardSnapshot.navigationContexts!!.contexts.filter { it.relationship == "SELECTION_TEMPLATE" }
        assertEquals(wizardSelections.map { WicketContextExporter().export(it) }, wizardContexts.map { WicketContextExporter().export(it.report) })
        assertEquals(wizardPrimary, wizardSnapshot.report)
        val wizardFactories = PsiTreeUtil.findChildrenOfType(wizardSource, com.intellij.psi.PsiLambdaExpression::class.java).filter {
            (it.parent.parent as? com.intellij.psi.PsiMethodCallExpression)?.methodExpression?.referenceName == "addStep"
        }
        assertEquals(3, wizardFactories.size)
        wizardFactories.forEach { lambda ->
            val targets = dev.wicketlens.editor.WicketReplacementNavigation.targets(lambda)
            assertEquals(1, targets.size)
            assertTrue(targets.single().containingFile.virtualFile.path.endsWith("/OatWizard.html"))
            assertEquals(3, targets.single().references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.count { it.element is com.intellij.psi.PsiLambdaExpression })
        }
        wizardQuery.requireCurrent(project, wizardSnapshot)
        val productSource = pages.single { it.name == "ProductsPage.java" }
        val productFields = productSource.classes.single().innerClasses.single { it.name == "ProductFields" }
        val nestedReport = requireNotNull(WicketPageAnalyzer().analyze(productFields))
        val nestedMatches = nestedReport.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }
        assertTrue(nestedReport.markupPath.orEmpty().endsWith("/ProductsPage\$ProductFields.html"))
        val fieldIds = setOf("name", "category", "price", "stock", "description", "suppliers")
        assertEquals(fieldIds, nestedReport.markup!!.elements.filter { it.hasWicketId }.mapNotNull { it.wicketId }.toSet())
        assertEquals(nestedReport.warnings.toString(), fieldIds,
            nestedMatches.map { finding -> nestedReport.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }.toSet())
        assertTrue(nestedReport.nodes.filter { it.wicketId in fieldIds }.none { it.idRenameSafe })
        assertTrue(nestedReport.warnings.none { "origin-use audit exceeded" in it.message })
        val nestedHtml = WicketIdPsi.source(project, nestedReport.markupPath!!)!!
        assertEquals(productFields.qualifiedName, dev.wicketlens.analysis.WicketEditorTargetResolver().resolve(project, nestedHtml.virtualFile.url, 0).qualifiedClassName)
        assertEquals(productFields.qualifiedName, dev.wicketlens.analysis.WicketPageSelection().analyze(project, productSource.virtualFile.url, productFields.textOffset).qualifiedClassName)
        for (id in fieldIds) {
            val literal = PsiTreeUtil.findChildrenOfType(productFields, PsiLiteralExpression::class.java).single { it.value == id }
            val targets = literal.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }
            assertTrue("ProductFields:$id native navigation", targets.isNotEmpty())
            assertTrue(targets.any { target -> target.element!!.references.filterIsInstance<PsiPolyVariantReference>().any { reference ->
                reference.multiResolve(false).any { it.element == literal }
            } })
        }
        val nestedQuery = WicketPageAnalysisRequest()
        val nestedSnapshot = nestedQuery.capture(project, productFields.qualifiedName!!, includeNavigationContexts = true)
        val nestedStructure = JsonParser.parseString(nestedQuery.render(nestedSnapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(nestedReport)), nestedStructure["analysis"])
        assertTrue(nestedStructure["navigationContexts"].asJsonObject["contexts"].asJsonArray.any {
            it.asJsonObject["relationship"].asString == "OWN" && it.asJsonObject["bindings"].asJsonArray.size() == 6
        })
        assertTrue(nestedSnapshot.navigationContexts!!.contexts.any {
            it.report.qualifiedClassName == productSource.classes.single().qualifiedName && it.relationship == "CANDIDATE" && it.bindingNodeIds.isEmpty()
        })
        val events = nestedSnapshot.navigationContexts.contexts.filter { it.relationship == "EVENT_TEMPLATE" && it.report.qualifiedClassName == productSource.classes.single().qualifiedName }
        val event = events.single().report
        assertEquals("products:editor:dialog:form:content", event.lifecycle!!.componentPath)
        assertEquals(11, event.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(event.matching.findings.all { it.confidence == dev.wicketlens.model.Confidence.INFERRED })
        assertTrue(event.nodes.filter { it.wicketId != null }.all { it.template && !it.idRenameSafe })
        assertEquals(fieldIds, event.nodes.filter { it.parentNodeId == event.lifecycle.factoryNodeId }.mapNotNull { it.wicketId }.toSet())
        val factory = PsiTreeUtil.findChildrenOfType(productSource, com.intellij.psi.PsiMethodReferenceExpression::class.java).single { it.text == "ProductFields::new" }
        val factoryTargets = dev.wicketlens.editor.WicketReplacementNavigation.targets(factory)
        assertEquals(1, factoryTargets.size)
        assertTrue(factoryTargets.single().containingFile.virtualFile.path.endsWith("/OatDialog.html"))
        assertTrue(factoryTargets.single().references.filterIsInstance<PsiPolyVariantReference>().any { ref -> ref.multiResolve(false).any { it.element == factory } })
        nestedQuery.requireCurrent(project, nestedSnapshot)
        val splitSnapshot = nestedQuery.capture(project, "dev.jbaby.wicket.oat.components.OatSplitLayout", includeNavigationContexts = true)
        val inboxContext = splitSnapshot.navigationContexts!!.contexts.single { it.report.qualifiedClassName == "dev.jbaby.wicket.oat.app.InboxPage" && it.relationship=="CALLER" }
        assertEquals("CALLER", inboxContext.relationship)
        assertEquals(1, inboxContext.scopeNodeIds.size)
        val second = inboxContext.report.nodes.single { it.nodeId in inboxContext.bindingNodeIds && it.wicketId == "second" }
        assertTrue(second.javaLocation.file.endsWith("/InboxPage.java"))
        val splitHtml = WicketIdPsi.source(project, splitSnapshot.report.markupPath!!)!!
        val secondAttribute = PsiTreeUtil.findChildrenOfType(splitHtml, com.intellij.psi.xml.XmlAttributeValue::class.java).single { it.value == "second" }
        val splitTargets = secondAttribute.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false)
        assertTrue(splitTargets.any { it.element?.containingFile?.virtualFile?.path?.endsWith("/InboxPage.java") == true })
        nestedQuery.requireCurrent(project, splitSnapshot)
        val completionEvidence=mutableListOf<Map<String,Any>>()
        fun completionProbe(source:com.intellij.psi.PsiFile,before:String,after:String,expected:String,direction:String) {
            val original=source.text
            assertTrue(source.text,before in original)
            fun write(text:String) {
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(text) }
                com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
            try {
                write(original.replace(before,after))
                val selected=if(direction=="JAVA") {
                    val start=source.text.indexOf(after)+after.indexOf("\"\"")
                    dev.wicketlens.editor.WicketIdPsi.valueAt(source,start+1)!!
                } else {
                    val start=source.text.indexOf(after)+after.indexOf("''")
                    dev.wicketlens.editor.WicketIdPsi.valueAt(source,start+1)!!
                }
                val choices=dev.wicketlens.editor.WicketIdCompletionContributor().suggestions(selected)
                assertEquals("${source.name}: $choices",listOf(expected),choices.map { it.id })
                assertTrue(choices.all { it.confidence==dev.wicketlens.model.Confidence.INFERRED && it.componentTypes.isNotEmpty() && it.contexts.isNotEmpty() })
                assertFalse(dev.wicketlens.analysis.WicketCompletionAnalysis.active)
                completionEvidence+=mapOf("source" to source.name,"direction" to direction,"candidate" to expected,"confidence" to "INFERRED","contextCount" to choices.single().contexts.size)
            } finally { write(original) }
        }
        completionProbe(dialogSource,"OatDescriptionList(\"list\",","OatDescriptionList(\"\",","list","JAVA")
        val dialogHtml=dev.wicketlens.editor.WicketIdPsi.source(project,dialogReport.markupPath!!)!!
        completionProbe(dialogHtml,"wicket:id=\"list\"","wicket:id=''","list","HTML")
        completionProbe(dialogSource,"OatTextField<String>(\"name\")","OatTextField<String>(\"\")","name","JAVA")
        completionProbe(accordionSource,"Label(\"title\",","Label(\"\",","title","JAVA")
        val accordionHtml=dev.wicketlens.editor.WicketIdPsi.source(project,accordionReport.markupPath!!)!!
        completionProbe(accordionHtml,"wicket:id=\"title\"","wicket:id=''","title","HTML")
        val baseJava=baseType.containingFile
        completionProbe(baseJava,"Label(\"menuBadge\",","Label(\"\",","menuBadge","JAVA")
        val popupEvidence=mutableListOf<Map<String,Any>>()
        fun popupProbe(source:com.intellij.psi.PsiFile,before:String,after:String,expected:String,direction:String,prefix:String="") {
            val original=source.text
            val settings=com.intellij.codeInsight.CodeInsightSettings.getInstance();val automatic=settings.AUTOCOMPLETE_ON_CODE_COMPLETION
            fun write(text:String) {
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(text) }
                com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(source)!!)
            }
            try {
                assertTrue(source.text,before in original);write(original.replace(before,after))
                settings.AUTOCOMPLETE_ON_CODE_COMPLETION=false
                myFixture.configureFromExistingVirtualFile(source.virtualFile)
                val hole=(if(direction=="JAVA") "\"" else "'")+prefix
                val start=source.text.indexOf(after)+after.indexOf(hole)+1+prefix.length
                myFixture.editor.caretModel.moveToOffset(start)
                val items=myFixture.completeBasic().orEmpty().toList()
                assertEquals("${source.name} popup: $items",listOf(expected),items.map { it.lookupString })
                assertTrue(items.all { it.getUserData(dev.wicketlens.editor.WicketIdCompletionContributor.CANDIDATE)==true })
                assertTrue(source.text,after in source.text)
                popupEvidence+=mapOf("source" to source.name,"direction" to direction,"candidate" to expected,"prefix" to prefix,"allContributorsChecked" to true)
            } finally {
                myFixture.lookup?.hideLookup(false);settings.AUTOCOMPLETE_ON_CODE_COMPLETION=automatic
                write(original)
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).closeFile(source.virtualFile)
            }
        }
        popupProbe(dialogSource,"OatDialog(\"deleteDialog\",","OatDialog(\"\",","deleteDialog","JAVA")
        popupProbe(dialogHtml,"wicket:id=\"deleteDialog\"","wicket:id=''","deleteDialog","HTML")
        popupProbe(dialogSource,"OatDialog(\"deleteDialog\",","OatDialog(\"delSuffix\",","deleteDialog","JAVA","del")
        popupProbe(dialogHtml,"wicket:id=\"deleteDialog\"","wicket:id='delSuffix'","deleteDialog","HTML","del")
        popupProbe(dialogSource,"OatTextField<String>(\"name\")","OatTextField<String>(\"\")","name","JAVA")
        popupProbe(dialogHtml,"<div wicket:id=\"name\"","<div wicket:id=''","name","HTML")
        popupProbe(dialogSource,"OatDescriptionList(\"list\",","OatDescriptionList(\"\",","list","JAVA")
        popupProbe(dialogHtml,"wicket:id=\"list\"","wicket:id=''","list","HTML")
        val rowLabel = "Label(\"email\", item.getModelObject().email)"
        val rowCell = "<td wicket:id=\"email\">"
        popupProbe(dialogHtml, rowCell, "<td wicket:id=''>", "email", "HTML")
        popupProbe(dialogSource, rowLabel, rowLabel.replace("\"email\"", "\"\""), "email", "JAVA")
        popupProbe(dialogSource, rowLabel, rowLabel.replace("\"email\"", "\"emSuffix\""), "email", "JAVA", "em")

        fun gotoEmail(source: com.intellij.psi.PsiFile, snippet: String): List<com.intellij.psi.PsiElement> {
            myFixture.configureFromExistingVirtualFile(source.virtualFile)
            val offset = source.text.indexOf(snippet) + snippet.indexOf("\"email\"") + 2
            assertTrue(source.text, snippet in source.text)
            myFixture.editor.caretModel.moveToOffset(offset)
            return com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, offset)
                .map { it.navigationElement }
        }
        val rowJavaTarget = gotoEmail(dialogHtml, rowCell).single()
        assertEquals(dialogSource, rowJavaTarget.containingFile)
        assertEquals(dialogSource.text.indexOf(rowLabel) + rowLabel.indexOf("\"email\""), rowJavaTarget.textOffset)
        val rowHtmlTarget = gotoEmail(dialogSource, rowLabel).single()
        assertEquals(dialogHtml, rowHtmlTarget.containingFile)
        assertEquals(dialogHtml.text.indexOf(rowCell) + rowCell.indexOf("\"email\""), rowHtmlTarget.textRange.startOffset)

        val originalDialog = dialogSource.text
        val documents = com.intellij.psi.PsiDocumentManager.getInstance(project)
        val dialogDocument = documents.getDocument(dialogSource)!!
        fun writeDialog(text: String) {
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { dialogDocument.setText(text) }
            documents.commitAllDocuments()
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(dialogDocument)
        }
        try {
            writeDialog(originalDialog.replace("public DialogPage() {", "public DialogPage() { removeAll();"))
            val possibleJava = gotoEmail(dialogHtml, rowCell).map { dev.wicketlens.editor.WicketCompanionIds.presentation(it)!! }
            assertEquals(setOf("new $rowLabel", "new OatEmailField(\"email\")"), possibleJava.map { it.first }.toSet())
            assertTrue(possibleJava.any { it.second.contains("rows / populateItem") })
            assertTrue(possibleJava.any { it.second.contains("fragment editFields") })
            val possibleHtml = gotoEmail(dialogSource, rowLabel).map { dev.wicketlens.editor.WicketCompanionIds.presentation(it)!! }
            assertEquals(setOf(rowCell, "<div wicket:id=\"email\">"), possibleHtml.map { it.first }.toSet())
            assertTrue(possibleHtml.any { it.second.contains("people / rows") })
            assertTrue(possibleHtml.any { it.second.contains("editFields") })
        } finally {
            writeDialog(originalDialog)
            val editors = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            editors.closeFile(dialogSource.virtualFile)
            editors.closeFile(dialogHtml.virtualFile)
        }
        sourceHashes.forEach { (source, before) -> assertEquals("Application input changed: $source", before, hash(source)) }
        val output = Path.of(System.getProperty("wicketlens.test.oatOutput"))
        Files.createDirectories(output.parent)
        Files.writeString(output.parent.resolve("DataTableToolbar.txt"), toolbarText.text)
        Files.writeString(output.parent.resolve("DataTableEmptyState.txt"), emptyText.text)
        Files.writeString(output.parent.resolve("AccordionPage.txt"),dev.wicketlens.ui.WicketReportFormatter().format(accordionReport))
        Files.writeString(output.parent.resolve("DialogDetailsEvent.txt"),dev.wicketlens.ui.WicketReportFormatter().format(detailsEvent))
        val storedActionsReview=storedActionEvidence()
        val repeatedActionsReview=storedActionEvidence(repeated=true)
        val chainedActionsReview=storedActionEvidence(chained=true)
        val initializedActionsReview=storedActionEvidence(initialized=true)
        Files.writeString(output.parent.resolve("InvoiceSubmissions.txt"),invoiceSubmissions.joinToString("\n\n") { dev.wicketlens.ui.WicketReportFormatter().format(it) })
        Files.writeString(output.parent.resolve("DialogSubmissions.txt"),submissions.joinToString("\n\n") { dev.wicketlens.ui.WicketReportFormatter().format(it) })
        Files.writeString(output.parent.resolve("DialogPage.txt"),dev.wicketlens.ui.WicketReportFormatter().format(dialogReport))
        Files.writeString(output.parent.resolve("OatAppLayout.txt"),dev.wicketlens.ui.WicketReportFormatter().format(baseReport))
        Files.writeString(output, GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "wicketVersion" to System.getProperty("wicketlens.test.oatVersion"),
            "pageSourceCount" to summaries.size, "readOnlyInputHashesVerified" to true,
            "milestoneNativeAndMcpAssertionsPassed" to required.keys.sorted(), "pages" to summaries,
            "nestedComponents" to listOf(mapOf("className" to productFields.qualifiedName, "markupPaired" to true,
                "markupPath" to nestedReport.markupPath, "findings" to nestedReport.matching.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to nestedMatches.groupingBy { it.confidence.name }.eachCount(), "nativeAndMcpAssertionsPassed" to true)),
            "contextCompletion" to completionEvidence,
            "completionPopup" to popupEvidence,
            "projectTreeMarkers" to projectTreeMarkers,
            "mobileReportReview" to mapOf("baseLayoutWithheldAtAbstractModelHook" to true,"concretePageMatches" to 4,
                "rowTemplateMatches" to 3,"unsupportedTag" to "wicket:link","semanticBehaviorUnchanged" to true),
            "dataTableReportReview" to dataTableReview,
            "navigationRetrieval" to navigationRetrievalEvidence,
            "confirmationTemplate" to confirmationReview,
            "bulkSelectionTemplate" to bulkSelectionReview,
            "recordActionTemplates" to recordActionsReview,
            "storedActionAttachments" to storedActionsReview,
            "repeatedActionRegistrations" to repeatedActionsReview,
            "chainedActionRegistrations" to chainedActionsReview,
            "initializedActionRegistrations" to initializedActionsReview,
            "ownedTableReferenceForwarding" to forwardingEvidence,
            "fluentComponentConfiguration" to mapOf("probeClass" to fluentReport.qualifiedClassName,"probeKind" to "minimal_page_fixture_using_original_complete_OatDataTable_constructor_before_empty_state_configuration",
                "originalCoreConstructorAndNewToolsUsedWithoutModification" to true,"minimalDeferredProviderAvoidsInheritedLayoutBudget" to true,
                "findings" to fluentReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),"matchedBindings" to 2,"fluentConfigurationNotes" to 1,
                "enumInitializationModelValuesRenderingAjaxAndPrivateContents" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "ownedComponentAccessor" to mapOf("probeClass" to accessorReport.qualifiedClassName,"probeKind" to "fixture_copy_with_original_constructor_prefix_through_newTools_before_bulk_actions",
                "newToolsAndRowsWrapperUnchangedExceptEnclosingTypeName" to true,"sourcePopoverGetterAndConstructorUnchanged" to true,
                "findings" to accessorReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),"matchedBindings" to 26,"accessorDecorationNotes" to 1,
                "privateChildIdsModelsRenderingVisibilityAndLaterState" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "navigationToolbarConstruction" to mapOf("probeClass" to navigationReport.qualifiedClassName,"probeKind" to "fixture_copy_with_original_constructor_prefix_through_both_toolbar_registrations",
                "registrationsAndRowsWrapperUnchangedExceptEnclosingTypeName" to true,"sourceNavigationAndPagerWrappersUnchanged" to true,
                "findings" to navigationReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),"matchedBindings" to 26,"headerConstructorNotes" to 1,"navigationConstructorNotes" to 1,"registrationNotes" to 2,
                "pagingLinkInitializationAjaxRenderingResourceModelsAndProviderData" to "not_evaluated","runtimeIdsContentsVisibilityAndData" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "headerToolbarRegistration" to mapOf("probeClass" to headerReport.qualifiedClassName,"probeKind" to "fixture_copy_with_original_constructor_prefix_through_header_registration",
                "registrationAndAnonymousRenderHookUnchangedExceptEnclosingTypeName" to true,"rowsWrapperUnchangedExceptEnclosingTypeName" to true,
                "findings" to headerReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),"matchedBindings" to 26,"headerConstructorNotes" to 1,"registrationNotes" to 1,
                "renderSortingHeaderAndProviderCallbacks" to "not_evaluated","runtimeIdsContentsVisibilityAndData" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "dataTableConstruction" to mapOf("probeClass" to tableReport.qualifiedClassName,"probeKind" to "fixture_copy_with_original_constructor_prefix_and_rows_wrapper_before_toolbars",
                "rowsWrapperUnchangedExceptEnclosingTypeName" to true,"originalConstructorPrefixPreserved" to true,"providerAndQuerySourceUnchanged" to true,
                "findings" to tableReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),"matchedBindings" to 26,"constructorNotes" to 1,
                "providerColumnAndRowCallbacks" to "not_evaluated","frameworkOwnedContentsAndRuntimeData" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "providerConstruction" to mapOf("probeClass" to providerReport.qualifiedClassName,"probeKind" to "fixture_copy_with_original_seed_and_confirmation_and_isolated_provider_allocation","providerAndQuerySourceUnchanged" to true,"sourceDataSeedUnchanged" to true,"confirmationSetupRetained" to true,
                "findings" to providerReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),"matchedBindings" to 25,"constructorNotes" to 1,
                "callbacks" to "not_evaluated","pageQueriesRowsModelsAndSortState" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "dateColumnConstruction" to mapOf("probeClass" to dateReport.qualifiedClassName,"findings" to dateReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),
                "matchedBindings" to 11,"jdkEnumStorageNotes" to 2,"formattingLocaleTimeZoneAndModelValues" to "unknown","callbacks" to "not_evaluated","nativeAndMcpAssertionsPassed" to true),
            "lambdaColumnConstruction" to mapOf("probeClass" to lambdaReport.qualifiedClassName,"findings" to lambdaReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),
                "matchedBindings" to 11,"constructorStorageNotes" to 2,"headerCellDataModelAndDetachCallbacks" to "not_evaluated","modelSortAndFormattingValues" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "columnConstruction" to mapOf("probeClass" to columnReport.qualifiedClassName,"findings" to columnReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),
                "matchedBindings" to 11,"headerCellAndDetachCallbacks" to "not_evaluated","modelAndSortState" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "confirmationDialogDeclarations" to mapOf("probeClass" to confirmationReport.qualifiedClassName,"findings" to confirmationReport.matching.findings.groupingBy {it.code.name}.eachCount(),
                "matchedBindings" to 24,"messageType" to confirmationMessage.componentType,"constructorPlacementCount" to 1,
                "callbackExecution" to "not_evaluated","constructorEffects" to "unknown","nativeAndMcpAssertionsPassed" to true),
            "formSubmissionTemplates" to invoiceSubmissions.map { report -> val metadata=report.submissionTemplate!!;mapOf(
                "target" to report.qualifiedClassName,"phase" to "SUBMISSION_TEMPLATE","pathScope" to "OWN_COMPANION_DECLARATIONS",
                "formComponentPath" to metadata.formComponentPath,"triggerComponentPath" to metadata.triggerComponentPath,"callbackName" to metadata.callbackName,
                "formAssociation" to metadata.formCallback!!.formAssociation,
                "findings" to report.matching!!.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to report.matching.findings.groupingBy { it.confidence.name }.eachCount(),
                "effects" to metadata.effects.map { mapOf("kind" to it.kind,"methodName" to it.methodName,"targetComponentPath" to it.targetComponentPath) },"nativeAndMcpAssertionsPassed" to true) },
            "submissionTemplates" to submissions.map { submission -> val metadata=submission.submissionTemplate!!;mapOf(
                "target" to submission.qualifiedClassName,"phase" to "SUBMISSION_TEMPLATE","hostComponentPath" to metadata.hostComponentPath,
                "triggerComponentPath" to metadata.triggerComponentPath,"callbackName" to metadata.callbackName,
                "findings" to submission.matching!!.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to submission.matching.findings.groupingBy { it.confidence.name }.eachCount(),
                "effects" to metadata.effects.map { mapOf("kind" to it.kind,"methodName" to it.methodName,"targetComponentPath" to it.targetComponentPath) },
                "nativeAndMcpAssertionsPassed" to true) },
            "eventContentTemplates" to listOf(mapOf("target" to detailsEvent.qualifiedClassName,"componentPath" to detailsEvent.lifecycle.componentPath,
                "triggerComponentPath" to detailsEvent.lifecycle.directContent.triggerComponentPath,
                "triggerHostComponentPath" to detailsEvent.lifecycle.directContent.triggerHostComponentPath,
                "phase" to "EVENT_TEMPLATE","findings" to detailsEvent.matching.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to detailsEvent.matching.findings.groupingBy { it.confidence.name }.eachCount(),"nativeAndMcpAssertionsPassed" to true)),
            "initialContentSetups" to listOf(mapOf("target" to dialogReport.qualifiedClassName,"hostCount" to dialogReport.initialContentSetup.hosts.size,
                "placementCount" to dialogReport.initialContentSetup.hosts.sumOf { it.placementLocations.size },
                "findings" to dialogReport.matching.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to dialogReport.matching.findings.filter { it.code==DiagnosticCode.MATCHED }.groupingBy { it.confidence.name }.eachCount(),
                "nativeAndMcpAssertionsPassed" to true)),
            "registeredListTemplates" to listOf(mapOf("target" to accordionReport.qualifiedClassName, "hostComponentPath" to accordionPopulation.populationTemplate.hostComponentPath,
                "primaryFindings" to accordionReport.matching.findings.groupingBy { it.code.name }.eachCount(),
                "findings" to accordionPopulation.matching.findings.groupingBy { it.code.name }.eachCount(), "matchesByConfidence" to accordionPopulation.matching.findings.groupingBy { it.confidence.name }.eachCount(),
                "factoryCount" to accordionHost.registeredListPopulation.factoryLocations.size, "itemFactoryCount" to accordionHost.registeredListPopulation.itemFactoryLocations.size, "nativeAndMcpAssertionsPassed" to true)),
            "tableToolbarTemplates" to emptyResult.reports.map { context -> mapOf("target" to context.qualifiedClassName, "phase" to "TABLE_TOOLBAR_TEMPLATE", "pathScope" to "TOOLBAR_RELATIVE",
                "hostComponentPath" to context.toolbarTemplate!!.hostComponentPath, "toolbarClassName" to context.toolbarTemplate.toolbarClassName, "contentClassName" to context.toolbarTemplate.contentClassName,
                "findings" to context.matching!!.findings.groupingBy { it.code.name }.eachCount(), "matchesByConfidence" to context.matching.findings.groupingBy { it.confidence.name }.eachCount(),
                "callbackCount" to context.toolbarTemplate.callbackLocations.size, "nativeAndMcpAssertionsPassed" to true,"fullPageNavigationResponse" to "RESPONSE_TOO_LARGE","separateContextRetrieval" to "wicket_get_navigation_contexts") },
            "contentFactoryTemplates" to contentTemplates.map { context -> mapOf("target" to context.qualifiedClassName, "phase" to "REGISTRATION_TEMPLATE",
                "componentPath" to context.lifecycle!!.componentPath, "invocation" to "source_immediate",
                "findings" to context.matching!!.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to context.matching.findings.groupingBy { it.confidence.name }.eachCount(),
                "callbackCount" to context.lifecycle.contentFactory!!.callbackLocations.size, "nativeAndMcpAssertionsPassed" to true,"fullPageNavigationResponse" to "RESPONSE_TOO_LARGE","separateContextRetrieval" to "wicket_get_navigation_contexts") },
            "populationTemplates" to populations.map { context -> val population=context.populationTemplate!!;mapOf("target" to context.qualifiedClassName,"hostComponentPath" to population.hostComponentPath,
                "conditions" to population.conditions.map { mapOf("expression" to it.expression,"requiredResult" to it.result) },
                "ownedFindings" to context.matching!!.findings.filter { it.javaNodeId in population.ownedNodeIds }.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to context.matching.findings.filter { it.javaNodeId in population.ownedNodeIds }.groupingBy { it.confidence.name }.eachCount(),
                "callbackCount" to population.callbackLocations.size,"inactiveBodyCount" to population.inactiveBodyLocations.size) },
            "baseLayouts" to listOf(mapOf("target" to baseReport.qualifiedClassName,"constructorCount" to baseReport.baseLayoutTemplate!!.constructors.size,
                "extensionSlotCount" to baseReport.baseLayoutTemplate.extensionSlots.size,"findings" to baseReport.matching!!.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to baseMatches.groupingBy { it.confidence.name }.eachCount(),"nativeAndMcpAssertionsPassed" to true)),
            "registeredFormTemplates" to listOf(mapOf("target" to registered.qualifiedClassName,"wrapperClassName" to registered.formTemplate!!.wrapperClassName,"ownerClassName" to registered.formTemplate!!.registration!!.ownerClassName,
                "inputClassName" to registered.formTemplate.inputClassName,"pathScope" to "WRAPPER_RELATIVE","inputComponentPath" to registered.formTemplate.inputComponentPath,
                "findings" to registered.matching!!.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to registered.matching.findings.groupingBy { it.confidence.name }.eachCount(),"hooks" to registered.formTemplate.hooks.map { it.kind })),
            "formTemplates" to listOf(mapOf("target" to form.qualifiedClassName,"phase" to "LAZY_INPUT_TEMPLATE","pathScope" to "WRAPPER_RELATIVE",
                "inputComponentPath" to form.formTemplate.inputComponentPath,"inputClassName" to form.formTemplate.inputClassName,
                "hooks" to form.formTemplate.hooks.map { it.kind },"findings" to form.matching.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to form.matching.findings.groupingBy { it.confidence.name }.eachCount(),"nativeAndMcpAssertionsPassed" to true)),
            "treeTemplates" to treeTemplates.map { tree -> mapOf("target" to tree.qualifiedClassName,"phase" to "POPULATION_TEMPLATE",
                "pathScope" to "NODE_RELATIVE","hostComponentPath" to tree.treeTemplate!!.hostComponentPath,"frameworkKind" to tree.treeTemplate.frameworkKind,
                "conditions" to tree.treeTemplate.conditions,"nodeClassName" to tree.treeTemplate.nodeClassName,"contentClassName" to tree.treeTemplate.contentClassName,
                "findings" to tree.matching!!.findings.groupingBy { it.code.name }.eachCount(),"matchesByConfidence" to tree.matching.findings.groupingBy { it.confidence.name }.eachCount(),
                "nativeAndMcpAssertionsPassed" to true) },
            "selectionTemplates" to wizardSelections.map { selection -> mapOf("target" to selection.qualifiedClassName, "phase" to "SELECTION_TEMPLATE",
                "componentPath" to selection.lifecycle!!.componentPath, "registrationIndex" to selection.lifecycle.selection!!.registrationIndex,
                "registrationCount" to selection.lifecycle.selection.registrationCount, "title" to selection.lifecycle.selection.title,
                "definitionId" to selection.lifecycle.selection.definitionId, "findings" to selection.matching!!.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to selection.matching.findings.groupingBy { it.confidence.name }.eachCount(), "nativeAndMcpAssertionsPassed" to true) },
            "editorTemplates" to listOf(mapOf("target" to event.qualifiedClassName, "phase" to "EVENT_TEMPLATE",
                "componentPath" to event.lifecycle.componentPath, "findings" to event.matching.findings.groupingBy { it.code.name }.eachCount(),
                "matchesByConfidence" to event.matching.findings.groupingBy { it.confidence.name }.eachCount(), "nativeAndMcpAssertionsPassed" to true)),
            "navigationContexts" to mapOf("target" to "dev.jbaby.wicket.oat.components.OatSplitLayout", "caller" to inboxContext.report.qualifiedClassName,
                "scopeNodeCount" to inboxContext.scopeNodeIds.size, "bindingCount" to inboxContext.bindingNodeIds.size,
                "nativeAndMcpAssertionsPassed" to true))))
    }

    private fun hash(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun ownedTableForwardingProbe(fluentPage:PsiJavaFile,query:dev.wicketlens.mcp.WicketPageAnalysisRequest):Map<String,Any> {
        val forwardingPage=myFixture.addFileToProject("wicket-oat-examples/src/main/java/dev/jbaby/wicket/oat/app/TableForwardingProbe.java",
            fluentPage.text.replace("FluentConfigurationProbe","TableForwardingProbe").replace("rows,10));","rows,10).setEmptyState(id -> new dev.jbaby.wicket.oat.components.OatEmptyState(id,\"No rows\",\"Try again\")));")) as PsiJavaFile
        myFixture.addFileToProject("wicket-oat-examples/src/main/resources/dev/jbaby/wicket/oat/app/TableForwardingProbe.html","<html><body><div wicket:id='table'></div><b wicket:id='title'></b></body></html>")
        val forwardingReport=WicketPageAnalyzer().analyze(forwardingPage.classes.single())!!
        assertEquals(forwardingReport.warnings.toString(),1,forwardingReport.warnings.count {"Reviewed owned DataTable reference forwarding" in it.message && it.location?.offset==8234})
        assertEquals(forwardingReport.warnings.toString(),1,forwardingReport.warnings.count {"Reviewed owned DataTable columns getter" in it.message && it.location?.offset==8600})
        assertFalse(forwardingReport.warnings.toString(),forwardingReport.warnings.any {it.message.startsWith("Source layout setup stopped:")})
        assertEquals(forwardingReport.warnings.toString(),2,forwardingReport.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(1,forwardingReport.warnings.count {"Reviewed source null-guarded owned toolbar cache" in it.message})
        val forwardingSnapshot=query.capture(project,forwardingPage.classes.single().qualifiedName!!)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(forwardingReport)),JsonParser.parseString(query.render(forwardingSnapshot)).asJsonObject["analysis"])
        assertTrue(query.render(forwardingSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed owned DataTable reference forwarding"))
        assertTrue(query.render(forwardingSnapshot,WicketMcpReport.CONTEXT,"").contains("Reviewed owned DataTable reference forwarding"))
        assertTrue(query.render(forwardingSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed owned DataTable columns getter"))
        assertTrue(query.render(forwardingSnapshot,WicketMcpReport.CONTEXT,"").contains("Reviewed owned DataTable columns getter"))
        assertTrue(forwardingSnapshot.version.sources.any {it.path.endsWith("!/java.base/java/util/List.class")})
        assertTrue(query.render(forwardingSnapshot,WicketMcpReport.VALIDATION).contains("Reviewed source null-guarded owned toolbar cache"))
        assertTrue(query.render(forwardingSnapshot,WicketMcpReport.CONTEXT,"table").contains("Reviewed source null-guarded owned toolbar cache"))
        return mapOf("probeClass" to forwardingReport.qualifiedClassName,"probeKind" to "minimal_page_using_original_complete_OatDataTable_constructor_and_setEmptyState",
                "originalTableAndEmptyStateToolbarSourcesUsedWithoutModification" to true,"forwardingNotes" to 1,"columnsReadNotes" to 1,"columnsReadSdkMetadataTracked" to true,
                "cacheRegistrationNotes" to 1,"completeInitialSourceSetup" to true,
                "findings" to forwardingReport.matching!!.findings.groupingBy {it.code.name}.eachCount(),
                "toolbarIdsCacheAttachmentModelsRenderingAndLaterState" to "unknown","nativeAndMcpAssertionsPassed" to true)
    }
}
