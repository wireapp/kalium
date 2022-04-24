package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.MetadataQueries

// TODO: suggestion implement with preference
class MetadataDAOImpl(private val metadataQueries: MetadataQueries): MetadataDAO {

    override fun insertValue(value: String, key: String) = metadataQueries.insertValue(key, value)

    override fun valueByKey(key: String): String? = metadataQueries.selectValueByKey(key).executeAsOneOrNull()
}
