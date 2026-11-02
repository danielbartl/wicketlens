package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*
import java.nio.file.Files
import java.nio.file.Path

class WicketComputedBranchMergeTest : WicketProjectPsiTestCase() {
    fun testComputedCommonIdSurvivesAnOptionalSibling() {
        val report = report("String prefix = \"user\"; add(new Label(prefix + \"Name\")); if (flag) add(new Label(\"hint\"));",
            html = "<span wicket:id='userName'></span><span wicket:id='hint'></span>")
        matched(report, "userName")
        val common = report.children().single { it.wicketId == "userName" }
        assertTrue(common.computedId)
        assertFalse(common.conditional)
        assertEquals(listOf(common.idLocation), common.computedIdLocations)
        assertTrue(report.children().single { it.wicketId == "hint" }.conditional)
        assertTrue(report.matching!!.findings.none { it.code in MISSING })
    }

    fun testDifferentExpressionsWithTheSameValueShareOneBinding() {
        val report = report("if (flag) add(new Label(\"user\" + \"Name\")); else add(new Label(\"userN\" + \"ame\"));",
            html = "<span wicket:id='userName'></span>")
        matched(report, "userName")
        val node = report.children().single()
        assertTrue(node.computedId)
        assertEquals(2, node.computedIdLocations.size)
        assertEquals(setOf("\"user\" + \"Name\"", "\"userN\" + \"ame\""),
            WicketIdPsi.javaIdElements(project, node).map { it.text }.toSet())
    }

    fun testLiteralFirstMixedOriginsAreTypedIndividually() = mixedOrigins(literalFirst = true)
    fun testComputedFirstMixedOriginsAreTypedIndividually() = mixedOrigins(literalFirst = false)

    fun testMixedTernaryIdOriginsKeepOneAllocationAndBothKinds() {
        val report = report("add(new Label(flag ? \"title\" : \"ti\" + \"tle\"));")
        matched(report, "title")
        val node = report.children().single()
        assertTrue(node.computedId)
        assertEquals(1, node.computedIdLocations.size)
        assertEquals(2, node.idLocations().size)
        assertTrue(node.alternativeJavaLocations.isEmpty())
        assertEquals(setOf("\"title\"", "\"ti\" + \"tle\""), WicketIdPsi.javaIdElements(project, node).map { it.text }.toSet())
    }

    fun testAComputedIdCanBeStoredAndForwardedThroughALocal() =
        matched(report("String id = \"ti\" + \"tle\"; if (flag) add(new Label(id)); else add(new Label(id));"), "title")

    fun testAComputedContainerAndItsChildKeepTheirParentIdentity() {
        val report = report("""
            WebMarkupContainer box = new WebMarkupContainer("con" + "tent");
            add(box); box.add(new Label("ti" + "tle"));
            if (flag) box.add(new Label("details"));
        """, html = "<div wicket:id='content'><span wicket:id='title'></span><span wicket:id='details'></span></div>")
        matched(report, "content", "title")
        assertEquals(report.children().single { it.wicketId == "content" }.nodeId,
            report.children().single { it.wicketId == "title" }.parentNodeId)
        assertTrue(report.children().single { it.wicketId == "details" }.conditional)
    }

    fun testConditionalComputedParentsCannotConfirmTheirDescendants() {
        val report = report("""
            add(new Label("title"));
            if (flag) {
                WebMarkupContainer box = new WebMarkupContainer("con" + "tent"); add(box);
                box.add(new Label("ch" + "ild"));
            }
        """, html = "<span wicket:id='title'></span><div wicket:id='content'><span wicket:id='child'></span></div>")
        matched(report, "title")
        assertTrue(report.children().filter { it.wicketId != "title" }.all { it.conditional && it.computedId })
        assertTrue(report.matching!!.findings.none { it.code in MISSING })
    }

    fun testDifferentComputedValuesStayConditional() {
        val report = report("add(new Label(flag ? \"user\" + \"Name\" : \"account\" + \"Name\"));",
            html = "<span wicket:id='userName'></span><span wicket:id='accountName'></span>")
        assertTrue(report.children().all { it.conditional && it.computedId })
        assertTrue(report.matching!!.findings.all { it.code == DiagnosticCode.UNRESOLVED_BRANCH })
    }

