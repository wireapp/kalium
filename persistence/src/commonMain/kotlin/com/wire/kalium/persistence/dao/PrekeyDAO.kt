/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import io.mockative.Mockable
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Mockable
interface PrekeyDAO {
    suspend fun updateMostRecentPreKeyId(newKeyId: Int)
    suspend fun forceInsertMostRecentPreKeyId(newKeyId: Int)
    suspend fun mostRecentPreKeyId(): Int?
}

internal class PrekeyDAOImpl internal constructor(
    private val metadataQueries: MetadataQueries,
    private val queriesContext: CoroutineContext
) : PrekeyDAO {
    override suspend fun updateMostRecentPreKeyId(newKeyId: Int) = withContext(queriesContext) {
        metadataQueries.transaction {
            val currentId = metadataQueries.selectValueByKey(MOST_RECENT_PREKEY_ID).executeAsOneOrNull()?.toInt()
            if (currentId == null || newKeyId > currentId) {
                metadataQueries.insertValue(MOST_RECENT_PREKEY_ID, newKeyId.toString())
            }
        }
    }

    override suspend fun forceInsertMostRecentPreKeyId(newKeyId: Int) = withContext(queriesContext) {
        metadataQueries.insertValue(MOST_RECENT_PREKEY_ID, newKeyId.toString())
    }

    override suspend fun mostRecentPreKeyId(): Int? = withContext(queriesContext) {
        metadataQueries.selectValueByKey(MOST_RECENT_PREKEY_ID).executeAsOneOrNull()?.toInt()
    }

    private companion object {
        /**
         * Key used to store the most recent prekey ID in the metadata table.
         * In order to not be confused with "last prekey", which is the "last resort" permanent prekey
         * used when all prekeys are consumed, the variable was renamed to "most recent prekey".
         */
        const val MOST_RECENT_PREKEY_ID = "otr_last_pre_key_id"
    }

}
