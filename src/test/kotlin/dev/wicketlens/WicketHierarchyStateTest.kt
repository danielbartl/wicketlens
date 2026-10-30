package dev.wicketlens

import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.*
import junit.framework.TestCase

class WicketHierarchyStateTest : TestCase() {
    fun testStateIdentitySurvivesOffsetAndStatusChanges() {
        val old = HierarchyStateIndex(listOf(root(), row("old:10", "report", "title")))
        val newer = HierarchyStateIndex(listOf(root(), row("new:90", "report", "title").copy(status = DiagnosticCode.MATCHED, javaLocation = SourceLocation("/Page.java", 90))))
        assertEquals("new:90", newer.idFor(old.keyFor("old:10")))
    }

    fun testContainerAndLeafPresentationDoNotChangeComponentIdentity() {
        val old = HierarchyStateIndex(listOf(root(), row("old", "report", "form")))
        val newer = HierarchyStateIndex(listOf(root(), row("new", "report", "form").copy(kind = ReportEntryKind.CONTAINER)))
        assertEquals("new", newer.idFor(old.keyFor("old")))
    }

    fun testRepeatedIdsUnderDifferentParentsHaveDistinctAddresses() {
        val rows = listOf(root(), row("left", "report", "left"), row("right", "report", "right"), row("a", "left", "name"), row("b", "right", "name"))
        val index = HierarchyStateIndex(rows)
        assertNotNull(index.keyFor("a"))
        assertFalse(index.keyFor("a") == index.keyFor("b"))
    }

    fun testDuplicateSiblingAddressesAndTheirDescendantsAreNotRestorable() {
        val index = HierarchyStateIndex(listOf(root(), row("a", "report", "form"), row("b", "report", "form"), row("child", "a", "uniqueChild")))
        assertNull(index.keyFor("a"))
        assertNull(index.keyFor("b"))
        assertNull(index.keyFor("child"))
    }

    fun testChangedTypeParentOrRoleIsNotGuessed() {
        val key = HierarchyStateIndex(listOf(root(), row("old", "report", "title"))).keyFor("old")
        assertNull(HierarchyStateIndex(listOf(root(), row("new", "report", "title").copy(qualifiedTypeName = "example.Other"))).idFor(key))
        assertNull(HierarchyStateIndex(listOf(root(), row("parent", "report", "parent"), row("new", "parent", "title"))).idFor(key))
        assertNull(HierarchyStateIndex(listOf(root(), row("new", "report", "title").copy(kind = ReportEntryKind.MARKUP))).idFor(key))
    }

    fun testGroupCountsAndDiagnosticOrdinalsDoNotAffectIdentity() {
        val old = HierarchyStateIndex(listOf(root(), group("Diagnostics (1)"), diagnostic("diagnostic:0", 10)))
        val newer = HierarchyStateIndex(listOf(root(), group("Diagnostics (8)"), diagnostic("diagnostic:7", 80)))
        assertEquals("diagnostic:7", newer.idFor(old.keyFor("diagnostic:0")))
        assertEquals("diagnostics", newer.idFor(old.keyFor("diagnostics")))
    }

    fun testRemovedRowsDoNotRestoreToAnotherRowAtTheSameSourceOffset() {
        val old = HierarchyStateIndex(listOf(root(), row("same", "report", "title")))
        val newer = HierarchyStateIndex(listOf(root(), row("same", "report", "other")))
        assertNull(newer.idFor(old.keyFor("same")))
    }

    fun testCyclicAddressesAreNotRestored() {
        val index = HierarchyStateIndex(listOf(root(), row("a", "b", "a"), row("b", "a", "b")))
        assertNull(index.keyFor("a"))
        assertNull(index.keyFor("b"))
    }

    fun testEscapedDisplayNamesDoNotConflateDifferentRawIds() {
        val old = HierarchyStateIndex(listOf(root(), row("old", "report", "line\\nname").copy(stateName = "line\nname")))
        val newer = HierarchyStateIndex(listOf(root(), row("new", "report", "line\\nname").copy(stateName = "line\\nname")))
        assertNull(newer.idFor(old.keyFor("old")))
    }

    fun testDynamicIdsAndDescendantsOfUnknownParentsAreNotRestored() {
        val index = HierarchyStateIndex(listOf(root(), row("unknown", "report", "<unresolved ID>").copy(stateName = null), row("child", "unknown", "known")))
        assertNull(index.keyFor("unknown"))
        assertNull(index.keyFor("child"))
    }

    fun testLogicalTemplateItemsHaveTheirOwnIdentityDomain() {
        val old = HierarchyStateIndex(listOf(root(), row("item", "report", "item").copy(templateItem = true)))
        val newer = HierarchyStateIndex(listOf(root(), row("ordinary", "report", "item")))
        assertNotNull(old.keyFor("item"))
        assertNull(newer.idFor(old.keyFor("item")))
    }

    private fun root() = ReportTreeEntry("report", null, "Page", "", kind = ReportEntryKind.PAGE)
    private fun row(id: String, parent: String, name: String) = ReportTreeEntry(id, parent, name, "", SourceLocation("/Page.java", 10),
        kind = ReportEntryKind.COMPONENT, name = name, typeName = "Label", status = DiagnosticCode.JAVA_ONLY, qualifiedTypeName = "example.Label")
    private fun group(label: String) = ReportTreeEntry("diagnostics", "report", label, "", kind = ReportEntryKind.GROUP)
    private fun diagnostic(id: String, offset: Int) = ReportTreeEntry(id, "diagnostics", "Unreviewed source", "", SourceLocation("/Page.java", offset),
        kind = ReportEntryKind.DIAGNOSTIC, status = DiagnosticCode.UNRESOLVED_SCOPE)
}
