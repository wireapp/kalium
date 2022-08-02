package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

interface MetadataDAO {
    suspend fun insertValue(value: String, key: String)
    suspend fun deleteValue(key: String)
    suspend fun valueByKeyFlow(key: String): Flow<String?>
    fun valueByKey(key: String): String?
}
