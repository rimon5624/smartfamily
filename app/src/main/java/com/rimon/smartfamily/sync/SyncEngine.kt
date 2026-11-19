package com.rimon.smartfamily.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.FamilyEntity
import com.rimon.smartfamily.data.db.InventoryItemEntity
import com.rimon.smartfamily.data.db.InviteEntity
import com.rimon.smartfamily.data.db.ListItemEntity
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.data.db.ModuleSettingEntity
import com.rimon.smartfamily.data.db.ProfileEntity
import com.rimon.smartfamily.data.db.PurchaseEntity
import com.rimon.smartfamily.data.db.ShoppingListEntity
import com.rimon.smartfamily.data.prefs.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/** One synced table: how to read local changes, write server rows and mark rows clean. */
internal class TableSync<T>(
    val table: String,
    val onConflict: String,
    val serializer: KSerializer<T>,
    val dirty: suspend () -> List<T>,
    val keyOf: (T) -> String,
    val markClean: suspend (List<String>) -> Unit,
    val applyServer: suspend (List<T>) -> Unit,
    val serverUpdatedAt: (T) -> Long,
) {
    private val listSer = ListSerializer(serializer)

    suspend fun push(api: SupabaseApi) {
        val rows = dirty()
        if (rows.isEmpty()) return
        try {
            rows.chunked(200).forEach { chunk ->
                api.upsert(table, api.json.encodeToString(listSer, chunk), onConflict)
                markClean(chunk.map(keyOf))
            }
        } catch (e: SupabaseException) {
            if (!e.isRejection) throw e
            // A row was refused (usually row-level security): push one by one and let the
            // server copy win for refused rows, so one bad row never blocks the rest.
            dirty().forEach { row ->
                try {
                    api.upsert(table, api.json.encodeToString(listSer, listOf(row)), onConflict)
                } catch (single: SupabaseException) {
                    if (!single.isRejection) throw single
                }
                markClean(listOf(keyOf(row)))
            }
        }
    }

    /** Pulls rows changed since the last pull; returns them as plain objects. */
    suspend fun pull(api: SupabaseApi, settings: SettingsStore): List<Any?> {
        val all = mutableListOf<Any?>()
        var since = settings.lastPull(table)
        while (true) {
            val rows = api.json.decodeFromString(listSer, api.changedSince(table, since))
            if (rows.isEmpty()) break
            applyServer(rows)
            all.addAll(rows)
            since = rows.maxOf { serverUpdatedAt(it) }
            settings.setLastPull(table, since)
            if (rows.size < 500) break
        }
        return all
    }
}

/**
 * Offline-first sync (ARC-2): local edits are written to Room with dirty = true; the engine
 * pushes dirty rows, then pulls everything the server changed since the last pull.
 * Conflicts: the last write to reach the server wins (DM-1); the server stamps updated_at.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val api: SupabaseApi,
    private val settings: SettingsStore,
) {
    private val mutex = Mutex()

    private val tables: List<TableSync<*>> = handwrittenTables() + generatedTableSyncs(db)

    private fun handwrittenTables(): List<TableSync<*>> = listOf(
        TableSync(
            "profiles", "id", ProfileEntity.serializer(),
            { db.profiles().dirty() }, { it.id }, { db.profiles().markClean(it) },
            { rows -> db.profiles().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
        TableSync(
            "families", "id", FamilyEntity.serializer(),
            { db.families().dirty() }, { it.id }, { db.families().markClean(it) },
            { rows -> db.families().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
        TableSync(
            "family_members", "id", MemberEntity.serializer(),
            { db.members().dirty() }, { it.id }, { db.members().markClean(it) },
            { rows -> db.members().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
        TableSync(
            "invites", "code", InviteEntity.serializer(),
            { db.invites().dirty() }, { it.code }, { db.invites().markClean(it) },
            { rows -> db.invites().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
        TableSync(
            "module_settings", "id", ModuleSettingEntity.serializer(),
            { db.moduleSettings().dirty() }, { it.id }, { db.moduleSettings().markClean(it) },
            { rows -> db.moduleSettings().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
        TableSync(
            "shopping_lists", "id", ShoppingListEntity.serializer(),
            { db.lists().dirty() }, { it.id }, { db.lists().markClean(it) },
            { rows -> db.lists().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
        TableSync(
            "list_items", "id", ListItemEntity.serializer(),
            { db.listItems().dirty() }, { it.id }, { db.listItems().markClean(it) },
            { rows -> db.listItems().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
        TableSync(
            "purchase_history", "id", PurchaseEntity.serializer(),
            { db.purchases().dirty() }, { it.id }, { db.purchases().markClean(it) },
            { rows -> db.purchases().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
        TableSync(
            "inventory_items", "id", InventoryItemEntity.serializer(),
            { db.inventory().dirty() }, { it.id }, { db.inventory().markClean(it) },
            { rows -> db.inventory().upsertAll(rows.map { it.copy(dirty = false) }) }, { it.updatedAt },
        ),
    )

    val tableNames: List<String> get() = tables.map { it.table }

    /**
     * Quick sync of a few tables only, used every few seconds while a shared list is open
     * (SHP-9), so both phones see ticks almost at once without syncing everything.
     */
    suspend fun syncTables(names: Set<String>): Result<Unit> = mutex.withLock {
        val s = settings.current()
        if (!s.supabaseConfigured || !api.isSignedIn) return Result.success(Unit)
        runCatching {
            tables.filter { it.table in names }.forEach { it.push(api) }
            tables.filter { it.table in names }.forEach { it.pull(api, settings) }
        }
    }

    suspend fun syncNow(): Result<Unit> = mutex.withLock {
        val s = settings.current()
        if (!s.supabaseConfigured || !api.isSignedIn) return Result.success(Unit)
        val result = runCatching {
            tables.forEach { it.push(api) }
            val membershipChanged = pullAll(s.userId)
            // A membership that just became active makes older rows visible: pull everything again.
            if (membershipChanged) {
                settings.resetPulls(tableNames)
                pullAll(s.userId)
            }
        }
        settings.setSyncResult(System.currentTimeMillis(), result.exceptionOrNull()?.message ?: "")
        result
    }

    /** Returns true when one of the user's own membership rows changed. */
    private suspend fun pullAll(userId: String): Boolean {
        var membershipChanged = false
        tables.forEach { t ->
            val changed = t.pull(api, settings)
            if (t.table == "family_members") {
                membershipChanged = membershipChanged ||
                    changed.filterIsInstance<MemberEntity>().any { it.userId == userId }
            }
        }
        return membershipChanged
    }

    /** Joins a family with an invite code; the membership waits for admin approval (FAM-2). */
    suspend fun joinWithCode(code: String, displayName: String): Result<Unit> = runCatching {
        api.rpc(
            "join_family",
            buildJsonObject {
                put("p_code", code.trim().uppercase())
                put("p_display_name", displayName)
            },
        )
        syncNow().getOrThrow()
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SmartFamilyApp
        val ok = app.container.sync.syncNow().isSuccess
        runCatching { com.rimon.smartfamily.smart.ChatNotifier.afterSync(applicationContext, app.container) }
        return if (ok) Result.success() else Result.retry()
    }
}

object SyncScheduler {
    private const val PERIODIC = "sf_sync_periodic"
    private const val ONCE = "sf_sync_once"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Debounced one-off sync after a local change; runs as soon as there is a network. */
    fun requestSoon(context: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setInitialDelay(3, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
    }
}
