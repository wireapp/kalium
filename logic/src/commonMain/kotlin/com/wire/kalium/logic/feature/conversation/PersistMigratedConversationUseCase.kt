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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MigrationDAO
import com.wire.kalium.logic.functional.Either

/**
 * Persists a list of conversations migrated from old clients
 * Use carefully since normal conversations should come from the backend sync process
 *
 * @see [SyncConversationsUseCase]
 * @see [com.wire.kalium.logic.sync.SyncManager]
 */
fun interface PersistMigratedConversationUseCase {
    /**
     * Operation that persists a list of migrated conversations
     *
     * @param conversations list of migrated conversations
     * @return true or false depending on success operation
     */
    suspend operator fun invoke(conversations: List<Conversation>): Either<StorageFailure, Unit>
}

internal class PersistMigratedConversationUseCaseImpl(
    private val selfUserId: UserId,
    private val migrationDAO: MigrationDAO,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId)
) : PersistMigratedConversationUseCase {

    val logger by lazy { kaliumLogger.withFeatureId(CONVERSATIONS) }

    override suspend fun invoke(conversations: List<Conversation>): Either<StorageFailure, Unit> {
        return conversations.map { conversationMapper.fromMigrationModel(it) }.let {
            wrapStorageRequest { migrationDAO.insertConversation(it) }
        }
    }
}
