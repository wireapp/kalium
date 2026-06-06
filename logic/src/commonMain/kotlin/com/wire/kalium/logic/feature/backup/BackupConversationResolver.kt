/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.backup.BackupConversationProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.createconversation.ConversationCreationResult
import com.wire.kalium.logic.feature.conversation.createconversation.CreateRegularGroupUseCase

internal interface BackupConversationResolver : BackupConversationProvider

internal class BackupConversationResolverImpl(
    private val selfUserId: UserId,
    private val createRegularGroup: CreateRegularGroupUseCase,
    private val conversationListDetailsProvider: suspend () -> List<ConversationDetails>,
    private val defaultProtocol: suspend () -> CreateConversationParam.Protocol,
) : BackupConversationResolver {

    override suspend fun getOrCreateBackupConversation(): Either<CoreFailure, ConversationId> {
        val backupConversationName = backupConversationName()
        conversationListDetailsProvider()
            .filterIsInstance<ConversationDetails.Group.Regular>()
            .firstOrNull {
                it.conversation.name == backupConversationName &&
                        it.conversation.removedBy == null &&
                        it.wireCell != null
            }?.let {
                return Either.Right(it.conversation.id)
            }

        return when (
            val result = createRegularGroup(
                name = backupConversationName,
                userIdList = emptyList(),
                options = CreateConversationParam(
                    access = Conversation.defaultGroupAccess,
                    accessRole = Conversation.defaultGroupAccessRoles,
                    wireCellEnabled = true,
                    protocol = defaultProtocol(),
                    skipCreator = false,
                ),
            )
        ) {
            is ConversationCreationResult.Success -> Either.Right(result.conversation.id)
            is ConversationCreationResult.UnknownFailure -> Either.Left(result.cause)
            ConversationCreationResult.Forbidden ->
                Either.Left(NetworkFailure.ServerMiscommunication(IllegalStateException("Backup conversation creation is forbidden")))
            ConversationCreationResult.SyncFailure ->
                Either.Left(NetworkFailure.NoNetworkConnection(null))
            is ConversationCreationResult.BackendConflictFailure ->
                Either.Left(NetworkFailure.FederatedBackendFailure.ConflictingBackends(result.domains))
        }
    }

    private fun backupConversationName(): String = "$BACKUP_CONVERSATION_PREFIX${selfUserId.value}"

    private companion object {
        const val BACKUP_CONVERSATION_PREFIX = "auto_backup_"
    }
}
