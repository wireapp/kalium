/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
