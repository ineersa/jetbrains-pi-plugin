package com.github.ineersa.jetbrainspiplugin.startup

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.github.ineersa.jetbrainspiplugin.services.PiIdeService

class PiStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = PiIdeService.getInstance()

        // Listen for new editors being created
        EditorFactory.getInstance().addEditorFactoryListener(
            object : EditorFactoryListener {
                override fun editorCreated(event: EditorFactoryEvent) {
                    addSelectionListener(event.editor, service)
                }
            },
            project
        )

        // Add listener to all existing editors
        EditorFactory.getInstance().allEditors.forEach { editor ->
            addSelectionListener(editor, service)
        }

        // Listen for active file changes
        @Suppress("DEPRECATION")
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            .addFileEditorManagerListener(
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        val newFile = event.newEditor?.file
                        service.updateState(newFile, null, null, null)
                    }
                }
            )
    }

    private fun addSelectionListener(editor: Editor, service: PiIdeService) {
        editor.selectionModel.addSelectionListener(
            object : SelectionListener {
                override fun selectionChanged(e: SelectionEvent) {
                    val document = e.editor.document
                    val file = FileDocumentManager.getInstance().getFile(document)
                    val selection = e.editor.selectionModel

                    if (selection.hasSelection()) {
                        val startLine = document.getLineNumber(selection.selectionStart) + 1
                        val endLine = document.getLineNumber(selection.selectionEnd) + 1
                        service.updateState(file, selection.selectedText, startLine, endLine)
                    } else {
                        service.updateState(file, null, null, null)
                    }
                }
            }
        )
    }
}
