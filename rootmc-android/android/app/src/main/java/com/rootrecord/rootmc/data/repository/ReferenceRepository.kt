package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.dao.ReferenceDao
import com.rootrecord.rootmc.data.local.entity.ReferenceCacheEntity
import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.di.ROOTRECORD_BLOCKNOTES_BASE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ReferenceRepository @Inject constructor(
    private val referenceDao: ReferenceDao,
    @param:Named("rootrecord") private val http: OkHttpClient,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeByCategory(category: String): Flow<List<ReferenceCacheEntity>> =
        referenceDao.observeByCategory(category)

    fun observeCategories(): Flow<List<String>> = referenceDao.observeCategories()

    suspend fun getCached(id: String): ReferenceCacheEntity? = withContext(io) {
        referenceDao.getById(id)
    }

    suspend fun upsert(entry: ReferenceCacheEntity) = withContext(io) {
        referenceDao.upsert(entry)
    }

    /** Download reference bundles from D1 when manifest version differs from local cache. */
    suspend fun syncFromRemote(): Result<Unit> = withContext(io) {
        runCatching {
            val manifestReq = Request.Builder()
                .url("${ROOTRECORD_BLOCKNOTES_BASE}api/reference/manifest")
                .get()
                .build()
            val manifestText = http.newCall(manifestReq).execute().use { resp ->
                if (!resp.isSuccessful) error("manifest HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val manifest = AppJson.parseToJsonElement(manifestText).jsonObject
            val remoteVersion = manifest["version"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (remoteVersion.isBlank() || remoteVersion == "unknown") {
                error("reference manifest missing version")
            }

            val categories = manifest["categories"]?.jsonArray.orEmpty()
            for (element in categories) {
                val category = element.jsonObject["category"]?.jsonPrimitive?.content?.trim()
                    ?: continue
                val bundleId = "$category:bundle"
                val existing = referenceDao.getById(bundleId)
                if (existing?.version == remoteVersion) continue

                val categoryReq = Request.Builder()
                    .url("${ROOTRECORD_BLOCKNOTES_BASE}api/reference/$category")
                    .get()
                    .build()
                val jsonBlob = http.newCall(categoryReq).execute().use { resp ->
                    if (!resp.isSuccessful) error("$category HTTP ${resp.code}")
                    resp.body?.string().orEmpty()
                }
                if (jsonBlob.isBlank()) error("$category empty body")

                referenceDao.upsert(
                    ReferenceCacheEntity(
                        id = bundleId,
                        category = category,
                        jsonBlob = jsonBlob,
                        version = remoteVersion,
                    ),
                )
            }

            referenceDao.upsert(
                ReferenceCacheEntity(
                    id = "__remote_version__",
                    category = "meta",
                    jsonBlob = manifestText,
                    version = remoteVersion,
                ),
            )
        }
    }

    suspend fun checkForUpdates(): Result<Unit> = syncFromRemote()
}