    fun testDifferentTypesRemainDistinctEvenWhenComputedIdsAgree() {
        val report = report("add(flag ? new Label(\"ti\" + \"tle\") : new WebMarkupContainer(\"title\"));")
        assertEquals(2, report.children().size)
        assertTrue(report.children().all { it.conditional })
        assertEquals(1, report.children().count { it.computedId })
        assertTrue(report.matching!!.findings.none { it.code in MISSING || it.code == DiagnosticCode.MATCHED })
    }

    fun testComputedChildrenDoNotMergeAcrossDifferentParents() {
        val report = report("""
            WebMarkupContainer left = new WebMarkupContainer("left"), right = new WebMarkupContainer("right"); add(left, right);
            (flag ? left : right).add(new Label("ti" + "tle"));
        """, html = "<div wicket:id='left'><span wicket:id='title'></span></div><div wicket:id='right'><span wicket:id='title'></span></div>")
        matched(report, "left", "right")
        val children = report.children().filter { it.wicketId == "title" }
        assertEquals(2, children.size)
        assertTrue(children.all { it.computedId && it.conditional })
        assertEquals(2, children.map { it.parentNodeId }.distinct().size)
    }

    fun testComputedFactoryIdUsesTheCalculationLocationInItsSourceFile() {
        val helper = source("Ids", "public final class Ids { public static String id(String prefix) { return prefix + \"tle\"; } }")
        val report = report("add(flag ? new Label(Ids.id(\"ti\")) : new Label(\"title\"));")
        matched(report, "title")
        val node = report.children().single()
        assertEquals(helper.virtualFile.path, node.computedIdLocations.single().file)
        assertEquals(setOf("prefix + \"tle\"", "\"title\""), WicketIdPsi.javaIdElements(project, node).map { it.text }.toSet())
        assertTrue(helper.virtualFile.path in report.javaSourceFiles)
    }

    fun testFreshProviderComputationsRetainTheirExpressionProvenance() {
        source("Parts", """
            public final class Parts {
                private final String prefix;
                public Parts(String prefix) { this.prefix = prefix; }
                public Label label() { return new Label(prefix + "tle"); }
            }
        """)
        matched(report("if (flag) add(new Parts(\"ti\").label()); else add(new Label(\"title\"));"), "title")
    }

    fun testForwardedNonfirstIdArgumentsCanBeComputed() {
        source("Wrapped", "public class Wrapped extends Label { public Wrapped(String caption, String id) { super(id, caption); } }")
        matched(report("if (flag) add(new Wrapped(\"Caption\", \"ti\" + \"tle\")); else add(new Wrapped(\"Caption\", \"title\"));"), "title")
    }

    fun testCorrelatedFlagChecksCanComputeTheSameIdFromKnownPathValues() =
        matched(report("if (flag) add(new Label(\"item\" + flag)); if (!flag) add(new Label(\"item\" + !flag));",
            html = "<span wicket:id='itemtrue'></span>"), "itemtrue")

    fun testIntegralArithmeticAndShiftsReuseTheExistingEvaluator() =
        matched(report("add(new Label(flag ? \"item\" + (1 << 2) : \"item4\"));", html = "<span wicket:id='item4'></span>"), "item4")

    fun testOverflowKeepsItsJavaIntegralValueInsideAComputedId() =
        matched(report("add(new Label(flag ? \"item\" + (2147483647 + 1) : \"item-2147483648\"));",
            html = "<span wicket:id='item-2147483648'></span>"), "item-2147483648")

    fun testSourceConstantsRetainTheirUseSiteProvenanceAndDependencies() {
        val ids = source("Ids", "public final class Ids { public static final String TITLE = \"title\"; }")
        val report = report("add(new Label(flag ? Ids.TITLE : \"title\"));")
        matched(report, "title")
        assertTrue(ids.virtualFile.path in report.javaSourceFiles)
        assertEquals(setOf("Ids.TITLE", "\"title\""), WicketIdPsi.javaIdElements(project, report.children().single()).map { it.text }.toSet())
    }

    fun testJdkConstantCanAgreeWithALiteralInTheSameBranchMerge() =
        matched(report("add(new Label(flag ? javax.xml.XMLConstants.XML_NS_PREFIX : \"xml\"));",
            html = "<span wicket:id='xml'></span>"), "xml")

