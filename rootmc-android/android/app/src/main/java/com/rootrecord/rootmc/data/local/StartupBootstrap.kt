package com.rootrecord.rootmc.data.local

import android.content.Context
import android.util.Log
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when local DB was wiped and the process must restart once. */
class DatabaseResetRequiredException : Exception("Local database reset required")

@Singleton
class StartupBootstrap @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val databaseSeeder: DatabaseSeeder,
    private val database: RootMcDatabase,
    private val worldDao: WorldDao,
    private val prefs: RootMcPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun run(): Result<Unit> = withContext(io) {
        runCatching {
            databaseSeeder.seedIfNeeded()
            worldDao.count()
            prefs.clearDatabaseResetFlag()
        }.recoverCatching { error ->
            Log.e(TAG, "Startup bootstrap failed", error)
            if (prefs.markDatabaseResetIfAllowed()) {
                wipeLocalDatabaseFiles()
                throw DatabaseResetRequiredException()
            }
            throw error
        }
    }

    private fun wipeLocalDatabaseFiles() {
        runCatching { database.close() }
        listOf("rootmc.db", "rootmc.db-shm", "rootmc.db-wal").forEach { name ->
            runCatching { context.deleteDatabase(name) }
        }
    }

    private companion object {
        const val TAG = "RootMCBootstrap"
    }
}
