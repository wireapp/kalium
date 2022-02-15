package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

interface MetadataDAO {
    suspend fun insertValue(value: String, key: String)
    suspend fun valueByKey(key: String): Flow<String?>
}