    fun testPrivateFinalStringConfigurationCanSupplyTheComputedPrefix() =
        matched(report("add(new Label(prefix + \"tle\")); if (flag) {} else {}",
            "private final String prefix = \"ti\";"), "title")

    fun testUnknownComputationCannotPublishAMergedBinding() =
        noUpgrade(report("add(new Label(flag ? \"item\" + count : \"title\"));", parameters = "boolean flag, int count"))

    fun testMutableComputationInputCannotPublishAMergedBinding() =
        noUpgrade(report("add(new Label(flag ? prefix + \"tle\" : \"title\"));", "private String prefix = \"ti\";"))

    fun testPossibleComputationSideEffectsStillInvalidateEarlierRootAttachments() {
        val report = report("add(new Label(\"title\")); add(new Label(flag ? \"sub\" + suffix() : \"subtitle\"));",
            "private String suffix() { removeAll(); return \"title\"; }")
        noUpgrade(report)
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testDivisionByZeroOnAPossibleArmRejectsTheUpgrade() =
        noUpgrade(report("add(new Label(flag ? \"item\" + (1 / 0) : \"title\"));"))

    fun testFloatingPointCalculationsDoNotExpandTheReviewedSubset() =
        noUpgrade(report("add(new Label(flag ? \"item\" + (1.5 + 2.5) : \"item4.0\"));"))

    fun testInvalidComputedIdStillRejectsTheUpgrade() =
        noUpgrade(report("add(new Label(flag ? \"bad\" + \":id\" : \"title\"));"))

    fun testOversizedComputedStringStillExhaustsTheExistingBound() =
        noUpgrade(report("add(new Label(flag ? \"${"x".repeat(4096)}\" + \"x\" : \"title\"));"))

    fun testDuplicateSiblingValuesAreRejectedEvenWhenTheirOriginsDiffer() {
        val report = report("""
            if (flag) { add(new Label("ti" + "tle")); add(new Label("title")); }
            else add(new Label("title"));
        """)
        noUpgrade(report)
    }

    fun testFourIndependentChoicesStillExhaustTheProofBudget() {
        val body = (1..4).joinToString(" ") { "add(new Label(flag$it ? \"item\" + \"$it\" : \"item$it\"));" }
        noUpgrade(report(body, parameters = "boolean flag1, boolean flag2, boolean flag3, boolean flag4"))
    }

    fun testEqualValuedFragmentInAComputedArmIsNeverAnIdOrigin() {
        val file = page("add(flag ? new Label(\"title\") : new Label(\"title\" + \"\"));")
        val report = analyze(file)
        val literals = PsiTreeUtil.findChildrenOfType(file, PsiLiteralExpression::class.java).filter { it.value == "title" }.sortedBy { it.textOffset }
        assertEquals(2, literals.size)
        assertNotNull(WicketIdPsi.javaId(literals.first()))
        assertNotNull(WicketIdPsi.pair(literals.first()))
        assertNull(WicketIdPsi.javaId(literals.last()))
        assertNull(WicketIdPsi.pair(literals.last()))
        assertNull(WicketIdRenameTarget.at(file, literals.last().textOffset + 1))
        val node = report.children().single()
        assertEquals(1, node.computedIdLocations.size)
    }

    fun testAllComputedAlternativeProblemsCoverBothWholeExpressions() {
        val file = page("add(flag ? new Label(\"java\" + \"Only\") : new Label(\"javaO\" + \"nly\"));")
        val report = analyze(file)
        val expressions = WicketIdPsi.javaIdElements(project, report.children().single())
        assertEquals(setOf("\"java\" + \"Only\"", "\"javaO\" + \"nly\""), expressions.map { it.text }.toSet())
        val holder = problems(file)
        assertEquals(expressions.map { it.textRange }.toSet(), holder.results.map { it.psiElement.textRange }.toSet())
        assertTrue(holder.results.all { it.fixes.orEmpty().isEmpty() })
    }

    fun testMixedAlternativeProblemsCoverTheLiteralAndWholeExpression() {
        val file = page("add(flag ? new Label(\"javaOnly\") : new Label(\"java\" + \"Only\"));")
        val report = analyze(file)
        val node = report.children().single()
        assertEquals(setOf("\"javaOnly\"", "\"java\" + \"Only\""), WicketIdPsi.javaIdElements(project, node).map { it.text }.toSet())
        assertEquals(WicketIdPsi.javaIdElements(project, node).map { it.textRange }.toSet(),
            problems(file).results.map { it.psiElement.textRange }.toSet())
    }

    fun testARealLiteralOriginCanAlsoBeAnInputToTheComputedAlternative() {
        val file = page("String id = \"title\"; add(new Label(flag ? id : id + \"\"));")
        val report = analyze(file)
        matched(report, "title")
        val origin = WicketIdPsi.javaIdElements(project, report.children().single()).filterIsInstance<PsiLiteralExpression>().single()
        assertNotNull(WicketIdPsi.javaId(origin))
        assertNotNull(WicketIdPsi.pair(origin))
        assertNull(WicketIdRenameTarget.at(file, origin.textOffset + 1))
    }

    fun testComputedOnlyMarkupReferenceDoesNotInventALiteralPair() {
        val file = page("add(flag ? new Label(\"ti\" + \"tle\") : new Label(\"tit\" + \"le\"));")
        val report = analyze(file, "<span wicket:id=\"title\"></span>")
        val html = WicketIdPsi.source(project, report.markupPath!!)!!
        val value = html.idValue("title")
        assertNull(WicketIdPsi.pair(value))
        assertNull(WicketIdRenameTarget.at(html, value.textOffset + 1))
        assertTrue(problems(file).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
    }

    fun testBranchSpecificComputedIdsDoNotProduceNativeProblems() {
        val file = page("add(new Label(flag ? \"ti\" + \"tle\" : \"de\" + \"tails\"));")
        val report = analyze(file, "<span wicket:id='title'></span><span wicket:id='details'></span>")
        assertTrue(problems(file).results.isEmpty())
        assertTrue(problems(WicketIdPsi.source(project, report.markupPath!!)!!).results.isEmpty())
    }

    fun testMcpExportsEachComputedAndLiteralOriginWithItsKind() {
        val file = page("add(new Label(flag ? \"ti\" + \"tle\" : \"title\"));")
        analyze(file)
        val before = file.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "title")).asJsonObject
        val component = context["context"].asJsonObject["component"].asJsonObject
        assertTrue(component["computedId"].asBoolean)
        assertFalse(component["idRenameSafe"].asBoolean)
        val origins = component["idOrigins"].asJsonArray.map { it.asJsonObject }
        assertEquals(setOf("COMPUTED", "LITERAL"), origins.map { it["kind"].asString }.toSet())
        assertEquals(component["idLocations"].asJsonArray.toSet(), origins.map { it["location"] }.toSet())
        val expected = snapshot.report.children().single()
        assertEquals(expected.computedIdLocations.single().offset,
            origins.single { it["kind"].asString == "COMPUTED" }["location"].asJsonObject["offset"].asInt)
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(structure["analysis"].asJsonObject["matching"].asJsonObject["findings"], validation["validation"].asJsonObject["findings"])
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
        assertEquals(1, structure["schemaVersion"].asInt)
        assertEquals(before, file.text)
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
    }

