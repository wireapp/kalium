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
@file:OptIn(InternalKaliumApi::class, DelicateKaliumApi::class)

package com.wire.kalium.logic.di

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.EventBatchProcessor
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorker
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorkerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.sync.receiving.PendingConversationCleanup
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider

/** A typed factory allows consumer repositories to select the retained message-API coordinator.
 * Every create still reads each actual resource in the original constructor argument order.
 */
internal class UserSessionMessageRepositoryFactory(
    private val messageApi: Provider<com.wire.kalium.network.api.base.authenticated.message.MessageApi>,
    private val mlsMessageApi: Provider<com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi>,
    private val messageDao: Provider<com.wire.kalium.persistence.dao.message.MessageDAO>,
    private val userId: Provider<UserId>,
) {
    fun create(coordinator: NomadMessagePagingCoordinator?): MessageRepository = MessageDataSource(
        messageApi = messageApi(),
        mlsMessageApi = mlsMessageApi(),
        messageDAO = messageDao(),
        selfUserId = userId(),
        nomadMessagePagingCoordinator = coordinator,
    )
}

/** Caller-supplied gatherer override; recovery callbacks retain the existing lease/mutex owner. */
@Suppress("LongParameterList") // Explicit providers retain deferred worker callbacks.
internal class UserSessionIncrementalWorkerFactory(
    @UserSessionBinding("enableDurableMLSReceiveRecovery")
    private val enableDurableMLSReceiveRecovery: Provider<Boolean>,
    @UserSessionBinding("pendingConversationCleanup")
    private val pendingConversationCleanup: Provider<PendingConversationCleanup>,
    @UserSessionBinding("cryptoTransactionProvider")
    private val cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
    @UserSessionBinding("eventBatchProcessor")
    private val eventBatchProcessor: Provider<EventBatchProcessor>,
    @UserSessionBinding("clientRepository")
    private val clientRepository: Provider<ClientRepository>,
    @UserSessionBinding("legalHoldHandler")
    private val legalHoldHandler: Provider<LegalHoldHandlerImpl>,
    @UserSessionBinding("userScopedLogger")
    private val userScopedLogger: Provider<KaliumLogger>,
    @UserSessionBinding("userStorage")
    private val userStorage: Provider<UserStorage>,
    @UserSessionBinding("logout")
    private val logout: Provider<LogoutUseCase>,
    @UserSessionBinding("userId")
    private val userId: Provider<UserId>,
) {
    fun create(selectedEventGatherer: EventGatherer): IncrementalSyncWorker =
        IncrementalSyncWorkerImpl(
            selectedEventGatherer,
            batchProcessor = eventBatchProcessor(),
            logger = userScopedLogger(),
            consumePendingLegalHoldTransitions = { legalHoldHandler().recoverPendingTransitions() },
            consumePendingConversationCleanup = {
                if (enableDurableMLSReceiveRecovery()) {
                    val cleanup = pendingConversationCleanup()
                    cleanup.discardLegacyRequests()
                    if (userStorage().database.pendingActionDAO.getByActionType(
                            PendingActionType.WIPE_MLS_GROUP
                        ).isNotEmpty()
                    ) {
                        cryptoTransactionProvider().transaction("pending-local-conversation-cleanup") { context ->
                            Either.Right(cleanup.wipePending(context.mls))
                        }.getOrFail {
                            throw KaliumSyncException("Pending local conversation cleanup failed", it)
                        }.let { cleanup.committed(it) }
                    }
                }
            },
            consumePendingAccountInvalidation = {
                val client = clientRepository().currentClientId().getOrNull()
                val pending = client?.let {
                    com.wire.kalium.logic.sync.receiving.pendingAccountInvalidation(userStorage().database, userId(), it.value)
                }
                if (pending != null) {
                    logout()(
                        if (pending == com.wire.kalium.logic.sync.receiving.PendingAccountInvalidation.DELETED_ACCOUNT)
                            com.wire.kalium.logic.data.logout.LogoutReason.DELETED_ACCOUNT
                        else com.wire.kalium.logic.data.logout.LogoutReason.REMOVED_CLIENT,
                        waitUntilCompletes = false,
                    )
                }
                pending != null
            },
        )
}
