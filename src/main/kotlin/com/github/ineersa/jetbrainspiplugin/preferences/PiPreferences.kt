package com.github.ineersa.jetbrainspiplugin.preferences

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.UUID

@Service(Service.Level.APP)
@State(
    name = "PiPreferences",
    storages = [Storage("pi-plugin.xml")]
)
class PiPreferences : PersistentStateComponent<PiPreferences.State> {

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    fun getInstanceId(): String {
        if (myState.instanceId.isEmpty()) {
            myState.instanceId = UUID.randomUUID().toString()
        }
        return myState.instanceId
    }

    class State {
        var instanceId: String = ""
    }

    companion object {
        fun getInstance(): PiPreferences =
            ApplicationManager.getApplication().getService(PiPreferences::class.java)
    }
}
