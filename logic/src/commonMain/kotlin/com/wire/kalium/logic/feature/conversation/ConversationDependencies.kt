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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineScope

internal fun interface ConversationScopedFactory<T> {
    operator fun invoke(): T
}

/**
 * Runtime inputs from the manual user-session assembly root into the Metro graph.
 *
 * Factory inputs are scoped lazily to the user-session graph, so Metro evaluates them at most once
 * and only when they are reachable from a requested entry point. Other inputs are existing session
 * objects or unscoped collaborators. These inputs move into graph-native bindings as the session
 * assembly root is migrated.
 */
internal interface ConversationDependencies {
    val conversationRepositoryFactory: ConversationScopedFactory<ConversationRepository>
    val callRepositoryFactory: ConversationScopedFactory<CallRepository>
    val conversationGroupRepositoryFactory: ConversationScopedFactory<ConversationGroupRepository>
    val connectionRepositoryFactory: ConversationScopedFactory<ConnectionRepository>
    val userRepositoryFactory: ConversationScopedFactory<UserRepository>
    val conversationFolderRepositoryFactory: ConversationScopedFactory<ConversationFolderRepository>
    val syncManager: SyncManager
    val mlsConversationRepositoryFactory: ConversationScopedFactory<MLSConversationRepository>
    val currentClientIdProvider: CurrentClientIdProvider
    val messageSenderFactory: ConversationScopedFactory<MessageSender>
    val teamRepositoryFactory: ConversationScopedFactory<TeamRepository>
    val slowSyncRepositoryFactory: ConversationScopedFactory<SlowSyncRepository>
    val selfUserId: UserId
    val selfConversationIdProvider: SelfConversationIdProvider
    val persistMessage: PersistMessageUseCase
    val selfTeamIdProvider: SelfTeamIdProvider
    val sendConfirmation: SendConfirmationUseCase
    val renamedConversationHandler: RenamedConversationEventHandler
    val serverConfigRepositoryFactory: ConversationScopedFactory<ServerConfigRepository>
    val userStorage: UserStorage
    val userPropertyRepositoryFactory: ConversationScopedFactory<UserPropertyRepository>
    val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase
    val oneOnOneResolverFactory: ConversationScopedFactory<OneOnOneResolver>
    val userSessionCoroutineScope: CoroutineScope
    val kaliumLogger: KaliumLogger
    val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
    val serverConfigLinks: ServerConfig.Links
    val messageRepositoryFactory: ConversationScopedFactory<MessageRepository>
    val assetRepositoryFactory: ConversationScopedFactory<AssetRepository>
    val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
    val deleteConversationUseCase: DeleteConversationUseCase
    val persistConversationsUseCase: PersistConversationsUseCase
    val transactionProvider: CryptoTransactionProvider
    val resetMLSConversationUseCase: ResetMLSConversationUseCase
    val systemMessageInserter: SystemMessageInserter
    val persistenceEventHookNotifier: PersistenceEventHookNotifier
    val memberJoinEventHandler: MemberJoinEventHandler
    val joinExistingMLSConversation: JoinExistingMLSConversationUseCase
    val dispatcher: KaliumDispatcher
}
