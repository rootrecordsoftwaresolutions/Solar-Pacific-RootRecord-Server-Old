package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.local.dao.SyncSnapshotDao
import com.rootrecord.rootmc.data.sync.RootMcCloudSnapshot
import com.rootrecord.rootmc.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotRepository @Inject constructor(
    private val syncSnapshotDao: SyncSnapshotDao,
    private val prefs: RootMcPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun exportSnapshot(): RootMcCloudSnapshot = withContext(io) {
        val defaultWorldId = prefs.defaultWorldId.first()
        val sharedWorldIds = prefs.sharedWorldIds.first().toList().sorted()
        RootMcCloudSnapshot(
            exportedAt = System.currentTimeMillis(),
            defaultWorldId = defaultWorldId,
            sharedWorldIds = sharedWorldIds,
            worlds = syncSnapshotDao.allWorlds(),
            notebooks = syncSnapshotDao.allNotebooks(),
            notes = syncSnapshotDao.allNotes(),
            tags = syncSnapshotDao.allTags(),
            noteTagCrossRefs = syncSnapshotDao.allNoteTagCrossRefs(),
            coordinates = syncSnapshotDao.allCoordinates(),
            areas = syncSnapshotDao.allAreas(),
            noteLinks = syncSnapshotDao.allNoteLinks(),
            buildPlans = syncSnapshotDao.allBuildPlans(),
            buildPlanItems = syncSnapshotDao.allBuildPlanItems(),
            timelineEvents = syncSnapshotDao.allTimelineEvents(),
            trialChambers = syncSnapshotDao.allTrialChambers(),
            spawnerTracks = syncSnapshotDao.allSpawnerTracks(),
            mediaAttachments = syncSnapshotDao.allMediaAttachments(),
        )
    }

    suspend fun importSnapshot(snapshot: RootMcCloudSnapshot) = withContext(io) {
        syncSnapshotDao.replaceAllFromSnapshot(snapshot)
        snapshot.defaultWorldId?.takeIf { it > 0L }?.let { prefs.setDefaultWorldId(it) }
        val localWorldIds = snapshot.worlds.map { it.id }.toSet()
        snapshot.sharedWorldIds.forEach { worldId ->
            if (worldId in localWorldIds) {
                prefs.setWorldSharedOnProfile(worldId, shared = true)
            }
        }
    }
}
