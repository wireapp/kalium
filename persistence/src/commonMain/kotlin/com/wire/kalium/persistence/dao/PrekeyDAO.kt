package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.MetadataQueries

interface PrekeyDAO {
    suspend fun updateOTRLastPrekeyId(newKeyId: Int)
    suspend fun forceInsertOTRLastPrekeyId(newKeyId: Int)
    suspend fun lastOTRPrekeyId(): Int
}

internal class PrekeyDAOImpl internal constructor(
    private val metadataQueries: MetadataQueries
) : PrekeyDAO {
    override suspend fun updateOTRLastPrekeyId(newKeyId: Int) {
        metadataQueries.transaction {
            val currentId = metadataQueries.selectValueByKey(OTR_LAST_PRE_KEY_ID).executeAsOne().toInt()
            if (newKeyId > currentId) {
                metadataQueries.insertValue(OTR_LAST_PRE_KEY_ID, newKeyId.toString())
            }
        }
    }

    override suspend fun forceInsertOTRLastPrekeyId(newKeyId: Int) {
        metadataQueries.insertValue(OTR_LAST_PRE_KEY_ID, newKeyId.toString())
    }

    override suspend fun lastOTRPrekeyId(): Int =
        metadataQueries.selectValueByKey(OTR_LAST_PRE_KEY_ID).executeAsOne().toInt()

    private companion object {
        const val OTR_LAST_PRE_KEY_ID = "otr_last_pre_key_id"
    }

}
