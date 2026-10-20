package dev.markdownup

import java.util.concurrent.atomic.AtomicInteger

private const val OPEN = ''
private const val CLOSE = ''
private val nextId = AtomicInteger()

/** Placeholder stash: protects converted fragments (code spans, links, images) from later regex passes. */
internal class Stash {
    private val items = mutableListOf<String>()
    private val id = nextId.getAndIncrement()
    // Unique per instance so nested stashes never resolve each other's tokens.
    private val token = Regex("$OPEN$id:(\\d+)$CLOSE")

    fun put(value: String): String {
        items.add(value)
        return "$OPEN$id:${items.size - 1}$CLOSE"
    }

    /** Restores every placeholder, including ones nested inside other stashed values. */
    fun restore(text: String): String {
        var out = text
        var i = 0
        while (i++ < 10 && out.contains("$OPEN$id:")) {
            out = token.replace(out) { items[it.groupValues[1].toInt()] }
        }
        return out
    }
}
