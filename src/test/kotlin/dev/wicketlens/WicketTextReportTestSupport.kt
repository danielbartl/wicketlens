package dev.wicketlens

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import dev.wicketlens.settings.WicketLensSettings

/** Text-navigation tests opt into the debugging view instead of changing the product default. */
internal fun enableTextReport(parent: Disposable) {
    val settings = service<WicketLensSettings>()
    val previous = settings.state
    settings.showTextReport = true
    Disposer.register(parent) { settings.loadState(previous) }
}