    fun testNonmergedComputedIdsAlsoExportTheirReadonlyExpressionKind() {
        val file = page("add(new Label(\"ti\" + \"tle\"));", parameters = "")
        val report = analyze(file)
        val component = JsonParser.parseString(WicketContextExporter().export(report)).asJsonObject["components"].asJsonArray
            .single { it.asJsonObject["wicketId"].let { id -> !id.isJsonNull && id.asString == "title" } }.asJsonObject
        assertTrue(component["computedId"].asBoolean)
        assertFalse(component.has("branchMerged"))
        assertEquals("COMPUTED", component["idOrigins"].asJsonArray.single().asJsonObject["kind"].asString)
    }

    fun testLiteralOnlyMergedExportsKeepTheirExistingShape() {
        val report = report("add(new Label(flag ? \"title\" : \"title\"));")
        val component = JsonParser.parseString(WicketContextExporter().export(report)).asJsonObject["components"].asJsonArray
            .single { it.asJsonObject["wicketId"].let { id -> !id.isJsonNull && id.asString == "title" } }.asJsonObject
        assertTrue(component["branchMerged"].asBoolean)
        assertFalse(component.has("computedId"))
        assertFalse(component.has("idOrigins"))
    }

    fun testExternalCalculationEditInvalidatesAndRecomputesTheMergedSnapshot() {
        val helper = source("Ids", "public final class Ids { public static String id() { return \"ti\" + \"tle\"; } }")
        val file = page("add(flag ? new Label(Ids.id()) : new Label(\"title\"));")
        matched(analyze(file), "title")
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        assertTrue(helper.virtualFile.path in old.report.javaSourceFiles)
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("\"ti\" + \"tle\"", "\"de\" + \"tails\""))
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val report = query.capture(project, "example.ExamplePage").report
        assertEquals(setOf("title", "details"), report.children().map { it.wicketId }.toSet())
        assertTrue(report.children().all { it.conditional })
    }

    fun testMcpResponseBoundIncludesTheTypedOriginMetadata() {
        val file = page("add(new Label(flag ? \"ti\" + \"tle\" : \"title\"));")
        analyze(file)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val oversized = snapshot.copy(report = snapshot.report.copy(nodes = snapshot.report.nodes.map {
            if (it.computedId) it.copy(alternativeIdLocations = listOf(SourceLocation("界".repeat(180_000), 0))) else it
        }))
        try { query.render(oversized); fail("Expected size bound") }
        catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
    }

    fun testCancellationDoesNotPublishAMergedComputedReport() {
        val file = page("add(new Label(flag ? \"ti\" + \"tle\" : \"title\"));")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketPageAnalyzer().analyze(file.classes.single())
            }, indicator)
        }
    }

    fun testPortableDemoHasTwoKnownComputedBindingsAndOneConditionalId() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/ComputedBranchPage.java",
            Files.readString(root.resolve("main/java/example/ComputedBranchPage.java"))) as PsiJavaFile
        companion("ComputedBranchPage", Files.readString(root.resolve("main/resources/example/ComputedBranchPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "userName", "item4")
        assertEquals(2, report.children().count { it.computedId })
        assertTrue(report.children().single { it.wicketId == "hint" }.conditional)
        assertTrue(report.matching!!.findings.none { it.code in MISSING })
    }

    private fun mixedOrigins(literalFirst: Boolean) {
        val first = if (literalFirst) "\"title\"" else "\"ti\" + \"tle\""
        val second = if (literalFirst) "\"ti\" + \"tle\"" else "\"title\""
        val file = page("add(flag ? new Label($first) : new Label($second));")
        val report = analyze(file)
        matched(report, "title")
        val node = report.children().single()
        assertTrue(node.computedId)
        assertFalse(node.idRenameSafe)
        val origins = node.idLocations()
        assertEquals(2, origins.size)
        assertEquals(1, origins.count(node::isComputedIdOrigin))
        assertEquals(!literalFirst, node.isComputedIdOrigin(node.idLocation!!))
        val elements = WicketIdPsi.javaIdElements(project, node)
        assertEquals(setOf("\"title\"", "\"ti\" + \"tle\""), elements.map { it.text }.toSet())
        val literal = elements.filterIsInstance<PsiLiteralExpression>().single()
        assertNotNull(WicketIdPsi.javaId(literal))
        val pair = WicketIdPsi.pair(literal)!!
        assertEquals(literal, WicketIdPsi.pair(pair.attribute)!!.literal)
        assertNull(WicketIdRenameTarget.at(file, literal.textOffset + 1))
        assertNull(WicketIdRenameTarget.at(pair.attribute.containingFile, pair.attribute.textOffset + 1))
        assertTrue(WicketIdCompletionContributor().candidates(literal).isEmpty())
    }

    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java",
        "package example; import org.apache.wicket.markup.html.basic.Label; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "boolean flag") =
        source("ExamplePage", """
            import org.apache.wicket.markup.html.WebMarkupContainer;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage($parameters) { $body }
                $members
            }
        """.trimIndent()) as PsiJavaFile
    private fun analyze(file: PsiJavaFile, html: String = "<span wicket:id='title'></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(file.classes.single())!!
    }
    private fun report(body: String, members: String = "", parameters: String = "boolean flag",
        html: String = "<span wicket:id='title'></span>") = analyze(page(body, members, parameters), html)
    private fun matched(report: PageReport, vararg ids: String) =
        assertEquals(report.matching.toString(), ids.toSet(), report.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }
            .map { finding -> report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }.toSet())
    private fun noUpgrade(report: PageReport) = assertFalse(report.children().any { it.branchMerged })
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
    companion object { private val MISSING = setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) }
}
