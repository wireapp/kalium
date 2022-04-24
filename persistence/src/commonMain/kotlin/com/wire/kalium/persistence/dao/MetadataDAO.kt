package com.wire.kalium.persistence.dao

interface MetadataDAO {
    fun insertValue(value: String, key: String)
    fun valueByKey(key: String): String?
}
