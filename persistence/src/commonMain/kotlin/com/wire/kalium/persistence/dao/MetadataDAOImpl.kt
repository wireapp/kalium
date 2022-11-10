package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.MetadataQueries
import com.wire.kalium.persistence.cache.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn

class MetadataDAOImpl internal constructor(
    private val metadataQueries: MetadataQueries,
    private val metadataCache: Cache<String, Flow<String?>>,
    private val databaseScope: CoroutineScope
) : MetadataDAO {

    override suspend fun insertValue(value: String, key: String) {
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

    override suspend fun valueByKey(key: String): String? = valueByKeyFlow(key).first()

}
