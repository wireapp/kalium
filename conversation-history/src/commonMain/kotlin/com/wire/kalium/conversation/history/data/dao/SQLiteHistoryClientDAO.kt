/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.conversation.history.data.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.wire.kalium.logic.data.history.HistoryClient
import com.wire.kalium.persistence.HistoryClientQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/**
 * Implementation of [HistoryClientDAO] that uses SQLDelight to access the database.
 */
internal class SQLiteHistoryClientDAO internal constructor(
    private val historyClientQueries: HistoryClientQueries,
    private val queriesContext: CoroutineContext = Dispatchers.IO
) : HistoryClientDAO {

    /**
     * Maps a database entity to a domain model.
     */
    private fun mapToHistoryClient(
        id: String,
        secret: ByteArray,
        creationDate: Instant
    ): HistoryClient = HistoryClient(
        id = id,
        creationTime = creationDate,
        secret = HistoryClient.Secret(secret)
    )

    /**
     * Selects all history clients for a given conversation ID.
     *
     * @param conversationId the conversation ID
     * @return a list of history clients
     */
    override suspend fun getAllForConversation(conversationId: QualifiedIDEntity): List<HistoryClient> =
        withContext(queriesContext) {
            historyClientQueries.selectAllForConversation(
                conversation_id = conversationId,
                mapper = ::mapToHistoryClient
            ).executeAsList()
        }

    /**
     * Selects all history clients for a given conversation ID from a specific date onwards.
     *
     * @param conversationId the conversation ID
     * @param fromDate the date from which to select history clients
     * @return a list of history clients
     */
    override suspend fun getAllForConversationFromDateOnwards(
        conversationId: QualifiedIDEntity,
        fromDate: Instant
    ): List<HistoryClient> = withContext(queriesContext) {
        historyClientQueries.selectAllForConversationFromDateOnwards(
            conversation_id = conversationId,
            creation_date = fromDate,
            mapper = ::mapToHistoryClient
        ).executeAsList()
    }

    /**
     * Observes all history clients for a given conversation ID.
     *
     * @param conversationId the conversation ID
     * @return a flow of history clients
     */
    override fun observeAllForConversation(conversationId: QualifiedIDEntity): Flow<List<HistoryClient>> =
        historyClientQueries.selectAllForConversation(
            conversation_id = conversationId,
            mapper = ::mapToHistoryClient
        ).asFlow().mapToList(queriesContext)

    /**
     * Inserts a new history client.
     *
     * @param conversationId the conversation ID
     * @param id the client ID
     * @param secret the client secret
     * @param creationDate the creation date
     */
    override suspend fun insertClient(
        conversationId: QualifiedIDEntity,
        id: String,
        secret: ByteArray,
        creationDate: Instant
    ) = withContext(queriesContext) {
        historyClientQueries.insertClient(
            conversation_id = conversationId,
            id = id,
            secret = secret,
            creation_date = creationDate
        )
    }
}
