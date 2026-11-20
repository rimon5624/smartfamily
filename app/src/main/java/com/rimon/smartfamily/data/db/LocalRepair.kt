package com.rimon.smartfamily.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Low-level fixes that touch every table, written generically so new tables are always included.
 */
object LocalRepair {
    private val skip = setOf("room_master_table", "android_metadata", "sqlite_sequence")
    /** Tables kept only on this phone (never synced). */
    private val localOnly = setOf("price_history", "price_watch", "price_promos", "scheduled_messages")
    /** Chat messages are sealed with the sender id inside the encryption; changing it would make them unreadable. */
    private val sealed = setOf("chat_messages")

    private fun tables(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            buildList { while (c.moveToNext()) c.getString(0).let { if (it !in skip && !it.startsWith("room_")) add(it) } }
        }

    private fun columns(db: SupportSQLiteDatabase, t: String): List<Pair<String, String>> =
        db.query("PRAGMA table_info(`$t`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1) to c.getString(2).uppercase()) }
        }

    /** Replaces a temporary local user id by the account id in every text column (including lists like "a,b"). */
    fun replaceUserId(db: SupportSQLiteDatabase, old: String, new: String) {
        if (old.isBlank() || old == new) return
        db.beginTransaction()
        try {
            tables(db).filter { it !in sealed }.forEach { t ->
                val cols = columns(db, t)
                val hasDirty = cols.any { it.first == "dirty" }
                cols.filter { it.second.contains("TEXT") && it.first != "id" }.forEach { (col, _) ->
                    val dirty = if (hasDirty) ", dirty = 1" else ""
                    db.execSQL("UPDATE `$t` SET `$col` = REPLACE(`$col`, ?, ?)$dirty WHERE instr(`$col`, ?) > 0", arrayOf(old, new, old))
                }
                // profiles use the user id as their key
                if (t == "profiles") db.execSQL("UPDATE profiles SET id = ? WHERE id = ? AND NOT EXISTS (SELECT 1 FROM profiles WHERE id = ?)", arrayOf(new, old, new))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Marks every synced row to be sent again (used once after the 2.0 server permission problem). */
    fun markAllDirty(db: SupportSQLiteDatabase) {
        tables(db).filter { it !in localOnly }.forEach { t ->
            if (columns(db, t).any { it.first == "dirty" }) db.execSQL("UPDATE `$t` SET dirty = 1")
        }
    }

    /**
     * Finds a temporary id that was left behind by an older sign-in: a user value used in "who" columns that is
     * neither me, nor any family member, nor a child placeholder. Returns it only when there is exactly one.
     */
    fun findLeftoverId(db: SupportSQLiteDatabase, me: String): String? {
        val who = Regex("(?i)^(created_?by|owner_?id|user_?id|done_?by|added_?by|bought_?by|paid_?by|assignee|sender_?id|approved_?by|voter_?id)$")
        val members = db.query("SELECT DISTINCT userId FROM family_members").use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val found = mutableSetOf<String>()
        tables(db).forEach { t ->
            columns(db, t).filter { who.matches(it.first) }.forEach { (col, _) ->
                db.query("SELECT DISTINCT `$col` FROM `$t`").use { c ->
                    while (c.moveToNext()) c.getString(0)?.let { v -> if (uuid.matches(v) && v != me && v !in members) found += v }
                }
            }
        }
        return found.singleOrNull()
    }
}
