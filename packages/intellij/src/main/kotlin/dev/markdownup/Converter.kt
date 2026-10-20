package dev.markdownup

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

enum class Format(val extensions: List<String>) {
    MARKDOWN(listOf("md", "markdown")),
    JIRA(listOf("jira", "jiramarkup"));

    val other get() = if (this == MARKDOWN) JIRA else MARKDOWN
    val newExtension get() = other.extensions.first()

    companion object {
        fun ofExtension(ext: String?): Format? = entries.firstOrNull { f -> f.extensions.any { it.equals(ext, true) } }
    }
}

object Converter {
    fun convert(text: String, from: Format): String =
        if (from == Format.MARKDOWN) markdownToJira(text) else jiraToMarkdown(text)

    fun notifyError(project: Project?, message: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("MarkdownUp")
            .createNotification("MarkdownUp", message, NotificationType.ERROR).notify(project)
    }
}
