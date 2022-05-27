package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.MetadataQueries
import kotlinx.coroutines.flow.Flow

// TODO(refactor): suggestion implement with preference
class MetadataDAOImpl(private val metadataQueries: MetadataQueries): MetadataDAO {

    override suspend fun insertValue(value: String, key: String) {
        metadataQueries.insertValue(key, value)
    }

    override suspend fun valueByKey(key: String): Flow<String?> {
        return metadataQueries.selectValueByKey(key).asFlow().mapToOneOrNull()
    }
}
