package dev.markdownup

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

private fun VirtualFile.format() = Format.ofExtension(extension)

private fun VirtualFile.readText(): String =
    FileDocumentManager.getInstance().getDocument(this)?.text ?: String(contentsToByteArray(), charset)

/** Name of the sibling produced by converting [file]: same base name, swapped extension. */
private fun VirtualFile.targetName(from: Format) = "$nameWithoutExtension.${from.newExtension}"

private fun run(project: Project?, block: () -> Unit) {
    try { block() } catch (e: Exception) { Converter.notifyError(project, e.message ?: "Conversion failed") }
}

private fun createSibling(project: Project, source: VirtualFile, from: Format, content: String) {
    WriteCommandAction.runWriteCommandAction(project) {
        val parent = source.parent
        val name = source.targetName(from)
        val target = parent.findChild(name) ?: parent.createChildData(null, name)
        target.setBinaryContent(content.toByteArray(Charsets.UTF_8))
        FileEditorManager.getInstance(project).openFile(target, true)
    }
}

abstract class FileAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = files != null && files.isNotEmpty() && files.all { !it.isDirectory && it.format() != null }
    }
}

class ConvertFileAction : FileAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        run(project) {
            for (file in files) {
                val from = file.format() ?: continue
                val converted = Converter.convert(file.readText(), from)
                WriteCommandAction.runWriteCommandAction(project) {
                    file.rename(null, file.targetName(from))
                    FileDocumentManager.getInstance().getDocument(file)?.setText(converted)
                        ?: file.setBinaryContent(converted.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }
}

class NewFileAction : FileAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        run(project) {
            for (file in files) {
                val from = file.format() ?: continue
                createSibling(project, file, from, Converter.convert(file.readText(), from))
            }
        }
    }
}

abstract class EditorAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }

    /** Source format from the file extension; asks the user when the file type is not recognised. */
    protected fun sourceFormat(e: AnActionEvent): Format? {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        file?.format()?.let { return it }
        val choice = Messages.showChooseDialog(
            "Convert this content from:", "MarkdownUp", arrayOf("Markdown → Jira", "Jira → Markdown"), "Markdown → Jira", null,
        )
        return when (choice) { 0 -> Format.MARKDOWN; 1 -> Format.JIRA; else -> null }
    }
}

class ConvertEditorAction : EditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val from = sourceFormat(e) ?: return
        val selection = editor.selectionModel
        run(project) {
            if (selection.hasSelection()) {
                val converted = Converter.convert(selection.selectedText ?: "", from).trimEnd()
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.replaceString(selection.selectionStart, selection.selectionEnd, converted)
                }
            } else {
                val converted = Converter.convert(editor.document.text, from)
                WriteCommandAction.runWriteCommandAction(project) { editor.document.setText(converted) }
            }
        }
    }
}

class NewEditorFileAction : EditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val source = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (source == null) { Converter.notifyError(project, "Save the file first to generate a sibling file."); return }
        val from = sourceFormat(e) ?: return
        val text = editor.selectionModel.selectedText ?: editor.document.text
        run(project) { createSibling(project, source, from, Converter.convert(text, from)) }
    }
}
