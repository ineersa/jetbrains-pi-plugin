package com.github.ineersa.jetbrainspiplugin.services

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.github.ineersa.jetbrainspiplugin.preferences.PiPreferences
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class IdeState(
    val file: VirtualFile?,
    val selection: String?,
    val startLine: Int?,
    val endLine: Int?
)

@Service(Service.Level.APP)
class PiIdeService : Disposable {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "pi-ide-service").apply { isDaemon = true }
    }
    private val scheduledTask = AtomicReference<ScheduledFuture<*>?>(null)
    private val state = AtomicReference(IdeState(null, null, null, null))

    private val ideDir: File
    private val ideFile: File
    private val ideName: String
    private val gson = Gson()

    init {
        val home = System.getProperty("user.home")
        ideDir = File(home, ".pi/ide")
        val preferences = PiPreferences.getInstance()
        val instanceId = preferences.getInstanceId()
        this.ideName = getIdeName()
        ideFile = File(ideDir, "$ideName-$instanceId.json")

        ideDir.mkdirs()
    }

    fun updateState(file: VirtualFile?, selection: String?, startLine: Int?, endLine: Int?) {
        state.set(IdeState(file, selection, startLine, endLine))
        scheduleWrite()
    }

    private fun scheduleWrite() {
        scheduledTask.getAndSet(null)?.cancel(false)
        val future = scheduler.schedule({ writeState() }, 100, TimeUnit.MILLISECONDS)
        scheduledTask.set(future)
    }

    private fun writeState() {
        try {
            val current = state.get()
            if (current.file == null) {
                if (ideFile.exists()) {
                    ideFile.delete()
                }
                return
            }

            val json = buildJson(current)
            atomicWrite(json)
        } catch (_: Exception) {
            // Silently ignore write errors — never crash the IDE
        }
    }

    private fun buildJson(current: IdeState): String {
        val file = current.file!!
        val workspaceFolders = getWorkspaceFolders()

        val stateMap = mutableMapOf<String, Any?>(
            "pid" to ProcessHandle.current().pid(),
            "ideName" to ideName,
            "ideVersion" to ApplicationInfo.getInstance().fullVersion,
            "workspaceFolders" to workspaceFolders,
            "currentFile" to file.path,
            "selection" to if (!current.selection.isNullOrEmpty()) {
                mapOf(
                    "text" to current.selection,
                    "startLine" to (current.startLine ?: 0),
                    "endLine" to (current.endLine ?: 0)
                )
            } else null,
            "timestamp" to System.currentTimeMillis()
        )
        return gson.toJson(stateMap)
    }

    private fun getWorkspaceFolders(): List<String> {
        return ProjectManager.getInstance().openProjects
            .mapNotNull { it.basePath }
            .filter { it.isNotEmpty() }
    }

    private fun atomicWrite(content: String) {
        val tempFile = File(ideDir, "${ideFile.name}.tmp")
        tempFile.writeText(content)
        Files.move(tempFile.toPath(), ideFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun getIdeName(): String {
        val appName = ApplicationInfo.getInstance().fullApplicationName.lowercase()
        return when {
            "goland" in appName -> "goland"
            "intellij" in appName -> "intellij"
            "webstorm" in appName -> "webstorm"
            "pycharm" in appName -> "pycharm"
            "rider" in appName -> "rider"
            "clion" in appName -> "clion"
            "rubymine" in appName -> "rubymine"
            "phpstorm" in appName -> "phpstorm"
            "android studio" in appName -> "android-studio"
            "datagrip" in appName -> "datagrip"
            else -> "jetbrains"
        }
    }

    fun clear() {
        if (ideFile.exists()) {
            ideFile.delete()
        }
    }

    override fun dispose() {
        scheduledTask.get()?.cancel(false)
        scheduler.shutdown()
        clear()
    }

    companion object {
        fun getInstance(): PiIdeService =
            ApplicationManager.getApplication().getService(PiIdeService::class.java)
    }
}
