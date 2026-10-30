package dev.wicketlens

import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.*
import junit.framework.TestCase

class WicketHierarchyFilterTest : TestCase() {
    private val filter = ReportHierarchyFilter()
    private val entries = listOf(
        row("report", null, "CustomerPage", ReportEntryKind.PAGE),
        row("form", "report", "form", ReportEntryKind.CONTAINER, DiagnosticCode.MATCHED, "Form"),
        row("address", "form", "address", ReportEntryKind.CONTAINER, DiagnosticCode.MATCHED, "WebMarkupContainer"),
        row("postcode", "address", "postcode", ReportEntryKind.MARKUP, DiagnosticCode.MARKUP_ONLY, "<input>"),
        row("title", "form", "title", ReportEntryKind.COMPONENT, DiagnosticCode.JAVA_ONLY, "Label"),
        row("dynamic", "form", "<unresolved ID>", ReportEntryKind.COMPONENT, DiagnosticCode.UNRESOLVED_DYNAMIC_ID, "Label"),
        row("diagnostics", "report", "Diagnostics", ReportEntryKind.GROUP),
        row("scope", "diagnostics", "Unreviewed source setup", ReportEntryKind.DIAGNOSTIC, DiagnosticCode.UNRESOLVED_SCOPE),
        row("partial", "diagnostics", "Partial snapshot", ReportEntryKind.DIAGNOSTIC, DiagnosticCode.PARTIAL_ANALYSIS),
    )

    fun testNoFilterReturnsTheOriginalHierarchy() {
        val result = apply()
        assertSame(entries, result.entries)
        assertFalse(result.active)
    }

    fun testUnpairedFilterRetainsOnlyMatchingRowsAndTheirAncestors() {
        val result = apply(status = HierarchyStatusFilter.UNPAIRED)
        assertEquals(listOf("report", "form", "address", "postcode", "title"), result.entries.map { it.id })
        assertEquals(2, result.matchCount)
        assertTrue(result.entries.single { it.id == "address" }.contextOnly)
        assertFalse(result.entries.single { it.id == "postcode" }.contextOnly)
    }

    fun testSeparateJavaAndMarkupFiltersKeepTheirContexts() {
        assertEquals(listOf("report", "form", "title"), apply(status = HierarchyStatusFilter.JAVA_ONLY).entries.map { it.id })
        assertEquals(listOf("report", "form", "address", "postcode"), apply(status = HierarchyStatusFilter.MARKUP_ONLY).entries.map { it.id })
    }

    fun testMatchedFilterDoesNotPullInUnmatchedDescendants() {
        assertEquals(listOf("report", "form", "address"), apply(status = HierarchyStatusFilter.MATCHED).entries.map { it.id })
    }

    fun testUnresolvedFilterIncludesRealDiagnosticsButNotThePartialSnapshotNotice() {
        val result = apply(status = HierarchyStatusFilter.UNRESOLVED)
        assertEquals(setOf("report", "form", "dynamic", "diagnostics", "scope"), result.entries.map { it.id }.toSet())
        assertEquals(2, result.matchCount)
    }

    fun testSearchIsCaseInsensitiveAndKeepsAncestors() {
        assertEquals(listOf("report", "form", "address", "postcode"), apply("POSTcode").entries.map { it.id })
    }

    fun testTypeSearchUsesTheQualifiedComponentName() {
        val result = apply("org.apache.wicket.Label")
        assertEquals(setOf("report", "form", "title", "dynamic"), result.entries.map { it.id }.toSet())
    }

    fun testPathAndStatusFiltersAreCombined() {
        val result = apply("form:address:postcode", HierarchyStatusFilter.UNPAIRED)
        assertEquals(1, result.matchCount)
        assertEquals(listOf("report", "form", "address", "postcode"), result.entries.map { it.id })
    }

    fun testMultipleSearchTermsMustAllMatch() {
        assertEquals(1, apply("form postcode").matchCount)
        assertEquals(0, apply("form postcode Label").matchCount)
    }

    fun testSearchingAContainerPathIncludesItsMatchingDescendants() {
        val result = apply("form:address")
        assertEquals(2, result.matchCount)
        assertEquals(listOf("report", "form", "address", "postcode"), result.entries.map { it.id })
    }

    fun testEmptyResultsKeepOnlyThePageRoot() {
        val result = apply("absent")
        assertEquals(0, result.matchCount)
        assertEquals(listOf("report"), result.entries.map { it.id })
    }

    fun testRepeatedIdsRemainDistinctWhenSearchingAParentPath() {
        val rows = listOf(row("report", null, "Page", ReportEntryKind.PAGE),
            row("left", "report", "left", ReportEntryKind.CONTAINER), row("right", "report", "right", ReportEntryKind.CONTAINER),
            row("a", "left", "name", ReportEntryKind.COMPONENT), row("b", "right", "name", ReportEntryKind.COMPONENT))
        assertEquals(listOf("report", "right", "b"), filter.apply(rows, "right:name", HierarchyStatusFilter.ALL).entries.map { it.id })
    }

    fun testProjectionKeepsIdentityOrderDetailsAndLocationsWithoutMutatingInput() {
        val before = entries.toList()
        val selected = apply("postcode").entries.single { it.id == "postcode" }
        val original = entries.single { it.id == "postcode" }
        assertEquals(original, selected)
        assertEquals(before, entries)
        assertTrue(entries.none { it.contextOnly })
    }

    fun testFocusRestrictsMatchesButRetainsTheContainingPath() {
        val result = filter.apply(entries, "", HierarchyStatusFilter.ALL, "address")
        assertEquals(listOf("report", "form", "address", "postcode"), result.entries.map { it.id })
        assertEquals(2, result.matchCount)
        assertTrue(result.entries.single { it.id == "form" }.contextOnly)
    }

    fun testFocusCombinesWithStatusAndFullPathSearch() {
        val result = filter.apply(entries, "form:address:postcode", HierarchyStatusFilter.MARKUP_ONLY, "address")
        assertEquals(1, result.matchCount)
        assertEquals(listOf("report", "form", "address", "postcode"), result.entries.map { it.id })
    }

    fun testAnEmptyFocusedResultKeepsItsScopeAndAncestors() {
        val result = filter.apply(entries, "absent", HierarchyStatusFilter.UNPAIRED, "address")
        assertEquals(0, result.matchCount)
        assertEquals(listOf("report", "form", "address"), result.entries.map { it.id })
    }

    fun testInvalidOrPageRootFocusReturnsTheFullScope() {
        assertSame(entries, filter.apply(entries, "", HierarchyStatusFilter.ALL, "missing").entries)
        assertSame(entries, filter.apply(entries, "", HierarchyStatusFilter.ALL, "report").entries)
    }

    private fun apply(query: String = "", status: HierarchyStatusFilter = HierarchyStatusFilter.ALL) = filter.apply(entries, query, status)
    private fun row(id: String, parent: String?, name: String, kind: ReportEntryKind, status: DiagnosticCode? = null, type: String? = null) =
        ReportTreeEntry(id, parent, name, "Details for $id", SourceLocation("/example/Page.java", 10), SourceLocation("/example/Page.html", 20),
            kind, name, type, status, type?.let { "org.apache.wicket.$it" })
}
