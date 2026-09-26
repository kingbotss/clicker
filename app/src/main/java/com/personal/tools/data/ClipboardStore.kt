package com.personal.tools.data

import android.content.Context
import org.json.JSONArray

/**
 * Persisted, capped clipboard history (newest first). Stored as a JSON array of
 * strings in SharedPreferences so it survives app close / reboot.
 */
class ClipboardStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(): MutableList<String> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i -> arr.getString(i) }
        }.getOrElse { mutableListOf() }
    }

    /** Adds text to the front, de-duplicating and capping the list. */
    fun add(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val list = getAll()
        if (list.firstOrNull() == trimmed) return false // same as latest
        list.remove(trimmed)
        list.add(0, trimmed)
        while (list.size > MAX_ITEMS) list.removeAt(list.size - 1)
        save(list)
        return true
    }

    fun removeAt(index: Int) {
        val list = getAll()
        if (index in list.indices) {
            list.removeAt(index)
            save(list)
        }
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "clipboard_store"
        private const val KEY = "history"
        const val MAX_ITEMS = 200
    }
}
