package dev.wicketlens.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class WicketLensConfigurable : SearchableConfigurable, DumbAware {
    private var showTextReport: JBCheckBox? = null

    override fun getId() = "dev.wicketlens.settings"
    override fun getDisplayName() = "WicketLens"

    override fun createComponent(): JComponent {
        val checkbox = JBCheckBox("Show text report (debugging)", service<WicketLensSettings>().showTextReport).apply {
            toolTipText = "Display the analysis text report in a separate tool window tab."
        }
        showTextReport = checkbox
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(checkbox, BorderLayout.NORTH)
        }
    }

    override fun isModified() = showTextReport?.isSelected?.let { it != service<WicketLensSettings>().showTextReport } ?: false
    override fun apply() { showTextReport?.let { service<WicketLensSettings>().showTextReport = it.isSelected } }
    override fun reset() { showTextReport?.isSelected = service<WicketLensSettings>().showTextReport }
    override fun disposeUIResources() { showTextReport = null }
}
