package com.wire.kalium.persistence.dao

interface MetadataDAO {
    suspend fun insertValue(value: String, key: String)
    suspend fun valueByKey(key: String): String?
}
