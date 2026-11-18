package dev.wicketlens.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State as PersistentState
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic

/** Application-wide presentation preferences; semantic analysis and exports do not depend on them. */
@Service(Service.Level.APP)
@PersistentState(name = "WicketLensSettings", storages = [Storage("wicketlens.xml")])
class WicketLensSettings : PersistentStateComponent<WicketLensSettings.State> {
    data class State(var showTextReport: Boolean = false)

    @Volatile private var values = State()

    var showTextReport: Boolean
        get() = values.showTextReport
        set(value) {
            if (value == values.showTextReport) return
            values = State(value)
            ApplicationManager.getApplication().messageBus.syncPublisher(CHANGED).settingsChanged()
        }

    override fun getState(): State = values.copy()
    override fun loadState(state: State) { showTextReport = state.showTextReport }

    fun interface Listener { fun settingsChanged() }

    companion object {
        @JvmField val CHANGED: Topic<Listener> = Topic.create("WicketLens settings changed", Listener::class.java)
    }
}
