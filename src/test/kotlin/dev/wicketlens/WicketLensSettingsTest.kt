package dev.wicketlens

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.xmlb.XmlSerializer
import dev.wicketlens.settings.WicketLensConfigurable
import dev.wicketlens.settings.WicketLensSettings
import dev.wicketlens.ui.WicketLensService
import dev.wicketlens.ui.WicketReportPanel
import javax.swing.JPanel

class WicketLensSettingsTest : WicketProjectPsiTestCase() {
    private val settings get() = service<WicketLensSettings>()
    private val analysis get() = project.service<WicketLensService>()
    private lateinit var previous: WicketLensSettings.State

    override fun setUp() {
        super.setUp()
        previous = settings.state
        settings.loadState(WicketLensSettings.State())
        analysis.setEditorFollowing(false)
        analysis.cancel()
    }

    override fun tearDown() {
        try { analysis.cancel(); settings.loadState(previous) } finally { super.tearDown() }
    }

    private fun panel() = WicketReportPanel(analysis).also { Disposer.register(testRootDisposable, it) }
    private fun tabs(panel: WicketReportPanel) = (0 until panel.reportTabs.tabCount).map(panel.reportTabs::getTitleAt)
    private fun checkbox(configurable: WicketLensConfigurable) = (configurable.createComponent() as JPanel).components.filterIsInstance<JBCheckBox>().single()
    private fun configurable() = WicketLensConfigurable().also { value -> Disposer.register(testRootDisposable) { value.disposeUIResources() } }

    fun testTextReportIsHiddenByDefaultWhileNormalViewsRemain() {
        assertFalse(WicketLensSettings.State().showTextReport)
        assertFalse(settings.showTextReport)
        val panel = panel()
        assertEquals(listOf("Hierarchy", "Dependencies"), tabs(panel))
        assertEquals(0, panel.reportTabs.selectedIndex)
    }

    fun testConfigurableIsRegisteredUnderToolsAndUncheckedByDefault() {
        val entry = Configurable.APPLICATION_CONFIGURABLE.extensionList.single { it.id == "dev.wicketlens.settings" }
        assertEquals("tools", entry.parentId)
        val configurable = entry.createConfigurable() as WicketLensConfigurable
        try {
            assertEquals("WicketLens", configurable.displayName)
            assertFalse(checkbox(configurable).isSelected)
            assertFalse(configurable.isModified)
        } finally { configurable.disposeUIResources() }
    }

    fun testCheckboxIsAppliedExplicitlyAndResetDiscardsUnappliedChanges() {
        val panel = panel(); val configurable = configurable(); val checkbox = checkbox(configurable)
        checkbox.isSelected = true
        assertTrue(configurable.isModified)
        assertFalse(settings.showTextReport)
        assertEquals(listOf("Hierarchy", "Dependencies"), tabs(panel))
        configurable.reset()
        assertFalse(checkbox.isSelected); assertFalse(configurable.isModified)
        checkbox.isSelected = true; configurable.apply()
        assertTrue(settings.showTextReport); assertFalse(configurable.isModified)
        assertEquals(listOf("Hierarchy", "Text report", "Dependencies"), tabs(panel))
        checkbox.isSelected = false; configurable.apply()
        assertFalse(settings.showTextReport)
        assertEquals(listOf("Hierarchy", "Dependencies"), tabs(panel))
    }

    fun testApplyingUpdatesEveryExistingPanelAndNewPanelsUseThePreference() {
        val first = panel(); val second = panel()
        val configurable = configurable(); checkbox(configurable).isSelected = true; configurable.apply()
        for (panel in listOf(first, second, panel())) assertEquals(listOf("Hierarchy", "Text report", "Dependencies"), tabs(panel))
        settings.showTextReport = false
        for (panel in listOf(first, second)) assertEquals(listOf("Hierarchy", "Dependencies"), tabs(panel))
    }

