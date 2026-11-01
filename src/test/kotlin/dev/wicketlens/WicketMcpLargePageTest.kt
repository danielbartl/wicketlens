package dev.wicketlens

import com.google.gson.JsonParser
import dev.wicketlens.mcp.*
import dev.wicketlens.model.DiagnosticCode

class WicketMcpLargePageTest : WicketProjectPsiTestCase() {
    private val query = WicketPageAnalysisRequest()
    private fun fixture(ids: List<String>) {
        pageFile(body = ids.joinToString("\n") { "add(new Label(\"$it\"));" })
        companion(html = "<html><body>" + ids.joinToString("") { "<span wicket:id='$it'></span>" } + "</body></html>")
    }
    fun testTwoHundredBindingsAreCompleteAndConsistentAcrossAllViews() {
        val ids = (0 until 200).map { "label$it" }
        fixture(ids)
        val snapshot = query.capture(project, "example.ExamplePage", view = WicketMcpReport.DEPENDENCIES)
        assertEquals(201, snapshot.report.nodes.size)
        assertEquals(200, snapshot.report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        val outputs = WicketMcpReport.entries.associateWith { view ->
            val selected = if (view == WicketMcpReport.SPRING_INSPECTIONS) query.capture(project,"example.ExamplePage",view=view) else snapshot
            JsonParser.parseString(query.render(selected, view, componentPath = if (view == WicketMcpReport.CONTEXT) "" else null)).asJsonObject
        }
        val findings = outputs.getValue(WicketMcpReport.STRUCTURE)["analysis"].asJsonObject["matching"].asJsonObject["findings"]
        assertEquals(findings, outputs.getValue(WicketMcpReport.VALIDATION)["validation"].asJsonObject["findings"])
        assertEquals(200, outputs.getValue(WicketMcpReport.CONTEXT)["context"].asJsonObject["children"].asJsonArray.size())
        assertTrue(outputs.values.all { !it["truncated"].asBoolean })
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage")))
    }
    fun testRealUnicodeBindingsExceedingTheByteBudgetFailForBindingViews() {
        fixture((0 until 100).map { "界".repeat(2000) + it })
        val snapshot = query.capture(project, "example.ExamplePage")
        assertEquals(101, snapshot.report.nodes.size)
        for (view in WicketMcpReport.entries.filter { it !in setOf(WicketMcpReport.DEPENDENCIES,WicketMcpReport.SPRING_INSPECTIONS) }) {
            try { query.render(snapshot, view, componentPath = if (view == WicketMcpReport.CONTEXT) "" else null); fail("Expected byte limit for $view") }
            catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
        }
    }
    fun testSmallSelectionFromTheLargeUnicodePageStillUsesTheSameGraph() {
        val ids = (0 until 100).map { "界".repeat(2000) + it }
        fixture(ids)
        val snapshot = query.capture(project, "example.ExamplePage")
        val output = query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = ids.first())
        assertTrue(output.toByteArray().size < WicketPageAnalysisRequest.MAX_RESPONSE_BYTES)
        val context = JsonParser.parseString(output).asJsonObject["context"].asJsonObject
        assertEquals(ids.first(), context["component"].asJsonObject["wicketId"].asString)
        assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
    }
}
