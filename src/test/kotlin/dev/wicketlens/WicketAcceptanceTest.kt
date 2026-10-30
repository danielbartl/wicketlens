package dev.wicketlens

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.Severity
import dev.wicketlens.ui.WicketReportFormatter

/** The specification's eight scenarios through source selection, pairing, and export. */
class WicketAcceptanceTest : WicketProjectPsiTestCase() {
    fun testSimplePage() {
        val result = scenario("SimplePage", "add(new Label(\"title\", \"Hello\"));",
            "<span wicket:id=\"title\">Hello</span>")
        assertCodes(result, DiagnosticCode.MATCHED)
        val title = components(result).single { it["wicketId"].isJsonPrimitive }
        assertEquals("title", title["wicketId"].asString)
        assertEquals("org.apache.wicket.markup.html.basic.Label", title["componentType"].asString)
        assertEquals(result.report.rootNodeId, title["parentNodeId"].asString)
    }

    fun testNestedFormPage() {
        val result = scenario("NestedFormPage", """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new org.apache.wicket.markup.html.form.TextField<>("name"));
            add(form);
        """.trimIndent(), "<form wicket:id=\"form\"><div><input wicket:id=\"name\"/></div></form>")
        assertCodes(result, DiagnosticCode.MATCHED, DiagnosticCode.MATCHED)
        val form = components(result).single { it["wicketId"].asStringOrNull() == "form" }
        val name = components(result).single { it["wicketId"].asStringOrNull() == "name" }
        assertEquals(form["nodeId"].asString, name["parentNodeId"].asString)
        assertTrue(result.text.contains("└── name : TextField  [MATCHED]"))
        val elements = result.json.getAsJsonObject("markup").getAsJsonArray("elements").map { it.asJsonObject }
        val input = elements.single { it["tagName"].asString == "input" }
        val wrapper = elements.single { it["tagName"].asString == "div" }
        assertEquals(wrapper["nodeId"].asString, input["parentElementId"].asString)
        assertEquals(form["markupNodeId"].asString, input["parentWicketElementId"].asString)
    }

    fun testVariablePage() {
        val result = scenario("VariablePage", "Label heading = new Label(\"heading\"); add(heading);",
            "<h1 wicket:id=\"heading\"></h1>")
        assertCodes(result, DiagnosticCode.MATCHED)
        assertEquals(1, result.report.children().size)
        assertEquals("heading", result.report.children().single().wicketId)
        assertTrue(result.text.contains("heading : Label  [MATCHED]"))
    }

    fun testMissingMarkupIdPage() {
        val result = scenario("MissingMarkupIdPage", "add(new Label(\"title\"));", "<div>No IDs</div>")
        assertCodes(result, DiagnosticCode.JAVA_ONLY)
        val title = components(result).single { it["wicketId"].asStringOrNull() == "title" }
        assertEquals("INFERRED", title["matchingConfidence"].asString)
        assertTrue(title["markupLocation"].isJsonNull)
        assertTrue(title["markupNodeId"].isJsonNull)
        assertTrue(result.text.contains("[JAVA_ONLY]"))
    }

    fun testMarkupOnlyPage() {
        val result = scenario("MarkupOnlyPage", "", "<span wicket:id=\"extra\"></span>")
        assertCodes(result, DiagnosticCode.MARKUP_ONLY)
        assertTrue(result.report.children().isEmpty())
        val finding = result.json.getAsJsonObject("matching").getAsJsonArray("findings").single().asJsonObject
        assertTrue(finding["javaNodeId"].isJsonNull)
        assertTrue(finding["javaLocation"].isJsonNull)
        assertTrue(finding["markupLocation"].isJsonObject)
        assertTrue(result.text.contains("extra : <span>  [MARKUP_ONLY]"))
    }

    fun testDynamicIdPage() {
        val result = scenario("DynamicIdPage", "add(new Label(System.getProperty(\"componentId\")));",
            "<span wicket:id=\"title\"></span>")
        assertCodes(result, DiagnosticCode.UNRESOLVED_DYNAMIC_ID, DiagnosticCode.UNRESOLVED_PARENT)
        val node = components(result).single { it["nodeId"].asString != result.report.rootNodeId }
        assertTrue(node["wicketId"].isJsonNull)
        assertTrue(node["markupLocation"].isJsonNull)
        assertEquals("UNRESOLVED_DYNAMIC_ID", node["status"].asString)
        val findings = result.json.getAsJsonObject("matching").getAsJsonArray("findings")
        assertTrue(findings.all { it.asJsonObject.getAsJsonArray("causes").any() })
        assertTrue(result.text.contains("UNRESOLVED_DYNAMIC_ID"))
    }

    fun testNonWicketAddPage() {
        val result = scenario("NonWicketAddPage", "new java.util.ArrayList<Object>().add(new Label(\"title\"));",
            "<span wicket:id=\"title\"></span>")
        assertCodes(result, DiagnosticCode.MARKUP_ONLY)
        assertTrue(result.report.children().isEmpty())
        assertEquals(1, components(result).size) // Only the analyzed page root.
    }

