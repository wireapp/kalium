package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.MetadataQueries
import kotlinx.coroutines.flow.Flow

class MetadataDAOImpl(private val metadataQueries: MetadataQueries) : MetadataDAO {

    override suspend fun insertValue(value: String, key: String) {
        metadataQueries.insertValue(key, value)
    }

    override suspend fun deleteValue(key: String) {
        metadataQueries.deleteValue(key)
    }

    override suspend fun valueByKeyFlow(key: String): Flow<String?> {
        return metadataQueries.selectValueByKey(key).asFlow().mapToOneOrNull()
    }

    override fun valueByKey(key: String): String =
        metadataQueries.selectValueByKey(key).executeAsOne()

}
