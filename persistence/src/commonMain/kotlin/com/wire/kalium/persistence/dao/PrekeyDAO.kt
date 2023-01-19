package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.MetadataQueries
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface PrekeyDAO {
    suspend fun updateOTRLastPrekeyId(newKeyId: Int)
    suspend fun forceInsertOTRLastPrekeyId(newKeyId: Int)
    suspend fun lastOTRPrekeyId(): Int?
}

internal class PrekeyDAOImpl internal constructor(
    private val metadataQueries: MetadataQueries,
    private val queriesContext: CoroutineContext
) : PrekeyDAO {
    override suspend fun updateOTRLastPrekeyId(newKeyId: Int) = withContext(queriesContext) {
        metadataQueries.transaction {
            val currentId = metadataQueries.selectValueByKey(OTR_LAST_PRE_KEY_ID).executeAsOneOrNull()?.toInt()
            if (currentId == null || newKeyId > currentId) {
                metadataQueries.insertValue(OTR_LAST_PRE_KEY_ID, newKeyId.toString())
            }
        }
    }

    override suspend fun forceInsertOTRLastPrekeyId(newKeyId: Int) = withContext(queriesContext) {
        metadataQueries.insertValue(OTR_LAST_PRE_KEY_ID, newKeyId.toString())
    }

    override suspend fun lastOTRPrekeyId(): Int? = withContext(queriesContext) {
        metadataQueries.selectValueByKey(OTR_LAST_PRE_KEY_ID).executeAsOneOrNull()?.toInt()
    }

    private companion object {
        const val OTR_LAST_PRE_KEY_ID = "otr_last_pre_key_id"
    }

}