    fun testDependencySelectionSurvivesBothVisibilityChangesWithoutDuplicateTabs() {
        val panel = panel(); panel.reportTabs.selectedComponent = panel.dependenciesPanel
        settings.showTextReport = true; settings.showTextReport = true
        assertEquals(3, panel.reportTabs.tabCount)
        assertSame(panel.dependenciesPanel, panel.reportTabs.selectedComponent)
        settings.showTextReport = false
        assertSame(panel.dependenciesPanel, panel.reportTabs.selectedComponent)
        assertEquals(2, panel.reportTabs.tabCount)
    }

    fun testHidingSelectedTextReturnsToHierarchyWithoutReplacingAnalysisOrExport() {
        val java = pageFile(); companion()
        analysis.analyze(java.virtualFile.url, -1)
        PlatformTestUtil.waitWithEventsDispatching({ analysis.state.message }, { !analysis.state.running }, 30)
        val prepared = requireNotNull(analysis.state.prepared)
        val panel = panel()
        assertEquals(prepared.text, panel.reportText.text)
        settings.showTextReport = true; panel.reportTabs.selectedIndex = 1
        panel.reportText.caretPosition = panel.reportText.text.indexOf("title : Label")
        assertTrue(panel.javaAction.isEnabled)
        settings.showTextReport = false
        assertEquals("Hierarchy", panel.reportTabs.getTitleAt(panel.reportTabs.selectedIndex))
        assertSame(prepared, analysis.state.prepared)
        assertEquals(prepared.json, analysis.state.prepared!!.json)
        assertTrue(panel.copyAction.isEnabled)
        val selected = panel.hierarchyTree.selectionPath
        val javaEnabled = panel.javaAction.isEnabled
        panel.reportText.caretPosition = 0
        assertEquals(selected, panel.hierarchyTree.selectionPath)
        assertEquals(javaEnabled, panel.javaAction.isEnabled)
    }

    fun testEnabledPreferenceRoundTripsThroughPersistentStateAndLoadUpdatesOpenPanels() {
        settings.showTextReport = true
        val serialized = XmlSerializer.serialize(settings.state)
        val panel = panel()
        settings.loadState(WicketLensSettings.State())
        assertEquals(2, panel.reportTabs.tabCount)
        settings.loadState(XmlSerializer.deserialize(serialized, WicketLensSettings.State::class.java))
        assertTrue(settings.showTextReport)
        assertEquals(3, panel.reportTabs.tabCount)
        val detached = settings.state
        detached.showTextReport = false
        assertTrue(settings.showTextReport)
    }

    fun testDisposedPanelsStopReceivingPreferenceUpdates() {
        val panel = panel(); Disposer.dispose(panel)
        settings.showTextReport = true
        assertEquals(listOf("Hierarchy", "Dependencies"), tabs(panel))
    }

    fun testBackgroundStateLoadingUpdatesThePanelOnTheUiThread() {
        val panel = panel()
        val loader = Thread { settings.loadState(WicketLensSettings.State(showTextReport = true)) }
        loader.start(); loader.join(5_000)
        assertFalse("State loading should not block on the UI thread", loader.isAlive)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("Hierarchy", "Text report", "Dependencies"), tabs(panel))
    }

    fun testQueuedStateUpdateDoesNotChangeAnAlreadyDisposedPanel() {
        val panel = panel()
        val loader = Thread { settings.loadState(WicketLensSettings.State(showTextReport = true)) }
        loader.start(); loader.join(5_000)
        assertFalse(loader.isAlive)
        Disposer.dispose(panel)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("Hierarchy", "Dependencies"), tabs(panel))
    }

    fun testDisposedConfigurableCanBeRecreatedFromTheSavedPreference() {
        val configurable = configurable(); val first = checkbox(configurable)
        first.isSelected = true; configurable.disposeUIResources()
        assertFalse(settings.showTextReport); assertFalse(configurable.isModified)
        settings.showTextReport = true
        assertTrue(checkbox(configurable).isSelected)
    }
}
