package com.kingboat.automa.data

import android.content.Context
import org.json.JSONArray

/**
 * Persisted, capped clipboard history (newest first). Stored as a JSON array of
 * strings in SharedPreferences so it survives app close / reboot.
 */
class ClipboardStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): MutableList<String> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i -> arr.getString(i) }
        }.getOrElse { mutableListOf() }
    }

    /**
     * Adds text to the front, capping item size and list length. Already-saved
     * text is ignored (no reorder) so tapping an old row to re-copy doesn't
     * make the list jump under the user's finger.
     */
    @Synchronized
    fun add(text: String): Boolean {
        var trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.length > MAX_TEXT_CHARS) trimmed = trimmed.substring(0, MAX_TEXT_CHARS)
        val list = getAll()
        if (list.contains(trimmed)) return false
        list.add(0, trimmed)
        while (list.size > MAX_ITEMS) list.removeAt(list.size - 1)
        save(list)
        return true
    }

    @Synchronized
    fun removeAt(index: Int) {
        val list = getAll()
        if (index in list.indices) {
            list.removeAt(index)
            save(list)
        }
    }

    /** Removes the entry showing [text] (stable against list reordering). */
    @Synchronized
    fun removeValue(text: String) {
        val list = getAll()
        if (list.remove(text)) save(list)
    }

    @Synchronized
    fun clear() = prefs.edit().remove(KEY).commit()

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "clipboard_store"
        private const val KEY = "history"
        const val MAX_ITEMS = 200
        const val MAX_TEXT_CHARS = 4000
    }
}
