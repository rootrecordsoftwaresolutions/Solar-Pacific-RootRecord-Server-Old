package com.rootrecord.kilauea.alerts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KilaueaDataDao {
    @Query("SELECT * FROM kilauea_data WHERE cache_key = :key LIMIT 1")
    suspend fun getByKey(key: String): KilaueaDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KilaueaDataEntity)

    @Query("SELECT * FROM kilauea_data")
    suspend fun getAll(): List<KilaueaDataEntity>
}
