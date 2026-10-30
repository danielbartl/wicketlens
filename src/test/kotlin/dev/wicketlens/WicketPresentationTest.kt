package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.analysis.WicketGraphMatcher
import dev.wicketlens.analysis.WicketMarkupAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.ui.WicketReportFormatter

class WicketPresentationTest : WicketJavaPsiTestCase() {
    fun testJsonContainsVersionTypesIdsRelationshipsStatusesAndLocations() {
        val report = report()
        val json = JsonParser.parseString(WicketContextExporter().export(report)).asJsonObject
        assertEquals(1, json["schemaVersion"].asInt)
        assertEquals(report.qualifiedClassName, json["qualifiedClassName"].asString)
        assertFalse(json["scopeComplete"].asBoolean)
        val components = json.getAsJsonArray("components").map { it.asJsonObject }
        val title = components.single { it["wicketId"].isJsonPrimitive && it["wicketId"].asString == "title" }
        assertEquals("org.apache.wicket.markup.html.basic.Label", title["componentType"].asString)
        assertEquals("MATCHED", title["status"].asString)
        assertEquals(report.rootNodeId, title["parentNodeId"].asString)
        assertEquals(report.children().single { it.wicketId == "title" }.javaLocation.offset,
            title.getAsJsonObject("javaLocation")["offset"].asInt)
        assertTrue(title["markupLocation"].isJsonObject)
        assertTrue(title["markupNodeId"].isJsonPrimitive)
        assertTrue(json.getAsJsonObject("markup").getAsJsonArray("elements").any { it.asJsonObject["tagName"].asString == "div" })
        val codes = json.getAsJsonObject("matching").getAsJsonArray("findings").map { it.asJsonObject["code"].asString }
        assertTrue(codes.containsAll(listOf("MATCHED", "JAVA_ONLY", "MARKUP_ONLY")))
        assertTrue(components.single { it["nodeId"].asString == report.rootNodeId }["status"].isJsonNull)
    }

    fun testMissingMarkupAndUnknownIdsHaveExplicitNullsAndCauses() {
        val report = WicketGraphMatcher().match(analyze("add(new Label(configuredId));", members = "String configuredId;"))
        val json = JsonParser.parseString(WicketContextExporter().export(report)).asJsonObject
        assertTrue(json["markupPath"].isJsonNull)
        assertTrue(json["markup"].isJsonNull)
        val component = json.getAsJsonArray("components").last().asJsonObject
        assertTrue(component["wicketId"].isJsonNull)
        assertTrue(component["markupLocation"].isJsonNull)
        assertEquals("UNRESOLVED_DYNAMIC_ID", component["status"].asString)
        assertTrue(json.getAsJsonObject("matching").getAsJsonArray("findings").single()
            .asJsonObject.getAsJsonArray("causes").any())
    }

    fun testJsonEscapesControlCharactersAndPreservesUnicodeAndSourceText() {
        val value = "quotes \" slash \\ newline\n<html>& λ"
        val report = report().let { it.copy(warnings = it.warnings + Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, value)) }
        val json = WicketContextExporter().export(report)
        val warnings = JsonParser.parseString(json).asJsonObject.getAsJsonArray("warnings")
        assertTrue(warnings.any { it.asJsonObject["message"].asString == value })
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("λ"))
    }

    fun testJsonOrderingIsCanonicalWithoutTimestampsOrInputMutation() {
        val report = report()
        val reordered = report.copy(
            nodes = report.nodes.reversed().map { it.copy(diagnostics = it.diagnostics.reversed()) },
            warnings = report.warnings.reversed(),
            markup = report.markup?.let { it.copy(elements = it.elements.reversed(), diagnostics = it.diagnostics.reversed()) },
            matching = report.matching?.let { it.copy(findings = it.findings.reversed().map { finding -> finding.copy(causes = finding.causes.reversed()) }) },
        )
        val exporter = WicketContextExporter()
        assertEquals(exporter.export(report), exporter.export(reordered))
        assertEquals(exporter.export(report), exporter.export(report))
        assertFalse(exporter.export(report).contains("generatedAt"))
    }

    fun testReadableReportContainsHierarchyStatusesPairingAndSources() {
        val text = WicketReportFormatter().format(report())
        assertTrue(text.contains("WicketLens — example.ExamplePage"))
        assertTrue(text.contains("title : Label  [MATCHED]"))
        assertTrue(text.contains("name : TextField  [MATCHED]"))
        assertTrue(text.indexOf("form : Form") < text.indexOf("name : TextField"))
        assertTrue(text.contains("[JAVA_ONLY]"))
        assertTrue(text.contains("[MARKUP_ONLY]"))
        assertTrue(text.contains("Java: "))
        assertTrue(text.contains("Markup: "))
        assertTrue(text.contains("partial static snapshot"))
        assertTrue(text.contains("Diagnostics:"))
    }

    fun testReadableReportIncludesUnattachedNodesAndUncertaintyCauses() {
        val java = analyze("new WebMarkupContainer(\"form\").add(new Label(\"name\"));")
        val report = WicketGraphMatcher().match(java)
        val text = WicketReportFormatter().format(report)
        assertTrue(text.contains("Unattached or unresolved hierarchy:"))
        assertTrue(text.contains("name : Label"))
        assertTrue(text.contains("UNRESOLVED_PARENT"))
    }

    fun testReadableReportDoesNotInterpretHtmlOrEmbedControlCharactersInLabels() {
        val report = report().let { input -> input.copy(nodes = input.nodes.map {
            if (it.wicketId == "title") it.copy(wicketId = "<script>\nquote") else it
        }) }
        val text = WicketReportFormatter().format(report)
        assertTrue(text.contains("<script>\\nquote"))
        assertFalse(text.contains("<script>\nquote"))
    }

    fun testFormattingAndExportHonorCancellation() {
        val report = report()
        for (operation in listOf<() -> Unit>({ WicketContextExporter().export(report) }, { WicketReportFormatter().format(report) })) {
            val indicator = EmptyProgressIndicator()
            assertThrows(ProcessCanceledException::class.java) {
                ProgressManager.getInstance().runProcess(Runnable { indicator.cancel(); operation() }, indicator)
            }
        }
    }

    private fun report(): PageReport {
        val java = analyze("""
            add(new Label("title"));
            Form<?> form = new Form<>("form"); form.add(new TextField<>("name")); add(form);
            add(new Label("javaOnly"));
        """.trimIndent())
        val markup = WicketMarkupAnalyzer().analyze(myFixture.configureByText("ExamplePage.html", """
            <html><body><span wicket:id="title"></span><form wicket:id="form"><div><input wicket:id="name"/></div></form>
              <span wicket:id="markupOnly"></span>
            </body></html>
        """.trimIndent()))
        return WicketGraphMatcher().match(java.copy(markup = markup, markupPath = markup.file))
    }
}