    fun testRepeatedIdsPage() {
        val result = scenario("RepeatedIdsPage", """
            org.apache.wicket.markup.html.form.Form<?> left = new org.apache.wicket.markup.html.form.Form<>("left");
            org.apache.wicket.markup.html.form.Form<?> right = new org.apache.wicket.markup.html.form.Form<>("right");
            left.add(new Label("name")); right.add(new Label("name")); add(left, right);
        """.trimIndent(), """
            <form wicket:id="right"><span wicket:id="name"></span></form>
            <form wicket:id="left"><span wicket:id="name"></span></form>
        """.trimIndent())
        assertCodes(result, *Array(4) { DiagnosticCode.MATCHED })
        val nodes = components(result).associateBy { it["nodeId"].asString }
        val elements = result.json.getAsJsonObject("markup").getAsJsonArray("elements")
            .associate { it.asJsonObject["nodeId"].asString to it.asJsonObject }
        val names = nodes.values.filter { it["wicketId"].asStringOrNull() == "name" }
        assertEquals(2, names.size)
        assertEquals(2, names.map { it["parentNodeId"].asString }.distinct().size)
        assertEquals(2, names.map { it["markupNodeId"].asString }.distinct().size)
        names.forEach { node ->
            val parent = nodes.getValue(node["parentNodeId"].asString)
            val markup = elements.getValue(node["markupNodeId"].asString)
            val markupParent = elements.getValue(markup["parentWicketElementId"].asString)
            assertEquals(parent["wicketId"].asString, markupParent["wicketId"].asString)
        }
    }

    private fun scenario(name: String, body: String, html: String): Result {
        val javaFile = pageFile(name, body)
        val markupFile = companion(name, "<html><body>$html</body></html>")
        val javaBefore = javaFile.text
        val markupBefore = markupFile.text
        val javaStamp = javaFile.modificationStamp
        val markupStamp = markupFile.modificationStamp
        val selection = WicketPageSelection()
        val report = selection.analyze(project, javaFile.virtualFile.url, -1)
        assertEquals("example.$name", report.qualifiedClassName)
        assertEquals(markupFile.virtualFile.path, report.markupPath)
        assertFalse(report.scopeComplete)
        assertFalse(requireNotNull(report.matching).scopeComplete)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.PARTIAL_ANALYSIS })
        val exporter = WicketContextExporter()
        val exported = exporter.export(report)
        assertEquals(exported, exporter.export(selection.analyze(project, javaFile.virtualFile.url, -1)))
        val json = JsonParser.parseString(exported).asJsonObject
        assertEquals(1, json["schemaVersion"].asInt)
        assertEquals(report.qualifiedClassName, json["qualifiedClassName"].asString)
        assertEquals(report.nodes.size, json.getAsJsonArray("components").size())
        val jsonNodes = json.getAsJsonArray("components").associate { it.asJsonObject["nodeId"].asString to it.asJsonObject }
        report.nodes.forEach { node ->
            assertTrue(node.javaLocation.offset in javaBefore.indices)
            assertEquals(javaFile.virtualFile.path, node.javaLocation.file)
            val location = jsonNodes.getValue(node.nodeId).getAsJsonObject("javaLocation")
            assertEquals(node.javaLocation.file, location["file"].asString)
            assertEquals(node.javaLocation.offset, location["offset"].asInt)
        }
        requireNotNull(report.matching).findings.forEach { finding ->
            assertEquals(Severity.INFO, finding.severity)
            if (finding.code == DiagnosticCode.MATCHED) {
                val location = requireNotNull(finding.markupLocation)
                assertEquals(markupFile.virtualFile.path, location.file)
                assertEquals('<', markupBefore[location.offset])
                val node = jsonNodes.getValue(requireNotNull(finding.javaNodeId))
                assertEquals(location.offset, node.getAsJsonObject("markupLocation")["offset"].asInt)
                assertEquals(finding.markupNodeId, node["markupNodeId"].asString)
            }
        }
        assertTrue(report.warnings.all { it.severity == Severity.INFO })
        assertTrue(report.nodes.flatMap { it.diagnostics }.all { it.severity == Severity.INFO })
        val text = WicketReportFormatter().format(report)
        assertTrue(text.contains("WicketLens — example.$name"))
        assertTrue(text.contains(requireNotNull(report.markupPath)))
        assertTrue(text.contains("partial static snapshot"))
        assertEquals(javaBefore, javaFile.text)
        assertEquals(markupBefore, markupFile.text)
        assertEquals(javaStamp, javaFile.modificationStamp)
        assertEquals(markupStamp, markupFile.modificationStamp)
        return Result(report, json, text)
    }

    private fun assertCodes(result: Result, vararg expected: DiagnosticCode) {
        val findings = requireNotNull(result.report.matching).findings
        assertEquals(expected.toList().groupingBy { it }.eachCount(), findings.groupingBy { it.code }.eachCount())
        val exported = result.json.getAsJsonObject("matching").getAsJsonArray("findings")
        assertEquals(expected.map { it.name }.groupingBy { it }.eachCount(),
            exported.map { it.asJsonObject["code"].asString }.groupingBy { it }.eachCount())
        assertTrue(exported.all { it.asJsonObject["severity"].asString == "INFO" })
    }

    private fun components(result: Result) = result.json.getAsJsonArray("components").map { it.asJsonObject }
    private fun com.google.gson.JsonElement.asStringOrNull(): String? = if (isJsonNull) null else asString
    private data class Result(val report: PageReport, val json: JsonObject, val text: String)
}
