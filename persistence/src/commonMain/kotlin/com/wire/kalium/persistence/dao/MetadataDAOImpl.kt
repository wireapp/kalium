package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.MetadataQueries
import com.wire.kalium.persistence.cache.Cache
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class MetadataDAOImpl internal constructor(
    private val metadataQueries: MetadataQueries,
    private val metadataCache: Cache<String, Flow<String?>>,
    private val databaseScope: CoroutineScope,
    private val queriesContext: CoroutineContext
) : MetadataDAO {

    override suspend fun insertValue(value: String, key: String) = withContext(queriesContext) {
        metadataQueries.insertValue(key, value)
    }

    override suspend fun deleteValue(key: String) {
        metadataQueries.deleteValue(key)
    }

    override suspend fun valueByKeyFlow(key: String): Flow<String?> = metadataCache.get(key) {
        metadataQueries.selectValueByKey(key)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChanged()
            .shareIn(databaseScope, SharingStarted.Lazily, 1)
    }

    override suspend fun valueByKey(key: String): String? = withContext(queriesContext) {
        metadataQueries.selectValueByKey(key).executeAsOneOrNull()
    }
}
