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
package com.wire.kalium.conversation.history.data

import com.wire.kalium.logic.data.history.HistoryClient
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.time.ExperimentalTime

/**
 * Data Access Object for history clients.
 */
@OptIn(ExperimentalTime::class)
public interface HistoryClientDAO {
    /**
     * Selects all history clients for a given conversation ID.
     *
     * @param conversationId the conversation ID
     * @return a list of history clients
     */
    public suspend fun getAllForConversation(conversationId: QualifiedIDEntity): List<HistoryClient>

    /**
     * Selects all history clients for a given conversation ID from a specific date onwards.
     *
     * @param conversationId the conversation ID
     * @param fromDate the date from which to select history clients
     * @return a list of history clients
     */
    public suspend fun getAllForConversationFromDateOnwards(
        conversationId: QualifiedIDEntity,
        fromDate: Instant
    ): List<HistoryClient>

    /**
     * Observes all history clients for a given conversation ID.
     *
     * @param conversationId the conversation ID
     * @return a flow of history clients
     */
    public fun observeAllForConversation(conversationId: QualifiedIDEntity): Flow<List<HistoryClient>>

    /**
     * Inserts a new history client.
     *
     * @param conversationId the conversation ID
     * @param id the client ID
     * @param secret the client secret
     * @param creationDate the creation date
     */
    public suspend fun insertClient(
        conversationId: QualifiedIDEntity,
        id: String,
        secret: ByteArray,
        creationDate: Instant
    )
}
