package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiField
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.spring.WicketProjectDependencyRequest
import dev.wicketlens.spring.WicketSpringBeanLineMarkerProvider
import dev.wicketlens.ui.WicketLensService
import dev.wicketlens.ui.WicketSpringBeansPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.awt.Cursor
import java.awt.event.MouseEvent

class WicketProjectSpringBeansTest : WicketSpringInspectionTestCase() {
    private val request = WicketProjectDependencyRequest()

    private fun <T> query(block: suspend () -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val result = scope.async { block() }
            PlatformTestUtil.waitWithEventsDispatching("Project Spring beans analysis", { result.isCompleted }, 30)
            return runBlocking { result.await() }
        } finally {
            scope.cancel()
        }
    }

    private fun marker(field: PsiField): RelatedItemLineMarkerInfo<*>? {
        val results = mutableListOf<LineMarkerInfo<*>>()
        WicketSpringBeanLineMarkerProvider().collectSlowLineMarkers(listOf(field.nameIdentifier), results)
        return results.singleOrNull() as? RelatedItemLineMarkerInfo<*>
    }

    fun testProjectModeUsesSourceDeclarationsAndKeepsSourceSetsSeparate() {
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        val config = source("Config", """
            public class Config {
                @org.springframework.context.annotation.Bean({"orders", "alias"})
                Service orders() { return null; }
            }
        """.trimIndent())
        val label = page("@SpringBean(name=\"alias\") Service orders;", base = "org.apache.wicket.markup.html.basic.Label",
            name = "StandaloneLabel", body = "super(\"x\");")
        page("@Inject Service unverified;", name = "NoSpringSetupPage")
        source("Helper", "class Helper { @org.apache.wicket.spring.injection.annot.SpringBean Service unrelated; }")
        source("TestPage", """
            public class TestPage extends org.apache.wicket.markup.html.WebPage {
                @org.apache.wicket.spring.injection.annot.SpringBean(name="alias") Service testOrders;
            }
        """.trimIndent(), "src/test/java")
        val before = project.service<WicketLensService>().state
        val texts = label.text to config.text
        val response = query { request.execute(project, module.name, "any") }
        val json = JsonParser.parseString(response).asJsonObject
        assertTrue(json["target"].asJsonObject["className"].isJsonNull)
        val deps = json["dependencies"].asJsonObject
        assertEquals("project", deps["scope"].asString)
        assertFalse(deps["runtimeBindingsVerified"].asBoolean)
        val fields = deps["fields"].asJsonArray.map { it.asJsonObject }
        assertEquals(listOf("orders", "testOrders"), fields.map { it["name"].asString })
        assertEquals(label.virtualFile.path, fields.first()["location"].asJsonObject["file"].asString)
        val candidate = fields.first()["candidates"].asJsonArray.single().asJsonObject
        assertEquals(config.virtualFile.path, candidate["location"].asJsonObject["file"].asString)
        assertEquals(listOf("alias", "orders"), candidate["names"].asJsonArray.map { it.asString })
        assertTrue(fields.last()["candidates"].asJsonArray.isEmpty)
        assertSame(before, project.service<WicketLensService>().state)
        assertEquals(response, query { request.execute(project, module.name, "any") })
        assertEquals(texts, label.text to config.text)
        val main = JsonParser.parseString(query { request.execute(project, null, "main") }).asJsonObject
        assertEquals(1, main["dependencies"].asJsonObject["fieldCount"].asInt)
    }

    fun testGutterTargetsUniqueDefinitionsAndOffersAllDistinctCandidates() {
        val config = source("Config", """
            public class Config {
                @org.springframework.context.annotation.Bean({"first", "alias"})
                Service first() { return null; }
                @org.springframework.context.annotation.Bean
                Service second() { return null; }
            }
        """.trimIndent())
        val fields = page("""
            @SpringBean(name="alias") Service named;
            @SpringBean Service byType;
            @SpringBean(name="external") Service unresolved;
        """.trimIndent()).classes.single().fields
        val direct = marker(fields[0])!!
        assertEquals(listOf(config.classes.single().findMethodsByName("first", false).single()),
            direct.createGotoRelatedItems().map { it.element })
        assertTrue(direct.lineMarkerTooltip!!.contains("Runtime binding is unverified"))
        assertTrue(direct.createGutterRenderer()!!.isNavigateAction)
        assertNotNull(direct.createGutterRenderer()!!.clickAction)
        assertEquals(setOf("first", "second"), marker(fields[1])!!.createGotoRelatedItems()
            .map { (it.element as com.intellij.psi.PsiMethod).name }.toSet())
        assertNull(marker(fields[2]))
    }

    fun testClickOpensFieldAndRefusesStaleOffsetsUntilRefresh() {
        val page = page("@SpringBean Service orders;")
        val other = source("Other", "class Other {}")
        val panel = WicketSpringBeansPanel(project).also { Disposer.register(testRootDisposable, it) }
        panel.startDiscovery()
        PlatformTestUtil.waitWithEventsDispatching("Spring Beans tab populates", { panel.table.rowCount == 1 }, 30)
        panel.search.text = "not-present"
        assertEquals(0, panel.table.rowCount)
        panel.search.text = "orders"
        fun clickRow() {
            panel.table.setSize(480, 100)
            panel.table.doLayout()
            panel.table.dispatchEvent(MouseEvent(panel.table, MouseEvent.MOUSE_MOVED, 0, 0, 20, 5, 0, false))
            assertEquals(Cursor.HAND_CURSOR, panel.table.cursor.type)
            panel.table.dispatchEvent(MouseEvent(panel.table, MouseEvent.MOUSE_CLICKED, 0, 0, 20, 5, 1, false, MouseEvent.BUTTON1))
        }
        val editors = FileEditorManager.getInstance(project)
        clickRow()
        PlatformTestUtil.waitWithEventsDispatching("Opened injection field", {
            editors.selectedFiles.singleOrNull() == page.virtualFile &&
                editors.selectedTextEditor?.caretModel?.offset == page.classes.single().fields.single().nameIdentifier.textOffset
        }, 15)
        myFixture.openFileInEditor(other.virtualFile)
        edit(page, "// Shift the field offset\n" + page.text)
        clickRow()
        PlatformTestUtil.waitWithEventsDispatching("Old snapshot rejected", { "out of date" in panel.status.text }, 15)
        assertEquals(other.virtualFile, editors.selectedFiles.single())
        panel.refresh()
        PlatformTestUtil.waitWithEventsDispatching("Spring Beans tab refreshed", { "1 injection fields" in panel.status.text }, 30)
        clickRow()
        PlatformTestUtil.waitWithEventsDispatching("Opened refreshed injection field", {
            editors.selectedFiles.singleOrNull() == page.virtualFile &&
                editors.selectedTextEditor?.caretModel?.offset == page.classes.single().fields.single().nameIdentifier.textOffset
        }, 15)
    }

    fun testProjectSnapshotRejectsBeanSourceEditsAndRespectsFieldLimit() {
        val config = source("Config", "class Config { @org.springframework.context.annotation.Bean Service bean() { return null; } }")
        page((0..256).joinToString(" ") { "@SpringBean Service field$it;" })
        val snapshot = query { request.prepare(project) }
        assertTrue(snapshot.dependencies.truncated)
        assertEquals(256, snapshot.dependencies.fields.size)
        edit(config, config.text.replace("bean()", "updated()"))
        try {
            request.requireCurrent(project, snapshot)
            fail("Expected stale project injection snapshot")
        } catch (failure: PageAnalysisFailure) {
            assertEquals("STALE_SNAPSHOT", failure.code)
        }
    }

    fun testProjectInventoryReturnsABoundedErrorForALargeResponse() {
        val aliases = (1..7).joinToString(", ") { "\"alias$it${"界".repeat(26_000)}\"" }
        source("Config", "class Config { @org.springframework.context.annotation.Bean({\"entry\", $aliases}) Service bean() { return null; } }")
        page("@SpringBean(name=\"entry\") Service orders;")
        try {
            query { request.execute(project, null, "any") }
            fail("Expected the UTF-8 response bound")
        } catch (failure: PageAnalysisFailure) {
            assertEquals("RESPONSE_TOO_LARGE", failure.code)
        }
    }

    fun testCancellingAnInventoryRequestDuringIndexingAllowsAFreshRequest() {
        page("@SpringBean Service orders;")
        val token = DumbModeTestUtils.startEternalDumbModeTask(project)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val pending = scope.async { request.execute(project, null, "any") }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            assertFalse(pending.isCompleted)
            pending.cancel()
            PlatformTestUtil.waitWithEventsDispatching("Cancelled inventory request", { pending.isCompleted }, 15)
            try {
                runBlocking { pending.await() }
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                assertTrue(pending.isCancelled)
            }
        } finally {
            scope.cancel()
            DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token)
        }
        assertEquals(1, query { request.prepare(project) }.dependencies.fields.size)
    }
}
