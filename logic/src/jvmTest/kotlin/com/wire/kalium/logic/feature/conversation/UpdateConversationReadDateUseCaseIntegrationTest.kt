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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.feature.message.receipt.ParallelConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.stubs.ClientApiStub
import com.wire.kalium.logic.util.stubs.ConversationApiStub
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.sending.BroadcastMessage
import com.wire.kalium.messaging.sending.BroadcastMessageTarget
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.nomaddevice.NomadAuthenticatedNetworkAccess
import com.wire.kalium.nomaddevice.NomadRemoteBackupDebouncedSyncConfig
import com.wire.kalium.nomaddevice.createUserScopedDebouncedNomadRemoteBackupChangeLogHookNotifier
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.backup.ChangeLogEventType
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.DatabaseStorageType
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class UpdateConversationReadDateUseCaseIntegrationTest {

    private val selfUserId = TestUser.SELF.id
    private val testUserIdEntity = UserIDEntity(selfUserId.value, selfUserId.domain)
    private val conversationId = TestConversation.GROUP().id

    private var testDatabase: TestUserDatabase? = null

    @AfterTest
    fun tearDown() {
        testDatabase?.delete()
    }

    @Test
    fun givenQueuedLastRead_whenSessionScopeCancelledDuringDebounce_thenLastReadIsNotPersisted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persistedLastRead = stableNow()
        val newLastRead = persistedLastRead + 1.seconds
        val arrangement = arrange(dispatcher, persistedLastRead)

        arrangement.subject(conversationId, newLastRead)
        runCurrent()

        advanceTimeBy(1.seconds)
        arrangement.sessionScope.cancel()
        runCurrent()
        advanceUntilIdle()

        assertPersisted(arrangement.database, persistedLastRead)
        assertNomadChangeLogNotPersisted(arrangement.database)
    }

    @Test
    fun givenQueuedLastRead_whenSessionScopeCancelledDuringDurablePersistence_thenLastReadAndNomadChangeLogArePersisted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persistedLastRead = stableNow()
        val newLastRead = persistedLastRead + 1.seconds
        val arrangement = arrange(
            dispatcher = dispatcher,
            persistedLastRead = persistedLastRead,
            wrapRepository = { repository, sessionScope ->
                object : ConversationRepository by repository {
                    private var didCancel = false

                    override suspend fun updateConversationReadDate(qualifiedID: QualifiedID, date: Instant): Either<StorageFailure, Unit> {
                        if (!didCancel) {
                            didCancel = true
                            sessionScope.cancel()
                        }
                        return repository.updateConversationReadDate(qualifiedID, date)
                    }
                }
            }
        )

        arrangement.subject(conversationId, newLastRead)
        runCurrent()
        advanceTimeBy(3.seconds + 1.milliseconds)
        runCurrent()
        advanceUntilIdle()

        assertPersisted(arrangement.database, newLastRead)
        assertNomadChangeLogPersisted(arrangement.database)
    }

    private suspend fun arrange(
        dispatcher: TestDispatcher,
        persistedLastRead: Instant,
        wrapRepository: (ConversationRepository, TestScope) -> ConversationRepository = { repository, _ -> repository }
    ): Arrangement {
        val database = TestUserDatabase(testUserIdEntity, dispatcher)
        testDatabase = database
        database.builder.conversationDAO.insertConversation(
            TestConversation.ENTITY_GROUP.copy(lastReadDate = persistedLastRead)
        )

        val sessionScope = TestScope(dispatcher)
        val repository = wrapRepository(createRepository(database, persistedLastRead), sessionScope)
        val hookNotifier = createPersistenceHookNotifier(database, sessionScope)
        val queue = ParallelConversationWorkQueue(sessionScope, kaliumLogger, dispatcher)

        return Arrangement(
            database = database,
            sessionScope = sessionScope,
            subject = UpdateConversationReadDateUseCase(
                conversationRepository = repository,
                messageSender = FakeMessageSender(),
                currentClientIdProvider = CurrentClientIdProvider { Either.Right(TestClient.CLIENT_ID) },
                selfUserId = selfUserId,
                selfConversationIdProvider = SelfConversationIdProvider { Either.Right(emptyList()) },
                sendConfirmation = object : SendConfirmationUseCase {
                    override suspend fun invoke(
                        conversationId: ConversationId,
                        afterDateTime: Instant,
                        untilDateTime: Instant
                    ): MessageOperationResult = MessageOperationResult.Success
                },
                workQueue = queue,
                persistenceEventHookNotifier = hookNotifier,
                logger = kaliumLogger
            )
        )
    }

    private fun createRepository(database: TestUserDatabase, persistedLastRead: Instant): ConversationRepository {
        val delegate = ConversationDataSource(
            selfUserId = selfUserId,
            conversationDAO = database.builder.conversationDAO,
            memberDAO = database.builder.memberDAO,
            conversationApi = ConversationApiStub(),
            messageDAO = database.builder.messageDAO,
            messageDraftDAO = database.builder.messageDraftDAO,
            clientDAO = database.builder.clientDAO,
            clientApi = ClientApiStub(),
            conversationMetaDataDAO = database.builder.conversationMetaDataDAO,
        )
        val observedConversation = TestConversation.GROUP().copy(lastReadDate = persistedLastRead)
        return object : ConversationRepository by delegate {
            override suspend fun observeConversationById(conversationId: ConversationId): Flow<Either<StorageFailure, Conversation>> =
                flowOf(Either.Right(observedConversation))
        }
    }

    private fun createPersistenceHookNotifier(
        database: TestUserDatabase,
        sessionScope: TestScope
    ): PersistenceEventHookNotifier {
        val userStorageProvider = TestUserStorageProvider(selfUserId, database)
        return createUserScopedDebouncedNomadRemoteBackupChangeLogHookNotifier(
            selfUserId = selfUserId,
            userStorageProvider = userStorageProvider,
            nomadAuthenticatedNetworkAccess = NomadAuthenticatedNetworkAccess(PlatformUserAuthenticatedNetworkProvider()),
            scope = sessionScope,
            config = NomadRemoteBackupDebouncedSyncConfig(
                debounceMs = 1_000L,
                maxWaitMs = 3_000L,
                maxAttemptsTotal = 1,
                retryDelaysMs = emptyList()
            )
        )
    }

    private suspend fun assertPersisted(database: TestUserDatabase, expectedLastRead: Instant) {
        val persistedConversation = database.builder.conversationDAO.getConversationById(TestConversation.ENTITY_GROUP.id)
        assertNotNull(persistedConversation)
        assertEquals(expectedLastRead, persistedConversation.lastReadDate)
    }

    private suspend fun assertNomadChangeLogPersisted(database: TestUserDatabase) {
        val pendingChanges = database.builder.remoteBackupChangeLogDAO.getPendingChanges()
        val metadataChange = pendingChanges.singleOrNull { it.eventType == ChangeLogEventType.CONVERSATION_METADATA_SYNC }
        assertNotNull(metadataChange)
        assertEquals(TestConversation.ENTITY_GROUP.id, metadataChange.conversationId)
    }

    private suspend fun assertNomadChangeLogNotPersisted(database: TestUserDatabase) {
        val pendingChanges = database.builder.remoteBackupChangeLogDAO.getPendingChanges()
        val metadataChange = pendingChanges.singleOrNull { it.eventType == ChangeLogEventType.CONVERSATION_METADATA_SYNC }
        assertNull(metadataChange)
    }

    private data class Arrangement(
        val database: TestUserDatabase,
        val sessionScope: TestScope,
        val subject: UpdateConversationReadDateUseCase,
    )

    private fun stableNow(): Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    private class TestUserStorageProvider(
        userId: UserId,
        private val database: TestUserDatabase
    ) : UserStorageProvider() {

        init {
            getOrCreate(
                userId = userId,
                platformUserStorageProperties = PlatformUserStorageProperties(
                    rootPath = "",
                    databaseInfo = DatabaseStorageType.InMemory
                ),
                shouldEncryptData = false,
                dbInvalidationControlEnabled = false
            )
        }

        override fun create(
            userId: UserId,
            shouldEncryptData: Boolean,
            platformProperties: PlatformUserStorageProperties,
            dbInvalidationControlEnabled: Boolean
        ): UserStorage = UserStorage(database.builder)
    }

    private class FakeMessageSender : MessageSender {
        override suspend fun sendPendingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit> =
            Either.Right(Unit)

        override suspend fun sendMessage(
            message: Message.Sendable,
            messageTarget: com.wire.kalium.messaging.sending.MessageTarget
        ): Either<CoreFailure, Unit> = Either.Right(Unit)

        override suspend fun broadcastMessage(
            message: BroadcastMessage,
            target: BroadcastMessageTarget
        ): Either<CoreFailure, Unit> = Either.Right(Unit)
    }
}
