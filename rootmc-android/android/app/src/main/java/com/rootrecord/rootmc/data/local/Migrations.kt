package com.rootrecord.rootmc.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private fun SupportSQLiteDatabase.tableExists(table: String): Boolean {
    query(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        arrayOf(table),
    ).use { return it.moveToFirst() }
}

private fun SupportSQLiteDatabase.columnExists(table: String, column: String): Boolean {
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    }
    return false
}

private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, columnDef: String) {
    val columnName = columnDef.trim().split(Regex("\\s+")).first()
    if (!columnExists(table, columnName)) {
        execSQL("ALTER TABLE `$table` ADD COLUMN $columnDef")
    }
}

private fun SupportSQLiteDatabase.ensureAreasSchema() {
    if (!tableExists("areas")) {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS areas (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                worldId INTEGER NOT NULL,
                label TEXT NOT NULL,
                minX INTEGER NOT NULL,
                minZ INTEGER NOT NULL,
                maxX INTEGER NOT NULL,
                maxZ INTEGER NOT NULL,
                dimension TEXT NOT NULL,
                chunkArea INTEGER NOT NULL DEFAULT 0,
                chunkX INTEGER,
                chunkZ INTEGER,
                timestamp INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }
    execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_areas_chunk ON areas(worldId, dimension, chunkX, chunkZ) WHERE chunkArea = 1",
    )
    addColumnIfMissing("coordinates", "areaId INTEGER")
    addColumnIfMissing("notes", "waypointId INTEGER")
    addColumnIfMissing("notes", "areaId INTEGER")
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("worlds", "playMode TEXT NOT NULL DEFAULT 'SINGLEPLAYER'")
        db.addColumnIfMissing("worlds", "serverAddress TEXT")
        db.addColumnIfMissing("worlds", "mapUrl TEXT")
    }
}

/** Manual sort order for coords list (drag to reorder). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("coordinates", "sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE coordinates SET sortOrder = (
                SELECT COUNT(*) FROM coordinates AS newer
                WHERE newer.worldId = coordinates.worldId
                  AND (newer.timestamp > coordinates.timestamp
                       OR (newer.timestamp = coordinates.timestamp AND newer.id > coordinates.id))
            )
            WHERE sortOrder = 0
            """.trimIndent(),
        )
    }
}

/** Areas table, location note links, waypoint chunk areas. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureAreasSchema()
    }
}

/** Repair partial v4 installs (missing areas / note link columns). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureAreasSchema()
    }
}

/** Rebuild search index after note schema changes. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureAreasSchema()
        if (db.tableExists("notes_fts")) {
            runCatching {
                db.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
            }
        }
    }
}

/** Trial chamber + spawner cooldown tracking. */
val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS trial_chambers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT NOT NULL,
                worldId INTEGER NOT NULL,
                worldKey TEXT,
                label TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                x INTEGER,
                y INTEGER,
                z INTEGER,
                dimension TEXT NOT NULL,
                realmGroupId TEXT,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_trial_chambers_remoteId ON trial_chambers(remoteId)",
            )
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS spawner_tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT NOT NULL,
                chamberId INTEGER NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                spawnerType TEXT NOT NULL,
                mobType TEXT NOT NULL,
                customMobLabel TEXT,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                dimension TEXT NOT NULL,
                lastClearedAt INTEGER,
                cooldownEndsAt INTEGER,
                notes TEXT NOT NULL DEFAULT '',
                sortOrder INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(chamberId) REFERENCES trial_chambers(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_spawner_tracks_remoteId ON spawner_tracks(remoteId)",
            )
        }
    }

/** Tracks cloud ingame events imported into local waypoints/notes. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ingame_imports (
                ingameEventId INTEGER NOT NULL PRIMARY KEY,
                localType TEXT NOT NULL,
                localId INTEGER NOT NULL,
                importedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_ingame_imports_local ON ingame_imports(localType, localId)",
        )
    }
}
