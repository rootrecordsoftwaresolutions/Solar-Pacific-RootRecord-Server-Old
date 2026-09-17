package com.rootrecord.kilauea.alerts.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [KilaueaDataEntity::class], version = 1, exportSchema = false)
abstract class KilaueaDatabase : RoomDatabase() {
    abstract fun kilaueaDataDao(): KilaueaDataDao
}
