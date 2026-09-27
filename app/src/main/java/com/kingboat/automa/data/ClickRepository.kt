package com.kingboat.automa.data

import android.content.ContentValues
import android.content.Context
import com.kingboat.automa.model.ClickPoint
import com.kingboat.automa.model.Profile

/**
 * All profile/point/meta access. Thin wrapper over [Database]; safe to create
 * anywhere since the underlying helper is a process singleton.
 */
class ClickRepository(context: Context) {

    private val db = Database.get(context)

    // --- profiles ---

    /** Guarantees at least one profile exists and returns the active id. */
    fun ensureDefaultProfile(): Long {
        if (getProfiles().isEmpty()) {
            val id = createProfile(DEFAULT_NAME)
            setActiveProfileId(id)
            return id
        }
        return getActiveProfileId()
    }

    fun getProfiles(): List<Profile> {
        val out = mutableListOf<Profile>()
        db.readableDatabase.query(
            "profiles", arrayOf("id", "name"), null, null, null, null, "id ASC",
        ).use { c ->
            while (c.moveToNext()) out.add(Profile(c.getLong(0), c.getString(1)))
        }
        return out
    }

    fun createProfile(name: String): Long {
        val values = ContentValues().apply {
            put("name", name.ifBlank { DEFAULT_NAME })
            put("created_at", System.currentTimeMillis())
        }
        val id = db.writableDatabase.insert("profiles", null, values)
        setActiveProfileId(id)
        return id
    }

    fun renameProfile(id: Long, name: String) {
        val values = ContentValues().apply { put("name", name.ifBlank { DEFAULT_NAME }) }
        db.writableDatabase.update("profiles", values, "id=?", arrayOf(id.toString()))
    }

    fun deleteProfile(id: Long) {
        db.writableDatabase.delete("profiles", "id=?", arrayOf(id.toString()))
        if (getActiveProfileId() == id) {
            val next = getProfiles().firstOrNull()?.id ?: ensureDefaultProfile()
            setActiveProfileId(next)
        }
    }

    fun getActiveProfileId(): Long {
        val stored = getMeta(KEY_ACTIVE)?.toLongOrNull()
        val profiles = getProfiles()
        if (stored != null && profiles.any { it.id == stored }) return stored
        return profiles.firstOrNull()?.id ?: -1L
    }

    fun setActiveProfileId(id: Long) = setMeta(KEY_ACTIVE, id.toString())

    fun getActiveProfile(): Profile? {
        val id = getActiveProfileId()
        return getProfiles().firstOrNull { it.id == id }
    }

    // --- points ---

    fun getPoints(profileId: Long): List<ClickPoint> {
        val out = mutableListOf<ClickPoint>()
        db.readableDatabase.query(
            "points",
            arrayOf("id", "label", "x", "y", "delay_ms", "position"),
            "profile_id=?", arrayOf(profileId.toString()),
            null, null, "position ASC, id ASC",
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ClickPoint(
                        x = c.getInt(2), y = c.getInt(3), delayMs = c.getLong(4),
                        id = c.getLong(0), label = c.getString(1), position = c.getInt(5),
                    )
                )
            }
        }
        return out
    }

    fun getActivePoints(): List<ClickPoint> = getPoints(getActiveProfileId())

    fun addPoint(profileId: Long, x: Int, y: Int, delayMs: Long, label: String): Long {
        val position = getPoints(profileId).size
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("label", label)
            put("x", x)
            put("y", y)
            put("delay_ms", delayMs)
            put("position", position)
        }
        return db.writableDatabase.insert("points", null, values)
    }

    /** Adds to the active profile with a default label if none given. */
    fun addPointToActive(x: Int, y: Int, delayMs: Long = 500L, label: String = ""): Long {
        val pid = getActiveProfileId().takeIf { it > 0 } ?: ensureDefaultProfile()
        val name = label.ifBlank { "P${getPoints(pid).size + 1}" }
        return addPoint(pid, x, y, delayMs, name)
    }

    fun updatePoint(id: Long, label: String, x: Int, y: Int, delayMs: Long) {
        val values = ContentValues().apply {
            put("label", label)
            put("x", x)
            put("y", y)
            put("delay_ms", delayMs)
        }
        db.writableDatabase.update("points", values, "id=?", arrayOf(id.toString()))
    }

    fun deletePoint(id: Long) {
        db.writableDatabase.delete("points", "id=?", arrayOf(id.toString()))
    }

    // --- control mode (meta) ---

    var controlMode: String
        get() = getMeta(KEY_MODE) ?: MODE_OVERLAY
        set(value) = setMeta(KEY_MODE, value)

    /** Rest break: pause [breakSeconds] after every [breakAfterTaps] taps
     *  (0 on either = feature off). */
    var breakAfterTaps: Int
        get() = getMeta(KEY_BREAK_TAPS)?.toIntOrNull() ?: 0
        set(value) = setMeta(KEY_BREAK_TAPS, value.toString())

    var breakSeconds: Int
        get() = getMeta(KEY_BREAK_SECS)?.toIntOrNull() ?: 0
        set(value) = setMeta(KEY_BREAK_SECS, value.toString())

    // --- meta helpers ---

    private fun getMeta(key: String): String? {
        db.readableDatabase.query(
            "meta", arrayOf("value"), "key=?", arrayOf(key), null, null, null,
        ).use { c -> return if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun setMeta(key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.writableDatabase.insertWithOnConflict(
            "meta", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    companion object {
        private const val DEFAULT_NAME = "Default"
        private const val KEY_ACTIVE = "active_profile"
        private const val KEY_MODE = "control_mode"
        private const val KEY_BREAK_TAPS = "break_after_taps"
        private const val KEY_BREAK_SECS = "break_seconds"
        const val MODE_OVERLAY = "overlay"
        const val MODE_NOTIFICATION = "notification"
    }
}
