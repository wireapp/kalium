package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.db.MetadataQueries

class MetadataDAOImpl(private val metadataQueries: MetadataQueries): MetadataDAO {

    override suspend fun insertValue(value: String, key: String) {
        metadataQueries.insertValue(key, value)
    }

    override suspend fun valueByKey(key: String): String? {
        return metadataQueries.selectByKey(key).executeAsOneOrNull()?.stringValue
    }
}
