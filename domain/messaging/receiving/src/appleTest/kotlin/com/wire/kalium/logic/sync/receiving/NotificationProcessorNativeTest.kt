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

@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.notification.NotificationMessageKind
import com.wire.kalium.persistence.dao.message.NotificationMessageKey
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.F_GETPATH
import platform.posix.PATH_MAX
import platform.posix.fcntl
import platform.posix.getdtablesize
import app.cash.sqldelight.async.coroutines.synchronous
import com.wire.kalium.common.error.CommonizedMLSException
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandlerImpl
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.usernetwork.ExistingAccountSessionManager
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.DecryptedMessageBundle
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSDecryptResult
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.decryptMLSMessage
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.sync.receiver.conversation.memberPromotedMessage
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.string.hexToByteArray
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.authenticated.notification.conversation.MessageEventData
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.config.SelfDeletionTimerEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.databaseDriver
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.dbPassphrase.MLS_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.persistence.dbPassphrase.PROTEUS_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.db.existingUserDatabaseSecret
import com.wire.kalium.persistence.dbPassphrase.USER_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.persistence.model.SupportedCipherSuiteEntity
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import com.wire.kalium.persistence.dao.asset.AssetEntity
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

class NotificationProcessorNativeTest {
    @Test
    fun isolatedNSEDoesNotAlertAcrossAnOpaqueSelfAvailabilityPayload() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedProteusConversation()
                val unavailable = QualifiedIDEntity("unavailable", "federated.example")
                fixture.seedPendingIsolatedRecovery("combined", unavailable)
                val failure = fixture.event("failure", byteArrayOf(1), conversationId = unavailable)
                val availability = fixture.event("availability", fixture.encrypt("availability",
                    MessageContent.Availability(UserAvailabilityStatus.AWAY), protocolMLS = false),
                    protocolMLS = false, conversationId = DELETE_TARGET)
                val selfPayload: List<EventContentDTO> = availability.toEventResponse().payload.orEmpty().map {
                    (it as EventContentDTO.Conversation.NewMessageDTO).copy(qualifiedFrom = SELF.toApi())
                }
                val selfAvailability = availability.copy(payload = KtxSerializer.json.encodeToString(selfPayload))
                val target = fixture.event("target", fixture.encrypt("target"))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    CallSignalJournal(runtime.database).append(ReceivedCallEvent(
                        fixture.callContent().value, CONVERSATION_ID, SENDER.toDao(), SENDER_CLIENT,
                        Clock.System.now(), true, true, "pending-call"
                    ))
                })
                val result = fixture.receive(listOf(fixture.batch("combined", failure, selfAvailability), target), "target",
                    recoveryFactory = { fixture.unavailableRecovery(it) })
                val received = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertEquals("target", received.messageId)
                assertFalse(received.shouldNotify)
                assertFalse(received.mayPreview)
                assertNull(received.text)
                assertTrue(fixture.receivedCalls.isEmpty())
                assertTrue(fixture.receivedCallEvents.isEmpty())
                // A duplicate target must not evaluate queued calls while the earlier hole remains.
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "target")).value)
                assertTrue(fixture.receivedCallEvents.isEmpty())
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("combined")).isProcessed)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("target")).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById("target", CONVERSATION_ID))
                    assertNull(CallSignalJournal(runtime.database).signals().single().submittedGeneration)
                    // The alert must be withheld even though the earlier AWAY control has not run.
                    assertEquals(UserAvailabilityStatusEntity.NONE,
                        runtime.database.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.availabilityStatus)
                })
            }
        }
    }

    @Test
    fun isolatedNSERollsBackDecryptedCommitAndRetriesAfterPrePersistenceFailure() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedProteusConversation()
                val commit = fixture.event("commit", fixture.addThird(seedProfile = false))
                val following = fixture.event("following", fixture.encrypt("following"))
                val target = fixture.event("target", fixture.encrypt("target", protocolMLS = false),
                    protocolMLS = false, conversationId = DELETE_TARGET)
                fixture.releasePrepared()
                val result = fixture.receive(listOf(commit, following, target), "target",
                    nowEpochMillis = { 1_000L }, decorateMLSContext = { context ->
                        object : MlsCoreCryptoContext by context {
                            override suspend fun getGroupState(groupId: String): com.wire.kalium.cryptography.E2EIConversationState {
                                assertTrue(context.conversationEpoch(groupId) > 1uL)
                                throw com.wire.kalium.logic.sync.KaliumSyncException("Injected pre-persistence failure",
                                    com.wire.kalium.common.error.NetworkFailure.NoNetworkConnection(null))
                            }
                        }
                    })
                val received = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertEquals("target", received.messageId)
                assertFalse(received.shouldNotify)
                assertNull(received.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("commit")).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("following")).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("following", CONVERSATION_ID))
                    assertEquals(31_000L, runtime.database.notificationEventStore.deferredUntil("commit"))
                })
                val resumed = fixture.receive(emptyList(), "following", nowEpochMillis = { 32_000L })
                assertEquals("following", assertIs<Either.Right<ReceivedMessageResult?>>(resumed).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("commit")).isProcessed)
                    assertNull(runtime.database.notificationEventStore.deferredUntil("commit"))
                })
            }
        }
    }

    @Test
    fun isolatedNSECancellationKeepsTheNextEventPending() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val first = fixture.event("first", fixture.encrypt("first"))
                val target = fixture.event("target", fixture.encrypt("target"))
                fixture.releasePrepared()
                assertFailsWith<CancellationException> {
                    fixture.receive(listOf(first, target), "target", interruption = Interruption.CANCEL_BEFORE_COMMIT)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("target")).isProcessed)
                    assertNull(runtime.database.notificationEventStore.deferredUntil("first"))
                    assertNull(runtime.database.messageDAO.getMessageById("target", CONVERSATION_ID))
                })
                assertEquals("target", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "target")).value?.text)
            }
        }
    }

    @Test
    fun isolatedNSEDefersFederationAndDrainsIndependentMessagesAcrossRestart() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedProteusConversation()
                val missing = QualifiedIDEntity("unavailable", "federated.example")
                fixture.seedPendingIsolatedRecovery("blocked", missing)
                val failed = fixture.event("blocked", byteArrayOf(1), conversationId = missing)
                val blockedLater = fixture.event("blocked-later", byteArrayOf(2), conversationId = missing)
                val target = fixture.event("target", fixture.encrypt("target", protocolMLS = false),
                    protocolMLS = false, conversationId = DELETE_TARGET)
                val following = fixture.event("following", fixture.encrypt("following", protocolMLS = false),
                    protocolMLS = false, conversationId = DELETE_TARGET)
                fixture.releasePrepared()
                var fetches = 0
                val result = fixture.receive(listOf(failed, blockedLater, target, following), "target",
                    recoveryFactory = { fixture.unavailableRecovery(it) { fetches++ } }, nowEpochMillis = { 1_000L })
                val received = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertEquals("target", received.messageId)
                assertFalse(received.shouldNotify)
                assertNull(received.text)
                assertEquals(1, fetches)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("blocked")).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("blocked-later")).isProcessed)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("target")).isProcessed)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("following")).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById("following", DELETE_TARGET))
                    assertEquals(31_000L, runtime.database.notificationEventStore.deferredUntil("blocked"))
                    assertEquals("following", runtime.database.notificationEventStore.cursor())
                })
                // receive() reopens both native stores and SQL, rebuilding dependency state from pending rows.
                val restarted = fixture.receive(emptyList(), "following",
                    recoveryFactory = { fixture.unavailableRecovery(it) { fetches++ } }, nowEpochMillis = { 2_000L })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(restarted).value)
                assertEquals("QUEUE_END", fixture.receivedStop)
                assertEquals(1, fetches)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "blocked",
                    recoveryFactory = { fixture.unavailableRecovery(it) { fetches++ } }, nowEpochMillis = { 32_000L })).value)
                assertEquals(2, fetches)
            }
        }
    }

    @Test
    fun isolatedNSEPreservesMultiPayloadAndSharedProteusSessionDependencies() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedProteusConversation()
                val another = QualifiedIDEntity("another-proteus", SELF.domain)
                fixture.seedProteusConversation(another)
                val failed = fixture.event("blocked-mls", byteArrayOf(1),
                    conversationId = QualifiedIDEntity("unavailable", "federated.example"))
                val first = fixture.event("first", fixture.encrypt("first", protocolMLS = false),
                    protocolMLS = false, conversationId = DELETE_TARGET)
                val later = fixture.event("later", fixture.encrypt("later", protocolMLS = false),
                    protocolMLS = false, conversationId = another)
                val target = fixture.event("target", fixture.encrypt("target"))
                val combined = fixture.batch("combined", failed, first)
                fixture.seedPendingIsolatedRecovery("combined", QualifiedIDEntity("unavailable", "federated.example"))
                fixture.releasePrepared()
                val result = fixture.receive(listOf(combined, later, target), "target",
                    recoveryFactory = { fixture.unavailableRecovery(it) })
                val received = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertEquals("target", received.messageId)
                assertFalse(received.shouldNotify)
                assertNull(received.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("combined")).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("later")).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("first", DELETE_TARGET))
                    assertNull(runtime.database.messageDAO.getMessageById("later", another))
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("target")).isProcessed)
                })
            }
        }
    }

    @Test
    fun isolatedNSEStopsAtAccountEventsAndEncryptedCrossConversationEffects() = runTest {
        for (encrypted in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedProteusConversation()
                val failed = fixture.event("blocked", byteArrayOf(1),
                    conversationId = QualifiedIDEntity("unavailable", "federated.example"))
                fixture.seedPendingIsolatedRecovery("blocked", QualifiedIDEntity("unavailable", "federated.example"))
                val barrier = if (encrypted) fixture.event("barrier",
                    fixture.encrypt("barrier", MessageContent.DataTransfer(MessageContent.DataTransfer.TrackingIdentifier("changed")),
                        protocolMLS = false), protocolMLS = false, conversationId = DELETE_TARGET)
                else fixture.newClient()
                val target = fixture.event("target", fixture.encrypt("target"))
                fixture.releasePrepared()
                val result = fixture.receive(listOf(failed, barrier, target), "target",
                    recoveryFactory = { fixture.unavailableRecovery(it) })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    for (id in listOf("blocked", barrier.id, "target")) {
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(id)).isProcessed)
                    }
                    assertNull(runtime.database.messageDAO.getMessageById("target", CONVERSATION_ID))
                    assertTrue(runtime.database.userConfigDAO.getTrackingIdentifier() != "changed")
                })
            }
        }
    }

    @Test
    fun isolatedNSEDoesNotContinueAfterSQLOrCommitOrAcknowledgementFailure() = runTest {
        for (interruption in listOf(Interruption.DELIVERY, Interruption.MARKER, Interruption.AFTER_COMMIT)) {
            withContext(Dispatchers.Default) {
                withFixture(false) { fixture ->
                    val first = fixture.event("first", fixture.encrypt("first"))
                    val target = fixture.event("target", fixture.encrypt("target"))
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(first, target), "target", interruption = interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("first")).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("target")).isProcessed)
                        assertNull(runtime.database.notificationEventStore.deferredUntil("first"))
                        assertNull(runtime.database.messageDAO.getMessageById("target", CONVERSATION_ID))
                    })
                }
            }
        }
    }

    /** Separate simulator processes share only prepared stores and this synthetic event manifest. */
    fun runMLSStress(args: Array<String>) = kotlinx.coroutines.runBlocking {
        withContext(Dispatchers.Default) {
            val operation = args[0]
            val manifestPath = args[1].toPath()
            if (operation == "prepare") {
                val count = args[2].toInt()
                require(count in 1..5000)
                val fixture = Fixture(true)
                try {
                    fixture.prepare()
                    val events = mutableListOf<EventResponseToStore>()
                    val ids = (0 until count).map { "stress-$it" }
                    ids.forEachIndexed { index, id ->
                        if (index % 10 == 0) events += fixture.event("commit-$index", fixture.commit())
                        events += fixture.event(id, fixture.encrypt(id))
                    }
                    val notification = fixture.batch("notification", *events.toTypedArray())
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    FileSystem.SYSTEM.write(manifestPath) {
                        writeUtf8(fixture.manifest(notification, next, next, ids).toString())
                    }
                } finally {
                    fixture.close(deleteStorage = false)
                }
                println("MLS_STRESS_PREPARED messages=$count")
                return@withContext
            }
            val manifest = KtxSerializer.json.parseToJsonElement(FileSystem.SYSTEM.read(manifestPath) { readUtf8() }).jsonObject
            val fixture = Fixture(true, root = manifest.getValue("root").jsonPrimitive.content,
                keychainService = manifest.getValue("service").jsonPrimitive.content)
            val ids = manifest.getValue("messageIds").jsonArray.map { it.jsonPrimitive.content }
            val response = KtxSerializer.json.decodeFromString<NotificationResponse>(manifest.getValue("response").toString())
            when (operation) {
                "receive" -> {
                    val stage = args[2]
                    val gate = args[3]
                    val interruption = args[4].takeUnless { it == "NONE" }?.let { Interruption.valueOf(it) }
                    var paused = false
                    val result = fixture.receive(response.notifications, "notification", interruption,
                        transactionCheckpoint = { checkpoint ->
                            if (!paused && checkpoint == stage) {
                                paused = true
                                FileSystem.SYSTEM.write("$gate.ready".toPath()) { writeUtf8("ready") }
                                withTimeout(60.seconds) {
                                    while (!FileSystem.SYSTEM.exists("$gate.release".toPath())) kotlinx.coroutines.delay(10)
                                }
                            }
                        })
                    when {
                        result is Either.Left && result.value == AccountRuntimeFailure.BUSY -> println("MLS_STRESS_BUSY")
                        interruption != null -> { assertIs<Either.Left<*>>(result); println("MLS_STRESS_INTERRUPTED") }
                        else -> { assertIs<Either.Right<*>>(result); println("MLS_STRESS_RECEIVED") }
                    }
                }
                "verify" -> {
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val messages = runtime.database.messageDAO
                            .getMessagesByConversationAndVisibility(CONVERSATION_ID, ids.size + 100, 0).first()
                            .filterIsInstance<MessageEntity.Regular>()
                        assertEquals(ids.toSet(), messages.map { it.id }.toSet())
                        assertEquals(ids.size, messages.size)
                        messages.forEach { assertEquals(it.id, (it.content as MessageEntityContent.Text).messageBody) }
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        val deliveries = runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, ids.size.toLong() + 1)
                        // Main-app reception does not enqueue NSE deferred delivery receipts.
                        val expectedDeliveries = if (args.getOrNull(2) == "app-first") emptyList() else ids
                        assertEquals(expectedDeliveries.toSet(), deliveries.map { it.messageId }.toSet())
                        assertEquals(expectedDeliveries.size, deliveries.size)
                        assertEquals(1uL + ((ids.size + 9) / 10).toULong(),
                            checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    })
                    assertIs<Either.Right<*>>(fixture.decryptAndCheck(
                        Base64.decode(manifest.getValue("next").jsonPrimitive.content), "next"))
                    fixture.close(deleteStorage = false)
                    println("MLS_STRESS_VERIFIED messages=${ids.size}")
                }
                "cleanup-trigger" -> assertIs<Either.Right<*>>(fixture.open { fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive") })
                "cleanup" -> fixture.close()
                else -> error("Unknown stress operation")
            }
        }
    }

    @Test
    fun nonHeadInvalidationPreemptsBlockedWorkOfflineWithoutAcknowledgingQueue() = runTest {
        for (deleted in listOf(false, true)) for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val blocked = fixture.plainEvent("blocked", """{"type":"user.legalhold-enable","id":"${SELF.value}"}""")
                val terminal = if (deleted) fixture.userDelete(SELF) else fixture.clientRemove(CLIENT)
                val text = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.notificationEventStore.storePage(null, listOf(blocked, terminal, text).map {
                        NewEventEntity(it.id, it.payload, it.transient, false)
                    })
                })
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), text.id))
                assertEquals(NotificationAppWork.ACCOUNT_INVALIDATION, fixture.receivedAppWork)
                assertEquals("3", fixture.sqlValue("SELECT COUNT(*) FROM Events WHERE is_processed = 0"))
                assertEquals("0", fixture.sqlValue("SELECT COUNT(*) FROM Message WHERE id = 'tail'"))
                assertIs<Either.Left<*>>(fixture.open { error("Invalidated account reopened") })
            }
        }
    }

    @Test
    fun sentConnectionMetadataProteus() = sentConnectionMetadata(false)

    @Test
    fun sentConnectionMetadataMLS() = sentConnectionMetadata(true)

    private fun sentConnectionMetadata(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.sql("DELETE FROM Member WHERE user = '${SELF.toDao()}'")
                val event = fixture.sentConnectionEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                var metadata = 0
                var profiles = 0
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { metadata++; fixture.sentConnectionResponse() },
                    fetchConnectionPeer = { profiles++; fixture.connectionProfile(it) },
                    fetchProfiles = { error("Sent must use the app's member placeholders") })).value
                assertEquals(1, metadata)
                assertEquals(1, profiles)
                assertEquals(true, result?.shouldNotify)
                assertEquals("first", result?.messageId)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifySentConnection(it) })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun sentConnectionMetadataWithoutNativeGroup() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                fixture.seedProteusConversation()
                val event = fixture.batch("notification", fixture.connectionEvent("sent", CONVERSATION_ID.value, SENDER),
                    fixture.event("first", fixture.encrypt("first", protocolMLS = false), protocolMLS = false, conversationId = DELETE_TARGET))
                val next = fixture.encrypt("next", protocolMLS = false)
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { fixture.sentConnectionResponse() }, fetchConnectionPeer = fixture::connectionProfile))
                assertIs<Either.Right<*>>(fixture.open {
                    fixture.verifySentConnection(it, established = false, expectedPeerRole = com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin)
                    assertFalse(checkNotNull(fixture.openMLS(it)).transaction { ctx -> ctx.conversationExists(GROUP_ID) })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next", protocolMLS = false))
            }
        }
    }

    @Test
    fun sentConnectionRequiredFailures() = runTest {
        withContext(Dispatchers.Default) {
            for (failure in listOf("network", "native", "sql", "identity")) withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.connectionEvent("sent", CONVERSATION_ID.value, SENDER), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (failure == "sql") fixture.sql("CREATE TRIGGER interrupt_sent BEFORE INSERT ON Conversation BEGIN SELECT RAISE(ABORT, 'fixture'); END")
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id,
                    fetchConnectionPeer = fixture::connectionProfile,
                    fetchConversation = {
                        when (failure) {
                            "network" -> throw KaliumException.InvalidRequestError(GenericAPIErrorResponse(404, "not-found", "Fixture failure"))
                            "identity" -> fixture.sentConnectionResponse().copy(id = DELETE_TARGET.toApi())
                            else -> fixture.sentConnectionResponse()
                        }
                    },
                    decorateMLSContext = { real -> object : MlsCoreCryptoContext by real {
                        override suspend fun conversationExists(groupId: MLSGroupId): Boolean =
                            if (failure == "native") error("Required native existence check failed") else real.conversationExists(groupId)
                    } }))
                assertIs<Either.Right<*>>(fixture.open {
                    assertFalse(checkNotNull(it.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertNull(it.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    assertNull(it.database.connectionDAO.getConnection(CONVERSATION_ID))
                })
                fixture.sql("DROP TRIGGER IF EXISTS interrupt_sent")
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id,
                    fetchConnectionPeer = fixture::connectionProfile, fetchConversation = { fixture.sentConnectionResponse() }))
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun sentConnectionNativeReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (interruption in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) withFixture(true) { fixture ->
                val event = fixture.sentConnectionEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, interruption,
                    fetchConnectionPeer = fixture::connectionProfile, fetchConversation = { fixture.sentConnectionResponse() }))
                fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id,
                    fetchConnectionPeer = fixture::connectionProfile, fetchConversation = { fixture.sentConnectionResponse() }))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifySentConnection(it) })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun sentMetadataPastEarlierGroupBlocksReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.event("old-text", fixture.encrypt("old-text")),
                    fixture.connectionEvent("sent", CONVERSATION_ID.value, SENDER))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                val metadata: suspend () -> ConversationResponse = { fixture.sentConnectionResponse().copy(groupId = RESET_GROUP_ID) }
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, Interruption.BEFORE_COMMIT,
                    fetchConversation = metadata, fetchConnectionPeer = fixture::connectionProfile))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNotNull(runtime.database.messageDAO.getMessageById("old-text", CONVERSATION_ID))
                    assertEquals(RESET_GROUP_ID, (checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        .protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId)
                })
                repeat(2) { assertIs<Either.Left<*>>(fixture.receive(emptyList(), event.id,
                    fetchConversation = metadata, fetchConnectionPeer = fixture::connectionProfile)) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("tail", CONVERSATION_ID))
                    assertEquals(1uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                })
            }
        }
    }

    @Test
    fun acceptedConnectionProteus() = acceptedConnection(false)

    @Test
    fun acceptedConnectionMLS() = acceptedConnection(true)

    private fun acceptedConnection(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            for (consent in listOf(false, true)) withFixture(mls) { fixture ->
                fixture.seedAcceptedConnection(consent)
                val event = fixture.acceptedConnectionEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConnectionPeer = fixture::lifecyclePeer,
                    fetchProfiles = { ListUsersDTO(usersFound = listOf(fixture.lifecyclePeer(SENDER.toApi()))) })).value
                assertEquals(true, result?.shouldNotify)
                assertEquals("first", result?.messageId)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyAcceptedConnection(it, consent) })
                fixture.verifyStoredAndNext(next, "first", if (mls) listOf("prefix", "first") else listOf("first"))
            }
        }
    }

    @Test
    fun acceptedConnectionNativeReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (consent in listOf(false, true)) {
                for (interruption in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) withFixture(mls) { fixture ->
                    fixture.seedAcceptedConnection(consent)
                    val event = fixture.acceptedConnectionEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, interruption, fetchConnectionPeer = fixture::lifecyclePeer))
                    fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                    assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id, fetchConnectionPeer = fixture::lifecyclePeer))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyAcceptedConnection(it, consent) })
                    fixture.verifyStoredAndNext(next, "first", if (mls) listOf("prefix", "first") else listOf("first"))
                }
            }
        }
    }

    @Test
    fun acceptedConnectionWarningAndMembershipFailures() = runTest {
        withContext(Dispatchers.Default) {
            for (table in listOf("Message", "Member")) withFixture(false) { fixture ->
                fixture.seedAcceptedConnection(false)
                val event = fixture.acceptedConnectionEvents()
                fixture.releasePrepared()
                fixture.sql("CREATE TRIGGER interrupt_accepted BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'fixture'); END")
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, fetchConnectionPeer = fixture::lifecyclePeer))
                assertIs<Either.Right<*>>(fixture.open {
                    assertFalse(checkNotNull(it.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertEquals(ConversationEntity.Type.CONNECTION_PENDING, it.database.conversationDAO.getConversationById(CONVERSATION_ID)?.type)
                    assertNull(it.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                })
                fixture.sql("DROP TRIGGER interrupt_accepted")
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id, fetchConnectionPeer = fixture::lifecyclePeer))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyAcceptedConnection(it, false) })
            }
        }
    }

    @Test
    fun acceptedConnectionConsentHistoryDoesNotBlockReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (restoreMode in listOf("none", "before", "after")) {
                for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                    withFixture(false) { fixture ->
                        fixture.seedAcceptedConnection(true)
                        val prefix = if (restoreMode == "before") listOf(fixture.connectionEvent("missing-legalhold-consent", CONVERSATION_ID.value, SENDER)) else emptyList()
                        val suffix = if (restoreMode == "after") listOf(fixture.connectionEvent("missing-legalhold-consent", CONVERSATION_ID.value, SENDER)) else emptyList()
                        val event = fixture.batch("notification", *(prefix + fixture.connectionEvent("accepted", CONVERSATION_ID.value, SENDER) +
                            fixture.connectionEvent("ignored", CONVERSATION_ID.value, SENDER) + suffix + fixture.event("first", fixture.encrypt("first"))).toTypedArray())
                        val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                        val next = fixture.encrypt("next")
                        fixture.releasePrepared()
                        if (stage != null) {
                            assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id, stage,
                                fetchConnectionPeer = fixture::lifecyclePeer))
                            if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                        }
                        assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id, fetchConnectionPeer = fixture::lifecyclePeer))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                            assertEquals(if (restoreMode == "after") ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT else ConnectionEntity.State.IGNORED,
                                runtime.database.connectionDAO.getConnection(CONVERSATION_ID)?.status)
                            val warnings = runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                                .filter { it.content == MessageEntityContent.ConversationStartedUnverifiedWarning }
                            assertEquals(if (restoreMode == "none" && stage != null) 1 else 0, warnings.size)
                        })
                        fixture.verifyStoredAndNext(next, "tail", listOf("first", "tail"))
                    }
                }
            }
        }
    }

    @Test
    fun acceptedConnectionRetainedRequestAndMovedWarningReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedAcceptedConnection(false)
                val event = fixture.batch("notification", fixture.connectionEvent("pending", DELETE_TARGET.value, SENDER),
                    fixture.connectionEvent("accepted", DELETE_TARGET.value, SENDER), fixture.connectionEvent("ignored", DELETE_TARGET.value, SENDER),
                    fixture.event("first", fixture.encrypt("first")))
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT,
                    fetchConnectionPeer = fixture::lifecyclePeer))
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id, fetchConnectionPeer = fixture::lifecyclePeer))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyAcceptedConnection(it, false, expectedState = ConnectionEntity.State.IGNORED) })
            }
            withFixture(true) { fixture ->
                fixture.seedAcceptedConnection(false)
                val event = fixture.batch("notification", fixture.event("first", fixture.encrypt("first")),
                    fixture.connectionEvent("pending", DELETE_TARGET.value, SENDER), fixture.connectionEvent("accepted", DELETE_TARGET.value, SENDER))
                fixture.releasePrepared()
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConnectionPeer = fixture::lifecyclePeer)).value
                assertEquals(ReceivedNotificationType.CONNECTION_REQUEST, result?.type)
                assertEquals(DELETE_TARGET.value, result?.conversationValue)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyAcceptedConnection(it, false) })
            }
        }
    }

    @Test
    fun acceptedConnectionRetainsPositiveWarningAfterConsentChange() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                fixture.seedAcceptedConnection(false)
                val event = fixture.batch("notification", fixture.acceptedConnectionEvents(),
                    fixture.connectionEvent("missing-legalhold-consent", if (mls) DELETE_TARGET.value else CONVERSATION_ID.value, SENDER))
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT,
                    fetchConnectionPeer = fixture::lifecyclePeer))
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id, fetchConnectionPeer = fixture::lifecyclePeer))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyAcceptedConnection(it, false,
                    expectedState = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT,
                    expectedUserState = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT) })
            }
        }
    }

    @Test
    fun acceptedDeletedPeerUsesRetainedActiveConversation() = runTest {
        withContext(Dispatchers.Default) {
            for (interruption in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT)) withFixture(true) { fixture ->
                fixture.seedAcceptedConnection(true)
                fixture.sql("UPDATE User SET active_one_on_one_conversation_id = '$CONVERSATION_ID' WHERE qualified_id = '${SENDER.toDao()}'")
                fixture.sql("UPDATE Conversation SET mls_proposal_timer = '$TIME' WHERE qualified_id = '$CONVERSATION_ID'")
                val event = fixture.batch("notification", fixture.connectionEvent("accepted", DELETE_TARGET.value, SENDER),
                    fixture.event("first", fixture.encrypt("first", protocolMLS = false), protocolMLS = false, conversationId = DELETE_TARGET))
                val next = fixture.encrypt("next", protocolMLS = false)
                fixture.releasePrepared()
                if (interruption != null) assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, interruption,
                    fetchConnectionPeer = { fixture.lifecyclePeer(it).copy(deleted = true) }))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                    fetchConnectionPeer = { fixture.lifecyclePeer(it).copy(deleted = true) }))
                assertIs<Either.Right<*>>(fixture.open {
                    val peer = checkNotNull(it.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao()))
                    assertTrue(peer.deleted)
                    assertEquals(CONVERSATION_ID, peer.activeOneOnOneConversationId)
                    assertFalse(checkNotNull(fixture.openMLS(it)).transaction { context -> context.conversationExists(GROUP_ID) })
                    assertTrue(it.database.conversationDAO.getProposalTimers().first().isEmpty())
                    assertNotNull(it.database.messageDAO.getMessageById("first", DELETE_TARGET))
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next", protocolMLS = false))
            }
        }
    }

    @Test
    fun acceptedConnectionUnknownProfileUsesPlaceholder() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedProteusConversation()
                fixture.sql("DELETE FROM Member WHERE conversation = '$DELETE_TARGET'")
                val event = fixture.batch("notification", fixture.connectionEvent("accepted", DELETE_TARGET.value, THIRD),
                    fixture.event("first", fixture.encrypt("first")))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, fetchConnectionPeer = {
                    throw KaliumException.InvalidRequestError(GenericAPIErrorResponse(404, "not-found", "Missing peer"))
                }))
                assertIs<Either.Right<*>>(fixture.open {
                    val peer = checkNotNull(it.database.userDAO.getUserDetailsByQualifiedID(THIRD.toDao()))
                    assertTrue(peer.hasIncompleteMetadata)
                    assertEquals(ConnectionEntity.State.ACCEPTED, peer.connectionStatus)
                    assertEquals(DELETE_TARGET, peer.activeOneOnOneConversationId)
                    assertNotNull(it.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                })
            }
        }
    }

    @Test
    fun acceptedConnectionMissingMLSStaysPendingBeforeWelcome() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                fixture.seedProteusConversation()
                val event = fixture.batch("notification", fixture.connectionEvent("accepted", DELETE_TARGET.value, SENDER),
                    fixture.welcomeEvent(), fixture.event("first", fixture.encrypt("first")))
                fixture.releasePrepared()
                repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, fetchConnectionPeer = fixture::lifecyclePeer)) }
                assertIs<Either.Right<*>>(fixture.open {
                    assertFalse(checkNotNull(it.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertNull(it.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                    assertFalse(checkNotNull(fixture.openMLS(it)).transaction { context -> context.conversationExists(GROUP_ID) })
                })
            }
        }
    }

    @Test
    fun connectionStatesProteus() = connectionStates(false)

    @Test
    fun connectionStatesMLS() = connectionStates(true)

    private fun connectionStates(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedProteusConversation()
                fixture.seedConversationCleanupFiles(DELETE_TARGET)
                val event = fixture.connectionEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                var fetched = 0
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification",
                    fetchConnectionPeer = { fetched++; fixture.connectionProfile(it) })).value
                assertEquals(7, fetched)
                assertEquals(ReceivedNotificationType.CONNECTION_REQUEST, result?.type)
                assertEquals("request", result?.conversationValue)
                assertEquals("Peer request", result?.senderName)
                assertEquals("", result?.messageId)
                assertEquals(true, result?.shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyConnectionState(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun connectionFinalStateAndNotifiedFlag() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val event = fixture.batch("notification", fixture.connectionEvent("pending"), fixture.connectionEvent("cancelled"))
                fixture.releasePrepared()
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification",
                    fetchConnectionPeer = fixture::connectionProfile)).value)
                assertIs<Either.Right<*>>(fixture.open {
                    assertNull(it.database.connectionDAO.getConnection(QualifiedIDEntity("request", SELF.domain)))
                    assertNull(it.database.conversationDAO.getConversationById(QualifiedIDEntity("request", SELF.domain)))
                })
                val pending = fixture.connectionEvent("pending").copy(id = "pending-again")
                assertEquals(true, assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(pending), pending.id,
                    fetchConnectionPeer = fixture::connectionProfile)).value?.shouldNotify)
                assertIs<Either.Right<*>>(fixture.open {
                    it.database.connectionDAO.updateNotificationFlag(false, UserId("peer-request", SELF.domain).toDao())
                })
                val repeat = pending.copy(id = "pending-repeat")
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(repeat), repeat.id,
                    fetchConnectionPeer = fixture::connectionProfile)).value)
            }
        }
    }

    @Test
    fun connectionProfileNotFoundAndRequiredFailure() = runTest {
        withContext(Dispatchers.Default) {
            for (code in listOf(404, 403, 503)) withFixture(false) { fixture ->
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.connectionEvent("pending"), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = fixture.receive(listOf(event), "notification", fetchConnectionPeer = {
                    throw KaliumException.InvalidRequestError(GenericAPIErrorResponse(code, "profile-failure", "Fixture failure"))
                })
                if (code == 404) assertIs<Either.Right<*>>(result) else {
                    assertIs<Either.Left<*>>(result)
                    assertIs<Either.Right<*>>(fixture.open {
                        assertFalse(checkNotNull(it.database.eventDAO.getEventById("notification")).isProcessed)
                        assertNull(it.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(it.database.connectionDAO.getConnection(QualifiedIDEntity("request", SELF.domain)))
                    })
                    assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification", fetchConnectionPeer = fixture::connectionProfile))
                }
                if (code == 404) assertIs<Either.Right<*>>(fixture.open {
                    val peer = checkNotNull(it.database.userDAO.getUserDetailsByQualifiedID(UserId("peer-request", SELF.domain).toDao()))
                    assertTrue(peer.hasIncompleteMetadata)
                    assertEquals(ConnectionEntity.State.PENDING, peer.connectionStatus)
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun connectionTeamProfileNotFoundAndRequiredFailure() = runTest {
        withContext(Dispatchers.Default) {
            for (code in listOf(404, 503)) withFixture(false) { fixture ->
                fixture.sql("UPDATE User SET team = 'team' WHERE qualified_id = '${SELF.toDao()}'")
                val event = fixture.batch("notification", fixture.connectionEvent("pending"), fixture.event("first", fixture.encrypt("first")))
                fixture.releasePrepared()
                var fetched = 0
                val result = fixture.receive(listOf(event), "notification",
                    fetchConnectionPeer = { fixture.connectionProfile(it).copy(teamId = "team") },
                    fetchTeamMembers = {
                        fetched++
                        throw KaliumException.InvalidRequestError(GenericAPIErrorResponse(code, "team-failure", "Fixture failure"))
                    })
                assertEquals(1, fetched)
                if (code == 404) assertIs<Either.Right<*>>(result) else assertIs<Either.Left<*>>(result)
                assertIs<Either.Right<*>>(fixture.open {
                    assertEquals(code == 404, checkNotNull(it.database.eventDAO.getEventById("notification")).isProcessed)
                    if (code == 404) {
                        val peer = checkNotNull(it.database.userDAO.getUserDetailsByQualifiedID(UserId("peer-request", SELF.domain).toDao()))
                        assertTrue(peer.hasIncompleteMetadata)
                        assertEquals(ConnectionEntity.State.PENDING, peer.connectionStatus)
                    } else assertNull(it.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                })
            }
        }
    }

    @Test
    fun connectionRequiredSQLAndFileFailures() = runTest {
        withContext(Dispatchers.Default) {
            for (fileFailure in listOf(false, true)) withFixture(false) { fixture ->
                fixture.seedProteusConversation()
                fixture.seedConversationCleanupFiles(DELETE_TARGET)
                val event = fixture.connectionEvents()
                fixture.releasePrepared()
                if (!fileFailure) fixture.sql("CREATE TRIGGER interrupt_connection BEFORE INSERT ON Connection BEGIN SELECT RAISE(ABORT, 'fixture'); END")
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", fetchConnectionPeer = fixture::connectionProfile,
                    deleteFile = { path -> if (fileFailure) error("Required cleanup failed") else FileSystem.SYSTEM.delete(path.toPath(), false) }))
                assertIs<Either.Right<*>>(fixture.open {
                    assertFalse(checkNotNull(it.database.eventDAO.getEventById("notification")).isProcessed)
                    assertNull(it.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    assertNotNull(it.database.conversationDAO.getConversationById(DELETE_TARGET))
                    assertNotNull(it.database.messageDAO.getMessageById("remove-asset", DELETE_TARGET))
                    fixture.sql("DROP TRIGGER IF EXISTS interrupt_connection")
                })
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification", fetchConnectionPeer = fixture::connectionProfile))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyConnectionState(it) })
            }
        }
    }

    @Test
    fun connectionNativeCommitReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (interruption in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                withFixture(true) { fixture ->
                    fixture.seedProteusConversation()
                    fixture.seedConversationCleanupFiles(DELETE_TARGET)
                    val event = fixture.connectionEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", interruption,
                        fetchConnectionPeer = fixture::connectionProfile))
                    fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                    assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification", fetchConnectionPeer = fixture::connectionProfile))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyConnectionState(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun connectionCancellationAfterDependentTextStaysPending() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedProteusConversation()
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix"), conversationId = DELETE_TARGET),
                    fixture.connectionEvent("cancelled", DELETE_TARGET.value))
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", fetchConnectionPeer = fixture::connectionProfile))
                assertIs<Either.Right<*>>(fixture.open {
                    assertFalse(checkNotNull(it.database.eventDAO.getEventById("notification")).isProcessed)
                    assertNotNull(it.database.conversationDAO.getConversationById(DELETE_TARGET))
                    assertNull(it.database.messageDAO.getMessageById("prefix", DELETE_TARGET))
                })
            }
        }
    }

    @Test
    fun meetingConversationProteus() = runTest { meetingConversationWork(false) }

    @Test
    fun meetingConversationMLS() = runTest { meetingConversationWork(true) }

    @Test
    fun meetingConversationWelcomeFirst() = runTest { meetingConversationWork(true, welcomeFirst = true) }

    @Test
    fun meetingWelcomeWithoutCreationEvent() = runTest { meetingConversationWork(true, welcomeFirst = true, creation = false) }

    @Test
    fun meetingConversationReplay() = runTest {
        for (welcomeFirst in listOf(false, true)) {
            for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                meetingConversationWork(true, welcomeFirst, stage)
            }
        }
    }

    @Test
    fun meetingConversationRequiredFailures() = runTest {
        for (failure in listOf("metadata", "profile", "native", "verification", "INSERT ON Conversation",
            "INSERT ON Member", "INSERT ON MessageSystemContent", "INSERT ON MeetingOccurrence")) {
            meetingConversationWork(true, welcomeFirst = failure == "metadata", failure = failure)
        }
    }

    private suspend fun meetingConversationWork(mls: Boolean, welcomeFirst: Boolean = false,
        stage: Interruption? = null, failure: String? = null, creation: Boolean = true) = withContext(Dispatchers.Default) {
        withFixture(mls, newConversation = true) { fixture ->
            fixture.sql("UPDATE User SET name = NULL WHERE qualified_id = '${SENDER.toDao()}'")
            val event = fixture.meetingConversationEvent(welcomeFirst, creation)
            val tail = fixture.event("tail", fixture.encrypt("tail"))
            val next = fixture.encrypt("next")
            fixture.releasePrepared()
            val sqlFailure = failure?.takeIf { it.startsWith("INSERT") }
            if (sqlFailure != null) fixture.sql(
                "CREATE TRIGGER interrupt_receive BEFORE $sqlFailure BEGIN SELECT RAISE(ABORT, 'meeting creation interrupted'); END")
            if (stage != null || failure != null) {
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, stage,
                    fetchConversation = { if (failure == "metadata") throw KaliumException.NoNetwork() else fixture.meetingConversationResponse() },
                    fetchProfiles = { if (failure == "profile") throw KaliumException.NoNetwork() else fixture.meetingConversationProfiles() },
                    fetchMeeting = fixture::meetingDTO,
                    decorateMLSContext = { context -> object : MlsCoreCryptoContext by context {
                        override suspend fun processWelcomeMessage(message: ByteArray): String {
                            if (failure == "native") error("Meeting welcome interrupted")
                            return context.processWelcomeMessage(message)
                        }
                        override suspend fun getGroupState(groupId: String): com.wire.kalium.cryptography.E2EIConversationState {
                            if (failure == "verification") error("Meeting verification interrupted")
                            return context.getGroupState(groupId)
                        }
                    } }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    if (failure != null) {
                        assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertNull(runtime.database.meetingDao.getMeeting(QualifiedIDEntity("meeting", SELF.domain)))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    }
                })
                if (sqlFailure != null || stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
            }
            var occurrence: String? = null
            var messages = emptyList<MessageEntity>()
            if (stage != null) assertIs<Either.Right<*>>(fixture.open {
                occurrence = fixture.verifyMeetingState(it)
                messages = it.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
            })
            repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id,
                fetchConversation = fixture::meetingConversationResponse, fetchProfiles = fixture::meetingConversationProfiles,
                fetchMeeting = fixture::meetingDTO)) }
            assertIs<Either.Right<*>>(fixture.open {
                fixture.verifyCreation(it, expectMessages = creation, expectedType = ConversationEntity.Type.MEETING)
                val actual = fixture.verifyMeetingState(it)
                if (stage != null) {
                    assertEquals(occurrence, actual)
                    assertEquals(messages, it.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                        .filterNot { message -> message.id == "tail" })
                }
            })
            fixture.verifyStoredAndNext(next, "first", listOf("first", "tail"))
        }
    }

    @Test
    fun meetingConversationCreationBeforeLaterWelcome() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val first = fixture.meetingCreationEvent()
                val later = fixture.batch("notification", fixture.welcomeEvent(), fixture.meetingEvent("create"),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(first), first.id)).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertEquals(ConversationEntity.Type.MEETING, conversation.type)
                    assertEquals(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE,
                        assertIs<ConversationEntity.ProtocolInfo.MLS>(conversation.protocolInfo).groupState)
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
                assertIs<Either.Right<*>>(fixture.receive(listOf(later), later.id, fetchMeeting = fixture::meetingDTO))
                assertIs<Either.Right<*>>(fixture.open {
                    fixture.verifyCreation(it, expectedType = ConversationEntity.Type.MEETING)
                    fixture.verifyMeetingState(it)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun federationDeleteProteus() = runTest { federationWork(false, true) }

    @Test
    fun federationDeleteMLS() = runTest { federationWork(true, true) }

    @Test
    fun federationConnectionRemovedProteus() = runTest { federationWork(false, false) }

    @Test
    fun federationConnectionRemovedMLS() = runTest { federationWork(true, false) }

    @Test
    fun federationReplay() = runTest {
        for (deleted in listOf(false, true)) {
            for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                federationWork(true, deleted, stage = stage, clear = true)
            }
        }
    }

    @Test
    fun federationRequiredFailures() = runTest {
        for (failure in listOf("file", "DELETE ON Connection", "UPDATE ON User WHEN NEW.defederated = 1",
            "DELETE ON Member", "INSERT ON MessageSystemContent WHEN NEW.content_type = 'FEDERATION_TERMINATED'",
            "INSERT ON MessageSystemContent WHEN NEW.content_type = 'MEMBER_CHANGE'")) {
            federationWork(false, true, failure = failure)
        }
    }

    private suspend fun federationWork(mls: Boolean, deleted: Boolean, stage: Interruption? = null,
        failure: String? = null, clear: Boolean = false) = withContext(Dispatchers.Default) {
        withFixture(mls) { fixture ->
            fixture.seedFederationState(deleteRequests = deleted)
            val event = fixture.federationEvents(deleted, clear)
            val tail = fixture.event("tail", fixture.encrypt("tail"))
            val next = fixture.encrypt("next")
            fixture.releasePrepared()
            if (failure != null && failure != "file") fixture.sql(
                "CREATE TRIGGER interrupt_receive BEFORE $failure BEGIN SELECT RAISE(ABORT, 'federation interrupted'); END")
            if (stage != null || failure != null) {
                var deletedFiles = 0
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, stage, deleteFile = {
                    if (failure == "file" && deletedFiles++ == 1) error("File deletion interrupted")
                    FileSystem.SYSTEM.delete(it.toPath(), mustExist = false)
                }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    if (failure != null) {
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNotNull(runtime.database.connectionDAO.getConnection(DELETE_TARGET))
                        assertNotNull(runtime.database.memberDAO.getMemberRole(THIRD.copy(domain = "federated.example").toDao(), CONVERSATION_ID))
                        assertNotNull(runtime.database.assetDAO.getAssetByKey("delete-asset").first()?.dataPath)
                    }
                })
                if ((failure != null && failure != "file") || stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
            }
            var before: List<MessageEntity> = emptyList()
            if (stage != null) assertIs<Either.Right<*>>(fixture.open { before = fixture.verifyFederationState(it, deleted) })
            repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id)) }
            assertIs<Either.Right<*>>(fixture.open {
                val after = fixture.verifyFederationState(it, deleted)
                if (stage != null) assertEquals(before, after)
                if (clear) fixture.verifyPendingConversationCleanup(it, CONVERSATION_ID)
            })
            fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first", "tail"))
        }
    }

    @Test
    fun federationConnectionDeletionNeedsEarlierMetadataReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedFederationState()
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix"), conversationId = DELETE_TARGET),
                    fixture.federationEvent(true))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNotNull(runtime.database.connectionDAO.getConnection(DELETE_TARGET))
                    assertNull(runtime.database.messageDAO.getMessageById("prefix", DELETE_TARGET))
                    assertNull(runtime.database.messageDAO.getMessageById("tail", CONVERSATION_ID))
                    assertTrue(FileSystem.SYSTEM.exists(fixture.messageFile("delete-asset").toPath()))
                })
            }
        }
    }

    @Test
    fun federationInvalidDomainsContinue() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedFederationState()
                val event = fixture.batch("notification", *listOf(emptyList(), listOf("federated.example"),
                    listOf("federated.example", "other.example", SELF.domain)).map { fixture.federationEvent(false, it) }.toTypedArray(),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(4, runtime.database.memberDAO.getConversationMembers(CONVERSATION_ID).size)
                    assertNotNull(runtime.database.connectionDAO.getConnection(DELETE_TARGET))
                    assertFalse(checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(
                        THIRD.copy(domain = "federated.example").toDao())).defederated)
                    assertTrue(runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                        .none { it.content is MessageEntityContent.Federation })
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun federationMemberJoinOrderAndRetry() = runTest {
        withContext(Dispatchers.Default) {
            for (deleted in listOf(false, true)) withFixture(true) { fixture ->
                fixture.seedFederationState()
                val peer = THIRD.copy(domain = "federated.example")
                val event = fixture.batch("notification", fixture.memberJoin("before", peer), fixture.federationEvent(deleted),
                    fixture.memberJoin("after", peer), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNotNull(runtime.database.memberDAO.getMemberRole(peer.toDao(), CONVERSATION_ID))
                    val warnings = runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                    assertEquals(1, warnings.count { it.content is MessageEntityContent.Federation })
                    assertEquals(1, warnings.count { (it.content as? MessageEntityContent.MemberChange)?.memberChangeType ==
                        MessageEntity.MemberChangeType.FEDERATION_REMOVED })
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun meetingEventsProteus() = runTest { meetingWork(false) }

    @Test
    fun meetingEventsMLS() = runTest { meetingWork(true) }

    @Test
    fun meetingEventsReplay() = runTest {
        for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            meetingWork(true, stage = stage)
        }
    }

    @Test
    fun meetingRequiredFailures() = runTest {
        for (failure in listOf("network", "INSERT ON Meeting", "INSERT ON MeetingOccurrence", "DELETE ON Meeting")) {
            meetingWork(false, failure = failure)
        }
    }

    private suspend fun meetingWork(mls: Boolean, stage: Interruption? = null, failure: String? = null) =
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.meetingEvents()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val sqlFailure = failure?.takeUnless { it == "network" }
                if (sqlFailure != null) fixture.sql(
                    "CREATE TRIGGER interrupt_receive BEFORE $sqlFailure BEGIN SELECT RAISE(ABORT, 'meeting interrupted'); END")
                if (stage != null || failure != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, stage,
                        fetchMeeting = { id -> if (failure == "network") throw KaliumException.NoNetwork() else fixture.meetingDTO(id) }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (failure != null) {
                            assertNull(runtime.database.meetingDao.getMeeting(QualifiedIDEntity("meeting", SELF.domain)))
                            assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                            assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        }
                    })
                    if (sqlFailure != null || stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                }
                var occurrenceBefore: String? = null
                if (stage != null) assertIs<Either.Right<*>>(fixture.open {
                    occurrenceBefore = fixture.verifyMeetingState(it)
                })
                repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id, fetchMeeting = fixture::meetingDTO)) }
                assertIs<Either.Right<*>>(fixture.open {
                    val occurrenceAfter = fixture.verifyMeetingState(it)
                    if (occurrenceBefore != null) assertEquals(occurrenceBefore, occurrenceAfter)
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first", "tail"))
            }
        }

    @Test
    fun meetingToleratedFetchResultsContinue() = runTest {
        withContext(Dispatchers.Default) {
            for (mode in listOf("unsupported-api", "not-found", "unsupported-recurrence")) withFixture(false) { fixture ->
                val event = fixture.batch("notification", fixture.meetingEvent("create"), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val initial = fixture.meetingEvent("create").copy(id = "meeting-initial")
                assertIs<Either.Right<*>>(fixture.receive(listOf(initial, event), initial.id, fetchMeeting = fixture::meetingDTO))
                var originalOccurrence: String? = null
                assertIs<Either.Right<*>>(fixture.open {
                    originalOccurrence = fixture.verifyMeetingState(it)
                })
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, fetchMeeting = { id ->
                    when (mode) {
                        "unsupported-api" -> throw com.wire.kalium.network.exceptions.APINotSupported("meeting unavailable")
                        "not-found" -> throw KaliumException.InvalidRequestError(GenericAPIErrorResponse(code = 404, message = "Deleted", label = "meeting-not-found"))
                        else -> fixture.meetingDTO(id).copy(recurrence =
                            com.wire.kalium.network.api.authenticated.meeting.MeetingRecurrenceDTO(
                                com.wire.kalium.network.api.authenticated.meeting.MeetingFrequencyDTO.MONTHLY, 1, null))
                    }
                }))
                assertIs<Either.Right<*>>(fixture.open {
                    assertEquals(originalOccurrence, fixture.verifyMeetingState(it))
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun meetingDeletionAfterCreationPreservesConversationAndReceiving() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.meetingEvent("create"), fixture.meetingEvent("delete"),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT,
                    fetchMeeting = fixture::meetingDTO))
                repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, fetchMeeting = fixture::meetingDTO)) }
                assertIs<Either.Right<*>>(fixture.open {
                    assertNull(it.database.meetingDao.getMeeting(QualifiedIDEntity("meeting", SELF.domain)))
                    assertNull(it.database.meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(
                        QualifiedIDEntity("meeting", SELF.domain), fixture.meetingStart))
                    assertNotNull(it.database.conversationDAO.getConversationById(CONVERSATION_ID))
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun localIdentityEventsProteus() = localIdentity(false)

    @Test
    fun localIdentityEventsMLS() = localIdentity(true)

    @Test
    fun localIdentitySQLAndNativeReplay() = runTest {
        for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            localIdentityWork(true, stage = stage)
        }
    }

    @Test
    fun localIdentityRequiredSQLFailures() = runTest {
        for (trigger in listOf("INSERT ON NewClient", "UPDATE ON User WHEN NEW.deleted = 1", "INSERT ON MessageSystemContent WHEN NEW.content_type = 'MEMBER_CHANGE'")) {
            localIdentityWork(false, sqlFailure = trigger)
        }
    }

    private fun localIdentity(mls: Boolean) = runTest { localIdentityWork(mls) }

    private suspend fun localIdentityWork(mls: Boolean, stage: Interruption? = null, sqlFailure: String? = null) =
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedIdentityState()
                val event = fixture.identityEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (sqlFailure != null) fixture.sql(
                    "CREATE TRIGGER interrupt_receive BEFORE $sqlFailure BEGIN SELECT RAISE(ABORT, 'identity interrupted'); END")
                if (stage != null || sqlFailure != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, stage))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (sqlFailure != null) {
                            assertTrue(runtime.database.newClientDAO.observeNewClients().first().isEmpty())
                            assertFalse(checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(THIRD.toDao())).deleted)
                            assertNotNull(runtime.database.memberDAO.getMemberRole(THIRD.toDao(), CONVERSATION_ID))
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        }
                        if (sqlFailure != null || stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    })
                }
                repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyIdentityState(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first", "tail"))
            }
        }

    @Test
    fun teamAndConversationLeavePreserveBackendOrder() = runTest {
        for (mls in listOf(false, true)) for (teamFirst in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedIdentityState()
                val team = fixture.teamLeave()
                val leave = fixture.memberLeave(member = THIRD, reason = "user-deleted")
                val ordered = if (teamFirst) listOf(team, leave) else listOf(leave, team)
                val event = fixture.batch("notification", *(ordered + fixture.event("first", fixture.encrypt("first"))).toTypedArray())
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyIdentityState(it, client = false) })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun foreignClearBeforeTeamLeavePreservesHistory() = runTest {
        for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(true) { fixture ->
                    fixture.seedIdentityState()
                    fixture.seed(storedText("old"))
                    val clear = fixture.encrypt("clear", MessageContent.Cleared(CONVERSATION_ID.toModel(), TIME, false))
                    val event = fixture.batch("notification", fixture.event("clear", clear), fixture.teamLeave(),
                        fixture.event("first", fixture.encrypt("first")))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    if (stage == Interruption.MARKER) assertIs<Either.Right<*>>(fixture.open { fixture.sql("DROP TRIGGER interrupt_receive") })
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyIdentityState(it, client = false)
                        assertNotNull(it.database.messageDAO.getMessageById("old", CONVERSATION_ID))
                        fixture.verifyPendingConversationCleanup(it, CONVERSATION_ID)
                    })
                    fixture.verifyStoredAndNext(next, "first")
                }
            }
        }
    }

    @Test
    fun ownTeamLeaveKeepsCryptoAndReceiving() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedIdentityState()
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.teamLeave(SELF), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, Interruption.BEFORE_COMMIT))
                repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(SELF.toDao())).deleted)
                    assertNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                    assertNotNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), DELETE_TARGET))
                    assertNull(checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).removedBy)
                })
                // The queue is drained after own team leave, and replay does not select the tail again.
                fixture.verifyProcessedText(tail, "tail")
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first", "tail"))
            }
        }
    }

    @Test
    fun otherUserDeletionPreservesQualifiedIdentityAndHistory() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedIdentityState()
                val event = fixture.batch("notification", fixture.userDelete(THIRD), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyIdentityState(it, client = false, team = false) })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun mlsSettingsProteus() = mlsSettings(false)

    @Test
    fun mlsSettingsMLS() = mlsSettings(true)

    private fun mlsSettings(mls: Boolean) = runTest {
        for (enabled in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMLSSettings()
                val event = fixture.mlsSettingsEvents(enabled)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSSettings(it, enabled) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsSettingsReplayPreservesConfiguration() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSSettings()
                    val event = fixture.mlsSettingsEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSSettings(it) })
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSSettings(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun mlsSettingsRequiredFailuresKeepQueuePending() = runTest {
        for (mls in listOf(false, true)) for (failure in listOf("read", "enable_mls", "default_protocol", "SUPPORTED_PROTOCOLS", "DEFAULT_CIPHER_SUITE", "later")) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSSettings()
                    val event = fixture.mlsSettingsEvents()
                    val tail = fixture.event("tail", fixture.encrypt("tail"))
                    fixture.releasePrepared()
                    if (failure == "read") fixture.sql("UPDATE Metadata SET stringValue = 'invalid-json' WHERE key = 'SUPPORTED_PROTOCOLS'")
                    else {
                        val write = if (failure == "later") "Message WHEN NEW.id = 'first'" else "Metadata WHEN NEW.key = '$failure'"
                        fixture.sql("CREATE TRIGGER interrupt_config BEFORE INSERT ON $write BEGIN SELECT RAISE(ABORT, 'required MLS setting failed'); END")
                    }
                    repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertEquals(mls, runtime.database.userPrefsDAO.isMLSEnabled())
                        assertNull(runtime.database.metadataDAO.valueByKey("default_protocol"))
                        assertEquals(if (mls) listOf(CIPHER.tag) else null, runtime.database.userConfigDAO.getDefaultCipherSuite()?.supported)
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                    })
                    if (failure == "read") fixture.seedMLSSettings() else fixture.sql("DROP TRIGGER interrupt_config")
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSSettings(it) })
                }
            }
        }
    }

    @Test
    fun mlsClientRefreshProteus() = mlsClientRefresh(false)

    @Test
    fun mlsClientRefreshMLS() = mlsClientRefresh(true)

    private fun mlsClientRefresh(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMLSClients()
                val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                var calls = 0
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchSelfClients = { calls++; fixture.selfClients() })).value?.text)
                assertEquals(2, calls)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSClients(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsClientRefreshReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSClients()
                    val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage, fetchSelfClients = { fixture.selfClients() }))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSClients(it) })
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, fetchSelfClients = { fixture.selfClients() })) }
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSClients(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun mlsClientRefreshFailuresKeepQueuePending() = runTest {
        for (mls in listOf(false, true)) for (failure in listOf("network", "migration", "insert", "delete", "later")) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSClients()
                    val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                    val tail = fixture.event("tail", fixture.encrypt("tail"))
                    fixture.releasePrepared()
                    if (failure == "migration") fixture.sql("INSERT INTO Metadata(key, stringValue) VALUES ('MLS_MIGRATION', 'invalid-json')")
                    else if (failure != "network") {
                        val target = when (failure) {
                            "delete" -> "DELETE ON Client WHEN OLD.id = 'obsolete'"
                            "insert" -> "INSERT ON Client WHEN NEW.id = '$CLIENT'"
                            else -> "INSERT ON Message WHEN NEW.id = 'first'"
                        }
                        fixture.sql("CREATE TRIGGER interrupt_config BEFORE $target BEGIN SELECT RAISE(ABORT, 'client refresh failed'); END")
                    }
                    repeat(2) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, fetchSelfClients = {
                            if (failure == "network") throw KaliumException.NoNetwork() else fixture.selfClients()
                        }))
                    }
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertEquals(setOf(CLIENT, "obsolete"), runtime.database.clientDAO.getClientsOfUserByQualifiedID(SELF.toDao()).map { it.id }.toSet())
                        assertEquals(mls, runtime.database.userPrefsDAO.isMLSEnabled())
                        assertNull(runtime.database.metadataDAO.valueByKey("default_protocol"))
                        assertEquals(2, runtime.database.userConfigDAO.getSupportedProtocols()?.size)
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                    })
                    if (failure == "migration") fixture.sql("DELETE FROM Metadata WHERE key = 'MLS_MIGRATION'")
                    else if (failure != "network") fixture.sql("DROP TRIGGER interrupt_config")
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id, fetchSelfClients = { fixture.selfClients() }))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSClients(it) })
                }
            }
        }
    }

    @Test
    fun changedMLSProtocolsBelowSupportedAPIDoesNotFetchClients() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedMLSSettings()
                val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, receivingAPIVersion = 5,
                    fetchSelfClients = { kotlin.test.fail("Old backend must not refresh self protocols") }))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSSettings(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsMigrationSettingsWithoutRefresh() = runTest {
        for (mls in listOf(false, true)) for (phase in listOf("disabled", "future", "started", "missing-times")) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSSettings()
                    val event = fixture.mlsMigrationEvents(phase = phase)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                        fetchSelfClients = { kotlin.test.fail("Unfinished migration only persists settings") }))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSMigration(it, phase) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun mlsMigrationEndedProteus() = mlsMigrationEnded(false)

    @Test
    fun mlsMigrationEndedMLS() = mlsMigrationEnded(true)

    private fun mlsMigrationEnded(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMLSProtocolResolution()
                val event = fixture.mlsMigrationEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val calls = mutableListOf<String>()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                    fetchSelfClients = { calls += "clients"; fixture.selfClients() },
                    uploadSelfProtocols = { assertEquals(com.wire.kalium.network.api.model.SupportedProtocolDTO.entries.toSet(), it.toSet()); calls += "upload" },
                    fetchProfiles = { calls += "profiles"; fixture.protocolProfiles() }))
                assertEquals(listOf("clients", "upload", "profiles", "clients"), calls)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSMigration(it); fixture.verifyMLSProtocolResolution(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsMigrationReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSProtocolResolution()
                    val event = fixture.mlsMigrationEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage,
                        fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {}, fetchProfiles = { fixture.protocolProfiles() }))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSMigration(it); fixture.verifyMLSProtocolResolution(it) })
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                        fetchSelfClients = { fixture.selfClients() },
                        uploadSelfProtocols = { kotlin.test.fail("Self protocols are already canonical") })) }
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSMigration(it); fixture.verifyMLSProtocolResolution(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun mlsMigrationUsesPreviousPolicyAndReplayReconcilesCanonicalPolicy() = runTest {
        for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(true) { fixture ->
                    fixture.seedMLSProtocolResolution()
                    fixture.sql("UPDATE User SET supported_protocols = 'PROTEUS,MLS' WHERE qualified_id = '${SELF.toDao()}'")
                    fixture.sql("UPDATE Metadata SET stringValue = '[\"MLS\"]' WHERE key = 'SUPPORTED_PROTOCOLS'")
                    val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                        fixture.mlsMigrationEvent("ended"), fixture.event("first", fixture.encrypt("first")))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage,
                        fetchSelfClients = { fixture.selfClients() },
                        uploadSelfProtocols = { kotlin.test.fail("First calculation must use previous disabled migration") }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        fixture.verifyMLSMigration(runtime)
                        assertEquals(SupportedProtocolEntity.entries.toSet(), runtime.database.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.supportedProtocols)
                    })
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    var uploads = 0
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                        fetchSelfClients = { fixture.selfClients() },
                        uploadSelfProtocols = { assertEquals(listOf(com.wire.kalium.network.api.model.SupportedProtocolDTO.MLS), it); uploads++ },
                        fetchProfiles = { fixture.protocolProfiles() }))
                    assertEquals(1, uploads)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertEquals(setOf(SupportedProtocolEntity.MLS), runtime.database.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.supportedProtocols)
                        assertEquals(CONVERSATION_ID, runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                        assertNotNull(runtime.database.messageDAO.getMessageById("history", CONVERSATION_ID))
                    })
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun mlsMigrationRequiredFailuresKeepQueuePending() = runTest {
        for (mls in listOf(false, true)) for (failure in listOf("clients", "upload", "profiles", "migration-write", "later")) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSProtocolResolution()
                    val event = fixture.mlsMigrationEvents()
                    val tail = fixture.event("tail", fixture.encrypt("tail"))
                    fixture.releasePrepared()
                    if (failure in listOf("migration-write", "later")) {
                        val target = if (failure == "migration-write") "INSERT ON Metadata WHEN NEW.key = 'MLS_MIGRATION' AND NEW.stringValue LIKE '%2000-01-02%' AND NEW.stringValue LIKE '%true%'"
                            else "INSERT ON Message WHEN NEW.id = 'first'"
                        fixture.sql("CREATE TRIGGER interrupt_config BEFORE $target BEGIN SELECT RAISE(ABORT, 'migration failed'); END")
                    }
                    repeat(2) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id,
                            fetchSelfClients = { if (failure == "clients") throw KaliumException.NoNetwork() else fixture.selfClients() },
                            uploadSelfProtocols = { if (failure == "upload") throw KaliumException.NoNetwork() },
                            fetchProfiles = { if (failure == "profiles") throw KaliumException.NoNetwork() else fixture.protocolProfiles() }))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            val db = runtime.database
                            assertFalse(checkNotNull(db.eventDAO.getEventById(event.id)).isProcessed)
                            assertFalse(checkNotNull(db.eventDAO.getEventById(tail.id)).isProcessed)
                            assertNull(db.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                            assertNull(db.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertNull(db.userConfigDAO.getMigrationConfiguration())
                            assertNull(db.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                            assertEquals(setOf(SupportedProtocolEntity.PROTEUS), db.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.supportedProtocols)
                            assertEquals(setOf(CLIENT, "obsolete"), db.clientDAO.getClientsOfUserByQualifiedID(SELF.toDao()).map { it.id }.toSet())
                            if (mls) assertNotNull(db.messageDAO.getMessageById("history", DELETE_TARGET))
                            assertTrue(db.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        })
                    }
                    if (failure in listOf("migration-write", "later")) fixture.sql("DROP TRIGGER interrupt_config")
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id,
                        fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {}, fetchProfiles = { fixture.protocolProfiles() }))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSMigration(it); fixture.verifyMLSProtocolResolution(it) })
                }
            }
        }
    }

    @Test
    fun mlsMigrationRequiredPreviousPolicyReadKeepsQueuePending() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMLSProtocolResolution()
                fixture.sql("INSERT INTO Metadata(key, stringValue) VALUES ('MLS_MIGRATION', 'invalid-json')")
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.mlsMigrationEvent("ended"), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, fetchSelfClients = { fixture.selfClients() }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertEquals("invalid-json", runtime.database.metadataDAO.valueByKey("MLS_MIGRATION"))
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                    })
                }
                fixture.sql("DELETE FROM Metadata WHERE key = 'MLS_MIGRATION'")
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id, fetchSelfClients = { fixture.selfClients() },
                    uploadSelfProtocols = {}, fetchProfiles = { fixture.protocolProfiles() }))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSMigration(it) })
            }
        }
    }

    @Test
    fun mlsMigrationEndedBelowSupportedAPIOnlyPersistsSettings() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedMLSSettings()
                val event = fixture.mlsMigrationEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, receivingAPIVersion = 5,
                    fetchSelfClients = { kotlin.test.fail("Old backend does not update MLS protocols") }))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSMigration(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsProtocolUploadProteus() = mlsProtocolUpload(false)

    @Test
    fun mlsProtocolUploadMLS() = mlsProtocolUpload(true)

    private fun mlsProtocolUpload(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMLSProtocolResolution()
                val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val calls = mutableListOf<String>()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                    fetchSelfClients = { calls += "clients"; fixture.selfClients() },
                    uploadSelfProtocols = { assertEquals(com.wire.kalium.network.api.model.SupportedProtocolDTO.entries.toSet(), it.toSet()); calls += "upload" },
                    fetchProfiles = { calls += "profiles"; fixture.protocolProfiles() }))
                assertEquals(listOf("clients", "clients", "upload", "profiles"), calls)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSProtocolResolution(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsProtocolUploadReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSProtocolResolution()
                    val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage,
                        fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {}, fetchProfiles = { fixture.protocolProfiles() }))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSProtocolResolution(it) })
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                        fetchSelfClients = { fixture.selfClients() },
                        uploadSelfProtocols = { kotlin.test.fail("Committed self advertisement is already canonical") },
                        fetchProfiles = { kotlin.test.fail("Committed profile resolution is already canonical") })) }
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSProtocolResolution(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun mlsProtocolUploadFailuresKeepQueuePending() = runTest {
        for (mls in listOf(false, true)) for (failure in listOf("upload", "profiles", "self", "profile-write", "active", "later") +
            if (mls) listOf("history", "warning") else emptyList()) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedMLSProtocolResolution()
                    val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                    val tail = fixture.event("tail", fixture.encrypt("tail"))
                    fixture.releasePrepared()
                    if (failure !in listOf("upload", "profiles")) {
                        val target = when (failure) {
                            "self" -> "UPDATE OF supported_protocols ON User WHEN NEW.qualified_id = '${SELF.toDao()}'"
                            "profile-write" -> "INSERT ON User WHEN NEW.qualified_id = '${SENDER.toDao()}'"
                            "active" -> "UPDATE OF active_one_on_one_conversation_id ON User WHEN NEW.qualified_id = '${SENDER.toDao()}'"
                            "history" -> "UPDATE ON Message WHEN OLD.id = 'history'"
                            "warning" -> "INSERT ON Message WHEN NEW.id LIKE '%one-to-one%'"
                            else -> "INSERT ON Message WHEN NEW.id = 'first'"
                        }
                        fixture.sql("CREATE TRIGGER interrupt_config BEFORE $target BEGIN SELECT RAISE(ABORT, 'protocol resolution failed'); END")
                    }
                    repeat(2) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id,
                            fetchSelfClients = { fixture.selfClients() },
                            uploadSelfProtocols = { if (failure == "upload") throw KaliumException.NoNetwork() },
                            fetchProfiles = { if (failure == "profiles") throw KaliumException.NoNetwork() else fixture.protocolProfiles() }))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            val db = runtime.database
                            assertFalse(checkNotNull(db.eventDAO.getEventById(event.id)).isProcessed)
                            assertFalse(checkNotNull(db.eventDAO.getEventById(tail.id)).isProcessed)
                            assertNull(db.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                            assertNull(db.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertNull(db.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                            assertEquals(setOf(SupportedProtocolEntity.PROTEUS), db.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.supportedProtocols)
                            assertEquals(setOf(CLIENT, "obsolete"), db.clientDAO.getClientsOfUserByQualifiedID(SELF.toDao()).map { it.id }.toSet())
                            if (mls) assertNotNull(db.messageDAO.getMessageById("history", DELETE_TARGET))
                            assertTrue(db.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        })
                    }
                    if (failure !in listOf("upload", "profiles")) fixture.sql("DROP TRIGGER interrupt_config")
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id,
                        fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {}, fetchProfiles = { fixture.protocolProfiles() }))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSProtocolResolution(it) })
                }
            }
        }
    }

    @Test
    fun mlsProtocolBatchResolvesAllPeersWithFederationFallback() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedMLSProtocolResolution()
                val second = UserId("second", SELF.domain)
                val groupPeer = UserId("group-only", SELF.domain)
                val foreign = UserId("foreign", "remote.example")
                val secondConversation = QualifiedIDEntity("second-conversation", SELF.domain)
                val group = QualifiedIDEntity("group-conversation", SELF.domain)
                listOf(second, groupPeer, foreign).forEach { fixture.sql("INSERT INTO User(qualified_id, name) VALUES ('${it.toDao()}', 'Before')") }
                fixture.seedProteusConversation(secondConversation)
                fixture.seedProteusConversation(group)
                fixture.sql("UPDATE Member SET user = '${second.toDao()}' WHERE conversation = '$secondConversation' AND user = '${SENDER.toDao()}'")
                fixture.sql("UPDATE Member SET user = '${groupPeer.toDao()}' WHERE conversation = '$group' AND user = '${SENDER.toDao()}'")
                fixture.sql("UPDATE Conversation SET type = 'GROUP' WHERE qualified_id = '$group'")
                val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val requests = mutableListOf<Set<com.wire.kalium.network.api.model.QualifiedID>>()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                    fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {}, fetchProfilesRequest = { request ->
                        val ids = assertIs<com.wire.kalium.network.api.authenticated.userDetails.QualifiedUserIdListRequest>(request).qualifiedIds.toSet()
                        requests += ids
                        if (foreign.toApi() in ids) throw com.wire.kalium.network.exceptions.FederationError(
                            com.wire.kalium.network.api.model.FederationErrorResponse.Generic(400, "no federator configured", "federation-not-enabled", null))
                        ListUsersDTO(usersFound = ids.map { fixture.connectionProfile(it).copy(name = "Protocol peer",
                            supportedProtocols = listOf(com.wire.kalium.network.api.model.SupportedProtocolDTO.PROTEUS)) })
                    }))
                assertEquals(setOf(SENDER, second, groupPeer, foreign).map { it.toApi() }.toSet(), requests[0])
                assertEquals(setOf(SENDER, second, groupPeer).map { it.toApi() }.toSet(), requests[1])
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyMLSProtocolResolution(runtime)
                    val users = runtime.database.userDAO
                    assertEquals(secondConversation, users.getUserDetailsByQualifiedID(second.toDao())?.activeOneOnOneConversationId)
                    assertEquals("Protocol peer", users.getUserDetailsByQualifiedID(groupPeer.toDao())?.name)
                    assertNull(users.getUserDetailsByQualifiedID(groupPeer.toDao())?.activeOneOnOneConversationId)
                    assertEquals("Before", users.getUserDetailsByQualifiedID(foreign.toDao())?.name)
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsProtocolBatchPreservesDeletedAndFailedProfiles() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedMLSProtocolResolution()
                val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                    fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {}, fetchProfiles = {
                        ListUsersDTO(usersFound = listOf(fixture.protocolProfiles().usersFound.single().copy(deleted = true)),
                            usersFailed = listOf(UserId("failed", SELF.domain).toApi()))
                    }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val peer = checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao()))
                    assertTrue(peer.deleted)
                    assertNull(peer.activeOneOnOneConversationId)
                    assertNotNull(runtime.database.messageDAO.getMessageById("history", DELETE_TARGET))
                    // The app insert-or-ignore DAO currently stores false even for failed-profile placeholders.
                    assertFalse(checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(UserId("failed", SELF.domain).toDao())).hasIncompleteMetadata)
                    assertTrue(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsProtocolMalformedProfileRecoveryIsAtomic() = runTest {
        for (failDelete in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedMLSProtocolResolution()
                val malformed = UserId("", SELF.domain)
                fixture.sql("INSERT INTO User(qualified_id, name) VALUES ('${malformed.toDao()}', 'Malformed')")
                val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (failDelete) fixture.sql("CREATE TRIGGER interrupt_config BEFORE DELETE ON User WHEN OLD.qualified_id = '${malformed.toDao()}' " +
                    "BEGIN SELECT RAISE(ABORT, 'malformed deletion failed'); END")
                val requests = mutableListOf<Set<com.wire.kalium.network.api.model.QualifiedID>>()
                val fetch: suspend (ListUserRequest) -> ListUsersDTO = { request ->
                    val ids = assertIs<com.wire.kalium.network.api.authenticated.userDetails.QualifiedUserIdListRequest>(request).qualifiedIds.toSet()
                    requests += ids
                    if (malformed.toApi() in ids) throw KaliumException.InvalidRequestError(
                        com.wire.kalium.network.api.model.GenericAPIErrorResponse(400,
                            "Error in \$['qualified_ids'][1].domain: alphanumeric character: not enough input", "bad-request"))
                    fixture.protocolProfiles()
                }
                if (failDelete) {
                    repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id,
                        fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {}, fetchProfilesRequest = fetch)) }
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(malformed.toDao()))
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertEquals(setOf(SupportedProtocolEntity.PROTEUS), runtime.database.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.supportedProtocols)
                    })
                    fixture.sql("DROP TRIGGER interrupt_config")
                }
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                    fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {}, fetchProfilesRequest = fetch))
                assertEquals(setOf(SENDER.toApi(), malformed.toApi()), requests[0])
                assertEquals(setOf(SENDER.toApi()), requests[1])
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyMLSProtocolResolution(runtime)
                    assertNull(runtime.database.userDAO.getUserDetailsByQualifiedID(malformed.toDao()))
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun mlsProtocolResolutionWithoutNativeCandidateStaysPending() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedMLSProtocolResolution()
                val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id,
                    fetchSelfClients = { fixture.selfClients() }, uploadSelfProtocols = {},
                    fetchProfiles = { ListUsersDTO(usersFound = listOf(fixture.connectionProfile(SENDER.toApi()))) })) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    assertEquals(setOf(SupportedProtocolEntity.PROTEUS), runtime.database.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.supportedProtocols)
                })
            }
        }
    }

    @Test
    fun changedSelfAdvertisementRequiresSuccessfulUpload() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMLSClients()
                fixture.sql("UPDATE User SET supported_protocols = 'PROTEUS' WHERE qualified_id = '${SELF.toDao()}'")
                val event = fixture.mlsSettingsEvents(changeTeamProtocols = true)
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, fetchSelfClients = { fixture.selfClients() })) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    assertEquals(setOf(CLIENT, "obsolete"), runtime.database.clientDAO.getClientsOfUserByQualifiedID(SELF.toDao()).map { it.id }.toSet())
                    assertEquals(2, runtime.database.userConfigDAO.getSupportedProtocols()?.size)
                })
            }
        }
    }

    @Test
    fun changedMLSProtocolsStillRequireTheirEffects() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMLSSettings()
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.mlsSettingsEvent(protocols = "[\"mls\"]"), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertNull(runtime.database.metadataDAO.valueByKey("default_protocol"))
                    assertEquals(2, runtime.database.userConfigDAO.getSupportedProtocols()?.size)
                    assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun preparedCipherChangeWithoutCredentialKeepsQueuePending() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedMLSSettings()
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.mlsSettingsEvent(defaultCipher = 3), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    assertEquals(CIPHER.tag, runtime.database.userConfigDAO.getDefaultCipherSuite()?.default)
                    // Configuring a new default does not manufacture its credential in the native store.
                    val changed = checkNotNull(runtime.mlsCoreCrypto).mlsClient(
                        CryptoQualifiedClientId(CLIENT, SELF.toCrypto()),
                        com.wire.kalium.cryptography.MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519,
                        fixture.transport, object : MLSEpochObserver {
                            override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) = Unit
                        }, runtime.processingScope)
                    assertNull(changed.getCredentialRef(com.wire.kalium.cryptography.CredentialType.Basic))
                    runtime.database.userConfigDAO.setDefaultCipherSuite(SupportedCipherSuiteEntity(listOf(1, 3), 3))
                })
                // A canonical default without its native credential still rejects both reentries.
                repeat(2) {
                    val result = assertIs<Either.Left<AccountRuntimeFailure>>(fixture.open { kotlin.test.fail("Changed prepared cipher entered receiving") })
                    assertEquals(AccountRuntimeFailure.STORAGE, result.value)
                }
            }
        }
    }

    @Test
    fun preparedCipherChangeWithInstalledCredential() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMLSSettings()
                fixture.installChangedCipherCredential()
                val event = fixture.mlsCipherEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSCipher(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun preparedCipherChangeReplayUsesCanonicalDefault() = runTest {
        for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(true) { fixture ->
                    fixture.seedMLSSettings()
                    fixture.installChangedCipherCredential()
                    val event = fixture.mlsCipherEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyMLSCipher(it)
                        assertFalse(checkNotNull(it.database.eventDAO.getEventById(event.id)).isProcessed)
                    })
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun preparedCipherRequiredFailuresKeepQueuePending() = runTest {
        for (failure in listOf("credential", "cipher-write", "later", "unknown")) withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedMLSSettings()
                fixture.installChangedCipherCredential()
                val event = if (failure == "unknown") fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.mlsSettingsEvent(defaultCipher = 999), fixture.event("first", fixture.encrypt("first"))) else fixture.mlsCipherEvents()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (failure in listOf("cipher-write", "later")) fixture.sql("CREATE TRIGGER cipher_failure BEFORE INSERT ON " +
                    (if (failure == "cipher-write") "Metadata WHEN NEW.key = 'DEFAULT_CIPHER_SUITE'" else "MessageTextContent WHEN NEW.message_id = 'first'") +
                    " BEGIN SELECT RAISE(ABORT, 'cipher failure'); END")
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id,
                        hasCredential = if (failure == "credential") ({ error("native credential read failed") }) else null))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertEquals(CIPHER.tag, runtime.database.userConfigDAO.getDefaultCipherSuite()?.default)
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                    })
                }
                if (failure != "unknown") {
                    if (failure != "credential") fixture.sql("DROP TRIGGER cipher_failure")
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMLSCipher(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first", "tail"))
                } else assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun preparedCipherChangeBackAcrossNotifications() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedMLSSettings()
                fixture.installChangedCipherCredential()
                val changed = fixture.mlsCipherEvents()
                val restored = fixture.batch("restored", fixture.mlsSettingsEvent(defaultCipher = 1), fixture.event("tail", fixture.encrypt("tail")))
                fixture.releasePrepared()
                assertEquals("tail", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(changed, restored), restored.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyMLSCipher(runtime, defaultCipher = 1)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(changed.id)).isProcessed)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(restored.id)).isProcessed)
                })
            }
        }
    }

    @Test
    fun typingNoopsProteus() = typingNoops(false)

    @Test
    fun typingNoopsMLS() = typingNoops(true)

    private fun typingNoops(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.typingNoopEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { error("Typing must not fetch metadata") })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(runtime.database.userPrefsDAO.isTypingIndicatorEnabled().first())
                    assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                    for (index in 1..7) assertNull(runtime.database.messageDAO.getMessageById("notification:$index", CONVERSATION_ID))
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun typingNoopsReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.typingNoopEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun typingHasNoPreferenceOrMetadataDependency() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.typingEvent("started", SELF), fixture.typingEvent("stopped", SELF),
                    fixture.typingEvent("started"), fixture.typingEvent("stopped"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = fixture.receive(listOf(event), event.id, decorateProteusContext = { context ->
                    fixture.sql("ALTER TABLE Conversation RENAME TO Conversation_saved")
                    context
                })
                fixture.sql("ALTER TABLE Conversation_saved RENAME TO Conversation")
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun textFailureAfterTypingKeepsQueuePending() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.typingEvent("started"), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                fixture.sql("CREATE TRIGGER interrupt_receive BEFORE INSERT ON Message WHEN NEW.id = 'first' BEGIN SELECT RAISE(ABORT, 'interrupted'); END")
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id))
                    fixture.verifyPendingTextQueue(event, tail)
                }
                fixture.sql("DROP TRIGGER interrupt_receive")
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id))
                fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
            }
        }
    }

    @Test
    fun typingDoesNotBlockText() = runTest {
        for (mls in listOf(false, true)) for (enabled in listOf(null, false, true)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    if (enabled != null) fixture.seedTyping(enabled)
                    val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                        fixture.typingEvent("started"), fixture.typingEvent("stopped"), fixture.event("first", fixture.encrypt("first")))
                    val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertEquals("tail", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), tail.id)).value?.text)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                    })
                    fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
                }
            }
        }
    }

    @Test
    fun typingBeforeLaterEnableCompletesOnReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedTyping(false)
                    val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")), fixture.typingEvent("started"),
                        fixture.propertyEvent("WIRE_TYPING_INDICATOR_MODE", 1), fixture.event("first", fixture.encrypt("first")))
                    val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id, stage))
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertTrue(runtime.database.userPrefsDAO.isTypingIndicatorEnabled().first())
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    })
                    fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
                }
            }
        }
    }

    @Test
    fun memberRolesProteus() = memberRoles(false)

    @Test
    fun memberRolesMLS() = memberRoles(true)

    private fun memberRoles(mls: Boolean, system: Boolean = false) = runTest {
        val kinds = if (system) listOf("member") else listOf("member", "admin", "absent-member", "incomplete")
        for (kind in kinds) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val member = if (kind == "absent-member") THIRD else SENDER
                val target = if (kind == "incomplete") DELETE_TARGET else CONVERSATION_ID
                if (kind == "admin") fixture.sql("UPDATE Member SET role = 'wire_admin' WHERE user = '${SENDER.toDao()}'")
                if (kind == "incomplete") {
                    fixture.seedProteusConversation(target)
                    fixture.sql("UPDATE Conversation SET incomplete_metadata = 1 WHERE qualified_id = '$target'")
                }
                val event = fixture.memberRoleEvents(member, target, system)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { error("Known and incomplete conversations must not fetch") })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMemberRoles(it, member, target, kind == "absent-member") })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun memberRolesReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.memberRoleEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyMemberRoles(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun memberRoleUnknownConversationKeepsReadBeforeFetchOrder() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.sql("DELETE FROM Conversation WHERE qualified_id = '$CONVERSATION_ID'")
                    val event = fixture.batch("notification", fixture.memberRoleEvent("wire_admin"), fixture.memberRoleEvent("wire_member"),
                        fixture.event("first", fixture.encrypt("first")))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    var fetches = 0
                    val first = fixture.receive(listOf(event), event.id, stage, fetchConversation = { fetches++; fixture.creationResponse() })
                    if (stage == null) assertIs<Either.Right<*>>(first) else {
                        assertIs<Either.Left<*>>(first)
                        assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, fetchConversation = { error("Canonical conversation exists") }))
                    }
                    assertEquals(1, fetches)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertFalse(conversation.hasIncompleteMetadata)
                        assertEquals("Created group", conversation.name)
                        assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member,
                            runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                        // Fetch returns Admin, but the app read null before that fetch: promotion is required.
                        fixture.verifyPromotion(runtime, "notification", SENDER)
                        assertNull(runtime.database.messageDAO.getMessageById("notification:1", CONVERSATION_ID))
                        if (mls) assertEquals(ConversationEntity.GroupState.ESTABLISHED,
                            assertIs<ConversationEntity.ProtocolInfo.MLS>(conversation.protocolInfo).groupState)
                    })
                    fixture.verifyStoredAndNext(next, "first")
                }
            }
        }
    }

    @Test
    fun memberRolesRequiredFailures() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf("read", "write", "promotion", "fetch")) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val target = if (stage == "fetch") DELETE_TARGET else CONVERSATION_ID
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.memberRoleEvent("wire_admin", target = target), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (stage in listOf("write", "promotion")) fixture.sql(if (stage == "write")
                    "CREATE TRIGGER interrupt_receive BEFORE UPDATE OF role ON Member BEGIN SELECT RAISE(ABORT, 'interrupted'); END"
                    else "CREATE TRIGGER interrupt_receive BEFORE INSERT ON MessageSystemContent WHEN NEW.content_type = 'MEMBER_CHANGE' BEGIN SELECT RAISE(ABORT, 'interrupted'); END")
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id, decorateProteusContext = { context ->
                        if (stage == "read") fixture.sql("ALTER TABLE Member RENAME TO Member_saved")
                        context
                    }, fetchConversation = { error("Required metadata fetch failed") }))
                    if (stage == "read") fixture.sql("ALTER TABLE Member_saved RENAME TO Member")
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member,
                            runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                    })
                }
                if (stage in listOf("write", "promotion")) fixture.sql("DROP TRIGGER interrupt_receive")
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id, fetchConversation = {
                    fixture.creationResponse().copy(id = target.toApi(), groupId = null, epoch = null, protocol = ConvProtocol.PROTEUS)
                }))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyPromotion(it, "notification:1", SENDER, target) })
                fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
            }
        }
    }

    @Test
    fun memberPromotionDeletionAndClearReplay() = runTest {
        for (mls in listOf(false, true)) for (mode in listOf("delete", "clear-before", "clear-after")) withContext(Dispatchers.Default) {
            withFixture(mls, if (mode == "delete") SELF else SENDER) { fixture ->
                val clear = if (mode == "delete") null else fixture.event("clear", fixture.encrypt("clear",
                    MessageContent.Cleared(CONVERSATION_ID.toModel(), TIME, false)))
                val promotion = fixture.memberRoleEvent("wire_admin")
                val event = when (mode) {
                    "delete" -> fixture.batch("notification", promotion, fixture.event("delete", fixture.encrypt("delete", MessageContent.DeleteMessage("notification"))),
                        fixture.event("first", fixture.encrypt("first")))
                    "clear-before" -> fixture.batch("notification", checkNotNull(clear), promotion, fixture.event("first", fixture.encrypt("first")))
                    else -> fixture.batch("notification", promotion, checkNotNull(clear), fixture.event("first", fixture.encrypt("first")))
                }
                val id = if (mode == "clear-before") "notification:1" else "notification"
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val message = runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID)
                    if (mode == "delete") {
                        assertEquals(MessageEntity.Visibility.DELETED, checkNotNull(message).visibility)
                    } else {
                        fixture.verifyPromotion(runtime, id, SENDER)
                        fixture.verifyPendingConversationCleanup(runtime, CONVERSATION_ID)
                    }
                    assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin,
                        runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                })
                fixture.verifyStoredAndNext(next, "first", if (mode == "delete") emptyList() else listOf("first"))
            }
        }
    }

    @Test
    fun memberRoleRefreshAndLeaveReplaysRequiredRemoval() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.memberRoleEvent("wire_admin"), fixture.memberJoin(member = SELF), fixture.memberLeave(member = SENDER),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                suspend fun receive(stage: Interruption? = null) = fixture.receive(listOf(event), event.id, stage,
                    fetchConversation = { fixture.creationResponse() }, fetchProfiles = {
                        ListUsersDTO(usersFound = listOf(fixture.connectionProfile(SELF.toApi()), fixture.connectionProfile(SENDER.toApi())))
                    })
                assertIs<Either.Left<*>>(receive(Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(receive())
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                    fixture.verifyPromotion(runtime, "notification:1", SENDER)
                    assertEquals(MessageEntity.MemberChangeType.REMOVED, assertIs<MessageEntityContent.MemberChange>(
                        assertIs<MessageEntity.System>(runtime.database.messageDAO.getMessageById("notification:3", CONVERSATION_ID)).content).memberChangeType)
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun unchangedAdminBeforeDemotionOrLeaveCompletesOnReplay() = runTest {
        for (mls in listOf(false, true)) for (leave in listOf(false, true)) {
            for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                withContext(Dispatchers.Default) {
                    withFixture(mls) { fixture ->
                        fixture.sql("UPDATE Member SET role = 'wire_admin' WHERE user = '${SENDER.toDao()}'")
                        val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                            fixture.memberRoleEvent("wire_admin"),
                            if (leave) fixture.memberLeave(member = SENDER) else fixture.memberRoleEvent("wire_member"),
                            fixture.event("first", fixture.encrypt("first")))
                        val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                        val next = fixture.encrypt("next")
                        fixture.releasePrepared()
                        if (stage != null) {
                            assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id, stage))
                            assertIs<Either.Right<*>>(fixture.open { runtime ->
                                assertNull(runtime.database.messageDAO.getMessageById("notification:1", CONVERSATION_ID))
                                assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                            })
                            if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                        }
                        assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            val role = runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID)
                            if (leave) assertNull(role) else assertEquals(
                                com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member, role)
                            // Retry may add a promotion because the original unchanged role is no longer stored.
                            if (stage == null) assertNull(runtime.database.messageDAO.getMessageById("notification:1", CONVERSATION_ID))
                            else fixture.verifyPromotion(runtime, "notification:1", SENDER)
                            assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        })
                        assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), tail.id)).value)
                        fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
                    }
                }
            }
        }
    }

    @Test
    fun accessBeforeUnknownRoleFetchReappliesFieldsOnReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.sql("DELETE FROM Conversation WHERE qualified_id = '$CONVERSATION_ID'")
                    val event = fixture.batch("notification", fixture.accessEvent(false), fixture.memberRoleEvent("wire_admin"),
                        fixture.event("first", fixture.encrypt("first")))
                    val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    var fetches = 0
                    suspend fun receive(interruption: Interruption? = null) = fixture.receive(listOf(event, tail), tail.id, interruption,
                        fetchConversation = { fetches++; fixture.creationResponse() })
                    if (stage != null) {
                        assertIs<Either.Left<*>>(receive(stage))
                        if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    }
                    assertIs<Either.Right<*>>(receive())
                    assertEquals(1, fetches)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        // First access has no row to update. Retry reapplies the event to the fetched row.
                        assertEquals(stage == null, ConversationEntity.AccessRole.SERVICE in conversation.accessRole)
                        assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin,
                            runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                        fixture.verifyPromotion(runtime, "notification:1", SENDER)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    })
                    fixture.verifyStoredAndNext(next, "tail", listOf("first", "tail"))
                }
            }
        }
    }

    @Test
    fun ignoredMemberUpdateHasNoMetadataDependency() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.memberRoleEvent(null, target = DELETE_TARGET),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, fetchConversation = { error("Ignored member update must not fetch") }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                    assertNull(runtime.database.messageDAO.getMessageById("notification", DELETE_TARGET))
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun accessChangesProteus() = accessChanges(false)

    @Test
    fun accessChangesMLS() = accessChanges(true)

    private fun accessChanges(mls: Boolean) = runTest {
        for (enabled in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedAccess(!enabled)
                val event = fixture.accessEvents(enabled)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyAccess(it, enabled) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun accessChangesReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.accessEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    val canonical = assertIs<Either.Right<List<MessageEntity>>>(fixture.open { fixture.verifyAccess(it) }).value
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertEquals(canonical.map { it.id to it.date }, fixture.verifyAccess(runtime).map { it.id to it.date })
                    })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun accessChangesRequiredFailures() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf("read", "fields", "message")) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.accessEvents()
                val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (stage != "read") fixture.sql(if (stage == "fields")
                    "CREATE TRIGGER interrupt_receive BEFORE UPDATE OF access_list ON Conversation BEGIN SELECT RAISE(ABORT, 'interrupted'); END"
                    else "CREATE TRIGGER interrupt_receive BEFORE INSERT ON MessageSystemContent WHEN NEW.content_type = 'CONVERSATION_APPS_ENABLED_CHANGED' BEGIN SELECT RAISE(ABORT, 'interrupted'); END")
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id, decorateProteusContext = { context ->
                        if (stage == "read") fixture.sql("ALTER TABLE Conversation RENAME TO Conversation_saved")
                        context
                    }))
                    if (stage == "read") fixture.sql("ALTER TABLE Conversation_saved RENAME TO Conversation")
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertTrue(checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).accessRole.isEmpty())
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                    })
                }
                if (stage != "read") fixture.sql("DROP TRIGGER interrupt_receive")
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyAccess(it) })
                fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
            }
        }
    }

    @Test
    fun accessChangesCanonicalDeletionAndClearReplay() = runTest {
        for (mls in listOf(false, true)) for (mode in listOf("delete", "clear-before", "clear-after")) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val clear = if (mode == "delete") null else fixture.event("clear", fixture.encrypt("clear",
                    MessageContent.Cleared(CONVERSATION_ID.toModel(), TIME, false)))
                val first = fixture.accessEvent(true)
                val event = when (mode) {
                    "delete" -> fixture.batch("notification", first, fixture.event("delete", fixture.encrypt("delete", MessageContent.DeleteMessage("notification"))),
                        fixture.event("first", fixture.encrypt("first")))
                    "clear-before" -> fixture.batch("notification", checkNotNull(clear), first, fixture.event("first", fixture.encrypt("first")))
                    else -> fixture.batch("notification", first, checkNotNull(clear), fixture.event("first", fixture.encrypt("first")))
                }
                val id = if (mode == "clear-before") "notification:1" else "notification"
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                val previous = assertIs<Either.Right<MessageEntity?>>(fixture.open { it.database.messageDAO.getMessageById(id, CONVERSATION_ID) }).value
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val message = runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID)
                    assertEquals(previous?.date, message?.date)
                    if (mode == "delete") {
                        assertEquals(MessageEntity.Visibility.DELETED, checkNotNull(message).visibility)
                    } else {
                        // Access messages use local handling time, which is later than this historical cutoff.
                        assertEquals(MessageEntityContent.ConversationAppsAccessChanged(true), assertIs<MessageEntity.System>(message).content)
                        fixture.verifyPendingConversationCleanup(runtime, CONVERSATION_ID)
                    }
                    assertTrue(ConversationEntity.AccessRole.SERVICE in checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).accessRole)
                })
                fixture.verifyStoredAndNext(next, "first", listOf("first"))
            }
        }
    }

    @Test
    fun unchangedAccessAndMissingMetadata() = runTest {
        for (mls in listOf(false, true)) for (kind in listOf("same-final", "absent", "incomplete")) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val target = if (kind == "same-final") CONVERSATION_ID else DELETE_TARGET
                if (kind == "incomplete") {
                    fixture.seedProteusConversation(target)
                    fixture.sql("UPDATE Conversation SET incomplete_metadata = 1 WHERE qualified_id = '$target'")
                }
                val changes = if (kind == "same-final") listOf(fixture.accessEvent(true), fixture.accessEvent(false)) else emptyList()
                val event = fixture.batch("notification", *(listOf(fixture.accessEvent(false, target = target)) + changes +
                    fixture.event("first", fixture.encrypt("first"))).toTypedArray())
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("notification", target))
                    if (kind == "absent") assertNull(runtime.database.conversationDAO.getConversationById(target)) else {
                        val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(target))
                        assertEquals(listOf(ConversationEntity.Access.CODE, ConversationEntity.Access.INVITE), conversation.access)
                        assertEquals(listOf(ConversationEntity.AccessRole.TEAM_MEMBER, ConversationEntity.AccessRole.GUEST), conversation.accessRole)
                        assertEquals(kind == "incomplete", conversation.hasIncompleteMetadata)
                    }
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun unchangedAccessBeforeOppositeValueCompletesOnReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                        fixture.accessEvent(false), fixture.accessEvent(true), fixture.event("first", fixture.encrypt("first")))
                    val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    if (stage != null) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id, stage))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            assertNull(runtime.database.messageDAO.getMessageById("notification:1", CONVERSATION_ID))
                            assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        })
                        if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    }
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertTrue(ConversationEntity.AccessRole.SERVICE in
                            checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).accessRole)
                        // Retry may add a message for the first, originally unchanged access event.
                        if (stage == null) assertNull(runtime.database.messageDAO.getMessageById("notification:1", CONVERSATION_ID))
                        else assertEquals(MessageEntityContent.ConversationAppsAccessChanged(false), assertIs<MessageEntity.System>(
                            runtime.database.messageDAO.getMessageById("notification:1", CONVERSATION_ID)).content)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    })
                    assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), tail.id)).value)
                    fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
                }
            }
        }
    }

    @Test
    fun roomSettingsProteus() = roomSettings(false)

    @Test
    fun roomSettingsMLS() = roomSettings(true)

    private fun roomSettings(mls: Boolean) = runTest {
        for (final in listOf("fallback", "uri", "delete")) for (targetKind in listOf("existing", "incomplete", "absent")) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val target = if (targetKind == "existing") CONVERSATION_ID else DELETE_TARGET
                    if (targetKind != "absent") fixture.seedRoomSettings(target, incomplete = targetKind == "incomplete")
                    val event = fixture.roomSettingsEvents(final, target)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyRoomSettings(it, final, target, absent = targetKind == "absent") })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun roomSettingsReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedRoomSettings()
                    val event = fixture.roomSettingsEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyRoomSettings(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun roomSettingsRequiredFailures() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf("permission", "code", "delete")) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    fixture.seedRoomSettings()
                    val event = fixture.roomSettingsEvents()
                    val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    val column = if (stage == "permission") "channel_add_permission" else "guest_room_link"
                    val condition = if (stage == "delete") "WHEN NEW.guest_room_link IS NULL" else ""
                    fixture.sql("CREATE TRIGGER interrupt_receive BEFORE UPDATE OF $column ON Conversation $condition " +
                        "BEGIN SELECT RAISE(ABORT, 'interrupted'); END")
                    repeat(2) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                            assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                            assertEquals(ConversationEntity.ChannelAddPermission.ADMINS,
                                runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.channelAddPermission)
                            assertEquals("https://initial.example", runtime.database.conversationDAO.observeGuestRoomLinkByConversationId(
                                CONVERSATION_ID).first()?.link)
                            assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                            assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        })
                    }
                    fixture.sql("DROP TRIGGER interrupt_receive")
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyRoomSettings(it) })
                    fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
                }
            }
        }
    }

    @Test
    fun roomSettingsBeforeConversationDeletionReplay() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedRoomSettings(DELETE_TARGET)
                val event = fixture.batch("notification", fixture.roomCodeEvent(target = DELETE_TARGET),
                    fixture.roomPermissionEvent(target = DELETE_TARGET), fixture.deletedConversationEvent(),
                    fixture.roomCodeEvent(id = "after-delete", target = DELETE_TARGET), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyRoomSettings(it, target = DELETE_TARGET, absent = true) })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun ignoredHandRaiseDoesNotBlockFollowingMessages() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) {
                withFixture(mls) { fixture ->
                    val event = fixture.batch("notification",
                        fixture.event("raise", fixture.encrypt("raise", MessageContent.InCallHandRaise(true))),
                        fixture.event("lower", fixture.encrypt("lower", MessageContent.InCallHandRaise(false))),
                        fixture.event("first", fixture.encrypt("first")))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    val result = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(
                        fixture.receive(listOf(event), event.id)).value)
                    assertTrue(result.shouldNotify)
                    assertNull(fixture.receivedAppWork)
                    assertTrue(fixture.receivedCalls.isEmpty())
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        for (id in listOf("raise", "lower")) {
                            assertNull(runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID))
                        }
                    })
                    fixture.verifyStoredAndNext(next, "first")
                }
            }
        }
    }

    @Test
    fun ignoredRemoteMuteProteus() = ignoredRemoteMute(false)

    @Test
    fun ignoredRemoteMuteMLS() = ignoredRemoteMute(true)

    private fun ignoredRemoteMute(mls: Boolean) = runTest {
        for (own in listOf(false, true)) for (mode in listOf("non-admin", "admin", "null", "other-user", "other-client", "other-domain", "target", "reaction")) {
            withContext(Dispatchers.Default) {
                withFixture(mls, senderId = if (own) SELF else SENDER) { fixture ->
                    fixture.seedRemoteMute(mode, own)
                    val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                        fixture.event("mute", fixture.encrypt("mute", if (mode == "reaction") MessageContent.InCallEmoji(mapOf("👍" to 1)) else fixture.remoteMute(mode))),
                        fixture.event("first", fixture.encrypt("first")))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    val result = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value)
                    assertEquals(!own, result.shouldNotify)
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyIgnoredRemoteMute(it) })
                    fixture.verifyStoredAndNext(next, "first", if (own) emptyList() else listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun ignoredRemoteMuteReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.ignoredRemoteMuteEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyIgnoredRemoteMute(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun remoteMuteBeforeAdminRefreshCompletesOnReplay() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.event("mute", fixture.encrypt("mute", fixture.remoteMute())),
                    fixture.memberJoin(member = SELF), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                suspend fun receive(stage: Interruption? = null) = fixture.receive(listOf(event, tail), tail.id, stage,
                    fetchConversation = { fixture.creationResponse() },
                    fetchProfiles = { ListUsersDTO(usersFound = listOf(fixture.connectionProfile(SELF.toApi()), fixture.connectionProfile(SENDER.toApi()))) })
                assertIs<Either.Left<*>>(receive(Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(receive())
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(runtime.database.memberDAO.isMemberAdmin(CONVERSATION_ID, SENDER.toDao()))
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    fixture.verifyIgnoredRemoteMute(runtime)
                })
                fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
            }
        }
    }

    @Test
    fun incomingCallsSurviveProcessedTargetUntilHostHandoff() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val now = Clock.System.now()
                val call = fixture.event("call", fixture.encrypt("call", fixture.callContent()), time = now)
                val text = fixture.event("notification", fixture.encrypt("first"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(call, text), text.id)).value?.text)
                val result = fixture.receivedCalls.single()
                assertEquals(ReceivedCallType.INCOMING, result.type)
                assertEquals(CONVERSATION_ID.value, result.conversationValue)
                assertEquals(CONVERSATION_ID.domain, result.conversationDomain)
                assertEquals(SENDER.value, result.callerValue)
                assertEquals(SENDER.domain, result.callerDomain)
                assertEquals(SENDER_CLIENT, result.callerClientId)
                assertEquals(now.epochSeconds, result.messageTimeSeconds)
                assertTrue(result.shouldRing)
                assertEquals("Fixture", result.callerName)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(call.id)).isProcessed)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(text.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("call", CONVERSATION_ID))
                    assertEquals(listOf("first"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), text.id)).value)
                assertEquals(result.handoffId, fixture.receivedCalls.single().handoffId)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val journal = CallSignalJournal(runtime.database)
                    val signal = journal.signals().single()
                    assertEquals("call", signal.messageId)
                    assertEquals(result.handoffId, signal.id)
                    journal.submitted(signal.id, "test-native-generation")
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), text.id)).value)
                assertTrue(fixture.receivedCalls.isEmpty())
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    // Enqueue ACK suppresses duplicate wakes, but a new engine can reconstruct.
                    assertEquals("test-native-generation", CallSignalJournal(runtime.database).signals().single().submittedGeneration)
                })
                fixture.verifyStoredAndNext(next, "first", listOf("first"))
            }
        }
    }

    @Test
    fun incomingCallPresentationSuppressesOwnAccountAndPreservesSignaling() = runTest {
        val callers = listOf(SELF.toDao(), SENDER.toDao(), QualifiedIDEntity(SELF.value, "other.example"))
        for (mls in listOf(false, true)) for (caller in callers) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val event = ReceivedCallEvent(
                        fixture.callContent().value, CONVERSATION_ID, caller, SENDER_CLIENT,
                        Clock.System.now(), true, mls, "own-device-start"
                    )
                    val journal = CallSignalJournal(runtime.database)
                    val signal = journal.append(event)
                    repeat(2) {
                        val result = evaluateCallingEvents(listOf(event), SELF, CLIENT, runtime.database).single()
                        assertEquals(ReceivedCallType.INCOMING, result.type)
                        assertEquals(caller != SELF.toDao(), result.shouldRing, "caller=$caller mls=$mls")
                        assertEquals(signal.id, result.handoffId)
                        assertEquals(listOf(signal), journal.signals())
                        assertTrue(journal.presentations().isEmpty())
                    }
                })
            }
        }
    }

    @Test
    fun incomingCallPresentationHonorsMutedConversationAndAwayStatus() = runTest {
        for (mls in listOf(false, true)) for (away in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.event("call", fixture.encrypt("call", fixture.callContent()), time = Clock.System.now())
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    if (away) runtime.database.userDAO.updateUserAvailabilityStatus(SELF.toDao(),
                        com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity.AWAY)
                    else runtime.database.conversationDAO.updateConversationMutedStatus(CONVERSATION_ID,
                        com.wire.kalium.persistence.dao.conversation.ConversationEntity.MutedStatus.ALL_MUTED, Clock.System.now())
                })
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertFalse(fixture.receivedCalls.single().shouldRing)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(1, CallSignalJournal(runtime.database).signals().size)
                })
            }
        }
    }

    @Test
    fun callJournalSurvivesMarkerFailureAndRollsBackWithReceiveEffects() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.event("call", fixture.encrypt("call", fixture.callContent()), time = Clock.System.now())
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, interruption = stage))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertEquals(if (stage == Interruption.BEFORE_COMMIT) 0 else 1, CallSignalJournal(runtime.database).signals().size)
                    })
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    val id = fixture.receivedCalls.single().handoffId
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertEquals(id, CallSignalJournal(runtime.database).signals().single().id)
                    })
                }
            }
        }
    }

    @Test
    fun sameSecondNSEAndFullEngineReferencesKeepTheFirstStartIdentity() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val now = Clock.System.now()
                val time = kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilliseconds() / 1000 * 1000)
                val notification = fixture.batch("notification",
                    fixture.event("first-start", fixture.encrypt("first-start", fixture.callContent()), time = time),
                    fixture.event("repeated-start", fixture.encrypt("repeated-start", fixture.callContent()), time = time))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(notification), notification.id))
                val incoming = fixture.receivedCalls.filter { it.type == ReceivedCallType.INCOMING && it.shouldRing }
                assertTrue(incoming.isNotEmpty())
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val journal = CallSignalJournal(runtime.database)
                    val first = journal.signals().first()
                    incoming.forEach { call ->
                        val reference = IncomingCallReference(
                            com.wire.kalium.persistence.dao.QualifiedIDEntity(call.conversationValue, call.conversationDomain),
                            com.wire.kalium.persistence.dao.QualifiedIDEntity(call.callerValue, call.callerDomain),
                            call.callerClientId, call.messageTimeSeconds,
                        )
                        assertEquals(first.id, call.handoffId)
                        assertEquals(call.handoffId, journal.incoming(reference, true, false, "engine-one")?.id)
                        assertEquals(call.handoffId, journal.incoming(reference, true, false, "engine-one")?.id)
                        assertEquals(call.handoffId, journal.incoming(reference, true, false, "engine-two")?.id)
                    }
                    assertEquals(1, journal.presentations().size)
                })
            }
        }
    }

    @Test
    fun callPresentationReplayPreservesTerminalTombstonesAndNextCallIdentity() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val first = fixture.event("start-one", fixture.encrypt("start-one", fixture.callContent()), time = Clock.System.now())
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(first), first.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val journal = CallSignalJournal(runtime.database)
                    val original = journal.signals().single()
                    suspend fun incoming(generation: String, seconds: Long) = journal.incoming(
                        IncomingCallReference(CONVERSATION_ID, SENDER.toDao(), SENDER_CLIENT, seconds),
                        true, false, generation)
                    val one = checkNotNull(incoming("engine-one", original.timeMillis / 1000))
                    journal.closed(CONVERSATION_ID.value, CONVERSATION_ID.domain, 5)
                    val next = journal.append(ReceivedCallEvent(original.payload, CONVERSATION_ID, SENDER.toDao(), SENDER_CLIENT,
                        kotlin.time.Instant.fromEpochMilliseconds(original.timeMillis + 2000), false, false, "start-two"))
                    val two = checkNotNull(incoming("engine-one", next.timeMillis / 1000))
                    assertTrue(one.id != two.id)
                    assertTrue(two.ringing)
                    // A new full engine replays old signals in order. Old incoming is still ended.
                    assertFalse(checkNotNull(incoming("engine-two", original.timeMillis / 1000)).ringing)
                    journal.closed(CONVERSATION_ID.value, CONVERSATION_ID.domain, 5)
                    assertTrue(checkNotNull(incoming("engine-two", next.timeMillis / 1000)).ringing)
                    assertTrue(journal.presentations().last().ringing)
                    assertEquals(two.id, journal.presentations().last().id)
                })
            }
        }
    }

    @Test
    fun callingStartAndEndAcrossNotificationsProduceOneMissedCallback() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val now = Clock.System.now()
                val start = fixture.event("start", fixture.encrypt("start", fixture.callContent()), time = now)
                val end = fixture.batch("notification",
                    fixture.event("end", fixture.encrypt("end", fixture.callContent("CONFEND")), time = now),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(start, end), end.id))
                assertEquals(listOf(ReceivedCallType.MISSED), fixture.receivedCalls.map { it.type })
                assertFalse(fixture.receivedCalls.single().shouldRing)
                assertTrue(fixture.receivedCalls.single().shouldNotify)
                val missedId = fixture.receivedCalls.single().handoffId
                assertNotNull(missedId)
                assertIs<Either.Right<*>>(fixture.receive(listOf(start, end), end.id))
                assertEquals(missedId, fixture.receivedCalls.single().handoffId)
                assertFalse(fixture.receivedCalls.single().shouldNotify)
                fixture.verifyStoredAndNext(next, "first", listOf("first"))
            }
        }
    }

    @Test
    fun readingDuringRingingDoesNotHideTheLaterMissedCall() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (dateColumn in listOf("last_read_date", "last_notified_date")) withFixture(mls) { fixture ->
                val now = Clock.System.now()
                val startTime = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 10_000)
                fixture.sql("UPDATE Conversation SET $dateColumn = ${now.toEpochMilliseconds() - 5_000}")
                val start = fixture.event("start", fixture.encrypt("start", fixture.callContent()), time = startTime)
                val end = fixture.event("end", fixture.encrypt("end", fixture.callContent("CONFEND")), time = now)
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(start, end), end.id))
                assertEquals(ReceivedCallType.MISSED, fixture.receivedCalls.single().type)
                assertTrue(fixture.receivedCalls.single().shouldNotify)
            }
        }
    }

    @Test
    fun expiredCallingDoesNotHideLaterIncomingCallAndDegradedNamesStayPrivate() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.sql("UPDATE Conversation SET proteus_verification_status = 'DEGRADED', verification_status = 'DEGRADED'")
                val event = fixture.batch("notification",
                    fixture.event("expired", fixture.encrypt("expired", fixture.callContent(session = "expired"))),
                    fixture.event("incoming", fixture.encrypt("incoming", fixture.callContent()), time = Clock.System.now()),
                    fixture.event("first", fixture.encrypt("first")))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertEquals(listOf(ReceivedCallType.INCOMING), fixture.receivedCalls.map { it.type })
                assertNull(fixture.receivedCalls.single().callerName)
            }
        }
    }

    @Test
    fun interruptedCallingDoesNotEvaluateUncommittedEvents() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification",
                    fixture.event("incoming", fixture.encrypt("incoming", fixture.callContent()), time = Clock.System.now()),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, interruption = Interruption.BEFORE_COMMIT))
                assertTrue(fixture.receivedCalls.isEmpty())
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                })
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertEquals(listOf(ReceivedCallType.INCOMING), fixture.receivedCalls.map { it.type })
                fixture.verifyStoredAndNext(next, "first", listOf("first"))
            }
        }
    }

    @Test
    fun callingUsesAuthenticatedCarrierUnlessSentByOwnDevice() = runTest {
        for (mls in listOf(false, true)) for (own in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls, senderId = if (own) SELF else SENDER) { fixture ->
                fixture.seedProteusConversation()
                val content = fixture.callContent().copy(conversationId = DELETE_TARGET.toModel())
                val event = fixture.event("notification", fixture.encrypt("call", content), time = Clock.System.now())
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertEquals(if (own) DELETE_TARGET else CONVERSATION_ID, fixture.receivedCallEvents.single().conversationId)
                assertEquals(mls, fixture.receivedCallEvents.single().isMLS)
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun selfDeviceCallingTargetRetainsDestructiveReplayGuard() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls, senderId = SELF) { fixture ->
                fixture.seedProteusConversation()
                val content = fixture.callContent().copy(conversationId = DELETE_TARGET.toModel())
                val event = fixture.batch("notification",
                    fixture.event("call", fixture.encrypt("call", content), time = Clock.System.now()),
                    fixture.deletedConversationEvent())
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id))
                    assertTrue(fixture.receivedCalls.isEmpty())
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNotNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    })
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun malformedCallingIsConsumedWithItsNotification() = runTest {
        for (mls in listOf(false, true)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val content = MessageContent.Calling("{")
                    val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                        fixture.event("calling", fixture.encrypt("calling", content)), fixture.event("first", fixture.encrypt("first")))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertNotNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    })
                    assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
                }
            }
        }
    }

    @Test
    fun nativeX509RequiresPkiEnvironment() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, x509 = true) { fixture ->
                val first = fixture.encrypt("first")
                fixture.releasePrepared()
                fixture.probeMissingPki(first)
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(
                    listOf(fixture.event("notification", first)), "notification")).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyX509(it) })
            }
        }
    }

    @Test
    fun x509ReceivingUsesStoredPkiForBothCredentialTypes() = runTest {
        for (basicSelf in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(true, x509 = true, basicSelf = basicSelf) { fixture ->
                val event = fixture.x509Events()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value)
                assertEquals("first", result.text)
                assertTrue(result.mayPreview)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyX509(it) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun x509ReplayRestoresPkiAndCanonicalState() = runTest {
        for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(true, x509 = true) { fixture ->
                    val event = fixture.x509Events()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyX509(it) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun x509RequiredFailuresKeepQueuePending() = runTest {
        for (failure in listOf("identity", "sql")) withContext(Dispatchers.Default) {
            withFixture(true, x509 = true) { fixture ->
                val event = fixture.x509Events()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (failure == "sql") fixture.sql("CREATE TRIGGER x509_failure BEFORE INSERT ON MessageTextContent " +
                    "WHEN NEW.message_id = 'first' BEGIN SELECT RAISE(ABORT, 'x509 SQL failed'); END")
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, decorateMLSContext = { real ->
                        if (failure != "identity") real else object : MlsCoreCryptoContext by real {
                            override suspend fun getUserIdentities(groupId: MLSGroupId, users: List<com.wire.kalium.cryptography.CryptoQualifiedID>)
                                : Map<String, List<com.wire.kalium.cryptography.WireIdentity>> = error("X509 identity read failed")
                        }
                    }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        assertEquals(1uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    })
                }
                if (failure == "sql") fixture.sql("DROP TRIGGER x509_failure")
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id))
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first", "tail"))
            }
        }
    }

    @Test
    fun irrelevantProtocolVerificationDoesNotHideText() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val column = if (mls) "proteus_verification_status" else "verification_status"
                fixture.sql("UPDATE Conversation SET $column = 'DEGRADED'")
                val event = fixture.event("notification", fixture.encrypt("first"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value
                assertTrue(checkNotNull(result).mayPreview)
                assertTrue(result.shouldNotify)
                assertEquals("first", result.text)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun relevantDegradedVerificationShowsText() = previewPolicyMatchesAndroid(mixed = false, ephemeral = false)

    @Test
    fun mixedDegradedVerificationShowsText() = previewPolicyMatchesAndroid(mixed = true, ephemeral = false)

    @Test
    fun ephemeralContentHidesText() = previewPolicyMatchesAndroid(mixed = false, ephemeral = true)

    private fun previewPolicyMatchesAndroid(mixed: Boolean, ephemeral: Boolean) = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                if (mixed) fixture.sql("UPDATE Conversation SET protocol = 'MIXED'")
                val column = if (mls) "verification_status" else "proteus_verification_status"
                fixture.sql("UPDATE Conversation SET $column = 'DEGRADED'")
                val event = fixture.event("notification", fixture.encrypt("first",
                    expiresAfterMillis = if (ephemeral) 60_000L else null))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(listOf(event), event.id)).value)
                assertEquals(!ephemeral, result.mayPreview)
                assertEquals("first", result.text)
                if (!ephemeral) assertNull(result.previewPolicyReason)
                assertTrue(result.shouldNotify)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun x509CreationWelcomeAndFirstText() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true, x509 = true) { fixture ->
                val page = fixture.creationPage(true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "notification")).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyX509(it) })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun ownedCredentialCheckPersistsPeerRevocation() = runTest {
        for (proxy in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(true, x509 = true) { fixture ->
                val event = fixture.x509Events()
                val next = fixture.encrypt("next")
                fixture.x509Control("revoke=peer")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.configureX509Proxy(runtime, proxy)
                    fixture.verifyX509(runtime)
                    checkNotNull(runtime.mlsCoreCrypto).checkCredentials()
                    // The native environment retains its prior cached status until re-created.
                    // The following receiving attempt must load the newly persisted CRL.
                    fixture.verifyX509(runtime)
                })
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value
                assertTrue(checkNotNull(result).mayPreview)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyX509(runtime, revoked = true)
                    assertEquals(ConversationEntity.VerificationStatus.DEGRADED,
                        checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).mlsVerificationStatus)
                    assertFalse(runtime.database.conversationDAO.observeDegradedConversationNotified(CONVERSATION_ID).first())
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun ownedCredentialCheckPersistsSelfRevocation() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, x509 = true) { fixture ->
                fixture.x509Control("revoke=self")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.openMLS(runtime)
                    checkNotNull(runtime.mlsCoreCrypto).checkCredentials()
                })
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val client = checkNotNull(fixture.openMLS(runtime))
                    val self = checkNotNull(client.getUserIdentities(GROUP_ID, listOf(SELF.toCrypto()))[SELF.value]).single()
                    assertEquals(com.wire.kalium.cryptography.CryptoCertificateStatus.REVOKED, self.status)
                })
            }
        }
    }

    @Test
    fun ownedCredentialCheckFailureAndCancellationAllowReopen() = runTest {
        for (mode in listOf("failure", "slow")) withContext(Dispatchers.Default) {
            withFixture(true, x509 = true) { fixture ->
                val event = fixture.x509Events()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                fixture.x509Control("mode=$mode")
                if (mode == "slow") {
                    assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
                        kotlinx.coroutines.withTimeout(500) {
                            fixture.open { runtime ->
                                fixture.openMLS(runtime)
                                checkNotNull(runtime.mlsCoreCrypto).checkCredentials()
                            }
                        }
                    }
                } else assertIs<Either.Left<*>>(fixture.open { runtime ->
                    fixture.openMLS(runtime)
                    checkNotNull(runtime.mlsCoreCrypto).checkCredentials()
                })
                fixture.x509Control("mode=valid")
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyX509(runtime)
                    checkNotNull(runtime.mlsCoreCrypto).checkCredentials()
                })
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun e2eiSettingsProteus() = e2eiSettings(false)

    @Test
    fun e2eiSettingsMLS() = e2eiSettings(true)

    private fun e2eiSettings(mls: Boolean) = runTest {
        for (required in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.e2eiEvents(required)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyE2EISettings(it, required) })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun e2eiSettingsReplayRetainsGraceAndNotificationTime() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.e2eiEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    val before = assertIs<Either.Right<Pair<Long?, Long?>>>(fixture.open { fixture.verifyE2EISettings(it) }).value
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    repeat(2) { assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id)) }
                    assertEquals(before, assertIs<Either.Right<Pair<Long?, Long?>>>(fixture.open { fixture.verifyE2EISettings(it) }).value)
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun e2eiSettingsRequiredFailuresKeepQueuePending() = runTest {
        for (mls in listOf(false, true)) for (failure in listOf("read", "settings", "notification", "later")) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.e2eiEvents()
                    val tail = fixture.event("tail", fixture.encrypt("tail"))
                    fixture.releasePrepared()
                    if (failure == "read") fixture.sql("INSERT INTO Metadata(key, stringValue) VALUES ('end_to_end_identity_settings', 'invalid-json')")
                    else {
                        val write = when (failure) {
                            "settings" -> "INSERT ON Metadata WHEN NEW.key = 'end_to_end_identity_settings'"
                            "notification" -> "INSERT ON Metadata WHEN NEW.key = 'end_to_end_identity_notification_time'"
                            else -> "INSERT ON Message WHEN NEW.id = 'first'"
                        }
                        fixture.sql("CREATE TRIGGER interrupt_e2ei BEFORE $write BEGIN SELECT RAISE(ABORT, 'required E2EI write failed'); END")
                    }
                    repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id)) }
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertNull(runtime.database.userPrefsDAO.getE2EINotificationTime())
                        assertEquals(if (failure == "read") "invalid-json" else null,
                            runtime.database.metadataDAO.valueByKey("end_to_end_identity_settings"))
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        if (failure == "read") fixture.sql("DELETE FROM Metadata WHERE key = 'end_to_end_identity_settings'")
                        else fixture.sql("DROP TRIGGER interrupt_e2ei")
                    })
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyE2EISettings(it) })
                }
            }
        }
    }

    @Test
    fun e2eiSettingsChangesInSeparateNotificationsContinue() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val first = fixture.e2eiEvents()
                val second = fixture.batch("disabled", fixture.e2eiEvent(required = false), fixture.event("first", fixture.encrypt("disabled")))
                val third = fixture.batch("different-url", fixture.e2eiEvent(url = "https://discovery.example/changed"),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(first), first.id))
                val before = assertIs<Either.Right<Pair<Long?, Long?>>>(fixture.open { fixture.verifyE2EISettings(it) }).value
                assertIs<Either.Right<*>>(fixture.receive(listOf(second), second.id))
                val disabled = assertIs<Either.Right<Pair<Long?, Long?>>>(fixture.open { fixture.verifyE2EISettings(it, required = false) }).value
                assertEquals(before.second, disabled.second)
                assertIs<Either.Right<*>>(fixture.receive(listOf(third), third.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val settings = checkNotNull(runtime.database.userPrefsDAO.getE2EISettings())
                    assertTrue(settings.status)
                    assertEquals("https://discovery.example/changed", settings.discoverUrl)
                    assertEquals(before.second, runtime.database.userPrefsDAO.getE2EINotificationTime())
                    assertEquals(listOf("prefix", "first", "disabled"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun distinctE2eiSettingsInOneNotificationNeedReplayableDeadline() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.e2eiEvent(url = "https://discovery.example/changed"), fixture.e2eiEvent())
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                repeat(2) { assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id)) }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNull(runtime.database.userPrefsDAO.getE2EISettings())
                    assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    val original = com.wire.kalium.logic.data.featureConfig.E2EIModel(
                        com.wire.kalium.logic.data.featureConfig.E2EIConfigModel("https://discovery.example/e2ei", 3600, true, "https://crl.example/"),
                        com.wire.kalium.logic.data.featureConfig.Status.ENABLED)
                    val changed = original.copy(config = original.config.copy(discoverUrl = "https://discovery.example/changed"))
                    suspend fun persistSettings() = runtime.database.writeMessageEffects {
                        val repo = com.wire.kalium.logic.configuration.UserConfigDataSource(prefs, config, lazy { ReceivingOptions().fileRestrictionState })
                        val handler = com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler(repo)
                        handler.handle(changed, requireSettings = true).orThrow()
                        handler.handle(original, requireSettings = true).orThrow()
                        prefs.getE2EISettings()?.gracePeriodEndMs
                    }
                    var committedGrace: Long? = null
                    // Run the app's actual settings policy across a SQL/native gap. No payload-local
                    // deadline survives in canonical storage after another identity overwrites it.
                    val interrupted = assertFailsWith<com.wire.kalium.cryptography.exceptions.ProteusException> {
                        runtime.proteusCoreCrypto.proteusClient().transaction("e2ei-deadline-probe") {
                            committedGrace = persistSettings()
                            error("native commit interrupted after SQL")
                        }
                    }
                    assertIs<IllegalStateException>(interrupted.cause)
                    kotlinx.coroutines.delay(10)
                    assertTrue(checkNotNull(persistSettings()) > checkNotNull(committedGrace))
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun terminalIdentityEventsRetainRecoveryStoresAndBlockReceiving() = runTest {
        for (mls in listOf(false, true)) for (currentClient in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val terminal = if (currentClient) fixture.clientRemove(CLIENT) else fixture.userDelete(SELF)
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")), terminal,
                    fixture.event("after-terminal", fixture.encrypt("after-terminal")))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val account = appleAccountPath(fixture.root, SELF)
                val file = "$account/storage/files/terminal-retained.bin".toPath()
                FileSystem.SYSTEM.createDirectories(checkNotNull(file.parent))
                FileSystem.SYSTEM.write(file) { writeUtf8("retained fixture asset") }
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id))
                assertEquals(NotificationAppWork.ACCOUNT_INVALIDATION, fixture.receivedAppWork)
                repeat(2) {
                    assertEquals(Either.Left(AccountRuntimeFailure.NOT_PREPARED), fixture.receive(emptyList(), event.id))
                }
                val lease = assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(fixture.root).tryAcquire(SELF)).value
                try {
                    val database = com.wire.kalium.persistence.db.existingUserDatabaseBuilder(
                        "$account/storage", SELF.toDao(), Dispatchers.Default, fixture.databaseSecret)
                    try {
                        assertFalse(checkNotNull(database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(database.eventDAO.getEventById(tail.id)).isProcessed)
                        for (id in listOf("prefix", "after-terminal", "tail")) {
                            assertNull(database.messageDAO.getMessageById(id, CONVERSATION_ID))
                        }
                        assertTrue(database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        assertEquals(CLIENT, ClientRegistrationStorageImpl(database.metadataDAO).getRegisteredClientId())
                        assertTrue(FileSystem.SYSTEM.exists(file))
                        assertTrue(FileSystem.SYSTEM.exists("$account/proteus".toPath()))
                        if (mls) assertTrue(FileSystem.SYSTEM.exists("$account/mls/$CLIENT/keystore/keystore".toPath()))
                    } finally { database.close() }
                } finally { lease.release() }
            }
        }
    }


    @Test
    fun conversationClearAppliesHistoricalCutoffProteus() = conversationCleanup(false, CleanupMode.CLEAR)

    @Test
    fun conversationClearAppliesHistoricalCutoffMLS() = conversationCleanup(true, CleanupMode.CLEAR)

    @Test
    fun conversationLocalDeleteFromBothSelfConversationProtocols() = runTest {
        for (mls in listOf(false, true)) conversationCleanupWork(mls, CleanupMode.LOCAL_DELETE)
    }

    @Test
    fun conversationBackendDeleteContinuesBothProtocols() = runTest {
        for (mls in listOf(false, true)) conversationCleanupWork(mls, CleanupMode.BACKEND_DELETE)
    }

    @Test
    fun conversationClearAndDeletionReplay() = runTest {
        for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            conversationCleanupWork(true, CleanupMode.CLEAR, stage)
            for (mls in listOf(false, true)) conversationCleanupWork(mls, CleanupMode.LOCAL_DELETE, stage)
            conversationCleanupWork(true, CleanupMode.BACKEND_DELETE, stage)
        }
    }

    @Test
    fun conversationCleanupRequiredFailures() = runTest {
        conversationCleanupWork(true, CleanupMode.BACKEND_DELETE, fileFailure = true)
        for (mode in CleanupMode.entries) {
            conversationCleanupWork(false, mode, sqlFailure = true)
        }
    }

    private enum class CleanupMode { CLEAR, LOCAL_DELETE, BACKEND_DELETE }

    private fun conversationCleanup(mls: Boolean, mode: CleanupMode) = runTest { conversationCleanupWork(mls, mode) }

    private suspend fun conversationCleanupWork(
        mls: Boolean,
        mode: CleanupMode,
        interruption: Interruption? = null,
        fileFailure: Boolean = false,
        sqlFailure: Boolean = false,
    ) = withContext(Dispatchers.Default) {
        withFixture(mls, senderId = if (mode == CleanupMode.BACKEND_DELETE) SENDER else SELF) { fixture ->
            if (mode != CleanupMode.BACKEND_DELETE) fixture.seedDeleteForMeTarget()
            else if (mode == CleanupMode.BACKEND_DELETE) fixture.seedProteusConversation()
            val target = DELETE_TARGET
            fixture.seedConversationCleanupFiles(target)
            val event = fixture.conversationCleanupEvent(mode, target)
            val tail = fixture.event("tail", fixture.encrypt("tail"))
            val next = fixture.encrypt("next")
            fixture.releasePrepared()
            if (sqlFailure) {
                val operation = if (mode == CleanupMode.BACKEND_DELETE) "DELETE ON Conversation" else "INSERT ON PendingActions"
                fixture.sql("CREATE TRIGGER interrupt_receive BEFORE $operation BEGIN SELECT RAISE(ABORT, 'cleanup interrupted'); END")
            }
            if (interruption != null || fileFailure || sqlFailure) {
                var deleted = 0
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption, deleteFile = { path ->
                    if (fileFailure && ++deleted == 2) error("Required conversation file cleanup failed")
                    FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
                }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    if (fileFailure || sqlFailure) {
                        assertNotNull(runtime.database.conversationDAO.getConversationById(target))
                        assertNotNull(runtime.database.messageDAO.getMessageById("remove-asset", target))
                        assertNotNull(runtime.database.assetDAO.getAssetByKey("delete-asset").first())
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertEquals(listOf("remove-asset"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20).map { it.messageId })
                        assertTrue(runtime.database.pendingActionDAO.getByActionType(PendingActionType.CLEAR_CONVERSATION_CONTENT).isEmpty())
                        assertTrue(runtime.database.pendingActionDAO.getByActionType(PendingActionType.DELETE_CONVERSATION_LOCALLY).isEmpty())
                    }
                    fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                })
            }
            val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id)).value
            if (interruption == null && mode == CleanupMode.BACKEND_DELETE) {
                assertEquals(ReceivedNotificationType.CONVERSATION_DELETED, checkNotNull(result).type)
                assertTrue(result.shouldNotify)
                assertNotNull(result.conversationName)
                assertNotNull(result.senderName)
            }
            assertIs<Either.Right<*>>(fixture.open { runtime ->
                if (mode != CleanupMode.CLEAR) fixture.verifyConversationCleanup(runtime, target, deleted = true)
                else {
                    fixture.verifyHistoricalCutoffCleanup(runtime, target)
                    if (mode == CleanupMode.CLEAR) assertNotNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                }
                assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
            })
            assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
            fixture.verifyProcessedText(tail, "tail")
            assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
        }
    }

    @Test
    fun conversationClearUsesEarlierContentOrEnvelopeCutoff() = runTest {
        for (contentAfterEnvelope in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(false, senderId = SELF) { fixture ->
                fixture.seedDeleteForMeTarget()
                fixture.seed(
                    storedText("old").copy(conversationId = DELETE_TARGET, date = TIME - 1.seconds),
                    storedText("newer").copy(conversationId = DELETE_TARGET, date = TIME + 1.seconds),
                )
                val contentTime = if (contentAfterEnvelope) TIME + 2.seconds else TIME
                val envelopeTime = if (contentAfterEnvelope) TIME else TIME + 2.seconds
                val event = fixture.batch("notification", fixture.event("clear", fixture.encrypt("clear",
                    MessageContent.Cleared(DELETE_TARGET.toModel(), contentTime, false)), envelopeTime))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("old", DELETE_TARGET))
                    assertNotNull(runtime.database.messageDAO.getMessageById("newer", DELETE_TARGET))
                    fixture.verifyPendingConversationCleanup(runtime, DELETE_TARGET)
                })
            }
        }
    }

    @Test
    fun appClearRecoveryPreservesLaterMessageAtTheSameTimestamp() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false, senderId = SELF) { fixture ->
                fixture.seedDeleteForMeTarget()
                fixture.seed(storedText("old").copy(conversationId = DELETE_TARGET))
                val event = fixture.batch("notification", fixture.event("clear", fixture.encrypt("clear",
                    MessageContent.Cleared(DELETE_TARGET.toModel(), TIME, false))),
                    fixture.event("later", fixture.encrypt("later"), conversationId = DELETE_TARGET))
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val cleanup = PendingConversationCleanup(runtime.database) {
                        FileSystem.SYSTEM.delete(it.toPath(), mustExist = false)
                    }
                    assertTrue(cleanup.recover(CONVERSATION_ID, "clear", DELETE_TARGET))
                    assertNull(runtime.database.messageDAO.getMessageById("old", DELETE_TARGET))
                    assertNotNull(runtime.database.messageDAO.getMessageById("later", DELETE_TARGET))
                })
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id))
            }
        }
    }

    @Test
    fun legacyConversationCleanupRequestsAreDiscardedWithoutDeletion() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedDeleteForMeTarget()
                // The old producer stored TIME although the user's cutoff was TIME - 2 seconds.
                // Deleting through createdAt would wrongly remove this message.
                fixture.seed(storedText("after-original-cutoff").copy(date = TIME - 1.seconds))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.pendingActionDAO.upsert(
                        PendingActionType.CLEAR_CONVERSATION_CONTENT, CONVERSATION_ID, TIME.toEpochMilliseconds()
                    )
                    runtime.database.pendingActionDAO.upsert(
                        PendingActionType.DELETE_CONVERSATION_LOCALLY, DELETE_TARGET, TIME.toEpochMilliseconds()
                    )
                    PendingConversationCleanup(runtime.database) { error("Legacy requests must not delete files") }
                        .discardLegacyRequests()
                    assertNotNull(runtime.database.messageDAO.getMessageById("after-original-cutoff", CONVERSATION_ID))
                    assertNotNull(runtime.database.messageDAO.getMessageById("delete-me", DELETE_TARGET))
                    assertNotNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                    assertTrue(runtime.database.pendingActionDAO.getByActionType(
                        PendingActionType.CLEAR_CONVERSATION_CONTENT
                    ).isEmpty())
                    assertTrue(runtime.database.pendingActionDAO.getByActionType(
                        PendingActionType.DELETE_CONVERSATION_LOCALLY
                    ).isEmpty())
                })
            }
        }
    }

    @Test
    fun conversationLocalDeletePreservesLaterSameTargetPayload() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls, senderId = SELF) { fixture ->
                fixture.seedDeleteForMeTarget()
                val event = fixture.batch(
                    "notification",
                    fixture.event("clear", fixture.encrypt("clear",
                        MessageContent.Cleared(DELETE_TARGET.toModel(), TIME, true))),
                    fixture.event("later-target", fixture.encrypt("later-target", protocolMLS = false),
                        protocolMLS = false, conversationId = DELETE_TARGET),
                )
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNotNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                    assertNull(runtime.database.messageDAO.getMessageById("delete-me", DELETE_TARGET))
                    assertNotNull(runtime.database.messageDAO.getMessageById("later-target", DELETE_TARGET))
                    fixture.verifyPendingConversationCleanup(runtime, DELETE_TARGET, deleted = true)
                })
            }
        }
    }

    @Test
    fun conversationLocalMLSDeleteRetriesNativeCleanupBoundaries() = runTest {
        for (checkpoint in listOf<String?>(null, "cleanup-before-commit", "cleanup-after-commit")) {
            withContext(Dispatchers.Default) {
                withFixture(true, senderId = SELF) { fixture ->
                    fixture.seedMLSDeleteForMeTarget()
                    val event = fixture.batch("notification", fixture.event("clear", fixture.encrypt("clear",
                        MessageContent.Cleared(DELETE_TARGET.toModel(), TIME, true))),
                        fixture.event("first", fixture.encrypt("first")))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    var interrupted = false
                    val result = fixture.receive(listOf(event), event.id, transactionCheckpoint = { current ->
                        if (!interrupted && current == checkpoint) {
                            interrupted = true
                            error("Interrupt local MLS cleanup at $current")
                        }
                    })
                    if (checkpoint == null) assertIs<Either.Right<*>>(result)
                    else {
                        assertTrue(interrupted)
                        assertIs<Either.Left<*>>(result)
                        assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    }
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                        assertNull(runtime.database.messageDAO.getMessageById("delete-me", DELETE_TARGET))
                        assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction {
                            it.conversationExists(RESET_GROUP_ID)
                        })
                        assertTrue(checkNotNull(fixture.openMLS(runtime)).transaction {
                            it.conversationExists(GROUP_ID)
                        })
                        assertTrue(runtime.database.pendingActionDAO.getByActionType(
                            PendingActionType.WIPE_MLS_GROUP
                        ).isEmpty())
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    })
                    assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
                }
            }
        }
    }

    @Test
    fun conversationClearSenderMatrix() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (selfSender in listOf(false, true)) {
                for (selfCarrier in listOf(false, true)) for (removeLocally in listOf(false, true)) {
                    withFixture(mls, senderId = if (selfSender) SELF else SENDER) { fixture ->
                        if (selfCarrier) fixture.seedDeleteForMeTarget()
                        val target = if (selfCarrier) DELETE_TARGET else CONVERSATION_ID
                        fixture.seedConversationCleanupFiles(target)
                        val accepted = selfSender && selfCarrier
                        val clear = fixture.encrypt("clear", MessageContent.Cleared(target.toModel(), TIME, removeLocally))
                        val first = fixture.encrypt("first")
                        val next = fixture.encrypt("next")
                        fixture.releasePrepared()
                        val event = fixture.batch("notification", fixture.event("clear", clear), fixture.event("first", first))
                        repeat(2) {
                            assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, deleteFile = { path ->
                                check(accepted) { "Rejected clear must not delete files" }
                                FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
                            }))
                            assertIs<Either.Right<*>>(fixture.open { runtime ->
                                if (accepted) fixture.verifyConversationCleanup(runtime, target, deleted = removeLocally)
                                else {
                                    fixture.verifyHistoricalCutoffCleanup(runtime, target)
                                    assertNull(runtime.database.metadataDAO.valueByKey(clearReplayKey(CONVERSATION_ID, "clear", target)))
                                }
                                assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                                assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                            })
                        }
                        assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
                    }
                }
            }
        }
    }

    @Test
    fun conversationClearIgnoresMissingTarget() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (removeLocally in listOf(false, true)) {
                withFixture(mls, senderId = SELF) { fixture ->
                    fixture.seedDeleteForMeTarget()
                    val target = QualifiedIDEntity("missing", SELF.domain)
                    val event = fixture.batch("notification", fixture.event("clear", fixture.encrypt("clear",
                        MessageContent.Cleared(target.toModel(), TIME, removeLocally))), fixture.event("first", fixture.encrypt("first")))
                    fixture.releasePrepared()
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                        deleteFile = { error("Missing target must not delete files") }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.metadataDAO.valueByKey(clearReplayKey(CONVERSATION_ID, "clear", target)))
                        fixture.verifyPendingConversationCleanup(runtime, target)
                        assertNotNull(runtime.database.messageDAO.getMessageById("delete-me", DELETE_TARGET))
                        assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    })
                }
            }
        }
    }

    @Test
    fun foreignClearDoesNotBlockNativeReplayDependencies() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seed(storedText("protected"))
                val clear = fixture.encrypt("clear", MessageContent.Cleared(CONVERSATION_ID.toModel(), TIME, false), protocolMLS = false)
                val first = fixture.encrypt("first")
                val event = fixture.batch("notification", fixture.event("clear", clear, protocolMLS = false), fixture.event("first", first))
                fixture.releasePrepared()
                repeat(2) {
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNotNull(runtime.database.messageDAO.getMessageById("protected", CONVERSATION_ID))
                        assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        fixture.verifyPendingConversationCleanup(runtime, CONVERSATION_ID)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    })
                }
            }
        }
    }

    @Test
    fun foreignClearBeforeMLSVerificationPreservesHistory() = runTest {
        withContext(Dispatchers.Default) {
            for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) withFixture(true) { fixture ->
                fixture.seed(storedText("protected"))
                val clear = fixture.encrypt("clear", MessageContent.Cleared(CONVERSATION_ID.toModel(), TIME, false))
                val commit = fixture.commit()
                val first = fixture.encrypt("first")
                val event = fixture.batch("notification", fixture.event("clear", clear), fixture.event("commit", commit), fixture.event("first", first))
                val next = fixture.encrypt("next")
                fixture.sql("UPDATE Conversation SET verification_status = 'VERIFIED'")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                })
                repeat(2) {
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNotNull(runtime.database.messageDAO.getMessageById("protected", CONVERSATION_ID))
                        fixture.verifyPendingConversationCleanup(runtime, CONVERSATION_ID)
                        assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertEquals(MessageEntityContent.ConversationDegradedMLS, checkNotNull(runtime.database.messageDAO.getMessageById(
                            "${com.wire.kalium.logic.data.event.metadataSystemMessageId(event.id, 1)}:mls-verification", CONVERSATION_ID)).content)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertEquals(2uL, checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                            .protocolInfo.let { it as ConversationEntity.ProtocolInfo.MLSCapable }.epoch)
                    })
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun foreignClearBeforeMemberLeavePreservesHistory() = runTest {
        withContext(Dispatchers.Default) {
            for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) withFixture(false) { fixture ->
                fixture.seed(storedText("protected"))
                val clear = fixture.encrypt("clear", MessageContent.Cleared(CONVERSATION_ID.toModel(), TIME, false))
                val event = fixture.batch("notification", fixture.event("clear", clear), fixture.memberLeave(member = SENDER),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                })
                repeat(2) {
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNotNull(runtime.database.messageDAO.getMessageById("protected", CONVERSATION_ID))
                        fixture.verifyPendingConversationCleanup(runtime, CONVERSATION_ID)
                        assertNull(runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                        assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertEquals(MessageEntityContent.MemberChange(listOf(SENDER.toDao()), MessageEntity.MemberChangeType.REMOVED),
                            checkNotNull(runtime.database.messageDAO.getMessageById(
                                com.wire.kalium.logic.data.event.metadataSystemMessageId(event.id, 1), CONVERSATION_ID)).content)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    })
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun conversationLocalDeleteRejectsItsOwnSelfCarrier() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (removeLocally in listOf(false, true)) withFixture(mls, senderId = SELF) { fixture ->
                fixture.seedDeleteForMeTarget()
                fixture.seed(storedText("protected"))
                val event = fixture.batch("notification", fixture.event("clear", fixture.encrypt("clear",
                    MessageContent.Cleared(CONVERSATION_ID.toModel(), TIME, removeLocally))), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                repeat(2) {
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id, deleteFile = { error("Rejected clear must not delete files") }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertNotNull(runtime.database.messageDAO.getMessageById("protected", CONVERSATION_ID))
                        assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        fixture.verifyPendingConversationCleanup(runtime, CONVERSATION_ID, deleted = true)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    })
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun conversationDeletionMetadataDependenciesStayPending() = runTest {
        withContext(Dispatchers.Default) {
            for (variant in listOf("prefix", "creation-after-prefix", "recreate")) {
                withFixture(false) { fixture ->
                    fixture.sql("UPDATE Conversation SET type = 'GROUP' WHERE qualified_id = '$CONVERSATION_ID'")
                    val protected = storedText("protected")
                    fixture.seed(protected)
                    val first = fixture.encrypt("first")
                    val deleted = fixture.deletedConversationEvent(CONVERSATION_ID)
                    val event = when (variant) {
                        "prefix" -> fixture.batch("notification", fixture.event("first", first), deleted)
                        "creation-after-prefix" -> fixture.batch("notification", fixture.event("first", first),
                            fixture.creationEvent(), deleted)
                        "recreate" -> fixture.batch("notification", deleted,
                            fixture.creationEvent(response = fixture.creationResponse().copy(protocol = ConvProtocol.PROTEUS, groupId = null)),
                            fixture.event("first", first))
                        else -> fixture.batch("notification", deleted)
                    }
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertNotNull(runtime.database.messageDAO.getMessageById("protected", CONVERSATION_ID))
                    })
                }
            }
        }
    }

    @Test
    fun mlsConversationDeletionUnblocksUnrelatedNotifications() = mlsConversationDeletionRoundTrip(null)

    @Test
    fun mlsConversationDeletionRetriesAfterSQLCommitNativeAbort() = mlsConversationDeletionRoundTrip(Interruption.BEFORE_COMMIT)

    @Test
    fun mlsConversationDeletionRetriesAfterNativeCommit() = mlsConversationDeletionRoundTrip(Interruption.AFTER_COMMIT)

    @Test
    fun mlsConversationDeletionRetriesAfterMarkerFailure() = mlsConversationDeletionRoundTrip(Interruption.MARKER)

    @Test
    fun mlsConversationDeletionRetriesAfterCancellation() = mlsConversationDeletionRoundTrip(Interruption.CANCEL_BEFORE_COMMIT)

    private fun mlsConversationDeletionRoundTrip(interruption: Interruption?, system: Boolean = false) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.sql("UPDATE Conversation SET type = 'GROUP' WHERE qualified_id = '$CONVERSATION_ID'")
                fixture.seedProteusConversation()
                fixture.seedConversationCleanupFiles(CONVERSATION_ID)
                val rawDeleted = fixture.deletedConversationEvent(CONVERSATION_ID, sender = SELF)
                val deleted = if (system) rawDeleted.copy(payload = checkNotNull(rawDeleted.payload)
                    .replace("conversation.delete", "conversation.system.delete")
                    .replace("\"qualified_from\":{\"id\":\"${SELF.value}\",\"domain\":\"${SELF.domain}\"}", "\"qualified_from\":null")) else rawDeleted
                val first = fixture.event("first", fixture.encrypt("first", protocolMLS = false),
                    protocolMLS = false, conversationId = DELETE_TARGET)
                val second = fixture.event("second", fixture.encrypt("second", protocolMLS = false),
                    protocolMLS = false, conversationId = DELETE_TARGET)
                fixture.releasePrepared()
                val page = listOf(deleted, first, second)
                if (interruption != null) {
                    if (interruption == Interruption.CANCEL_BEFORE_COMMIT) {
                        assertFailsWith<CancellationException> {
                            fixture.receive(page, first.id, interruption, recoveryFactory = { fixture.unavailableRecovery(it) })
                        }
                    } else {
                        assertIs<Either.Left<*>>(fixture.receive(page, first.id, interruption, recoveryFactory = { fixture.unavailableRecovery(it) }))
                    }
                    // Each open closes its owner. This checks a fresh runtime, not an in-memory retry.
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(deleted.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById(first.id, DELETE_TARGET))
                        val rolledBack = interruption in listOf(Interruption.BEFORE_COMMIT, Interruption.CANCEL_BEFORE_COMMIT)
                        assertEquals(rolledBack, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    })
                    if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, first.id, recoveryFactory = { fixture.unavailableRecovery(it) })).value
                assertEquals("first", result?.text)
                assertEquals(true, result?.shouldNotify)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), second.id, recoveryFactory = { fixture.unavailableRecovery(it) })).value)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), first.id, recoveryFactory = { fixture.unavailableRecovery(it) })).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyConversationCleanup(runtime, CONVERSATION_ID, deleted = true)
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    for (event in page) assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById(first.id, DELETE_TARGET))
                    assertNotNull(runtime.database.messageDAO.getMessageById(second.id, DELETE_TARGET))
                })
            }
        }
    }

    @Test
    fun mlsConversationDeletionSupportsMixedMeetingsAndMissingNativeGroups() = runTest {
        withContext(Dispatchers.Default) {
            for (mixed in listOf(false, true)) for (meeting in listOf(false, true)) for (missingNative in listOf(false, true)) {
                withFixture(true) { fixture ->
                    fixture.sql("UPDATE Conversation SET type = '${if (meeting) "MEETING" else "GROUP"}'" +
                        ", protocol = '${if (mixed) "MIXED" else "MLS"}' WHERE qualified_id = '$CONVERSATION_ID'")
                    val deleted = fixture.deletedConversationEvent(CONVERSATION_ID, meeting = meeting, sender = SELF)
                    fixture.releasePrepared()
                    if (missingNative) assertIs<Either.Right<*>>(fixture.open { runtime ->
                        checkNotNull(fixture.openMLS(runtime)).transaction { it.wipeConversation(GROUP_ID) }
                    })
                    val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(deleted), deleted.id, recoveryFactory = { fixture.unavailableRecovery(it) })).value
                    assertEquals(ReceivedNotificationType.CONVERSATION_DELETED, result?.type)
                    assertEquals(!meeting, result?.shouldNotify)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(deleted.id)).isProcessed)
                    })
                    assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), deleted.id, recoveryFactory = { fixture.unavailableRecovery(it) })).value)
                }
            }
        }
    }

    @Test
    fun conversationCreationBeforeTextRestoresDeletedMetadataOnReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                withFixture(false) { fixture ->
                    fixture.sql("UPDATE Conversation SET type = 'GROUP' WHERE qualified_id = '$CONVERSATION_ID'")
                    val event = fixture.batch("notification", fixture.creationEvent(),
                        fixture.event("first", fixture.encrypt("first")), fixture.deletedConversationEvent(CONVERSATION_ID))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    if (stage != null) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                            assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        })
                        if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    }
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    })
                    assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
                    assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
                }
            }
        }
    }

    @Test
    fun nativeMLSDeletionLosesGroupIdentityOnSQLCommitNativeAbort() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        context.wipeConversation(GROUP_ID)
                        runtime.database.writeMessageEffects { conversations.deleteConversationByQualifiedID(CONVERSATION_ID) }
                        error("Abort native after SQL deletion")
                    }
                })
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertNull(runtime.database.conversationDAO.getConversationMLSInfo(CONVERSATION_ID))
                    assertTrue(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
            }
        }
    }

    @Test
    fun deletedMeetingIsSilentAndMissingConversationIsStale() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedProteusConversation()
                fixture.sql("UPDATE Conversation SET type = 'MEETING' WHERE qualified_id = '$DELETE_TARGET'")
                val event = fixture.batch("notification", fixture.deletedConversationEvent(DELETE_TARGET, meeting = true))
                val follow = fixture.batch("follow", fixture.deletedConversationEvent(DELETE_TARGET), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, follow), event.id)).value
                assertEquals(false, result?.shouldNotify)
                fixture.verifyProcessedText(follow, "first")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun mlsForeignGroupCiphertextClassificationKeepsQueue() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.prepareResetWelcome()
                val foreign = fixture.encrypt("foreign", groupId = RESET_GROUP_ID)
                val first = fixture.encrypt("first")
                val notification = fixture.batch("notification", fixture.event("foreign", foreign), fixture.event("first", first))
                fixture.releasePrepared()
                var observed: MLSFailure? = null
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(notification), "notification", decorateMLSContext = { real ->
                        object : MlsCoreCryptoContext by real {
                            override suspend fun decryptMessage(groupId: MLSGroupId, message: ByteArray): MLSDecryptResult = try {
                                real.decryptMessage(groupId, message)
                            } catch (failure: Exception) {
                                observed = com.wire.kalium.common.error.commonizeMLSException(failure).failure
                                throw failure
                            }
                        }
                    }))
                    println("MLS_FOREIGN_GROUP_FAILURE: $observed")
                    assertTrue(observed == MLSFailure.InvalidGroupId || observed is MLSFailure.Other)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    })
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(first, "first"))
            }
        }
    }

    @Test
    fun nativeWrongEpochBecomesPlaintextAfterLaterCommits() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val firstCommit = fixture.commit()
                val secondCommit = fixture.commit()
                val future = fixture.encrypt("future")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        var observed: MLSFailure? = null
                        try { context.decryptMessage(GROUP_ID, future) } catch (failure: Exception) {
                            observed = com.wire.kalium.common.error.commonizeMLSException(failure).failure
                        }
                        assertEquals(MLSFailure.WrongEpoch, observed)
                        context.decryptMessage(GROUP_ID, firstCommit)
                        context.decryptMessage(GROUP_ID, secondCommit)
                    }
                })
                // The app's no-rejoin branch creates no canonical message for the
                // failed payload. Native commit followed by Events marker failure
                // would therefore turn that same payload into a new visible message.
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(future, "future"))
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun externalRejoinCannotDecryptPreviousEpochAfterReopen() = externalRejoinCheckpoint(abort = false)

    @Test
    fun externalRejoinAndCheckpointRollBackTogetherAfterRemoteCommit() = externalRejoinCheckpoint(abort = true)

    @Test
    fun externalRejoinReplayBecomesTooOldAfterThreeOrdinaryCommits() = externalRejoinCheckpoint(abort = false, advanceEpochs = true)

    @Test
    fun pendingRecoverySurvivesNativeRollbackWithoutACompletionToken() = externalRejoinCheckpoint(abort = true, useRecoveryStore = true)

    @Test
    fun uncertainRecoveryIntentWriteReloadsBeforeTheNextLookup() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    var reads = 0
                    var writes = 0
                    val metadata = object : com.wire.kalium.persistence.dao.MetadataDAO by runtime.database.metadataDAO {
                        override suspend fun valuesByKeyRange(fromInclusive: String, untilExclusive: String): Map<String, String> {
                            reads++
                            return runtime.database.metadataDAO.valuesByKeyRange(fromInclusive, untilExclusive)
                        }

                        override suspend fun insertValue(value: String, key: String) {
                            runtime.database.metadataDAO.insertValue(value, key)
                            writes++
                            throw CancellationException("Interrupted after durable SQL write")
                        }
                    }
                    val store = MLSReceiveRecoveryStore(metadata, runtime.database.notificationEventStore, CLIENT)
                    val payload = "recovery-payload".encodeToByteArray()
                    assertNull(store.find("recovery-event") { error("No outstanding intent") })
                    assertFailsWith<CancellationException> { store.begin("recovery-event", GROUP_ID, payload) }
                    val recovered = checkNotNull(store.find("recovery-event") { payload })
                    assertEquals("recovery-event", recovered.eventId)
                    assertEquals(GROUP_ID, recovered.groupId)
                    assertEquals(recovered, store.begin("recovery-event", GROUP_ID, payload))
                    assertEquals(2, reads)
                    assertEquals(1, writes)
                })
            }
        }
    }

    @Test
    fun completedRecoverySurvivesReopenUntilItsQueueMarkerCommits() = externalRejoinCheckpoint(abort = false, useRecoveryStore = true)

    @Test
    fun completedRecoveryDoesNotProveARecreatedGroup() = externalRejoinCheckpoint(
        abort = false, useRecoveryStore = true, replaceGroupBeforeAck = true)

    @Test
    fun pendingRejoinAfterCatchingUpInvalidatesEarlierDecryptionInTheBatch() = externalRejoinCheckpoint(
        abort = false, useRecoveryStore = true, catchUpBeforeRejoin = true)

    @Test
    fun recoveryPayloadReceiptsSurviveReopenAndSeparateEventAcknowledgments() = recoveryPayloadReceipts(abort = false)

    @Test
    fun recoveryPayloadReceiptsCannotBypassDecryptionAfterNativeRollback() = recoveryPayloadReceipts(abort = true)

    @Test
    fun pendingRecoveryCannotBeAcknowledgedAsAnUnavailableConversation() = runTest {
        withContext(Dispatchers.Default) {
            for ((status, label) in listOf(404 to "no-conversation", 403 to "access-denied")) withFixture(true) { fixture ->
                val ciphertext = fixture.encrypt("notification")
                val event = fixture.event("notification", ciphertext)
                val failure = KaliumException.InvalidRequestError(GenericAPIErrorResponse(status, label, "Unavailable"))
                var ordinaryFetches = 0
                var recoveryFetches = 0
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                        .begin(event.id, GROUP_ID, ciphertext)
                    fixture.sql("DELETE FROM Conversation")
                })
                val result = fixture.receive(listOf(event), event.id,
                    fetchConversation = { ordinaryFetches++; throw failure },
                    recoveryFactory = { runtime ->
                        val api = object : ConversationApi by runtime.network.conversationApi {
                            override suspend fun fetchConversationDetails(conversationId: com.wire.kalium.network.api.model.ConversationId)
                                : NetworkResponse<ConversationResponse> {
                                recoveryFetches++
                                return NetworkResponse.Error(failure)
                            }
                        }
                        MLSReceiveRecovery(
                            MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT),
                            api, SELF,
                        ) { _, _, _ -> error("Unavailable recovery must not send a commit") }
                    })
                assertIs<Either.Left<*>>(result)
                assertEquals(0, ordinaryFetches)
                assertEquals(1, recoveryFetches)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertNotNull(MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                        .find(event.id) { ciphertext })
                })
            }
        }
    }

    @Test
    fun nseRecoveryReplaysCaughtUpBatchAfterMarkerFailureWithoutAnotherJoin() = recoveryBatchReplay(false)

    @Test
    fun recoveryReplayPreservesCanonicalMembershipAfterHistoricalLeave() = recoveryBatchReplay(true)

    @Test
    fun recoveryEffectsReplayStillDecryptsProteusAfterOuterRollback() = recoveryBatchReplay(true, proteusRollback = true)

    private fun recoveryBatchReplay(historicalLeave: Boolean, proteusRollback: Boolean = false) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val firstCommit = fixture.commit()
                val secondCommit = fixture.commit()
                val trigger = fixture.encrypt("trigger")
                val prefix = fixture.encrypt("prefix")
                val groupInfo = checkNotNull(fixture.transport.bundle).groupInfoBundle.payload.drop(4).toByteArray()
                val proteusPayload = if (proteusRollback) listOf(fixture.event("proteus-prefix",
                    fixture.encrypt("proteus-prefix", protocolMLS = false), protocolMLS = false)) else emptyList()
                var proteusDecryptions = 0
                val observeProteus: (ProteusCoreCryptoContext) -> ProteusCoreCryptoContext = { context ->
                    object : ProteusCoreCryptoContext by context {
                        override suspend fun <T : Any> decryptMessage(sessionId: CryptoSessionId, message: ByteArray,
                            handleDecryptedMessage: suspend (ByteArray) -> T): T = context.decryptMessage(sessionId, message) {
                            proteusDecryptions++
                            handleDecryptedMessage(it)
                        }
                    }
                }
                val payloads = proteusPayload + listOf(fixture.event("prefix", prefix)) +
                    (if (historicalLeave) listOf(fixture.memberLeave(member = SENDER)) else emptyList()) +
                    fixture.event("trigger", trigger)
                val notification = fixture.batch("notification", *payloads.toTypedArray())
                var joins = 0
                var metadataReads = 0
                val transporter = object : MLSTransporter {
                    override suspend fun sendCommitBundle(commitBundle: CommitBundle) {
                        joins++
                        checkNotNull(fixture.senderMLS).transaction { it.decryptMessage(GROUP_ID, commitBundle.commit) }
                    }
                }
                val recoveryFactory: (AppleAccountRuntime) -> MLSReceiveRecovery = { runtime ->
                    val api = object : ConversationApi by runtime.network.conversationApi {
                        override suspend fun fetchConversationDetails(conversationId: com.wire.kalium.network.api.model.ConversationId)
                            : NetworkResponse<ConversationResponse> {
                            metadataReads++
                            return NetworkResponse.Success(fixture.creationResponse().copy(epoch = 3uL), emptyMap(), 200)
                        }
                        override suspend fun fetchGroupInfo(conversationId: com.wire.kalium.network.api.model.QualifiedID) =
                            NetworkResponse.Success(groupInfo, emptyMap(), 200)
                    }
                    MLSReceiveRecovery(
                        MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT),
                        api, SELF,
                    ) { context, groupId, info ->
                        assertEquals(groupId, context.joinByExternalCommit(info))
                        context.conversationEpoch(groupId)
                    }
                }
                fixture.releasePrepared()
                val operation = assertIs<Either.Right<PendingMLSReceiveRecovery>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        context.decryptMessage(GROUP_ID, firstCommit)
                        context.decryptMessage(GROUP_ID, secondCommit)
                    }
                    MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                        .begin(notification.id, GROUP_ID, trigger)
                }).value
                assertIs<Either.Left<*>>(fixture.receive(
                    listOf(notification), notification.id, if (proteusRollback) Interruption.AFTER_COMMIT else Interruption.MARKER,
                    decorateProteusContext = observeProteus, recoveryFactory = recoveryFactory, mlsTransport = transporter))
                assertEquals(1, joins)
                assertEquals(1, metadataReads)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(notification.id)).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    assertNotNull(runtime.database.messageDAO.getMessageById("mls-recovery:${operation.id}", CONVERSATION_ID))
                    assertEquals(setOf(SELF.toDao(), SENDER.toDao()),
                        runtime.database.memberDAO.getConversationMembers(CONVERSATION_ID).toSet())
                    assertEquals(4uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    if (!proteusRollback) fixture.sql("DROP TRIGGER interrupt_receive")
                })
                assertIs<Either.Right<*>>(fixture.receive(
                    emptyList(), notification.id, decorateProteusContext = observeProteus,
                    recoveryFactory = recoveryFactory, mlsTransport = transporter))
                assertEquals(1, joins)
                assertEquals(1, metadataReads)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(notification.id)).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    assertNotNull(runtime.database.messageDAO.getMessageById("mls-recovery:${operation.id}", CONVERSATION_ID))
                    assertEquals(setOf(SELF.toDao(), SENDER.toDao()),
                        runtime.database.memberDAO.getConversationMembers(CONVERSATION_ID).toSet())
                })
                if (proteusRollback) assertEquals(2, proteusDecryptions, "Both attempts must decrypt after the outer rollback")
                val following = fixture.event("following", fixture.encrypt("following"))
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(
                    listOf(following), following.id,
                    recoveryFactory = recoveryFactory, mlsTransport = transporter)).value
                assertEquals("following", result?.text)
                assertEquals(1, joins)
            }
        }
    }

    @Suppress("LongMethod")
    private fun recoveryPayloadReceipts(abort: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val firstCommit = fixture.commit()
                val secondCommit = fixture.commit()
                val trigger = fixture.encrypt("trigger")
                val prefix = fixture.encrypt("prefix")
                fun payload(id: String, bytes: ByteArray) = com.wire.kalium.logic.data.event.Event.Conversation.NewMLSMessage(
                    id, CONVERSATION_ID.toModel(), null, SENDER, TIME, Base64.encode(bytes))
                val earlierEvent = payload("earlier-event", prefix)
                val recoveryEvent = payload("recovery-event", trigger)
                val groupInfo = checkNotNull(fixture.transport.bundle).groupInfoBundle.payload.drop(4).toByteArray()
                val transporter = object : MLSTransporter {
                    override suspend fun sendCommitBundle(commitBundle: CommitBundle) {
                        checkNotNull(fixture.senderMLS).transaction { it.decryptMessage(GROUP_ID, commitBundle.commit) }
                    }
                }
                fixture.releasePrepared()
                val result = fixture.open { runtime ->
                    runtime.database.eventDAO.insertEvents(listOf(earlierEvent, recoveryEvent).map {
                        NewEventEntity(it.id, "[]", false, false)
                    })
                    val store = MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                    val operation = store.begin(recoveryEvent.id, GROUP_ID, trigger)
                    checkNotNull(fixture.openMLS(runtime, transporter)).transaction { context ->
                        assertFalse(store.wasProcessed(context, earlierEvent))
                        context.decryptMessage(GROUP_ID, firstCommit)
                        context.decryptMessage(GROUP_ID, secondCommit)
                        assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, prefix))
                        assertEquals(GROUP_ID, context.joinByExternalCommit(groupInfo))
                        store.complete(context, operation)
                        // As in receiving, SQL effects finish before the native transaction commits.
                        runtime.database.messageDAO.insertOrIgnoreMessage(storedText("prefix"))
                        store.recordProcessed(context, listOf(earlierEvent, recoveryEvent))
                        if (abort) error("Abort after SQL receipts, before native commit")
                    }
                }
                if (abort) assertIs<Either.Left<*>>(result) else assertIs<Either.Right<*>>(result)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val store = MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                    assertNotNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertEquals(!abort, store.wasProcessed(context, earlierEvent))
                        assertEquals(!abort, store.wasProcessed(context, recoveryEvent))
                        assertFalse(store.wasProcessed(context, earlierEvent.copy(content = Base64.encode(trigger))))
                        if (abort) {
                            assertEquals(1uL, context.conversationEpoch(GROUP_ID))
                            context.decryptMessage(GROUP_ID, firstCommit)
                            context.decryptMessage(GROUP_ID, secondCommit)
                            assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, prefix))
                        } else {
                            val failure = assertFailsWith<Exception> { context.decryptMessage(GROUP_ID, prefix) }
                            assertIs<MLSFailure.Other>(com.wire.kalium.common.error.commonizeMLSException(failure).failure)
                        }
                    }
                    if (!abort) store.acknowledge(listOf(recoveryEvent.id))
                })
                if (!abort) {
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val store = MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(recoveryEvent.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(earlierEvent.id)).isProcessed)
                        checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                            assertTrue(store.wasProcessed(context, earlierEvent))
                            assertFalse(store.wasProcessed(context, recoveryEvent))
                        }
                        store.acknowledge(listOf(earlierEvent.id))
                    })
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val store = MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(earlierEvent.id)).isProcessed)
                        checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                            assertFalse(store.wasProcessed(context, earlierEvent))
                            assertFalse(store.wasProcessed(context, recoveryEvent))
                        }
                    })
                }
            }
        }
    }

    private fun externalRejoinCheckpoint(
        abort: Boolean,
        advanceEpochs: Boolean = false,
        useRecoveryStore: Boolean = false,
        replaceGroupBeforeAck: Boolean = false,
        catchUpBeforeRejoin: Boolean = false,
    ) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val checkpoint = "known-recovery-trigger".encodeToByteArray()
                val initialPrefix = fixture.encrypt("prefix")
                val firstCommit = fixture.commit()
                val secondCommit = fixture.commit()
                val future = fixture.encrypt("future")
                val prefix = if (catchUpBeforeRejoin) fixture.encrypt("caught-up-prefix") else initialPrefix
                val wrappedGroupInfo = checkNotNull(fixture.transport.bundle).groupInfoBundle.payload
                assertContentEquals(byteArrayOf(0, 1, 0, 4), wrappedGroupInfo.copyOfRange(0, 4))
                val groupInfo = wrappedGroupInfo.copyOfRange(4, wrappedGroupInfo.size)
                var posts = 0
                val transporter = object : MLSTransporter {
                    override suspend fun sendCommitBundle(commitBundle: CommitBundle) {
                        posts++
                        checkNotNull(fixture.senderMLS).transaction { context ->
                            assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, commitBundle.commit))
                        }
                    }
                }
                fixture.releasePrepared()
                val result = fixture.open { runtime ->
                    val recovery = if (useRecoveryStore) {
                        runtime.database.eventDAO.insertEvents(listOf(com.wire.kalium.persistence.dao.event.NewEventEntity(
                            "recovery-event", "[]", false, false)))
                        var reads = 0
                        val metadata = object : com.wire.kalium.persistence.dao.MetadataDAO by runtime.database.metadataDAO {
                            override suspend fun valuesByKeyRange(fromInclusive: String, untilExclusive: String): Map<String, String> {
                                reads++
                                return runtime.database.metadataDAO.valuesByKeyRange(fromInclusive, untilExclusive)
                            }
                        }
                        MLSReceiveRecoveryStore(metadata, runtime.database.notificationEventStore, CLIENT).also { store ->
                            repeat(100) { assertNull(store.find("ordinary-$it") { error("No pending event needs no payload") }) }
                            assertEquals(1, reads)
                        }
                    } else null
                    val operation = recovery?.begin("recovery-event", GROUP_ID, future)
                    if (recovery != null) {
                        assertFailsWith<IllegalStateException> { recovery.begin("recovery-event", "changed-group", future) }
                        val otherClient = MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, "other")
                        assertNull(otherClient.find("recovery-event") { error("Another client cannot see this intent") })
                    }
                    checkNotNull(fixture.openMLS(runtime, transporter)).transaction { context ->
                        assertNull(context.readTransactionCheckpoint())
                        if (operation != null) assertFalse(checkNotNull(recovery).isCompleted(context, operation))
                        if (!catchUpBeforeRejoin) assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, prefix))
                        val before = assertFailsWith<Exception> { context.decryptMessage(GROUP_ID, future) }
                        assertIs<MLSFailure.WrongEpoch>(com.wire.kalium.common.error.commonizeMLSException(before).failure)
                        if (catchUpBeforeRejoin) {
                            context.decryptMessage(GROUP_ID, firstCommit)
                            context.decryptMessage(GROUP_ID, secondCommit)
                            assertEquals(3uL, context.conversationEpoch(GROUP_ID))
                            assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, prefix))
                        }
                        assertEquals(GROUP_ID, context.joinByExternalCommit(groupInfo))
                        assertEquals(4uL, context.conversationEpoch(GROUP_ID))
                        if (operation == null) context.writeTransactionCheckpoint(checkpoint)
                        else checkNotNull(recovery).complete(context, operation)
                        if (abort) error("Abort native transaction after accepted external commit")
                    }
                }
                if (abort) assertIs<Either.Left<*>>(result) else assertIs<Either.Right<*>>(result)
                assertEquals(1, posts)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val recovery = if (useRecoveryStore) {
                        MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                    } else null
                    val operation = recovery?.let { assertNotNull(it.find("recovery-event") { future }) }
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        if (abort) {
                            assertNull(context.readTransactionCheckpoint())
                            if (operation != null) assertFalse(checkNotNull(recovery).isCompleted(context, operation))
                            assertEquals(1uL, context.conversationEpoch(GROUP_ID))
                            assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, prefix))
                            return@transaction
                        }
                        if (operation == null) assertContentEquals(checkpoint, context.readTransactionCheckpoint())
                        else assertTrue(checkNotNull(recovery).isCompleted(context, operation))
                        val replay = assertFailsWith<Exception> { context.decryptMessage(GROUP_ID, future) }
                        val mapped = com.wire.kalium.common.error.commonizeMLSException(replay).failure
                        // CoreCrypto 10.5.1 deliberately reports a generic decryption failure
                        // after an external rejoin replaces the previous epoch's secrets.
                        // It is not the typed MessageEpochTooOld case and cannot be ignored globally.
                        assertEquals(MLSFailure.Other("An application message failed to decrypt"), mapped)
                        // If a pending operation rejoins after the group caught up, its
                        // replacement keys also invalidate preceding successful work.
                        val earlierReplay = assertFailsWith<Exception> { context.decryptMessage(GROUP_ID, prefix) }
                        assertEquals(
                            if (catchUpBeforeRejoin) MLSFailure.Other("An application message failed to decrypt")
                            else MLSFailure.MessageEpochTooOld,
                            com.wire.kalium.common.error.commonizeMLSException(earlierReplay).failure
                        )
                        assertEquals(4uL, context.conversationEpoch(GROUP_ID))
                        if (replaceGroupBeforeAck) {
                            context.wipeConversation(GROUP_ID)
                            assertFalse(checkNotNull(recovery).isCompleted(context, checkNotNull(operation)))
                            context.createConversation(GROUP_ID, Base64.decode(PROPOSAL_PUBLIC_KEY))
                            assertEquals(0uL, context.conversationEpoch(GROUP_ID))
                            assertFalse(recovery.isCompleted(context, operation))
                        }
                    }
                    if (recovery != null) {
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("recovery-event")).isProcessed)
                        if (!abort && !replaceGroupBeforeAck) {
                            recovery.acknowledge(listOf("recovery-event"))
                            assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("recovery-event")).isProcessed)
                            val reopened = MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT)
                            assertNull(reopened.find("recovery-event") { future })
                        }
                    }
                })
                assertEquals(4uL, checkNotNull(fixture.senderMLS).transaction { it.conversationEpoch(GROUP_ID) })
                if (!abort && !replaceGroupBeforeAck) {
                    val following = fixture.encrypt("following")
                    assertIs<Either.Right<*>>(fixture.decryptAndCheck(following, "following"))
                }
                if (advanceEpochs) repeat(3) { index ->
                    val commit = fixture.commit()
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                            context.decryptMessage(GROUP_ID, commit)
                            assertEquals((5 + index).toULong(), context.conversationEpoch(GROUP_ID))
                            val replay = assertFailsWith<Exception> { context.decryptMessage(GROUP_ID, future) }
                            val failure = com.wire.kalium.common.error.commonizeMLSException(replay).failure
                            if (index < 2) assertIs<MLSFailure.Other>(failure) else assertEquals(MLSFailure.MessageEpochTooOld, failure)
                            assertContentEquals(checkpoint, context.readTransactionCheckpoint())
                        }
                    })
                }
            }
        }
    }

    @Test
    fun mlsWrongEpochDefersConversationAndKeepsLaterQueuePending() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val prefix = fixture.encrypt("prefix")
                val firstCommit = fixture.commit()
                val secondCommit = fixture.commit()
                val future = fixture.encrypt("future")
                val next = fixture.encrypt("next")
                val notification = fixture.batch("notification", fixture.event("prefix", prefix),
                    fixture.event("future", future), fixture.event("commit-one", firstCommit), fixture.event("commit-two", secondCommit))
                val tail = fixture.event("tail", next)
                fixture.releasePrepared()
                repeat(2) {
                    assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(notification, tail), "tail",
                        fetchConversation = { fixture.creationResponse().copy(epoch = 1uL) })).value)
                    assertEquals("QUEUE_END", fixture.receivedStop)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("tail")).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("future", CONVERSATION_ID))
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        assertEquals(1uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    })
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(prefix, "prefix"))
            }
        }
    }

    @Test
    fun mlsOwnCommitWithoutPendingContinues() = mlsIgnoredCiphertext(old = false)

    @Test
    fun mlsPastEpochMessageContinues() = mlsIgnoredCiphertext(old = true)

    @Test
    fun mlsOwnCommitWithoutPendingMarkerReplay() = mlsIgnoredCiphertext(old = false, stage = Interruption.MARKER)

    @Test
    fun mlsPastEpochAfterCommitReplay() = mlsIgnoredCiphertext(old = true, stage = Interruption.AFTER_COMMIT)

    @Test
    fun mlsIgnoredThenSQLFailureKeepsQueue() = mlsIgnoredCiphertext(old = true, stage = Interruption.DELIVERY)

    private fun mlsIgnoredCiphertext(old: Boolean, stage: Interruption? = null) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val ignored = fixture.ignoredMLSCiphertext(old)
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                val notification = fixture.batch("notification", fixture.event("ignored", ignored), fixture.event("first", first))
                fixture.releasePrepared()
                var observed: MLSFailure? = null
                val decorate: (MlsCoreCryptoContext) -> MlsCoreCryptoContext = { real ->
                    object : MlsCoreCryptoContext by real {
                        override suspend fun decryptMessage(groupId: MLSGroupId, message: ByteArray): MLSDecryptResult = try {
                            real.decryptMessage(groupId, message)
                        } catch (failure: Exception) {
                            if (message.contentEquals(ignored)) observed =
                                com.wire.kalium.common.error.commonizeMLSException(failure).failure
                            throw failure
                        }
                    }
                }
                if (stage != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(notification), "notification", stage, decorateMLSContext = decorate))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        if (stage == Interruption.DELIVERY) assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(
                    listOf(notification), "notification", decorateMLSContext = decorate
                )).value
                assertEquals(if (old) MLSFailure.MessageEpochTooOld else MLSFailure.SelfCommitIgnored, observed)
                if (stage == Interruption.AFTER_COMMIT || stage == Interruption.MARKER) assertNull(result)
                else assertEquals("first", result?.messageId)
                fixture.verifyStoredAndNext(next, "first")
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("old", CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("notification", CONVERSATION_ID))
                })
            }
        }
    }

    @Test
    fun mlsBufferedInactiveCommitRetainsFinalEpochAndReplays() = runTest {
        withContext(Dispatchers.Default) {
            for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT)) withFixture(true) { fixture ->
                val missing = fixture.commit()
                checkNotNull(fixture.senderMLS).transaction {
                    it.removeMember(GROUP_ID, listOf(CryptoQualifiedClientId(CLIENT, SELF.toCrypto())))
                }
                val removal = checkNotNull(fixture.transport.bundle).commit
                fixture.seedProteusConversation()
                val first = fixture.encrypt("first", protocolMLS = false)
                val next = fixture.encrypt("next", protocolMLS = false)
                val buffer = fixture.event("buffer", removal)
                val notification = fixture.batch("notification", fixture.event("missing", missing),
                    fixture.event("first", first, protocolMLS = false, conversationId = DELETE_TARGET))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(buffer), "buffer"))
                if (stage == Interruption.BEFORE_COMMIT) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(notification), "notification", decorateMLSContext = { real ->
                        object : MlsCoreCryptoContext by real {
                            override suspend fun wipeConversation(groupId: MLSGroupId) { error("Required buffered removal wipe failed") }
                        }
                    }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("notification:mls-removal", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", DELETE_TARGET))
                    })
                }
                assertIs<Either.Left<*>>(fixture.receive(listOf(notification), "notification", stage, decorateMLSContext = { real ->
                    object : MlsCoreCryptoContext by real {
                        override suspend fun decryptMessage(groupId: MLSGroupId, message: ByteArray): MLSDecryptResult =
                            real.decryptMessage(groupId, message).also {
                                println("MLS_BUFFERED_INACTIVE_BUNDLES: $it; groupExists=${real.conversationExists(groupId)}")
                            }
                    }
                }))
                assertIs<Either.Right<*>>(fixture.receive(listOf(notification), "notification"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                        as ConversationEntity.ProtocolInfo.MLS
                    assertEquals(3uL, protocol.epoch)
                    assertEquals(ConversationEntity.GroupState.PENDING_JOIN, protocol.groupState)
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    assertEquals(MessageEntityContent.MLSWrongEpochWarning, assertIs<MessageEntity.System>(
                        runtime.database.messageDAO.getMessageById("notification:mls-removal", CONVERSATION_ID)
                    ).content)
                    assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("first", DELETE_TARGET))
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next", protocolMLS = false))
            }
        }
    }

    @Test
    fun mlsFutureBufferThenCommitAndText() = mlsBufferedReceiving(proposal = false)

    @Test
    fun mlsMissingProposalBufferThenProposalAndText() = mlsBufferedReceiving(proposal = true)

    @Test
    fun mlsFutureBufferBeforeCommitReplay() = mlsBufferedReceiving(proposal = false, stage = Interruption.BEFORE_COMMIT)

    @Test
    fun mlsFutureBufferAfterCommitReplay() = mlsBufferedReceiving(proposal = false, stage = Interruption.AFTER_COMMIT)

    @Test
    fun mlsFutureBufferMarkerReplay() = mlsBufferedReceiving(proposal = false, stage = Interruption.MARKER)

    @Test
    fun mlsMissingProposalBufferMarkerReplay() = mlsBufferedReceiving(proposal = true, stage = Interruption.MARKER)

    @Test
    fun mlsBufferedDeliveryFailureRollsBack() = mlsBufferedReceiving(proposal = false, stage = Interruption.DELIVERY)

    private fun mlsBufferedReceiving(proposal: Boolean, stage: Interruption? = null) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val trigger: ByteArray
                val buffered: ByteArray
                if (proposal) {
                    val addition = fixture.addThird()
                    checkNotNull(fixture.receiverMLS).transaction { it.decryptMessage(GROUP_ID, addition) }
                    fixture.sql("UPDATE Conversation SET mls_epoch = 2")
                    buffered = fixture.commitRemovalProposal()
                    trigger = Base64.decode(REMOVE_THIRD_PROPOSAL)
                } else {
                    trigger = fixture.commit()
                    buffered = fixture.encrypt("buffered")
                }
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                val earlier = fixture.event("buffer", buffered)
                val notification = fixture.batch("notification", fixture.event("trigger", trigger), fixture.event("first", first))
                fixture.releasePrepared()
                // A separate notification makes native buffer durability observable before consumption.
                if (stage != null && stage != Interruption.DELIVERY) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(earlier), "buffer", stage))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("buffer")).isProcessed)
                        fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                    })
                }
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(earlier), "buffer")).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("buffer")).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("buffered", CONVERSATION_ID))
                    assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                })
                if (stage != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(notification), "notification", stage))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        if (stage == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertNull(runtime.database.messageDAO.getMessageById("buffered", CONVERSATION_ID))
                        }
                        fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(notification), "notification")).value
                if (stage == Interruption.AFTER_COMMIT || stage == Interruption.MARKER) assertNull(result)
                else assertEquals("first", result?.messageId)
                fixture.verifyStoredAndNext(next, "first", if (proposal) listOf("first") else listOf("buffered", "first"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                    val protocol = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                        as ConversationEntity.ProtocolInfo.MLS
                    assertEquals(if (proposal) 3uL else 2uL, protocol.epoch)
                    if (!proposal) {
                        val text = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("buffered", CONVERSATION_ID))
                        assertEquals("buffered", assertIs<MessageEntityContent.Text>(text.content).messageBody)
                        assertEquals(SENDER.toDao(), text.senderUserId)
                    }
                })
            }
        }
    }

    @Test
    fun nativeFutureMessageBufferSurvivesReopen() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val commit = fixture.commit()
                val future = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertEquals(MLSDecryptResult.BufferedFutureMessage, context.decryptMessage(GROUP_ID, future))
                    }
                })
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        val bundles = assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, commit)).messages
                        assertEquals(2, bundles.size)
                        println("MLS_FUTURE_BUFFER_REOPEN: $bundles")
                    }
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun nativeMissingProposalBufferSurvivesReopen() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                checkNotNull(fixture.receiverMLS).transaction { it.decryptMessage(GROUP_ID, addition) }
                val commit = fixture.commitRemovalProposal()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertEquals(MLSDecryptResult.BufferedCommit, context.decryptMessage(GROUP_ID, commit))
                    }
                })
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        val bundles = assertIs<MLSDecryptResult.Success>(
                            context.decryptMessage(GROUP_ID, Base64.decode(REMOVE_THIRD_PROPOSAL))
                        ).messages
                        println("MLS_PROPOSAL_BUFFER_REOPEN: $bundles")
                        assertEquals(3uL, context.conversationEpoch(GROUP_ID))
                    }
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun mlsMalformedCiphertextWithoutTypedFailureKeepsQueue() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.event("first", fixture.encrypt("first")),
                    fixture.event("bad", byteArrayOf(0)))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                repeat(2) {
                    var reported: com.wire.kalium.common.error.MLSFailure? = null
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, decorateMLSContext = { real ->
                        object : MlsCoreCryptoContext by real {
                            override suspend fun decryptMessage(groupId: MLSGroupId, message: ByteArray): MLSDecryptResult = try {
                                real.decryptMessage(groupId, message)
                            } catch (failure: Exception) {
                                reported = com.wire.kalium.common.error.commonizeMLSException(failure).failure
                                throw failure
                            }
                        }
                    }))
                    assertTrue(reported is com.wire.kalium.common.error.MLSFailure.Other ||
                        reported is com.wire.kalium.common.error.MLSFailure.Generic)
                    println("MLS_UNCLASSIFIED_INPUT_FAILURE: $reported")
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById(event.id, CONVERSATION_ID))
                    })
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun nativeMissingSessionFailureChangesAfterLaterPrekey() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val (prekey, message) = fixture.missingSessionSequence()
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val error = runtime.proteusCoreCrypto.proteusClient().transaction("probe-session-failure") { context ->
                        val failure = try {
                            context.decryptMessage(CryptoSessionId(SENDER.toCrypto(), CryptoClientId(SENDER_CLIENT)), message) { it }
                            null
                        } catch (failure: com.wire.kalium.cryptography.exceptions.ProteusException) { failure }
                        if (failure != null) context.decryptMessage(CryptoSessionId(SENDER.toCrypto(), CryptoClientId(SENDER_CLIENT)), prekey) { it }
                        failure
                    }
                    assertEquals(com.wire.kalium.cryptography.exceptions.ProteusException.Code.INVALID_MESSAGE, error?.code)
                })
                // Reopening after the prekey changes the error (202 -> 406), but both are
                // message failures. Replay must preserve the canonical original failure row.
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val error = runtime.proteusCoreCrypto.proteusClient().transaction("probe-reopened-session") { context ->
                        try {
                            context.decryptMessage(CryptoSessionId(SENDER.toCrypto(), CryptoClientId(SENDER_CLIENT)), message) { it }
                            null
                        } catch (failure: com.wire.kalium.cryptography.exceptions.ProteusException) { failure }
                    }
                    assertEquals(406, error?.intCode)
                    assertEquals(com.wire.kalium.cryptography.exceptions.ProteusException.Code.INVALID_SIGNATURE, error?.code)
                })
            }
        }
    }

    @Test
    fun proteusStorageFailureKeepsQueue() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val event = fixture.batch("notification", fixture.event("bad", byteArrayOf(0)), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, decorateProteusContext = { real ->
                        object : ProteusCoreCryptoContext by real {
                            override suspend fun <T : Any> decryptMessage(sessionId: CryptoSessionId, message: ByteArray,
                                handleDecryptedMessage: suspend (ByteArray) -> T): T {
                                throw com.wire.kalium.cryptography.exceptions.ProteusException("storage",
                                    com.wire.kalium.cryptography.exceptions.ProteusException.Code.STORAGE_ERROR, 501)
                            }
                        }
                    }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById(event.id, CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    })
                }
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value?.text)
                fixture.verifyProcessedText(tail, "tail")
            }
        }
    }

    @Test
    fun proteusMissingSessionThenPrekeySameNotificationReplays() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val (prekey, message) = fixture.missingSessionSequence()
                val event = fixture.batch("notification", fixture.event("bad", message, protocolMLS = false),
                    fixture.event("prekey", prekey, protocolMLS = false), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, Interruption.MARKER))
                fixture.sql("DROP TRIGGER interrupt_receive")
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val stored = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById(event.id, CONVERSATION_ID))
                    assertEquals(202, assertIs<MessageEntityContent.FailedDecryption>(stored.content).code)
                    assertNotNull(runtime.database.messageDAO.getMessageById("prekey", CONVERSATION_ID))
                    assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("recoverable", CONVERSATION_ID))
                })
                fixture.verifyProcessedText(tail, "tail")
            }
        }
    }

    @Test
    fun proteusMissingSessionSeparateNotifications() = proteusMissingSessionSeparate(null)

    @Test
    fun proteusMissingSessionMarkerReplay() = proteusMissingSessionSeparate(Interruption.MARKER)

    private fun proteusMissingSessionSeparate(interruption: Interruption?) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val (prekey, message) = fixture.missingSessionSequence()
                val event = fixture.event("notification", message, protocolMLS = false)
                val tail = fixture.event("tail", prekey, protocolMLS = false)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption))
                    fixture.sql("DROP TRIGGER interrupt_receive")
                }
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id)).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val stored = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById(event.id, CONVERSATION_ID))
                    assertEquals(202, assertIs<MessageEntityContent.FailedDecryption>(stored.content).code)
                    assertEquals(listOf("prekey"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                })
                fixture.verifyProcessedText(tail, "prekey")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun proteusInvalidSignatureContinues() = proteusDecryptionFailure(false, invalidSignature = true)

    @Test
    fun proteusInvalidSignatureAfterCommitReplay() = proteusDecryptionFailure(false, Interruption.AFTER_COMMIT, invalidSignature = true)

    @Test
    fun proteusDecryptionFailureContinues() = proteusDecryptionFailure(false)

    @Test
    fun proteusDecryptionFailureThenMLS() = proteusDecryptionFailure(true)

    @Test
    fun proteusDecryptionFailureSQLRollback() = proteusDecryptionFailure(false, sqlFailure = true)

    @Test
    fun proteusDecryptionFailureBeforeCommitReplay() = proteusDecryptionFailure(false, Interruption.BEFORE_COMMIT)

    @Test
    fun proteusDecryptionFailureAfterCommitReplay() = proteusDecryptionFailure(false, Interruption.AFTER_COMMIT)

    @Test
    fun proteusDecryptionFailureMarkerReplay() = proteusDecryptionFailure(false, Interruption.MARKER)

    @Test
    fun proteusDecryptionFailureIsSilent() = proteusDecryptionFailure(false, onlyFailure = true)

    private fun proteusDecryptionFailure(mls: Boolean, interruption: Interruption? = null,
        sqlFailure: Boolean = false, onlyFailure: Boolean = false, invalidSignature: Boolean = false,
    ) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val firstCipher = if (onlyFailure) null else fixture.encrypt("first")
                val first = firstCipher?.let { fixture.event("first", it) }
                val bytes = if (invalidSignature) checkNotNull(firstCipher).copyOf().also { it[16] = (it[16].toInt() xor 1).toByte() }
                    else byteArrayOf(0)
                val failure = fixture.event("bad", bytes, protocolMLS = false)
                val event = if (onlyFailure) fixture.batch("notification", failure, failure)
                    else fixture.batch("notification", failure, failure, checkNotNull(first))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (sqlFailure || interruption != null) {
                    if (sqlFailure) fixture.sql("CREATE TRIGGER fail_decryption_row BEFORE INSERT ON MessageSystemContent " +
                        "WHEN NEW.content_type = 'FAILED_DECRYPT' BEGIN SELECT RAISE(ABORT, 'decryption row'); END")
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (sqlFailure) {
                            assertNull(runtime.database.messageDAO.getMessageById(event.id, CONVERSATION_ID))
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        }
                    })
                    if (sqlFailure) fixture.sql("DROP TRIGGER fail_decryption_row")
                    if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id)).value
                if (onlyFailure) assertNull(result)
                else if (interruption !in listOf(Interruption.AFTER_COMMIT, Interruption.MARKER)) assertEquals("first", result?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val message = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById(event.id, CONVERSATION_ID))
                    val content = assertIs<MessageEntityContent.FailedDecryption>(message.content)
                    assertEquals(if (invalidSignature) com.wire.kalium.cryptography.exceptions.ProteusException.Code.INVALID_SIGNATURE
                        else com.wire.kalium.cryptography.exceptions.ProteusException.Code.DECODE_ERROR,
                        com.wire.kalium.cryptography.exceptions.ProteusException.fromProteusCode(checkNotNull(content.code)))
                    assertEquals(SENDER.toDao(), message.senderUserId)
                    assertEquals(SENDER_CLIENT, message.senderClientId)
                    assertEquals(TIME, message.date)
                    assertEquals(MessageEntity.Visibility.VISIBLE, message.visibility)
                    // The app's current SQL insert stores unresolved even when the input model is resolved.
                    assertFalse(content.isDecryptionResolved)
                    assertEquals(if (onlyFailure) listOf("tail") else listOf("first", "tail"),
                        runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun previewImagesProteus() = previewImages(false)

    @Test
    fun previewImagesMLS() = previewImages(true)

    @Test
    fun previewImagesSQLFailure() = previewImages(true, sqlFailure = true)

    @Test
    fun previewImageFailuresDoNotBlockText() = runTest {
        for (mls in listOf(false, true)) for (failure in listOf("network", "hash")) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.previewEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id,
                    linkPreviewEnabled = true, downloadPreview = { sink ->
                        if (failure == "network") error("Preview download unavailable")
                        sink.write(okio.Buffer().writeUtf8("invalid"), 7)
                    }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyPreviewMetadata(runtime, downloaded = false, includeTail = true)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    val directory = runtime.assetDirectory.toPath().resolve("nse-previews")
                    assertTrue(FileSystem.SYSTEM.list(directory).isEmpty())
                })
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun previewImagesBeforeCommitReplay() = previewImages(true, Interruption.BEFORE_COMMIT)

    @Test
    fun previewImagesAfterCommitReplay() = previewImages(true, Interruption.AFTER_COMMIT)

    @Test
    fun previewImagesMarkerReplay() = previewImages(false, Interruption.MARKER)

    @Test
    fun previewImagesCachedAsset() = previewImages(false, cached = true)

    @Test
    fun previewImagesMissingCachedFile() = previewImages(true, cached = true, missing = true)

    private fun previewImages(mls: Boolean, interruption: Interruption? = null, sqlFailure: Boolean = false,
        cached: Boolean = false, missing: Boolean = false,
    ) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.previewEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                var downloads = 0
                val download: suspend (okio.Sink) -> Unit = { sink ->
                    downloads++
                    kotlinx.coroutines.delay(25) // Exercise native transaction suspension, not a synchronous stub.
                    val bytes = fixture.encryptedAsset
                    sink.write(okio.Buffer().write(bytes), bytes.size.toLong())
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val root = runtime.assetDirectory.toPath().resolve("nse-previews")
                    FileSystem.SYSTEM.createDirectories(root)
                    FileSystem.SYSTEM.write(root.resolve("abandoned")) { writeUtf8("partial") }
                    if (cached) {
                        val path = root.resolve("cached")
                        if (!missing) FileSystem.SYSTEM.write(path) { writeUtf8(ASSET_TEXT) }
                        runtime.database.assetDAO.insertAsset(AssetEntity("preview-image", SELF.domain, path.toString(),
                            ASSET_TEXT.encodeToByteArray().size.toLong(), downloadedDate = 1))
                    }
                })
                if (sqlFailure || interruption != null) {
                    if (sqlFailure) fixture.sql("CREATE TRIGGER fail_preview_image BEFORE INSERT ON MessageTextContent " +
                        "WHEN NEW.message_id = 'preview-quoted' BEGIN SELECT RAISE(ABORT, 'preview image'); END")
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption,
                        linkPreviewEnabled = true, downloadPreview = download))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (sqlFailure) {
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertTrue(runtime.database.assetDAO.getAssets().isEmpty())
                            assertTrue(FileSystem.SYSTEM.list(runtime.assetDirectory.toPath().resolve("nse-previews")).isEmpty())
                        } else fixture.verifyPreviewMetadata(runtime, downloaded = true)
                    })
                    if (sqlFailure) fixture.sql("DROP TRIGGER fail_preview_image")
                    if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                }
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id,
                    linkPreviewEnabled = true, downloadPreview = download))
                assertEquals(if (cached && !missing) 0 else if (sqlFailure) 2 else 1, downloads)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyPreviewMetadata(runtime, downloaded = true, includeTail = true)
                    assertEquals(1, FileSystem.SYSTEM.list(runtime.assetDirectory.toPath().resolve("nse-previews")).size)
                })
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun previewImagesLaterEditKeepsCanonicalReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val initial = fixture.previewEvent()
                val event = fixture.batch("notification", initial, fixture.event("edit", fixture.encrypt("preview-edit",
                    MessageContent.TextEdited("first", "edited")), Instant.fromEpochMilliseconds(TIME.toEpochMilliseconds() + 1_000)))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                var downloads = 0
                val download: suspend (okio.Sink) -> Unit = { sink ->
                    downloads++
                    kotlinx.coroutines.delay(25)
                    sink.write(okio.Buffer().write(fixture.encryptedAsset), fixture.encryptedAsset.size.toLong())
                }
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, Interruption.BEFORE_COMMIT,
                    linkPreviewEnabled = true, downloadPreview = download))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id,
                    linkPreviewEnabled = true, downloadPreview = download))
                assertEquals(1, downloads)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    val message = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("preview-edit", CONVERSATION_ID))
                    val content = assertIs<MessageEntityContent.Text>(message.content)
                    assertEquals("edited", content.messageBody)
                    assertTrue(content.linkPreview.isEmpty())
                    val asset = checkNotNull(runtime.database.assetDAO.getAssetByKey("preview-image").first())
                    assertEquals(ASSET_TEXT, FileSystem.SYSTEM.read(asset.dataPath.toPath()) { readUtf8() })
                })
                fixture.verifyProcessedText(tail, "tail")
            }
        }
    }

    @Test
    fun previewImagesTimeoutRollsBackAndRetries() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.previewEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(1_000) {
                        fixture.receive(listOf(event, tail), event.id, linkPreviewEnabled = true, downloadPreview = { sink ->
                            sink.write(okio.Buffer().writeUtf8("partial"), 7)
                            awaitCancellation()
                        })
                    }
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertTrue(FileSystem.SYSTEM.list(runtime.assetDirectory.toPath().resolve("nse-previews")).isEmpty())
                })
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id, linkPreviewEnabled = true, downloadPreview = { sink ->
                    sink.write(okio.Buffer().write(fixture.encryptedAsset), fixture.encryptedAsset.size.toLong())
                }))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyPreviewMetadata(it, downloaded = true, includeTail = true) })
                fixture.verifyProcessedText(tail, "tail")
            }
        }
    }

    @Test
    fun previewMetadataProteus() = previewMetadata(false)

    @Test
    fun previewMetadataMLS() = previewMetadata(true)

    @Test
    fun previewMetadataEnabledWithoutImage() = previewMetadata(true, image = false, enabled = true)

    @Test
    fun previewMetadataEnabledIncompleteImage() = previewMetadata(false, complete = false, enabled = true)

    @Test
    fun previewMetadataSQLFailure() = previewMetadata(true, sqlFailure = true)

    @Test
    fun previewMetadataBeforeCommitReplay() = previewMetadata(true, Interruption.BEFORE_COMMIT)

    @Test
    fun previewMetadataAfterCommitReplay() = previewMetadata(true, Interruption.AFTER_COMMIT)

    @Test
    fun previewMetadataMarkerReplay() = previewMetadata(false, Interruption.MARKER)

    private fun previewMetadata(mls: Boolean, interruption: Interruption? = null, sqlFailure: Boolean = false,
        image: Boolean = true, complete: Boolean = true, enabled: Boolean = false,
    ) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.previewEvent(image, complete)
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (sqlFailure || interruption != null) {
                    if (sqlFailure) fixture.sql("CREATE TRIGGER fail_preview BEFORE INSERT ON MessageLinkPreview " +
                        "WHEN NEW.url_offset = 5 BEGIN SELECT RAISE(ABORT, 'preview'); END")
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (sqlFailure) {
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            fixture.sql("DROP TRIGGER fail_preview")
                        }
                        if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    })
                }
                val received = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id,
                    linkPreviewEnabled = enabled)).value
                if (interruption != Interruption.AFTER_COMMIT && interruption != Interruption.MARKER) {
                    assertEquals("preview-quoted", received?.text)
                    assertEquals(true, received?.shouldNotify)
                }
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyPreviewMetadata(it, image, complete, includeTail = true) })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun previewImageNetworkErrorKeepsTextAndReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.previewEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                val received = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(
                    listOf(event, tail), event.id, linkPreviewEnabled = true)).value
                assertEquals("preview-quoted", received?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    fixture.verifyPreviewMetadata(runtime, downloaded = false, includeTail = true)
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(
                    listOf(event, tail), event.id, linkPreviewEnabled = true)).value)
                fixture.verifyProcessedText(tail, "tail")
            }
        }
    }

    @Test
    fun multipartProteus() = multipartReceiving(false)

    @Test
    fun multipartMLS() = multipartReceiving(true)

    @Test
    fun multipartSQLFailure() = multipartReceiving(true, sqlFailure = true)

    @Test
    fun multipartBeforeCommitReplay() = multipartReceiving(true, Interruption.BEFORE_COMMIT)

    @Test
    fun multipartAfterCommitReplay() = multipartReceiving(true, Interruption.AFTER_COMMIT)

    @Test
    fun multipartMarkerReplay() = multipartReceiving(false, Interruption.MARKER)

    @Test
    fun multipartEditProteus() = multipartReceiving(false, edited = true)

    @Test
    fun multipartEditMLS() = multipartReceiving(true, edited = true)

    @Test
    fun multipartEditSQLFailure() = multipartReceiving(true, sqlFailure = true, edited = true)

    @Test
    fun multipartEditBeforeCommitReplay() = multipartReceiving(true, Interruption.BEFORE_COMMIT, edited = true)

    @Test
    fun multipartEditAfterCommitReplay() = multipartReceiving(true, Interruption.AFTER_COMMIT, edited = true)

    @Test
    fun multipartEditMarkerReplay() = multipartReceiving(false, Interruption.MARKER, edited = true)

    @Test
    fun multipartEditLocalStateAndAuthorization() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val local = storedText("stale").copy(content = MessageEntityContent.Multipart("local"),
                    status = MessageEntity.Status.PENDING, editStatus = MessageEntity.EditStatus.Edited(TIME + 30.seconds))
                fixture.seed(local, local.copy(id = "newer"), local.copy(id = "unauthorized", senderUserId = SELF.toDao()))
                val event = fixture.batch("notification",
                    fixture.event("stale", fixture.encrypt("stale-edit", MessageContent.MultipartEdited("stale", "server-old"))),
                    fixture.event("newer", fixture.encrypt("newer-edit", MessageContent.MultipartEdited("newer", "server-new")), TIME + 60.seconds),
                    fixture.event("unauthorized", fixture.encrypt("unauthorized-edit", MessageContent.MultipartEdited("unauthorized", "forged"))),
                    fixture.event("missing", fixture.encrypt("missing-edit", MessageContent.MultipartEdited("missing", "ignored"))),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val dao = runtime.database.messageDAO
                    val stale = assertIs<MessageEntity.Regular>(dao.getMessageById("stale-edit", CONVERSATION_ID))
                    assertEquals("local", assertIs<MessageEntityContent.Multipart>(stale.content).messageBody)
                    assertEquals(MessageEntity.Status.PENDING, stale.status)
                    assertEquals(MessageEntity.EditStatus.Edited(TIME + 30.seconds), stale.editStatus)
                    val newer = assertIs<MessageEntity.Regular>(dao.getMessageById("newer-edit", CONVERSATION_ID))
                    assertEquals("server-new", assertIs<MessageEntityContent.Multipart>(newer.content).messageBody)
                    assertEquals(MessageEntity.Status.SENT, newer.status)
                    assertEquals(MessageEntity.EditStatus.Edited(TIME + 60.seconds), newer.editStatus)
                    assertNotNull(dao.getMessageById("unauthorized", CONVERSATION_ID))
                    assertNull(dao.getMessageById("unauthorized-edit", CONVERSATION_ID))
                    assertNull(dao.getMessageById("missing-edit", CONVERSATION_ID))
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    private fun multipartReceiving(
        mls: Boolean, interruption: Interruption? = null, sqlFailure: Boolean = false, edited: Boolean = false,
    ) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.multipartEvent(edited)
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (interruption != null || sqlFailure) {
                    if (sqlFailure) fixture.sql(if (edited) "CREATE TRIGGER fail_multipart BEFORE UPDATE ON Message " +
                        "WHEN NEW.id = 'multipart-edited' BEGIN SELECT RAISE(ABORT, 'edit'); END"
                        else "CREATE TRIGGER fail_multipart BEFORE INSERT ON MessageAttachments " +
                            "WHEN NEW.asset_index = 1 BEGIN SELECT RAISE(ABORT, 'attachment'); END")
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (sqlFailure) {
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertTrue(runtime.database.messageAttachments.observeAttachments().first().isEmpty())
                            fixture.sql("DROP TRIGGER fail_multipart")
                        }
                        if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    })
                    if (interruption == Interruption.BEFORE_COMMIT) {
                        fixture.sql("UPDATE CellFile SET localPath = 'cached', assetTransferStatus = 'SAVED_INTERNALLY' WHERE uuid = 'cell-two'")
                    }
                }
                val received = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id)).value
                if (interruption != Interruption.AFTER_COMMIT && interruption != Interruption.MARKER) assertEquals("first", received?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyMultipart(runtime, edited, includeTail = true)
                    if (interruption == Interruption.BEFORE_COMMIT) {
                        val cached = runtime.database.messageAttachments.getAttachment("cell-two")
                        assertEquals("cached", cached.localPath)
                        assertEquals("SAVED_INTERNALLY", cached.assetTransferStatus)
                    }
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun assetProteusMetadata() = assetEffects(false)

    @Test
    fun assetMLSMetadata() = assetEffects(true)

    @Test
    fun assetSelfMetadata() = assetEffects(true, sender = SELF)

    @Test
    fun assetBeforeCommitReplay() = assetEffects(true, Interruption.BEFORE_COMMIT)

    @Test
    fun assetAfterCommitReplay() = assetEffects(true, Interruption.AFTER_COMMIT)

    @Test
    fun assetMarkerReplay() = assetEffects(false, Interruption.MARKER)

    @Test
    fun assetDeliveryFailure() = assetEffects(true, Interruption.DELIVERY)

    @Test
    fun assetKeysWriteFailure() = assetEffects(false, sqlFailure = "UPDATE ON MessageAssetContent")

    private fun assetEffects(mls: Boolean, interruption: Interruption? = null, sqlFailure: String? = null, sender: UserId = SENDER) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls, sender) { fixture ->
                val event = fixture.assetEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"), TIME + 1.seconds)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (sqlFailure != null) fixture.sql(
                    "CREATE TRIGGER interrupt_receive BEFORE $sqlFailure BEGIN SELECT RAISE(ABORT, 'interrupted'); END")
                if (interruption != null || sqlFailure != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (sqlFailure != null || interruption == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("asset", CONVERSATION_ID))
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20).isEmpty())
                        }
                    })
                    if (sqlFailure != null || interruption == Interruption.MARKER || interruption == Interruption.DELIVERY) {
                        fixture.sql("DROP TRIGGER interrupt_receive")
                    }
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id)).value
                if (interruption != Interruption.AFTER_COMMIT && interruption != Interruption.MARKER) {
                    assertEquals("last-image", result?.messageId)
                    assertEquals(NotificationMessageKind.PICTURE, result?.messageKind)
                    assertNull(result?.text)
                    assertEquals(sender != SELF, result?.shouldNotify)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyAssets(runtime, includeTail = true)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun assetPreviewAndKeysAcrossNotifications() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val preview = fixture.event("preview", fixture.encrypt("asset", fixture.assetContent("asset", complete = false)))
                val keys = fixture.event("keys", fixture.encrypt("asset", fixture.assetContent("asset", name = null, mime = "*/*", size = 0)))
                val duplicate = preview.copy(id = "duplicate")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val prefix = fixture.batch("preview-event", fixture.configEvent("fileSharing"), preview)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(prefix), prefix.id)).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(MessageEntity.Visibility.HIDDEN, runtime.database.messageDAO.getMessageById("asset", CONVERSATION_ID)?.visibility)
                })
                assertEquals(true, assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(keys), keys.id)).value?.shouldNotify)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(duplicate), duplicate.id)).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val stored = checkNotNull(runtime.database.messageDAO.getMessageById("asset", CONVERSATION_ID))
                    assertEquals(MessageEntity.Visibility.VISIBLE, stored.visibility)
                    assertEquals("asset.txt", assertIs<MessageEntityContent.Asset>(stored.content).assetName)
                    assertEquals(listOf("asset"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20).map { it.messageId })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun assetFollowupAuthorAndClientRules() = runTest {
        withContext(Dispatchers.Default) {
            for (sender in listOf(SELF, SENDER)) withFixture(false, sender) { fixture ->
                fixture.seed(storedText("asset").copy(
                    content = com.wire.kalium.logic.data.message.RegularMessageMapper(SELF).toMessageEntityContent(
                        fixture.assetContent("asset", complete = false)),
                    visibility = MessageEntity.Visibility.HIDDEN,
                    senderClientId = "original-client"))
                val event = fixture.batch("notification", fixture.configEvent("fileSharing"),
                    fixture.event("keys", fixture.encrypt("asset", fixture.assetContent("asset", name = null, mime = "*/*", size = 0))),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val stored = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("asset", CONVERSATION_ID))
                    val content = assertIs<MessageEntityContent.Asset>(stored.content)
                    assertEquals("original-client", stored.senderClientId)
                    assertEquals(SENDER.toDao(), stored.senderUserId)
                    assertEquals("asset.txt", content.assetName)
                    assertEquals(if (sender == SENDER) "asset" else "", content.assetId)
                    assertEquals(if (sender == SENDER) MessageEntity.Visibility.VISIBLE else MessageEntity.Visibility.HIDDEN, stored.visibility)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun assetRestrictionsAndFollowups() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.configEvent("fileSharing"),
                    fixture.event("preview", fixture.encrypt("allowed", fixture.assetContent("allowed", complete = false))),
                    fixture.event("keys", fixture.encrypt("allowed", fixture.assetContent("allowed", name = null, mime = "*/*", size = 0))),
                    fixture.event("blocked", fixture.encrypt("blocked", fixture.assetContent("blocked", name = "blocked.exe"))),
                    fixture.event("uppercase", fixture.encrypt("uppercase", fixture.assetContent("uppercase", name = "image.PNG", mime = "image/png"))),
                    fixture.event("mime", fixture.encrypt("mime", fixture.assetContent("mime", name = null, mime = "image/png"))),
                    fixture.event("empty", fixture.encrypt("empty", fixture.assetContent("empty", name = "", mime = "image/png"))),
                    fixture.event("keys-first", fixture.encrypt("keys-first", fixture.assetContent("keys-first", name = null, mime = "*/*", size = 0))),
                    fixture.event("late-preview", fixture.encrypt("keys-first", fixture.assetContent("keys-first", complete = false))),
                    fixture.event("restricted-followup", fixture.encrypt("blocked", fixture.assetContent("blocked", name = null, mime = "*/*", size = 0))),
                    fixture.configEvent("fileSharing", "disabled"),
                    fixture.event("disabled", fixture.encrypt("disabled", fixture.assetContent("disabled"))),
                    fixture.event("ephemeral", fixture.encrypt("ephemeral-asset", fixture.assetContent("ephemeral-asset"), expiresAfterMillis = 60_000)))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    allowedFileExtensions = listOf("txt", "png"))).value
                assertEquals("ephemeral-asset", result?.messageId)
                assertEquals(true, result?.shouldNotify)
                assertEquals(false, result?.mayPreview)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    for (id in listOf("blocked", "uppercase", "empty", "keys-first", "disabled", "ephemeral-asset")) {
                        assertIs<MessageEntityContent.RestrictedAsset>(checkNotNull(runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID)).content)
                    }
                    for (id in listOf("allowed", "mime")) {
                        assertIs<MessageEntityContent.Asset>(checkNotNull(runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID)).content)
                    }
                    assertEquals("allowed.txt", assertIs<MessageEntityContent.Asset>(
                        checkNotNull(runtime.database.messageDAO.getMessageById("allowed", CONVERSATION_ID)).content).assetName)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun assetConfigurationFailureStaysPending() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val event = fixture.event("notification", fixture.encrypt("asset", fixture.assetContent("asset")))
                val tail = fixture.event("tail", fixture.encrypt("first"))
                val next = fixture.encrypt("next")
                fixture.sql("INSERT INTO Metadata(key, stringValue) VALUES('file_sharing', 'invalid-json')")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("asset", CONVERSATION_ID))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    runtime.database.userPrefsDAO.persistFileSharingStatus(true, false)
                })
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id))
                fixture.verifyProcessedText(tail, "first")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun assetDeletionWithoutDownloadedFiles() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.configEvent("fileSharing"),
                    fixture.event("asset", fixture.encrypt("asset", fixture.assetContent("asset"))),
                    fixture.event("delete", fixture.encrypt("delete", MessageContent.DeleteMessage("asset"))))
                val tail = fixture.event("tail", fixture.encrypt("first"))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(MessageEntity.Visibility.DELETED,
                        runtime.database.messageDAO.getMessageById("asset", CONVERSATION_ID)?.visibility)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                })
                fixture.verifyProcessedText(tail, "first")
            }
        }
    }

    @Test
    fun assetDeletionProteusFiles() = assetFileDeletion(false)

    @Test
    fun assetDeletionMLSFiles() = assetFileDeletion(true)

    @Test
    fun assetDeletionBeforeCommitReplay() = assetFileDeletion(true, Interruption.BEFORE_COMMIT)

    @Test
    fun assetDeletionAfterCommitReplay() = assetFileDeletion(false, Interruption.AFTER_COMMIT)

    @Test
    fun assetDeletionMarkerReplay() = assetFileDeletion(true, Interruption.MARKER)

    @Test
    fun assetDeletionPartialFileFailure() = assetFileDeletion(false, failAfter = 2)

    @Test
    fun assetDeletionSQLFailure() = assetFileDeletion(true, sqlFailure = true)

    private fun assetFileDeletion(
        mls: Boolean,
        interruption: Interruption? = null,
        failAfter: Int? = null,
        sqlFailure: Boolean = false,
    ) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seedMessageFiles()
                val event = fixture.messageFileDeletionEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (interruption != null || failAfter != null || sqlFailure) {
                    if (sqlFailure) fixture.sql("CREATE TRIGGER fail_cleanup BEFORE DELETE ON Asset BEGIN SELECT RAISE(ABORT, 'cleanup'); END")
                    var removed = 0
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption, deleteFile = {
                        FileSystem.SYSTEM.delete(it.toPath(), mustExist = false)
                        removed += 1
                        if (removed == failAfter) error("Interrupted filesystem cleanup")
                    }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (failAfter != null || sqlFailure) {
                            assertEquals(MessageEntity.Visibility.VISIBLE,
                                runtime.database.messageDAO.getMessageById("remove-asset", CONVERSATION_ID)?.visibility)
                            assertNotNull(runtime.database.assetDAO.getAssetByKey("delete-asset").first())
                            assertNotNull(runtime.database.assetDAO.getAssetByKey("delete-preview").first())
                        }
                        if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                        if (sqlFailure) fixture.sql("DROP TRIGGER fail_cleanup")
                    })
                }
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), event.id))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMessageFiles(it) })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun assetDeletionAuthorizationAndEphemeralRules() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.seedMessageFiles()
                fixture.sql("UPDATE Message SET sender_user_id = '${SELF.value}@${SELF.domain}' WHERE id IN ('remove-asset', 'remove-preview')")
                fixture.sql("UPDATE Message SET expire_after_millis = 1000 WHERE id = 'remove-preview'")
                val event = fixture.batch("notification",
                    fixture.event("unauthorized", fixture.encrypt("delete-asset", MessageContent.DeleteMessage("remove-asset"))),
                    fixture.event("expiry", fixture.encrypt("delete-preview", MessageContent.DeleteMessage("remove-preview"))),
                    fixture.event("first", fixture.encrypt("first")))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(MessageEntity.Visibility.VISIBLE, runtime.database.messageDAO.getMessageById("remove-asset", CONVERSATION_ID)?.visibility)
                    assertTrue(FileSystem.SYSTEM.exists(fixture.messageFile("delete-asset").toPath()))
                    assertNull(runtime.database.messageDAO.getMessageById("remove-preview", CONVERSATION_ID))
                    assertFalse(FileSystem.SYSTEM.exists(fixture.messageFile("delete-preview").toPath()))
                })
            }
        }
    }

    @Test
    fun assetDeletionForMeAcrossConversations() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, SELF) { fixture ->
                fixture.seedDeleteForMeTarget()
                fixture.seedMessageFiles(DELETE_TARGET)
                val event = fixture.batch("notification", *MESSAGE_FILE_IDS.map { id ->
                    fixture.event("delete-$id", fixture.encrypt("delete-$id",
                        MessageContent.DeleteForMe(id, com.wire.kalium.logic.data.id.ConversationId(DELETE_TARGET.value, DELETE_TARGET.domain))))
                }.toTypedArray(), fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyMessageFiles(it, DELETE_TARGET, removed = true) })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun deletionOfSystemContentBeforeCommitReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification",
                    fixture.event("reset", fixture.encrypt("session-reset", MessageContent.ClientAction)),
                    fixture.event("delete", fixture.encrypt("delete-reset", MessageContent.DeleteMessage("session-reset"))),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(MessageEntity.Visibility.DELETED,
                        runtime.database.messageDAO.getMessageById("session-reset", CONVERSATION_ID)?.visibility)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun metadataSystemDeletionBeforeCommitReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.sql("UPDATE Conversation SET type = 'GROUP'")
                val ids = listOf("notification", "notification:1", "notification:2")
                val deletions = ids.map { id -> fixture.event("delete-$id", fixture.encrypt("delete-$id", MessageContent.DeleteMessage(id))) }
                val event = fixture.batch("notification", *(fixture.metadataEvents() + deletions +
                    fixture.event("first", fixture.encrypt("first"))).toTypedArray())
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertEquals("Renamed", conversation.name)
                    assertEquals(45_000, conversation.messageTimer)
                    assertEquals(ConversationEntity.ReceiptMode.ENABLED, conversation.receiptMode)
                    for (id in ids) assertEquals(MessageEntity.Visibility.DELETED,
                        runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID)?.visibility)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun membershipSystemDeletionBeforeCommitReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                fixture.sql("UPDATE Conversation SET type = 'GROUP'")
                val event = fixture.batch("notification", fixture.memberJoin(member = SENDER),
                    fixture.event("delete-join", fixture.encrypt("delete-join", MessageContent.DeleteMessage("notification"))),
                    fixture.memberLeave(member = SENDER),
                    fixture.event("delete-leave", fixture.encrypt("delete-leave", MessageContent.DeleteMessage("notification:2"))),
                    fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                    for (id in listOf("notification", "notification:2")) assertEquals(MessageEntity.Visibility.DELETED,
                        runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID)?.visibility)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun signalingProteusSelf() = signalingEffects(false, SELF)

    @Test
    fun signalingMLSSelf() = signalingEffects(true, SELF)

    @Test
    fun signalingProteusOtherSender() = signalingEffects(false, SENDER)

    @Test
    fun signalingMLSOtherSender() = signalingEffects(true, SENDER)

    @Test
    fun signalingBeforeCommitReplay() = signalingEffects(true, SELF, Interruption.BEFORE_COMMIT)

    @Test
    fun signalingAfterCommitReplay() = signalingEffects(true, SELF, Interruption.AFTER_COMMIT)

    @Test
    fun signalingMarkerReplay() = signalingEffects(false, SELF, Interruption.MARKER)

    @Test
    fun signalingTrackingWriteFailure() = signalingEffects(true, SELF,
        sqlFailure = "INSERT ON Metadata WHEN NEW.key = 'analytics_tracking_identifier' AND NEW.stringValue = 'received'")

    @Test
    fun signalingSystemWriteFailure() = signalingEffects(false, SELF,
        sqlFailure = "INSERT ON Message WHEN NEW.id = 'session-reset'")

    private fun signalingEffects(mls: Boolean, sender: UserId, interruption: Interruption? = null, sqlFailure: String? = null) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls, sender) { fixture ->
                val event = fixture.signalingEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"), TIME + 1.seconds)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (sqlFailure != null) fixture.sql(
                    "CREATE TRIGGER interrupt_receive BEFORE $sqlFailure BEGIN SELECT RAISE(ABORT, 'interrupted'); END")
                if (interruption != null || sqlFailure != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (sqlFailure != null) {
                            assertNull(runtime.database.messageDAO.getMessageById("session-reset", CONVERSATION_ID))
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertEquals("initial", runtime.database.userConfigDAO.getTrackingIdentifier())
                            assertEquals("earlier", runtime.database.userConfigDAO.getPreviousTrackingIdentifier())
                        }
                    })
                    if (sqlFailure != null || interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id)).value
                if (interruption != Interruption.AFTER_COMMIT && interruption != Interruption.MARKER) {
                    assertEquals("first", result?.text)
                    assertEquals(sender != SELF, result?.shouldNotify)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifySignals(runtime, includeTail = true)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun firstTrackingIdentifierDoesNotInventPrevious() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false, SELF) { fixture ->
                val event = fixture.event("notification", fixture.encrypt("tracking", MessageContent.DataTransfer(
                    MessageContent.DataTransfer.TrackingIdentifier("first-id"))))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals("first-id", runtime.database.userConfigDAO.getTrackingIdentifier())
                    assertNull(runtime.database.userConfigDAO.getPreviousTrackingIdentifier())
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun compositeProteusEffects() = compositeEffects(false)

    @Test
    fun compositeMLSEffects() = compositeEffects(true)

    @Test
    fun compositeBeforeCommitReplay() = compositeEffects(true, Interruption.BEFORE_COMMIT)

    @Test
    fun compositeAfterCommitReplay() = compositeEffects(true, Interruption.AFTER_COMMIT)

    @Test
    fun compositeMarkerReplay() = compositeEffects(false, Interruption.MARKER)

    @Test
    fun compositeButtonInsertFailure() = compositeEffects(true, sqlFailure = "INSERT ON ButtonContent")

    @Test
    fun compositeButtonSelectionFailure() = compositeEffects(false, sqlFailure = "UPDATE ON ButtonContent WHEN NEW.is_selected = 1")

    private fun compositeEffects(mls: Boolean, interruption: Interruption? = null, sqlFailure: String? = null) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.compositeEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (sqlFailure != null) {
                    fixture.sql("CREATE TRIGGER interrupt_receive BEFORE $sqlFailure BEGIN SELECT RAISE(ABORT, 'interrupted'); END")
                }
                if (interruption != null || sqlFailure != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (sqlFailure != null) {
                            assertNull(runtime.database.messageDAO.getMessageById("composite", CONVERSATION_ID))
                            assertNull(runtime.database.messageDAO.getMessageById("buttons-edited", CONVERSATION_ID))
                            assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        }
                    })
                    if (sqlFailure != null || interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id)).value
                if (interruption != Interruption.AFTER_COMMIT && interruption != Interruption.MARKER) {
                    assertEquals("buttons-edited", result?.messageId)
                    assertEquals(false, result?.shouldNotify)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val messages = runtime.database.messageDAO
                    assertNull(messages.getMessageById("composite", CONVERSATION_ID))
                    assertNull(messages.getMessageById("composite-edited", CONVERSATION_ID))
                    val stored = assertIs<MessageEntity.Regular>(messages.getMessageById("buttons-edited", CONVERSATION_ID))
                    assertIs<MessageEntity.EditStatus.Edited>(stored.editStatus)
                    val content = assertIs<MessageEntityContent.Composite>(stored.content)
                    assertEquals("replacement", content.text?.messageBody)
                    assertEquals(listOf("second", "third"), content.buttonList.map { it.id }.sorted())
                    assertEquals(listOf("second"), content.buttonList.filter { it.isSelected }.map { it.id })
                    assertEquals(2L, messages.getConversationUnreadEventsCount(CONVERSATION_ID))
                    assertEquals(listOf("composite", "tail"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value)
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun compositeCanAddTextToButtonOnlyOriginal() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification",
                    fixture.event("button-only", fixture.encrypt("button-only", MessageContent.Composite(null,
                        listOf(com.wire.kalium.logic.data.message.composite.Button("first", "first", false))))),
                    fixture.event("edit", fixture.encryptCompositeEdit("with-text", "button-only", "added text", emptyList())))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertEquals(false, assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value?.shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("button-only", CONVERSATION_ID))
                    val message = checkNotNull(runtime.database.messageDAO.getMessageById("with-text", CONVERSATION_ID))
                    val content = assertIs<MessageEntityContent.Composite>(message.content)
                    assertEquals("added text", content.text?.messageBody)
                    assertTrue(content.buttonList.isEmpty())
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun compositeSelfActionAndUnauthorizedChanges() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false, SELF) { fixture ->
                val original = storedText("composite", "original").copy(content = MessageEntityContent.Composite(
                    MessageEntityContent.Text("original"), listOf(
                        com.wire.kalium.persistence.dao.message.ButtonEntity("first", "first", false),
                        com.wire.kalium.persistence.dao.message.ButtonEntity("second", "second", false))))
                fixture.seed(original)
                val event = fixture.batch("notification",
                    fixture.event("self-action", fixture.encrypt("self-action", MessageContent.ButtonAction("composite", "second"))),
                    fixture.event("impostor-confirmation", fixture.encrypt("impostor-confirmation",
                        MessageContent.ButtonActionConfirmation("composite", "first"))),
                    fixture.event("impostor-edit", fixture.encryptCompositeEdit("impostor-edit", "composite", "impostor", listOf("first"))),
                    fixture.event("missing-confirmation", fixture.encrypt("missing-confirmation",
                        MessageContent.ButtonActionConfirmation("missing", null))))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val content = assertIs<MessageEntityContent.Composite>(
                        checkNotNull(runtime.database.messageDAO.getMessageById("composite", CONVERSATION_ID)).content)
                    assertEquals("original", content.text?.messageBody)
                    assertEquals(listOf("second"), content.buttonList.filter { it.isSelected }.map { it.id })
                    assertNull(runtime.database.messageDAO.getMessageById("impostor-edit", CONVERSATION_ID))
                    assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun pollAndVotesAreStoredAndUnknownPollVotesAreDropped() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val poll = MessageContent.Poll(
                    question = "Lunch?",
                    options = listOf(MessageContent.Poll.Option("a", "Pizza"), MessageContent.Poll.Option("b", "Sushi")),
                    allowMultipleAnswers = false
                )
                val event = fixture.batch("notification",
                    fixture.event("poll", fixture.encrypt("poll", poll)),
                    fixture.event("vote", fixture.encrypt("vote", MessageContent.PollVote("poll", listOf("b")))),
                    fixture.event("invalid-vote", fixture.encrypt("invalid-vote", MessageContent.PollVote("poll", listOf("a", "b")))),
                    fixture.event("orphan-vote", fixture.encrypt("orphan-vote", MessageContent.PollVote("missing", listOf("a")))))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value
                assertEquals("poll", result?.messageId)
                assertEquals("Lunch?", result?.text)
                assertEquals(NotificationMessageKind.TEXT, result?.messageKind)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val stored = checkNotNull(runtime.database.messageDAO.getMessageById("poll", CONVERSATION_ID))
                    val content = assertIs<MessageEntityContent.Poll>(stored.content)
                    assertEquals("Lunch?", content.question)
                    assertEquals(listOf("a", "b"), content.options.map { it.id })
                    assertEquals(listOf(listOf("b")), content.votes.map { it.selectedOptionIds })
                    assertNull(runtime.database.messageDAO.getMessageById("missing", CONVERSATION_ID))
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun regularContentProteusEffects() = regularContentEffects(false)

    @Test
    fun regularContentMLSEffects() = regularContentEffects(true)

    @Test
    fun regularContentSQLFailure() = regularContentEffects(true, Interruption.DELIVERY)

    @Test
    fun regularContentBeforeCommitReplay() = regularContentEffects(true, Interruption.BEFORE_COMMIT)

    @Test
    fun regularContentAfterCommitReplay() = regularContentEffects(true, Interruption.AFTER_COMMIT)

    private fun regularContentEffects(mls: Boolean, interruption: Interruption? = null) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.regularContentEvent()
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), event.id, interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        if (interruption == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("location", CONVERSATION_ID))
                            fixture.sql("DROP TRIGGER interrupt_receive")
                        }
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event, tail), event.id)).value
                if (interruption != Interruption.AFTER_COMMIT) {
                    assertEquals("location", result?.messageId)
                    assertEquals(true, result?.shouldNotify)
                    assertNull(result?.text)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val messages = runtime.database.messageDAO
                    assertIs<MessageEntityContent.Knock>(checkNotNull(messages.getMessageById("knock", CONVERSATION_ID)).content)
                    val location = assertIs<MessageEntityContent.Location>(checkNotNull(messages.getMessageById("location", CONVERSATION_ID)).content)
                    assertEquals("Berlin", location.name)
                    val unknown = assertIs<MessageEntityContent.Unknown>(checkNotNull(messages.getMessageById("unknown", CONVERSATION_ID)).content)
                    assertNotNull(unknown.encodedData)
                    assertNull(messages.getMessageById("ignored", CONVERSATION_ID))
                    assertEquals(setOf("knock", "location", "unknown", "first", "tail"),
                        runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId }.toSet())
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                })
                fixture.verifyProcessedText(tail, "tail")
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun regularContentUnknownAndMutedAreSilent() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val unknown = fixture.event("unknown", fixture.encryptUnknown("unknown", GenericMessage.UnknownStrategy.DISCARD_AND_WARN))
                val ignored = fixture.event("ignored", fixture.encryptUnknown("ignored", GenericMessage.UnknownStrategy.IGNORE))
                val knock = fixture.event("knock", fixture.encrypt("knock", MessageContent.Knock(true)))
                fixture.releasePrepared()
                assertEquals(false, assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(unknown, ignored), unknown.id)).value?.shouldNotify)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), ignored.id)).value)
                fixture.sql("UPDATE Conversation SET muted_status = 'ALL_MUTED'")
                assertEquals(false, assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(knock), knock.id)).value?.shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val unknownContent = assertIs<MessageEntityContent.Unknown>(
                        checkNotNull(runtime.database.messageDAO.getMessageById("unknown", CONVERSATION_ID)).content)
                    assertNull(unknownContent.encodedData)
                    assertEquals(1L, runtime.database.messageDAO.getConversationUnreadEventsCount(CONVERSATION_ID))
                })
            }
        }
    }

    @Test
    fun regularContentActionsAndEphemeral() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val event = fixture.batch("notification",
                    fixture.event("knock", fixture.encrypt("knock", MessageContent.Knock(false), expiresAfterMillis = 60_000)),
                    fixture.event("delete", fixture.encrypt("delete", MessageContent.DeleteMessage("knock"))),
                    fixture.event("location", fixture.encrypt("location", MessageContent.Location(52f, 13f, "Berlin"), expiresAfterMillis = 60_000)),
                    fixture.event("reaction", fixture.encrypt("reaction", MessageContent.Reaction("location", setOf("❤")))))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value
                assertEquals("location", result?.messageId)
                assertEquals(false, result?.mayPreview)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("knock", CONVERSATION_ID))
                    val location = checkNotNull(runtime.database.messageDAO.getMessageById("location", CONVERSATION_ID))
                    assertEquals(60_000L, location.expireAfterMs)
                    assertNull(location.selfDeletionEndDate)
                    assertEquals(setOf("❤️"), runtime.database.reactionDAO.getReaction("location", CONVERSATION_ID, SENDER.toDao()))
                    assertEquals(listOf("location"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun deletedOneOnOneCleanupContinuesReceiving() = deletedOneOnOneCleanup()

    @Test
    fun deletedOneOnOneCleanupSQLFailure() = deletedOneOnOneCleanup(Interruption.DELIVERY)

    @Test
    fun deletedOneOnOneCleanupBeforeCommitReplay() = deletedOneOnOneCleanup(Interruption.BEFORE_COMMIT)

    @Test
    fun deletedOneOnOneCleanupAfterCommitReplay() = deletedOneOnOneCleanup(Interruption.AFTER_COMMIT)

    @Test
    fun userDeletionBeforeOneOnOneResolutionUsesDeletedState() =
        deletedOneOnOneCleanup(Interruption.BEFORE_COMMIT, deleteEvent = true)

    private fun deletedOneOnOneCleanup(interruption: Interruption? = null, deleteEvent: Boolean = false) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.seedProteusConversation()
                val receiving = fixture.batch("notification",
                    fixture.creationEvent(response = fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE)),
                    fixture.event("text", fixture.encrypt("first", protocolMLS = false),
                        protocolMLS = false, conversationId = DELETE_TARGET))
                val event = if (deleteEvent) fixture.batch("notification", fixture.userDelete(SENDER), receiving) else receiving
                val next = fixture.encrypt("next", protocolMLS = false)
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.userDAO.updateActiveOneOnOneConversation(SENDER.toDao(), CONVERSATION_ID)
                    if (!deleteEvent) runtime.database.userDAO.markUserAsDeletedAndRemoveFromGroupConv(SENDER.toDao())
                    runtime.database.conversationDAO.setProposalTimer(
                        com.wire.kalium.persistence.dao.conversation.ProposalTimerEntity(GROUP_ID, TIME)
                    )
                })
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        if (interruption == Interruption.DELIVERY) {
                            assertTrue(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                            assertEquals(1, runtime.database.conversationDAO.getProposalTimers().first().size)
                            assertNull(runtime.database.messageDAO.getMessageById("first", DELETE_TARGET))
                            fixture.sql("DROP TRIGGER interrupt_receive")
                        }
                    })
                }
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val user = checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao()))
                    assertTrue(user.deleted)
                    assertEquals(CONVERSATION_ID, user.activeOneOnOneConversationId)
                    assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    assertNotNull(runtime.database.messageDAO.getMessageById("first", DELETE_TARGET))
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next", protocolMLS = false))
            }
        }
    }

    @Test
    fun deletedOneOnOneNewMetadataPreservesDeletionAndCleansActiveGroup() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val response = fixture.creationResponse().copy(
                    id = DELETE_TARGET.toApi(), type = ConversationResponse.Type.ONE_TO_ONE,
                    protocol = ConvProtocol.PROTEUS, groupId = null, epoch = null, mlsCipherSuiteTag = null
                )
                val event = fixture.batch("notification", fixture.creationEvent(response = response),
                    fixture.event("text", fixture.encrypt("first", protocolMLS = false),
                        protocolMLS = false, conversationId = DELETE_TARGET))
                val next = fixture.encrypt("next", protocolMLS = false)
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.userDAO.updateActiveOneOnOneConversation(SENDER.toDao(), CONVERSATION_ID)
                    runtime.database.userDAO.markUserAsDeletedAndRemoveFromGroupConv(SENDER.toDao())
                })
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val user = checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao()))
                    assertTrue(user.deleted)
                    assertEquals(CONVERSATION_ID, user.activeOneOnOneConversationId)
                    assertNotNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                    assertNotNull(runtime.database.messageDAO.getMessageById("first", DELETE_TARGET))
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next", protocolMLS = false))
            }
        }
    }

    @Test
    fun deletedOneOnOneCleanupNativeFailureStaysPending() = deletedOneOnOneCleanupPending(false)

    @Test
    fun deletedOneOnOneCleanupEarlierCiphertextStaysPending() = deletedOneOnOneCleanupPending(true)

    private fun deletedOneOnOneCleanupPending(earlierCiphertext: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val creation = fixture.creationEvent(response = fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE))
                val text = fixture.event("text", fixture.encrypt("first"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.userDAO.updateActiveOneOnOneConversation(SENDER.toDao(), CONVERSATION_ID)
                    runtime.database.userDAO.markUserAsDeletedAndRemoveFromGroupConv(SENDER.toDao())
                })
                val event = if (earlierCiphertext) fixture.batch(creation.id, text, creation) else creation
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, decorateMLSContext = { real ->
                    if (earlierCiphertext) real else object : MlsCoreCryptoContext by real {
                        override suspend fun wipeConversation(groupId: String) { error("Required wipe failed") }
                    }
                }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(creation.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertTrue(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun oneOnOneWelcomeMigratesHistory() = oneOnOneWelcome()

    @Test
    fun oneOnOneWelcomeSQLFailure() = oneOnOneWelcome(Interruption.DELIVERY)

    @Test
    fun oneOnOneWelcomeBeforeCommitReplay() = oneOnOneWelcome(Interruption.BEFORE_COMMIT)

    @Test
    fun oneOnOneWelcomeAfterCommitReplay() = oneOnOneWelcome(Interruption.AFTER_COMMIT)

    @Test
    fun oneOnOneWelcomeMarkerReplay() = oneOnOneWelcome(Interruption.MARKER)

    @Test
    fun assetOneOnOneMigrationReplay() = oneOnOneWelcome(Interruption.BEFORE_COMMIT, assetPrefix = true)

    private fun oneOnOneWelcome(interruption: Interruption? = null, assetPrefix: Boolean = false) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val second = QualifiedIDEntity("legacy-second", SELF.domain)
                fixture.seedProteusConversation()
                fixture.seedProteusConversation(second)
                fixture.seed(storedText("history").copy(conversationId = DELETE_TARGET),
                    storedText("history-second").copy(conversationId = second))
                val response = fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE)
                val event = fixture.batch("notification",
                    fixture.configEvent("fileSharing"),
                    fixture.event("proteus", fixture.encrypt("proteus-first",
                        if (assetPrefix) fixture.assetContent("asset") else MessageContent.Text("first"), protocolMLS = false),
                        protocolMLS = false, conversationId = DELETE_TARGET),
                    fixture.welcomeEvent(),
                    fixture.event("text", fixture.encrypt("first", if (assetPrefix) MessageContent.Composite(null, emptyList())
                        else MessageContent.Text("first"))))
                val next = fixture.encrypt("next")
                val proteusNext = fixture.encrypt("proteus-next", protocolMLS = false)
                fixture.releasePrepared()
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, interruption, fetchConversation = { response }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        if (interruption == Interruption.DELIVERY) {
                            assertNull(runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                            assertNotNull(runtime.database.messageDAO.getMessageById("history", DELETE_TARGET))
                            assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                        }
                        if (interruption in listOf(Interruption.DELIVERY, Interruption.MARKER)) fixture.sql("DROP TRIGGER interrupt_receive")
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(listOf(event), event.id, fetchConversation = { response })).value
                if (assetPrefix) {
                    assertEquals("proteus-first", result?.messageId)
                    assertEquals(CONVERSATION_ID.value, result?.conversationValue)
                    assertEquals(true, result?.shouldNotify)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val db = runtime.database
                    assertEquals(CONVERSATION_ID, db.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                    listOf("history", "history-second", "proteus-first", "first").forEach {
                        assertNotNull(db.messageDAO.getMessageById(it, CONVERSATION_ID))
                        assertNull(db.messageDAO.getMessageById(it, DELETE_TARGET))
                        assertNull(db.messageDAO.getMessageById(it, second))
                    }
                    val messages = db.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                    assertEquals(1, messages.filterIsInstance<MessageEntity.System>().count {
                        it.content == MessageEntityContent.ConversationStartedUnverifiedWarning
                    })
                    assertEquals(1, messages.filterIsInstance<MessageEntity.System>().count {
                        it.content is MessageEntityContent.ConversationProtocolChanged
                    })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(proteusNext, "proteus-next", protocolMLS = false))
            }
        }
    }

    @Test
    fun oneOnOneProteusCreationAndFallback() = runTest {
        withContext(Dispatchers.Default) {
            for (protocols in listOf("proteus", "")) withFixture(false, newConversation = true) { fixture ->
                val response = fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE)
                val profiles = KtxSerializer.json.decodeFromString<ListUsersDTO>(
                    """{"found":[{"qualified_id":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},"id":"${SENDER.value}","name":"Peer","accent_id":1,"assets":[],"legalhold_status":"disabled","supported_protocols":${if (protocols.isEmpty()) "[]" else "[\"proteus\"]"}}],"failed":[]}"""
                )
                val event = fixture.batch("notification", fixture.creationEvent(response = response),
                    fixture.event("text", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchProfiles = { profiles })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(CONVERSATION_ID,
                        runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun oneOnOneCreationNeedsExternalEstablishment() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val response = fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE)
                val event = fixture.batch("notification", fixture.creationEvent(response = response),
                    fixture.welcomeEvent(), fixture.event("text", fixture.encrypt("first")))
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
            }
        }
    }

    @Test
    fun oneOnOneDuplicateWelcomePreservesAppNoOp() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.welcomeEvent(), fixture.event("text", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun oneOnOneProtocolUpdateBeforeCreation() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false, newConversation = true) { fixture ->
                val response = fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE)
                val profile = fixture.plainEvent("profile", """{"type":"user.update","user":{"id":"${SENDER.value}","supported_protocols":["proteus"]}}""")
                val event = fixture.batch("notification", profile, fixture.creationEvent(response = response),
                    fixture.event("text", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(CONVERSATION_ID,
                        runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun protocolRefreshPastEarlierGroupBlocksReplay() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val oldText = fixture.event("old-text", fixture.encrypt("old-text"))
                fixture.prepareResetWelcome()
                val event = fixture.batch("notification", oldText, fixture.protocolEvent(ConvProtocol.MIXED),
                    fixture.welcomeEvent(), fixture.event("new-text", fixture.encrypt("new-text", groupId = RESET_GROUP_ID)))
                fixture.releasePrepared()
                val metadata: suspend () -> ConversationResponse = { fixture.creationResponse().copy(groupId = RESET_GROUP_ID) }
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT, fetchConversation = metadata))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNotNull(runtime.database.messageDAO.getMessageById("old-text", CONVERSATION_ID))
                    assertNotNull(runtime.database.messageDAO.getMessageById("new-text", CONVERSATION_ID))
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(RESET_GROUP_ID) })
                })
                // Current metadata now names the replacement group; the queued old ciphertext
                // has no retained authenticated conversation-to-old-group mapping.
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), event.id, fetchConversation = metadata))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertEquals(1uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                })
            }
        }
    }

    @Test
    fun mixedCreationWelcomeAndText() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val event = fixture.batch("notification", fixture.creationEvent(response = fixture.creationResponse().copy(protocol = ConvProtocol.MIXED)),
                    fixture.welcomeEvent(), fixture.event("text", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, Interruption.BEFORE_COMMIT))
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = assertIs<ConversationEntity.ProtocolInfo.Mixed>(
                        runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.protocolInfo
                    )
                    assertEquals(1uL, protocol.epoch)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun protocolChangeRefreshesMLSStateAndEpoch() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.sql("UPDATE Conversation SET protocol = 'PROTEUS', type = 'GROUP', mls_epoch = 7, mls_last_keying_material_update_date = 77")
                val event = fixture.batch("notification", fixture.protocolEvent(ConvProtocol.MIXED), fixture.protocolEvent(ConvProtocol.PROTEUS),
                    fixture.protocolEvent(ConvProtocol.MLS), fixture.event("text", fixture.encrypt("first", protocolMLS = false), protocolMLS = false))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { fixture.creationResponse() })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = assertIs<ConversationEntity.ProtocolInfo.MLS>(
                        runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.protocolInfo
                    )
                    assertEquals(1uL, protocol.epoch)
                    assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                    assertEquals(Instant.fromEpochMilliseconds(77), protocol.keyingMaterialLastUpdate)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun protocolChangeWithoutWelcomeKeepsMissingGroupPending() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.sql("UPDATE Conversation SET protocol = 'PROTEUS', type = 'GROUP', mls_epoch = 0")
                val event = fixture.protocolEvent(ConvProtocol.MIXED)
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { it.wipeConversation(GROUP_ID) }
                })
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { fixture.creationResponse().copy(protocol = ConvProtocol.MIXED, epoch = 4uL) }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = assertIs<ConversationEntity.ProtocolInfo.Mixed>(
                        runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.protocolInfo
                    )
                    assertEquals(4uL, protocol.epoch)
                    assertEquals(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE, protocol.groupState)
                    assertTrue(runtime.database.conversationDAO.getConversationsByGroupState(
                        ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE).any { it.id == CONVERSATION_ID })
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
            }
        }
    }

    @Test
    fun protocolMigrationTogether() = protocolMigration(false)

    @Test
    fun creationThenProtocolMigration() = protocolMigration(true)

    @Test
    fun protocolMigrationSQLFailure() = protocolMigration(true, Interruption.DELIVERY)

    @Test
    fun protocolMigrationBeforeCommitReplay() = protocolMigration(true, Interruption.BEFORE_COMMIT)

    @Test
    fun protocolMigrationAfterCommitReplay() = protocolMigration(true, Interruption.AFTER_COMMIT)

    @Test
    fun protocolMigrationMarkerReplay() = protocolMigration(true, Interruption.MARKER)

    private fun protocolMigration(creation: Boolean, interruption: Interruption? = null) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = creation) { fixture ->
                fixture.sql("UPDATE Conversation SET protocol = 'PROTEUS', type = 'GROUP', verification_status = 'DEGRADED'")
                val event = fixture.migrationEvent(creation)
                val next = fixture.encrypt("next")
                val proteusNext = fixture.encrypt("proteus-next", protocolMLS = false)
                fixture.releasePrepared()
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, interruption,
                        fetchConversation = { fixture.creationResponse() }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        if (interruption == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("final", CONVERSATION_ID))
                            assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                            fixture.sql("DROP TRIGGER interrupt_receive")
                        }
                        if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { fixture.creationResponse() })).value
                assertEquals(when (interruption) {
                    Interruption.AFTER_COMMIT -> "proteus-first" // MLS committed, outer Proteus transaction aborted.
                    Interruption.MARKER -> null
                    else -> "final"
                }, result?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    val protocol = assertIs<ConversationEntity.ProtocolInfo.MLS>(conversation.protocolInfo)
                    assertEquals(GROUP_ID, protocol.groupId)
                    assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                    assertEquals(1uL, protocol.epoch)
                    assertEquals(ConversationEntity.ReceiptMode.DISABLED, conversation.receiptMode)
                    val messages = runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                    assertEquals(2, messages.filterIsInstance<MessageEntity.System>().count {
                        it.content is MessageEntityContent.ConversationProtocolChanged
                    })
                })
                fixture.verifyStoredAndNext(next, "final", listOf("proteus-first", "first", "final"))
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(proteusNext, "proteus-next", protocolMLS = false))
            }
        }
    }

    @Test
    fun protocolRefreshFailureAndUnchangedMetadata() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.protocolEvent(ConvProtocol.MLS), fixture.event("text", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id))
                fixture.sql("CREATE TRIGGER fail_protocol BEFORE INSERT ON Conversation BEGIN SELECT RAISE(ABORT, 'protocol refresh failed'); END")
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), event.id, fetchConversation = { fixture.creationResponse() }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                })
                fixture.sql("DROP TRIGGER fail_protocol")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), event.id,
                    fetchConversation = { fixture.creationResponse().copy(name = "Current metadata") })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals("Current metadata", runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.name)
                    assertNotNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun protocolChangedColumnsAndMissingConversation() = runTest {
        withContext(Dispatchers.Default) {
            for (missing in listOf(false, true)) withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.protocolEvent(ConvProtocol.MIXED), fixture.event("text", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (missing) fixture.sql("DELETE FROM Conversation")
                fixture.sql("CREATE TRIGGER fail_protocol BEFORE UPDATE OF mls_group_state ON Conversation BEGIN SELECT RAISE(ABORT, 'MLS state write failed'); END")
                if (!missing) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id,
                        fetchConversation = { fixture.creationResponse().copy(protocol = ConvProtocol.MIXED) }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertIs<ConversationEntity.ProtocolInfo.MLS>(
                            runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.protocolInfo
                        )
                    })
                }
                fixture.sql("DROP TRIGGER fail_protocol")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { fixture.creationResponse().copy(protocol = ConvProtocol.MIXED) })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = assertIs<ConversationEntity.ProtocolInfo.Mixed>(
                        runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.protocolInfo
                    )
                    assertEquals(1uL, protocol.epoch)
                    assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun protocolDeletedConversationIsStale() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.batch("notification", fixture.protocolEvent(ConvProtocol.MIXED), fixture.event("text", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = {
                        throw KaliumException.InvalidRequestError(GenericAPIErrorResponse(code = 404, message = "Deleted", label = "no-conversation"))
                    }
                )).value?.text)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun inactiveWelcomeAndTextTogether() = inactiveWelcomeTogether(null)

    @Test
    fun inactiveWelcomeTogetherSQLFailure() = inactiveWelcomeTogether(Interruption.DELIVERY)

    @Test
    fun inactiveWelcomeTogetherBeforeCommitReplay() = inactiveWelcomeTogether(Interruption.BEFORE_COMMIT)

    @Test
    fun inactiveWelcomeTogetherAfterCommitReplay() = inactiveWelcomeTogether(Interruption.AFTER_COMMIT)

    private fun inactiveWelcomeTogether(interruption: Interruption?) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val removal = fixture.prepareRejoinWelcome()
                fixture.sql("UPDATE Conversation SET mls_proposal_timer = '$TIME'")
                val page = listOf(fixture.batch("notification", fixture.event("inactive", removal), fixture.welcomeEvent(),
                    fixture.event("text", fixture.encrypt("first"))))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(page, "notification", interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        if (interruption == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertEquals(1uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                            assertEquals(1, runtime.database.conversationDAO.getProposalTimers().first().size)
                            fixture.sql("DROP TRIGGER interrupt_receive")
                        }
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "notification")).value
                assertEquals(if (interruption == Interruption.AFTER_COMMIT) null else "first", result?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                    assertEquals(3uL, (protocol as ConversationEntity.ProtocolInfo.MLS).epoch)
                    assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                    assertNotNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                    assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                    val messages = runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                    assertEquals(1, messages.count { it is MessageEntity.System && it.content == MessageEntityContent.MLSWrongEpochWarning })
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun resetThenWelcomeAndText() = resetRoundTrip(welcomeFirst = false, together = true)

    @Test
    fun welcomeThenResetAndText() = resetRoundTrip(welcomeFirst = true, together = true)

    @Test
    fun resetThenLaterWelcomeAndText() = resetRoundTrip(welcomeFirst = false, together = false)

    @Test
    fun welcomeThenLaterResetAndText() = resetRoundTrip(welcomeFirst = true, together = false)

    @Test
    fun resetRequiredSQLFailure() = resetRoundTrip(false, true, Interruption.DELIVERY)

    @Test
    fun resetBeforeNativeCommitReplay() = resetRoundTrip(false, true, Interruption.BEFORE_COMMIT)

    @Test
    fun welcomeBeforeResetNativeCommitReplay() = resetRoundTrip(true, true, Interruption.BEFORE_COMMIT)

    @Test
    fun resetAfterNativeCommitReplay() = resetRoundTrip(false, true, Interruption.AFTER_COMMIT)

    @Test
    fun welcomeBeforeResetMarkerReplay() = resetRoundTrip(true, true, Interruption.MARKER)

    private fun resetRoundTrip(welcomeFirst: Boolean, together: Boolean, interruption: Interruption? = null) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.prepareResetWelcome()
                val beforeText = if (welcomeFirst) listOf(fixture.welcomeEvent(), fixture.resetEvent())
                    else listOf(fixture.resetEvent(), fixture.welcomeEvent())
                val events = beforeText + fixture.event("notification", fixture.encrypt("first", groupId = RESET_GROUP_ID))
                val page = if (together) listOf(fixture.batch("notification", *events.toTypedArray())) else events
                val next = fixture.encrypt("next", groupId = RESET_GROUP_ID)
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.conversationDAO.setProposalTimer(
                        com.wire.kalium.persistence.dao.conversation.ProposalTimerEntity(GROUP_ID, TIME)
                    )
                })
                if (!together) {
                    assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(beforeText.first()), beforeText.first().id)).value)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val stored = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        val protocol = assertIs<ConversationEntity.ProtocolInfo.MLS>(stored.protocolInfo)
                        assertEquals(if (welcomeFirst) GROUP_ID else RESET_GROUP_ID, protocol.groupId)
                        assertEquals(if (welcomeFirst) ConversationEntity.GroupState.ESTABLISHED
                            else ConversationEntity.GroupState.PENDING_AFTER_RESET, protocol.groupState)
                        checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                            assertEquals(welcomeFirst, context.conversationExists(GROUP_ID))
                            assertEquals(welcomeFirst, context.conversationExists(RESET_GROUP_ID))
                        }
                    })
                }
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(page, "notification", interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        if (interruption == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            val protocol = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                            assertEquals(GROUP_ID, assertIs<ConversationEntity.ProtocolInfo.MLS>(protocol).groupId)
                            assertEquals(1, runtime.database.conversationDAO.getProposalTimers().first().size)
                            checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                                assertTrue(context.conversationExists(GROUP_ID))
                                assertFalse(context.conversationExists(RESET_GROUP_ID))
                            }
                        }
                        fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "notification")).value
                assertEquals(if (interruption in listOf(Interruption.AFTER_COMMIT, Interruption.MARKER)) null else "first", result?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                    assertEquals(RESET_GROUP_ID, assertIs<ConversationEntity.ProtocolInfo.MLS>(protocol).groupId)
                    assertEquals(1uL, protocol.epoch)
                    assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                    assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
                val duplicate = fixture.resetEvent("reset-again")
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(duplicate), duplicate.id)).value)
                fixture.verifyStoredAndNext(next, "first", groupId = RESET_GROUP_ID)
            }
        }
    }

    @Test
    fun resetNativeFailureLeavesOldGroupUsable() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val oldText = fixture.encrypt("old-text")
                fixture.prepareResetWelcome()
                val event = fixture.batch("notification", fixture.resetEvent(), fixture.welcomeEvent(),
                    fixture.event("text", fixture.encrypt("first", groupId = RESET_GROUP_ID)))
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", decorateMLSContext = { context ->
                    object : MlsCoreCryptoContext by context {
                        override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
                            if (groupId == RESET_GROUP_ID) error("Failed after native wipe")
                            return context.conversationExists(groupId)
                        }
                    }
                }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertEquals(GROUP_ID, (checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        .protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(oldText, "old-text"))
            }
        }
    }

    @Test
    fun resetAfterOldCiphertextRemainsPending() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val oldText = fixture.encrypt("old-text")
                fixture.prepareResetWelcome()
                val event = fixture.batch("notification", fixture.event("old-text", oldText), fixture.resetEvent(), fixture.welcomeEvent())
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("old-text", CONVERSATION_ID))
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(RESET_GROUP_ID) })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(oldText, "old-text"))
            }
        }
    }

    @Test
    fun nativeWipeCannotReplayEarlierCiphertext() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val text = fixture.encrypt("first")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, text))
                        context.wipeConversation(GROUP_ID)
                    }
                })
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertFalse(context.conversationExists(GROUP_ID))
                        val replay = assertIs<Either.Left<*>>(decryptMLSMessage(context, text, GroupID(GROUP_ID)))
                        println("NATIVE_WIPED_GROUP_REPLAY_FAILURE: ${replay.value}")
                    }
                })
            }
        }
    }

    @Test
    fun nativeInactiveWelcomeReplayClassification() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val removal = fixture.prepareRejoinWelcome()
                val welcome = (fixture.welcomeEvent().toEventResponse().payload.orEmpty().single()
                    as EventContentDTO.Conversation.MLSWelcomeDTO).message
                val text = fixture.encrypt("first")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertIs<Either.Right<*>>(decryptMLSMessage(context, removal, GroupID(GROUP_ID)))
                        assertFalse(context.conversationExists(GROUP_ID))
                        assertEquals(GROUP_ID, context.processWelcomeMessage(Base64.decode(welcome)))
                        assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, text))
                    }
                })
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        val replay = assertIs<Either.Left<*>>(decryptMLSMessage(context, removal, GroupID(GROUP_ID)))
                        println("NATIVE_INACTIVE_WELCOME_REPLAY_RESULT: ${replay.value}")
                        assertEquals(MLSFailure.StaleCommit, replay.value)
                        assertEquals(3uL, context.conversationEpoch(GROUP_ID))
                        assertEquals(MLSFailure.DuplicateMessage,
                            assertIs<Either.Left<*>>(decryptMLSMessage(context, text, GroupID(GROUP_ID))).value)
                    }
                })
            }
        }
    }

    @Test
    fun membershipRefreshKeepsLatestGroup() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                val event = fixture.batch("notification", fixture.event("addition", addition), fixture.memberJoin())
                val replacementId = Base64.encode("replacement-group".encodeToByteArray())
                val response = fixture.creationResponse().copy(groupId = replacementId, epoch = 4uL)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), "notification", fetchConversation = { response }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    val protocol = assertIs<ConversationEntity.ProtocolInfo.MLS>(conversation.protocolInfo)
                    assertEquals(replacementId, protocol.groupId)
                    assertEquals(4uL, protocol.epoch)
                    assertEquals(ConversationEntity.GroupState.PENDING_JOIN, protocol.groupState)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun recoveryWelcomeAfterInactiveRemoval() = recoveryWelcome(false, null)

    @Test
    fun recoveryWelcomeAfterSelfJoin() = recoveryWelcome(true, null)

    @Test
    fun recoveryWelcomeSQLFailure() = recoveryWelcome(true, Interruption.DELIVERY)

    @Test
    fun recoveryWelcomeBeforeNativeCommitReplay() = recoveryWelcome(false, Interruption.BEFORE_COMMIT)

    @Test
    fun recoveryWelcomeAfterNativeCommitReplay() = recoveryWelcome(true, Interruption.AFTER_COMMIT)

    @Test
    fun recoveryWelcomeRefreshFailureFallback() = recoveryWelcome(true, null, refreshFails = true)

    private fun recoveryWelcome(selfLeave: Boolean, interruption: Interruption?, refreshFails: Boolean = false) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val inactive = fixture.event("inactive", fixture.prepareRejoinWelcome())
                val prefix = if (selfLeave) listOf(inactive, fixture.memberLeave("leave", SELF)) else listOf(inactive)
                val joined = if (selfLeave) listOf(fixture.memberJoin("self-join", SELF)) else emptyList()
                val target = fixture.batch("notification", *(joined + listOf(
                    fixture.welcomeEvent(), fixture.event("text", fixture.encrypt("first"))
                )).toTypedArray())
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(prefix, prefix.last().id))
                var refreshes = 0
                val refresh: suspend () -> ConversationResponse = {
                    refreshes++
                    if (refreshFails) throw KaliumException.NoNetwork()
                    fixture.creationResponse().copy(name = "Rejoined group", epoch = 3uL, messageTimer = 45_000)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    assertEquals(!selfLeave, runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID) != null)
                    assertNull(runtime.database.eventDAO.getEventById("notification"))
                    if (selfLeave) {
                        runtime.database.userDAO.upsertUser(user(THIRD))
                        runtime.database.memberDAO.insertMember(com.wire.kalium.persistence.dao.member.MemberEntity(
                            THIRD.toDao(), com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member
                        ), CONVERSATION_ID)
                    }
                })
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(target), "notification", interruption, fetchConversation = refresh))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        if (interruption == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                            assertNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                            assertNotNull(runtime.database.memberDAO.getMemberRole(THIRD.toDao(), CONVERSATION_ID))
                            fixture.sql("DROP TRIGGER interrupt_receive")
                        }
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(listOf(target), "notification", fetchConversation = refresh)
                ).value
                assertEquals(if (interruption == Interruption.AFTER_COMMIT) null else "first", result?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                    assertEquals(ConversationEntity.GroupState.ESTABLISHED, (protocol as ConversationEntity.ProtocolInfo.MLS).groupState)
                    assertEquals(3uL, protocol.epoch)
                    assertNotNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                    assertNotNull(runtime.database.messageDAO.getMessageById("inactive:mls-removal", CONVERSATION_ID))
                    if (selfLeave) {
                        assertEquals(!refreshFails, runtime.database.memberDAO.getMemberRole(THIRD.toDao(), CONVERSATION_ID) == null)
                        val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertEquals(if (refreshFails) "Fixture" else "Rejoined group", conversation.name)
                        assertEquals(if (refreshFails) null else 45_000L, conversation.messageTimer)
                        assertEquals(if (interruption == null) 1 else 2, refreshes)
                    }
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun recoveryWelcomeCannotUndoAccountLeave() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val inactive = fixture.event("inactive", fixture.prepareRejoinWelcome())
                val page = listOf(inactive, fixture.memberLeave("leave", SELF), fixture.welcomeEvent(),
                    fixture.event("notification", fixture.encrypt("first")))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(page.take(2), "leave"))
                assertIs<Either.Left<*>>(fixture.receive(page.drop(2), "notification"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("welcome")).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                })
            }
        }
    }

    @Test
    fun membershipRefreshThenCreationRetainsMembers() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                val event = fixture.batch("notification", fixture.memberJoin(), fixture.event("addition", addition),
                    fixture.creationEvent(), fixture.event("text", fixture.encrypt("first")))
                val response = fixture.creationResponse().let { response ->
                    response.copy(name = "Refreshed group", members = response.members.copy(
                        otherMembers = response.members.otherMembers + ConversationMemberDTO.Other(THIRD.toApi(), "wire_member")
                    ))
                }
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), "notification", fetchConversation = { response }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(setOf(SELF.toDao(), SENDER.toDao(), THIRD.toDao()),
                        runtime.database.memberDAO.getConversationMembers(CONVERSATION_ID).toSet())
                    assertEquals("Refreshed group", runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.name)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun mixedProtocolOrdered() = mixedProtocolRoundTrip(null)

    @Test
    fun mixedProtocolRequiredSQLFailure() = mixedProtocolRoundTrip(Interruption.DELIVERY)

    @Test
    fun mixedProtocolPartialNativeCommitReplay() = mixedProtocolRoundTrip(Interruption.AFTER_COMMIT)

    @Test
    fun mixedProtocolMarkerFailureReplay() = mixedProtocolRoundTrip(Interruption.MARKER)

    private fun mixedProtocolRoundTrip(interruption: Interruption?) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val proteusConversation = QualifiedIDEntity("proteus-conversation", SELF.domain)
                suspend fun proteusText(id: String) = fixture.event(
                    id, fixture.encrypt(id, protocolMLS = false), protocolMLS = false, conversationId = proteusConversation
                )
                val first = proteusText("proteus-first")
                val second = fixture.event("mls-first", fixture.encrypt("mls-first"))
                val third = proteusText("proteus-last")
                val fourth = fixture.event("mls-last", fixture.encrypt("mls-last"))
                val notification = fixture.batch("notification", first, second, third, fourth)
                val following = fixture.batch(
                    "following", first, second, proteusText("proteus-fresh"), fixture.event("mls-fresh", fixture.encrypt("mls-fresh"))
                )
                val nextProteus = fixture.encrypt("next-proteus", protocolMLS = false)
                val nextMLS = fixture.encrypt("next-mls")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.conversationDAO.insertConversation(
                        conversation(ConversationEntity.ProtocolInfo.Proteus).copy(id = proteusConversation)
                    )
                    runtime.database.memberDAO.insertMembersWithQualifiedId(
                        listOf(SELF, SENDER).map {
                            com.wire.kalium.persistence.dao.member.MemberEntity(
                                it.toDao(), com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member
                            )
                        }, proteusConversation
                    )
                })
                if (interruption != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(notification, following), "notification", interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("following")).isProcessed)
                        if (interruption == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("proteus-first", proteusConversation))
                            assertNull(runtime.database.messageDAO.getMessageById("mls-first", CONVERSATION_ID))
                        }
                        if (interruption in listOf(Interruption.DELIVERY, Interruption.MARKER)) {
                            fixture.sql("DROP TRIGGER interrupt_receive")
                        }
                    })
                }
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(if (interruption == null) listOf(notification, following) else emptyList(), "notification")
                ).value
                // The inner MLS store commits first. Replay after its commit can select
                // only the still-fresh Proteus payloads; a fully committed retry is silent.
                assertEquals(when (interruption) {
                    Interruption.AFTER_COMMIT -> "proteus-last"
                    Interruption.MARKER -> null
                    else -> "mls-last"
                }, result?.text)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(emptyList(), "following")
                ).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    for (id in listOf("proteus-first", "proteus-last", "proteus-fresh")) {
                        assertEquals(id, (assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById(
                            id, proteusConversation
                        )).content as MessageEntityContent.Text).messageBody)
                    }
                    for (id in listOf("mls-first", "mls-last", "mls-fresh")) {
                        assertNotNull(runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID))
                    }
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("following")).isProcessed)
                    val deliveries = runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10)
                    assertEquals(6, deliveries.size)
                    assertEquals(6, deliveries.map { it.conversationId to it.messageId }.distinct().size)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(nextProteus, "next-proteus", protocolMLS = false))
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(nextMLS, "next-mls"))
            }
        }
    }

    @Test
    fun sessionRefreshProteus() = sessionRefreshReceiving(false)

    @Test
    fun sessionRefreshMLS() = sessionRefreshReceiving(true)

    private fun sessionRefreshReceiving(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.sessionRefreshEvents()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                var issued = 0
                var fetched = 0
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification",
                    refreshSession = { fixture.refreshHandler(it, issue = { fixture.refreshResponse(++issued) }) },
                    fetchConnectionPeer = {
                        assertEquals(++fetched, issued)
                        fixture.connectionProfile(it)
                    })).value?.text)
                assertEquals(2, issued)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyRefreshedSession(it, issued) })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value)
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun sessionRefreshReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (interruption in listOf(
                Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                withFixture(mls) { fixture ->
                    val event = fixture.sessionRefreshEvents()
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    var issued = 0
                    val refresh = { runtime: AppleAccountRuntime -> fixture.refreshHandler(runtime, issue = { fixture.refreshResponse(++issued) }) }
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", interruption,
                        refreshSession = refresh, fetchConnectionPeer = fixture::connectionProfile))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        fixture.verifyRefreshedSession(runtime, 2)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    })
                    if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification", refreshSession = refresh,
                        fetchConnectionPeer = fixture::connectionProfile))
                    assertEquals(4, issued)
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyRefreshedSession(it, 4) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
                }
            }
        }
    }

    @Test
    fun sessionRefreshRequiredFailures() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (failure in listOf("network", "persistence", "later-fetch")) {
                withFixture(mls) { fixture ->
                    val event = fixture.sessionRefreshEvents()
                    val tail = fixture.event("tail", fixture.encrypt("tail"))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    var issued = 0
                    var failing = true
                    val refresh = { runtime: AppleAccountRuntime -> fixture.refreshHandler(runtime,
                        failWrite = { failing && failure == "persistence" }, issue = {
                            if (failing && failure == "network") NetworkResponse.Error(KaliumException.NoNetwork())
                            else fixture.refreshResponse(++issued)
                        }) }
                    val peer: suspend (com.wire.kalium.network.api.model.UserId) -> com.wire.kalium.network.api.model.UserProfileDTO = {
                        check(!failing || failure != "later-fetch") { "Follow-up request failed" }
                        fixture.connectionProfile(it)
                    }
                    repeat(2) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), "tail", refreshSession = refresh, fetchConnectionPeer = peer))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            for (id in listOf("notification", "tail")) assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(id)).isProcessed)
                            for (id in listOf("prefix", "first", "tail")) assertNull(runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID))
                            assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        })
                    }
                    failing = false
                    assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification",
                        refreshSession = refresh, fetchConnectionPeer = peer)).value?.text)
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifyRefreshedSession(it, issued) })
                    fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first", "tail"))
                }
            }
        }
    }

    @Test
    fun selfConversationProteus() = selfConversationReceiving(false)

    @Test
    fun selfConversationMLS() = selfConversationReceiving(true)

    private fun selfConversationReceiving(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            for (mode in if (mls) listOf("together", "ordered", "welcome-first", "welcome-only") else listOf("together", "ordered")) {
                withFixture(mls, senderId = SELF, newConversation = true) { fixture ->
                    fixture.seedDeleteForMeTarget()
                    val page = fixture.selfConversationPage(mode)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    if (mode == "ordered") {
                        assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page.filter { it.id == "creation" }, "creation")).value)
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            assertEquals(CONVERSATION_ID, runtime.database.conversationDAO.getSelfConversationId(
                                if (mls) ConversationEntity.Protocol.MLS else ConversationEntity.Protocol.PROTEUS))
                            if (mls) assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                        })
                    }
                    val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "notification",
                        fetchConversation = { fixture.selfConversationResponse() })).value
                    assertFalse(checkNotNull(result).shouldNotify)
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifySelfConversation(it, mode != "welcome-only") })
                    assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value)
                    fixture.verifyStoredAndNext(next, "first", deliveries = emptyList())
                }
            }
        }
    }

    @Test
    fun selfConversationReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (interruption in listOf(
                Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                withFixture(mls, senderId = SELF, newConversation = true) { fixture ->
                    fixture.seedDeleteForMeTarget()
                    val page = fixture.selfConversationPage("together")
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(page, "notification", interruption))
                    if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    val retry = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value
                    assertTrue(retry?.shouldNotify != true)
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifySelfConversation(it) })
                    val duplicates = listOfNotNull(fixture.creationEvent("duplicate-creation", fixture.selfConversationResponse()),
                        if (mls) fixture.welcomeEvent("duplicate-welcome") else null)
                    assertIs<Either.Right<*>>(fixture.receive(duplicates, duplicates.last().id))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifySelfConversation(it) })
                    fixture.verifyStoredAndNext(next, "first", deliveries = emptyList())
                }
            }
        }
    }

    @Test
    fun selfConversationRequiredWrites() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (table in listOf("Conversation", "Member", "Message")) {
                withFixture(mls, senderId = SELF, newConversation = true) { fixture ->
                    fixture.seedDeleteForMeTarget()
                    val page = fixture.selfConversationPage("together")
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    val condition = if (table == "Message") "WHEN NEW.content_type = 'CONVERSATION_STARTED_UNVERIFIED_WARNING'" else ""
                    fixture.sql("CREATE TRIGGER fail_self BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'self write failed'); END")
                    assertIs<Either.Left<*>>(fixture.receive(page, "notification"))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertNotNull(runtime.database.messageDAO.getMessageById("delete-me", DELETE_TARGET))
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        if (mls) assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    })
                    fixture.sql("DROP TRIGGER fail_self")
                    assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification"))
                    assertIs<Either.Right<*>>(fixture.open { fixture.verifySelfConversation(it) })
                    fixture.verifyStoredAndNext(next, "first", deliveries = emptyList())
                }
            }
        }
    }

    @Test
    fun selfWelcomeRequiredMetadataAndNative() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, senderId = SELF, newConversation = true) { fixture ->
                fixture.seedDeleteForMeTarget()
                val page = fixture.selfConversationPage("welcome-only")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(page, "notification", fetchConversation = { error("metadata unavailable") }))
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "notification", fetchConversation = {
                    fixture.selfConversationResponse().copy(members = ConversationMembersResponse(null, emptyList())) }))
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "notification",
                    fetchConversation = { fixture.selfConversationResponse() },
                    decorateMLSContext = { context -> object : MlsCoreCryptoContext by context {
                        override suspend fun processWelcomeMessage(message: ByteArray): String = error("welcome failed")
                    } }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById("delete-me", DELETE_TARGET))
                })
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification",
                    fetchConversation = { fixture.selfConversationResponse() }))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifySelfConversation(it, expectWarning = false) })
                fixture.verifyStoredAndNext(next, "first", deliveries = emptyList())
            }
        }
    }

    @Test
    fun creationProteusOrdered() = creationRoundTrip(false, false)

    @Test
    fun creationProteusTogether() = creationRoundTrip(false, true)

    @Test
    fun creationMLSOrdered() = creationRoundTrip(true, false)

    @Test
    fun creationMLSTogether() = creationRoundTrip(true, true)

    private fun creationRoundTrip(mls: Boolean, together: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls, newConversation = true) { fixture ->
                val page = fixture.creationPage(together)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                // Exercise separate attempts as well as processing the full queue toward the target.
                if (!together) {
                    assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page.filter { it.id == "creation" }, "creation")).value)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val protocol = runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.protocolInfo
                        if (mls) {
                            assertEquals(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE,
                                assertIs<ConversationEntity.ProtocolInfo.MLS>(protocol).groupState)
                            assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                        }
                    })
                }
                val received = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "notification")).value
                assertEquals("first", received?.text)
                assertTrue(checkNotNull(received).shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyCreation(it) })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun pendingConnectionCreationAndReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(null, Interruption.BEFORE_COMMIT,
            Interruption.AFTER_COMMIT, Interruption.MARKER)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val creation = fixture.pendingConnectionCreation()
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    creation, creation, fixture.event("first", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                if (stage != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                    if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        fixture.verifyPendingConnectionCreation(runtime)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        runtime.database.conversationDAO.updateConversationName(DELETE_TARGET, "Newer name", TIME + 20.seconds)
                        runtime.database.memberDAO.updateMemberRole(SELF.toDao(), DELETE_TARGET,
                            com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member)
                    })
                }
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyPendingConnectionCreation(runtime, if (stage == null) "Pending connection" else "Newer name",
                        if (stage == null) com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin
                        else com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member)
                })
                val duplicate = creation.copy(id = "duplicate-creation")
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(duplicate), duplicate.id)).value)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), duplicate.id)).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    fixture.verifyPendingConnectionCreation(runtime, if (stage == null) "Pending connection" else "Newer name",
                        if (stage == null) com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin
                        else com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member)
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun pendingConnectionCreationThenSentMetadata() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(Interruption.BEFORE_COMMIT,
            Interruption.AFTER_COMMIT, Interruption.MARKER)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.pendingConnectionCreation(), fixture.connectionEvent("sent", DELETE_TARGET.value, SENDER),
                    fixture.event("first", fixture.encrypt("first")))
                val response = fixture.sentConnectionResponse().copy(id = DELETE_TARGET.toApi(), protocol = ConvProtocol.PROTEUS,
                    groupId = null, epoch = null, mlsCipherSuiteTag = null)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage,
                    fetchConversation = { response }, fetchConnectionPeer = fixture::connectionProfile))
                if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                // SENT added an unnamed placeholder after creation. Canonical replay now
                // requires that profile; unavailable transport must leave the queue pending.
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), event.id,
                    fetchConversation = { response }, fetchConnectionPeer = fixture::connectionProfile))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                })
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id,
                    fetchConversation = { response }, fetchConnectionPeer = fixture::connectionProfile,
                    fetchProfiles = { ListUsersDTO(usersFound = listOf(fixture.connectionProfile(UserId("sent-incomplete", SELF.domain).toApi()))) }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val db = runtime.database
                    val pending = checkNotNull(db.conversationDAO.getConversationById(DELETE_TARGET))
                    assertEquals(ConversationEntity.Type.CONNECTION_PENDING, pending.type)
                    assertEquals("Created group", pending.name)
                    assertIs<ConversationEntity.ProtocolInfo.Proteus>(pending.protocolInfo)
                    assertEquals(ConnectionEntity.State.SENT, db.connectionDAO.getConnection(DELETE_TARGET)?.status)
                    assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin, db.memberDAO.getMemberRole(SELF.toDao(), DELETE_TARGET))
                    assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin, db.memberDAO.getMemberRole(SENDER.toDao(), DELETE_TARGET))
                    assertEquals(1, db.messageDAO.getMessagesByConversationAndVisibility(DELETE_TARGET, 100, 0).first().count {
                        it.content == MessageEntityContent.ConversationStartedUnverifiedWarning })
                })
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun pendingConnectionCreationRequiredWritesKeepQueuePending() = runTest {
        for (mls in listOf(false, true)) for (table in listOf("Conversation", "Member", "Message")) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.pendingConnectionCreation(), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val condition = if (table == "Message") "WHEN NEW.conversation_id = '$DELETE_TARGET'" else ""
                fixture.sql("CREATE TRIGGER fail_pending_create BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'fixture'); END")
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id))
                    fixture.verifyPendingTextQueue(event, tail)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                        assertTrue(runtime.database.memberDAO.getConversationMembers(DELETE_TARGET).isEmpty())
                    })
                }
                fixture.sql("DROP TRIGGER fail_pending_create")
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), tail.id))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyPendingConnectionCreation(it) })
                fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
            }
        }
    }

    @Test
    fun creationProteusReplay() = creationReplay(false)

    @Test
    fun creationMLSReplay() = creationReplay(true)

    private fun creationReplay(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            for (interruption in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT)) {
                withFixture(mls, newConversation = true) { fixture ->
                    val page = fixture.creationPage(true)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(page, "notification", interruption))
                    var count = 0
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        count = runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first().size
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        runtime.database.conversationDAO.updateConversationName(CONVERSATION_ID, "Newer name", TIME + 20.seconds)
                        runtime.database.memberDAO.updateMemberRole(SENDER.toDao(), CONVERSATION_ID,
                            com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member)
                    })
                    val retry = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value
                    if (interruption == Interruption.AFTER_COMMIT) assertNull(retry) else assertEquals("first", retry?.text)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertEquals(count, runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first().size)
                        assertEquals("Newer name", runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.name)
                        assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member,
                            runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                    })
                    // A distinct server event with the same creation/welcome also preserves canonical state.
                    val duplicates = listOfNotNull(fixture.creationEvent("duplicate-creation"),
                        if (mls) fixture.welcomeEvent("duplicate-welcome") else null)
                    assertIs<Either.Right<*>>(fixture.receive(duplicates, duplicates.last().id))
                    fixture.verifyStoredAndNext(next, "first")
                }
            }
        }
    }

    @Test
    fun creationDuplicateBeforeWelcome() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val page = listOf(fixture.creationEvent(), fixture.creationEvent("creation-again"), fixture.welcomeEvent(),
                    fixture.event("notification", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page.filter { it.id == "creation" }, "creation")).value)
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page.filter { it.id == "creation-again" }, "creation-again")).value)
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "notification")).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val messages = runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                    assertEquals(1, messages.count { it is MessageEntity.System && it.content == MessageEntityContent.ConversationCreated })
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun creationAfterWelcomeInOnePayload() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val event = fixture.batch("notification", fixture.welcomeEvent(), fixture.creationEvent(), fixture.welcomeEvent("duplicate"),
                    fixture.creationEvent("creation-again"), fixture.event("text", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id,
                    fetchConversation = { fixture.creationResponse() })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyCreation(it) })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun creationWelcomeDuplicatesPreserveNewerEpoch() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val first = fixture.creationPage(true)
                val commit = fixture.event("commit", fixture.commit())
                val second = fixture.batch("second-notification", fixture.welcomeEvent(), commit, fixture.welcomeEvent("welcome-again"),
                    fixture.event("second", fixture.encrypt("second")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(first, "notification")).value?.text)
                assertEquals("second", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(second), second.id)).value?.text)
                fixture.sql("UPDATE Conversation SET incomplete_metadata = 1")
                val repair = listOf(fixture.creationEvent("repair-metadata"))
                assertIs<Either.Right<*>>(fixture.receive(repair, "repair-metadata",
                    fetchConversation = { fixture.creationResponse() }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val protocol = assertIs<ConversationEntity.ProtocolInfo.MLS>(
                        runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.protocolInfo)
                    assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                    assertEquals(2uL, protocol.epoch)
                    assertEquals(2uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                })
                fixture.verifyStoredAndNext(next, "second", listOf("first", "second"))
            }
        }
    }

    @Test
    fun creationProteusPersistenceFailures() = creationPersistenceFailures(false)

    @Test
    fun creationMLSPersistenceFailures() = creationPersistenceFailures(true)

    private fun creationPersistenceFailures(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            for (table in listOf("Conversation", "Member", "Message", "PendingDeliveryConfirmations", "MessageSystemContent")) {
                withFixture(mls, newConversation = true) { fixture ->
                    val page = fixture.creationPage(true)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    val condition = if (table == "MessageSystemContent") "WHEN NEW.enum_1 = 'CREATION_ADDED'" else ""
                    fixture.sql("CREATE TRIGGER fail_creation BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'creation write failed'); END")
                    assertIs<Either.Left<*>>(fixture.receive(page, "notification"))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        if (mls) assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    })
                    fixture.sql("DROP TRIGGER fail_creation")
                    assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value?.text)
                    fixture.verifyStoredAndNext(next, "first")
                }
            }
        }
    }

    @Test
    fun joinUnavailableConversationDoesNotBlockFollowingMessage() = runTest {
        withContext(Dispatchers.Default) {
            for (label in listOf("no-conversation", "not-found")) withFixture(true) { fixture ->
                val join = fixture.plainEvent("join", """{"type":"conversation.member-join",
                    "qualified_conversation":{"id":"missing-conversation","domain":"example.com"},
                    "qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},
                    "from":"${SENDER.value}","time":"$TIME",
                    "data":{"user_ids":[],"users":[]}}""")
                val tail = fixture.event("text", fixture.encrypt("first"))
                fixture.releasePrepared()
                val diagnostics = ReceivingDiagnostics()
                val result = fixture.receive(listOf(join, tail), tail.id, diagnostics = diagnostics, fetchConversation = {
                    throw KaliumException.InvalidRequestError(GenericAPIErrorResponse(code = 404, label = label, message = "private response"))
                })
                if (label == "no-conversation") {
                    assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                } else {
                    assertIs<Either.Left<*>>(result)
                    assertEquals(404, diagnostics.httpStatus)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(label == "no-conversation", checkNotNull(runtime.database.eventDAO.getEventById(join.id)).isProcessed)
                })
            }
        }
    }


    @Test
    fun welcomeUnavailableConversationDoesNotBlockFollowingMessage() = runTest {
        withContext(Dispatchers.Default) {
            for (status in listOf(404, 503)) withFixture(true) { fixture ->
                val welcome = fixture.plainEvent("stale-welcome", """{"type":"conversation.mls-welcome",
                    "qualified_conversation":{"id":"missing-conversation","domain":"example.com"},
                    "qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},
                    "from":"${SENDER.value}","time":"$TIME","data":"AA=="}""")
                val tail = fixture.event("text", fixture.encrypt("first"))
                fixture.releasePrepared()
                val diagnostics = ReceivingDiagnostics()
                var welcomeCalls = 0
                val result = fixture.receive(listOf(welcome, tail), tail.id, diagnostics = diagnostics, fetchConversation = {
                    throw KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(code = status, label = "no-conversation", message = "private response")
                    )
                }, decorateMLSContext = { context -> object : MlsCoreCryptoContext by context {
                    override suspend fun processWelcomeMessage(message: ByteArray): String {
                        welcomeCalls++
                        error("Stale welcome must not reach native crypto")
                    }
                } })
                assertEquals(0, welcomeCalls)
                if (status == 404) {
                    assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                } else {
                    assertIs<Either.Left<*>>(result)
                    assertEquals(status, diagnostics.httpStatus)
                    assertEquals("WELCOME_FETCH_CONVERSATION", diagnostics.stage)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(status == 404, checkNotNull(runtime.database.eventDAO.getEventById(welcome.id)).isProcessed)
                })
            }
        }
    }


    @Test
    fun unavailableMLSLifecycleDoesNotBlockOtherConversation() = runTest {
        withContext(Dispatchers.Default) {
            for ((status, label) in listOf(404 to "no-conversation", 403 to "access-denied", 403 to "operation-denied", 503 to "access-denied")) withFixture(true) { fixture ->
                fun stale(id: String, type: String, data: String) = fixture.plainEvent(id, """{"type":"$type",
                    "qualified_conversation":{"id":"missing-conversation","domain":"example.com"},
                    "qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},
                    "from":"${SENDER.value}","time":"$TIME","subconv":null,"data":$data}""")
                val protocol = stale("protocol", "conversation.protocol-update", """{"protocol":"mixed"}""")
                val join = stale("join", "conversation.member-join", """{"user_ids":[],"users":[]}""")
                val welcome = stale("welcome", "conversation.mls-welcome", "\"AA==\"")
                val message = stale("message", "conversation.mls-message-add", "\"AA==\"")
                val rename = stale("rename", "conversation.rename", """{"name":"Renamed"}""")
                val leave = stale("leave", "conversation.member-leave",
                    """{"qualified_user_ids":[{"id":"${SELF.value}","domain":"${SELF.domain}"}],"reason":"removed"}""")
                val tail = fixture.event("text", fixture.encrypt("first"))
                fixture.releasePrepared()
                val result = fixture.receive(listOf(protocol, join, welcome, rename, message, leave, tail), tail.id, fetchConversation = {
                    throw KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(code = status, label = label, message = "private response")
                    )
                })
                if (status == 404 || (status == 403 && label == "access-denied")) {
                    assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        for (event in listOf(protocol, join, welcome, rename, message, leave, tail)) {
                            assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        }
                    })
                } else {
                    assertIs<Either.Left<*>>(result)
                }
            }
        }
    }


    @Test
    fun unavailableCachedConversationSkipsWelcomeButPreservesDecryptableHistory() = runTest {
        withContext(Dispatchers.Default) {
            for ((status, label) in listOf(404 to "no-conversation", 403 to "access-denied")) withFixture(true) { fixture ->
                fixture.seedProteusConversation()
                val welcome = fixture.plainEvent("stale-welcome", """{"type":"conversation.mls-welcome",
                    "qualified_conversation":{"id":"${CONVERSATION_ID.value}","domain":"${CONVERSATION_ID.domain}"},
                    "qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},
                    "from":"${SENDER.value}","time":"$TIME","data":"AA=="}""")
                val tail = fixture.event("tail", fixture.encrypt("tail", protocolMLS = false),
                    protocolMLS = false, conversationId = DELETE_TARGET)
                val notification = fixture.batch("notification", fixture.memberJoin(), welcome,
                    fixture.event("history", fixture.encrypt("history")), tail)
                val next = fixture.event("next", fixture.encrypt("next"))
                fixture.releasePrepared()
                var welcomeCalls = 0
                var decryptCalls = 0
                var fetches = 0
                val result = fixture.receive(listOf(notification), notification.id, fetchConversation = {
                    fetches++
                    throw KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(code = status, label = label, message = "private response")
                    )
                }, decorateMLSContext = { context -> object : MlsCoreCryptoContext by context {
                    override suspend fun processWelcomeMessage(message: ByteArray): String {
                        welcomeCalls++
                        error("Unavailable conversation must not consume a Welcome")
                    }
                    override suspend fun decryptMessage(groupId: MLSGroupId, message: ByteArray): MLSDecryptResult {
                        decryptCalls++
                        return context.decryptMessage(groupId, message)
                    }
                } })
                assertEquals("tail", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                assertEquals(0, welcomeCalls)
                assertEquals(1, decryptCalls)
                assertEquals(1, fetches)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(notification.id)).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById("tail", DELETE_TARGET))
                    assertNotNull(runtime.database.messageDAO.getMessageById("history", CONVERSATION_ID))
                })
                // Unavailability belongs to this decode, not a persisted conversation state.
                assertEquals("next", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(next), next.id)).value?.text)
            }
        }
    }

    @Test
    fun missingConversationMetadataEventsDoNotBlockFollowingMessage() = runTest {
        withContext(Dispatchers.Default) {
            for (kind in listOf("rename", "receipt", "timer", "role", "preference", "self-leave")) withFixture(true) { fixture ->
                val missing = CONVERSATION_ID.copy(value = "missing-conversation")
                val event = when (kind) {
                    "role" -> fixture.memberRoleEvent("wire_admin", target = missing)
                    "preference" -> fixture.memberMutedEvent(missing)
                    "rename" -> fixture.metadataEvents(missing)[0]
                    "receipt" -> fixture.metadataEvents(missing)[1]
                    "timer" -> fixture.metadataEvents(missing)[2]
                    else -> fixture.plainEvent("stale-$kind", """{
                        "type":"conversation.member-leave",
                        "qualified_conversation":{"id":"${missing.value}","domain":"${missing.domain}"},
                        "qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},
                        "from":"${SENDER.value}","time":"$TIME",
                        "data":{"qualified_user_ids":[{"id":"${SELF.value}","domain":"${SELF.domain}"}],"reason":"removed"}
                    }""")
                }
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                val diagnostics = ReceivingDiagnostics()
                assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id, diagnostics = diagnostics, fetchConversation = {
                    throw KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(code = 404, label = "no-conversation", message = "private response")
                    )
                }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById("tail", CONVERSATION_ID))
                })
            }
        }
    }

    @Test
    fun missingConversationMetadataIsFetchedOnceAndAppliedInOrder() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val missing = CONVERSATION_ID.copy(value = "missing-conversation")
                val metadata = fixture.metadataEvents(missing)
                val notification = fixture.batch("metadata", *metadata.toTypedArray(), fixture.memberMutedEvent(missing),
                    fixture.memberLeave("leave-missing", SENDER, target = missing))
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                var fetches = 0
                val result = fixture.receive(listOf(notification, tail), tail.id, fetchConversation = {
                    fetches++
                    fixture.creationResponse().copy(id = missing.toApi(), protocol = ConvProtocol.PROTEUS, groupId = null, epoch = null)
                })
                assertEquals("tail", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                assertEquals(1, fetches)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(missing))
                    assertEquals("Renamed", conversation.name)
                    assertEquals(ConversationEntity.ReceiptMode.ENABLED, conversation.receiptMode)
                    assertEquals(45_000, conversation.messageTimer)
                    assertEquals(ConversationEntity.MutedStatus.ALL_MUTED, conversation.mutedStatus)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(notification.id)).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById("tail", CONVERSATION_ID))
                })
            }
        }
    }

    @Test
    fun missingConversationMetadataTransientFailuresKeepFollowingMessagePending() = runTest {
        withContext(Dispatchers.Default) {
            for (kind in listOf("rename", "receipt", "timer", "role", "preference", "leave")) withFixture(true) { fixture ->
                val missing = CONVERSATION_ID.copy(value = "missing-conversation")
                val event = when (kind) {
                    "rename" -> fixture.metadataEvents(missing)[0]
                    "receipt" -> fixture.metadataEvents(missing)[1]
                    "timer" -> fixture.metadataEvents(missing)[2]
                    "role" -> fixture.memberRoleEvent("wire_admin", target = missing)
                    "preference" -> fixture.memberMutedEvent(missing)
                    else -> fixture.memberLeave(target = missing)
                }
                val tail = fixture.event("tail", fixture.encrypt("tail"))
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id, fetchConversation = {
                    throw KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(code = 503, label = "access-denied", message = "private response")
                    )
                }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById("tail", CONVERSATION_ID))
                })
            }
        }
    }


    @Test
    fun mlsMessageFetchesMissingLocalConversation() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val event = fixture.event("text", fixture.encrypt("first"))
                fixture.releasePrepared()
                fixture.sql("DELETE FROM Conversation")
                val result = fixture.receive(listOf(event), event.id, fetchConversation = { fixture.creationResponse() })
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
            }
        }
    }


    @Test
    fun joinMissingConversationRetainsFetchFailure() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val event = fixture.memberJoin()
                fixture.releasePrepared()
                val diagnostics = ReceivingDiagnostics()
                val result = assertIs<Either.Left<AccountRuntimeFailure>>(
                    fixture.receive(listOf(event), event.id, diagnostics = diagnostics, fetchConversation = {
                        throw KaliumException.InvalidRequestError(GenericAPIErrorResponse(code = 503, label = "temporary", message = "private response"))
                    })
                )
                assertEquals(AccountRuntimeFailure.STORAGE, result.value)
                assertEquals("ServerMiscommunication", diagnostics.errorCode)
                assertEquals(503, diagnostics.httpStatus)
                assertEquals("JOIN_REFRESH_CONVERSATION", diagnostics.stage)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                })
            }
        }
    }


    @Test
    fun creationProfilesRequired() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val page = fixture.creationPage(true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                fixture.sql("UPDATE User SET name = NULL WHERE qualified_id = '${SENDER.toDao()}'")
                assertIs<Either.Left<*>>(fixture.receive(page, "notification"))
                val profiles = KtxSerializer.json.decodeFromString<ListUsersDTO>(
                    """{"found":[{"qualified_id":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},
                    "id":"${SENDER.value}","name":"Fetched sender","handle":"sender","accent_id":0,
                    "assets":[],"deleted":false,"legalhold_status":"disabled","supported_protocols":["proteus","mls"]}],"failed":[]}"""
                )
                fixture.sql("CREATE TRIGGER fail_profile BEFORE UPDATE ON User WHEN NEW.name = 'Fetched sender' BEGIN SELECT RAISE(ABORT, 'profile write failed'); END")
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "notification", fetchProfiles = { profiles }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                })
                fixture.sql("DROP TRIGGER fail_profile")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification", fetchProfiles = { profiles })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals("Fetched sender", runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.name)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun creationStoresUnreachableProfilesAsIncomplete() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val page = fixture.creationPage(true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                fixture.sql("UPDATE User SET name = NULL WHERE qualified_id = '${SENDER.toDao()}'")
                val result = fixture.receive(page, "notification", fetchProfiles = {
                    ListUsersDTO(usersFound = emptyList(), usersFailed = listOf(SENDER.toApi()))
                })
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertNull(checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())).name)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun creationWelcomeMetadataAndNativeFailures() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val page = listOf(fixture.batch("notification", fixture.welcomeEvent(), fixture.event("text", fixture.encrypt("first"))))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(page, "notification"))
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "notification", fetchConversation = { error("Required metadata fetch failed") }))
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "notification",
                    fetchConversation = { fixture.creationResponse().copy(id = DELETE_TARGET.toApi()) }))
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "notification", fetchConversation = { fixture.creationResponse() },
                    decorateMLSContext = { context -> object : MlsCoreCryptoContext by context {
                        override suspend fun processWelcomeMessage(message: ByteArray): String = error("Required welcome failed")
                    } }))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                })
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification",
                    fetchConversation = { fixture.creationResponse() })).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyCreation(it, expectMessages = false) })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun creationWelcomeVerificationFailures() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true, newConversation = true) { fixture ->
                val page = fixture.creationPage(true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(page, "notification", decorateMLSContext = { context ->
                    object : MlsCoreCryptoContext by context {
                        override suspend fun getGroupState(groupId: String): com.wire.kalium.cryptography.E2EIConversationState =
                            error("Required welcome verification failed")
                    }
                }))
                fixture.sql("CREATE TRIGGER fail_welcome_state BEFORE UPDATE OF mls_group_state ON Conversation BEGIN SELECT RAISE(ABORT, 'welcome state failed'); END")
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "notification"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                })
                fixture.sql("DROP TRIGGER fail_welcome_state")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value?.text)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun creationPartialCanonicalState() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (incomplete in listOf(false, true)) withFixture(mls, newConversation = true) { fixture ->
                val page = fixture.creationPage(true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val partial = com.wire.kalium.logic.data.conversation.ConversationResponseMapper(SELF)
                        .fromApiModelToDaoModel(fixture.creationResponse(), ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE, null)
                    runtime.database.conversationDAO.insertConversation(partial.copy(
                        name = "Newer name", hasIncompleteMetadata = incomplete, mutedStatus = ConversationEntity.MutedStatus.ALL_MUTED,
                        mutedTime = (TIME + 10.seconds).toEpochMilliseconds(), lastReadDate = TIME + 10.seconds,
                    ))
                    runtime.database.memberDAO.insertMember(
                        com.wire.kalium.persistence.dao.member.MemberEntity((if (incomplete) SENDER else SELF).toDao(),
                            com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member), CONVERSATION_ID)
                    // An existing system message does not prove metadata or membership is complete.
                    runtime.database.messageDAO.insertOrIgnoreMessages(listOf(MessageEntity.System(
                        id = "${CONVERSATION_ID.toModel()}:$TIME:creation:1",
                        content = MessageEntityContent.ConversationCreated,
                        conversationId = CONVERSATION_ID,
                        date = TIME,
                        senderUserId = SENDER.toDao(),
                        status = MessageEntity.Status.SENT,
                        expireAfterMs = null,
                        selfDeletionEndDate = null,
                        readCount = 0,
                        senderName = null,
                    )))
                })
                assertIs<Either.Left<*>>(fixture.receive(page, "notification"))
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification",
                    fetchConversation = { fixture.creationResponse() })).value
                assertFalse(checkNotNull(result).shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertEquals(if (incomplete) "Created group" else "Newer name", conversation.name)
                    assertEquals(ConversationEntity.MutedStatus.ALL_MUTED, conversation.mutedStatus)
                    assertEquals(TIME + 10.seconds, conversation.lastReadDate)
                    assertEquals(if (incomplete) com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member else
                        com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin,
                        runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                    assertNotNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun creationUnsupportedDependenciesStayPending() = runTest {
        withContext(Dispatchers.Default) {
            for (kind in listOf("one-to-one", "group-mismatch", "partial-group-mismatch", "missing-welcome", "recovery")) {
                withFixture(true, newConversation = true) { fixture ->
                    val response = when (kind) {
                        "one-to-one" -> fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE)
                        "group-mismatch" -> fixture.creationResponse().copy(groupId = Base64.encode("wrong-group".encodeToByteArray()))
                        else -> fixture.creationResponse()
                    }
                    val page = listOfNotNull(fixture.creationEvent(response = response),
                        fixture.welcomeEvent().takeUnless { kind == "missing-welcome" }, fixture.event("notification", fixture.encrypt("first")))
                    fixture.releasePrepared()
                    if (kind == "recovery") assertIs<Either.Right<*>>(fixture.open { runtime ->
                        runtime.database.conversationDAO.insertConversation(
                            com.wire.kalium.logic.data.conversation.ConversationResponseMapper(SELF)
                                .fromApiModelToDaoModel(response, ConversationEntity.GroupState.PENDING_JOIN, null))
                    })
                    if (kind == "partial-group-mismatch") assertIs<Either.Right<*>>(fixture.open { runtime ->
                        runtime.database.conversationDAO.insertConversation(
                            com.wire.kalium.logic.data.conversation.ConversationResponseMapper(SELF)
                                .fromApiModelToDaoModel(response.copy(groupId = Base64.encode("other-group".encodeToByteArray())),
                                    ConversationEntity.GroupState.ESTABLISHED, null).copy(hasIncompleteMetadata = true))
                    })
                    assertIs<Either.Left<*>>(fixture.receive(page, "notification", fetchConversation = { response }))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        // A welcome in an earlier notification may establish the replacement
                        // group before its reset metadata arrives. Text still blocks on the
                        // mismatching canonical group, without undoing the completed welcome.
                        assertEquals(kind == "group-mismatch",
                            checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) }, kind)
                    })
                }
            }
        }
    }

    @Test
    fun `prepared Proteus text persists required effects and reopened crypto decrypts the next message`() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = fixture.receive(listOf(fixture.event("notification", first)), "notification")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun `prepared MLS text persists authenticated sender and reopened crypto decrypts the next message`() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val result = fixture.receive(listOf(fixture.event("notification", first)), "notification")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun synchronizationProteus() = synchronization(false)

    @Test
    fun synchronizationMLS() = synchronization(true)

    private fun synchronization(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.synchronizationEvent()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", Interruption.BEFORE_COMMIT))
                fixture.open { fixture.verifySynchronization(it) }.also { assertIs<Either.Right<*>>(it, it.toString()) }
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value?.text)
                fixture.open { fixture.verifySynchronization(it) }.also { assertIs<Either.Right<*>>(it, it.toString()) }
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun synchronizationRollbackProteus() = synchronizationRollback(false)

    @Test
    fun synchronizationRollbackMLS() = synchronizationRollback(true)

    private fun synchronizationRollback(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.synchronizationEvent()
                fixture.releasePrepared()
                fixture.sql(
                    "CREATE TRIGGER fail_config BEFORE INSERT ON Metadata " +
                    "WHEN NEW.key = 'prevent_adminless_groups' BEGIN SELECT RAISE(ABORT, 'config write failed'); END"
                )
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                    assertEquals(
                        MessageEntity.Status.SENT,
                        runtime.database.messageDAO.getMessageById("receipt-target", CONVERSATION_ID)?.status
                    )
                    assertTrue(
                        runtime.database.receiptDAO.observeDetailedReceiptsForMessage(
                            CONVERSATION_ID,
                            "receipt-target",
                            ReceiptTypeEntity.READ
                        )
                        .first().isEmpty()
                    )
                    assertEquals("Fixture", runtime.database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.name)
                    assertFalse(runtime.database.userPrefsDAO.areReadReceiptsEnabled().first())
                    assertTrue(runtime.database.userPrefsDAO.isTypingIndicatorEnabled().first())
                    assertEquals(
                        ConversationEntity.MutedStatus.ALL_ALLOWED,
                        runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.mutedStatus
                    )
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                }
                )
                fixture.sql("DROP TRIGGER fail_config")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value?.text)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifySynchronization(it) })
            }
        }
    }

    @Test
    fun lastReadSynchronizationProteus() = lastReadSynchronization(false)

    @Test
    fun lastReadSynchronizationMLS() = lastReadSynchronization(true)

    private fun lastReadSynchronization(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls, senderId = SELF) { fixture ->
                val event = fixture.readStateEvent()
                val older = fixture.event(
                    "older-read",
                    fixture.encrypt("older-read", MessageContent.LastRead("read-me", DELETE_TARGET.toModel(), TIME - 10.seconds))
                )
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyReadState(it) })
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value
                assertFalse(checkNotNull(result).shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyReadState(it) })
                assertIs<Either.Right<*>>(
                    fixture.receive(listOf(older), "older-read")
                )
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyReadState(it) })
                fixture.verifyStoredAndNext(next, "first", emptyList())
            }
        }
    }

    @Test
    fun lastReadFailureProteus() = lastReadFailure(false)

    @Test
    fun lastReadFailureMLS() = lastReadFailure(true)

    private fun lastReadFailure(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls, senderId = SELF) { fixture ->
                val event = fixture.readStateEvent()
                fixture.releasePrepared()
                fixture.sql(
                    "CREATE TRIGGER fail_read BEFORE UPDATE OF last_read_date ON Conversation " +
                    "BEGIN SELECT RAISE(ABORT, 'read state write failed'); END"
                )
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                    assertEquals(Instant.fromEpochMilliseconds(Instant.DISTANT_PAST.toEpochMilliseconds()), runtime.database.conversationDAO.getConversationLastReadDate(DELETE_TARGET))
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertFalse(runtime.database.userPrefsDAO.areReadReceiptsEnabled().first())
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                }
                )
                fixture.sql("DROP TRIGGER fail_read")
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(fixture.open { fixture.verifyReadState(it) })
            }
        }
    }

    @Test
    fun lastReadAuthorizationProteus() = lastReadAuthorization(false)

    @Test
    fun lastReadAuthorizationMLS() = lastReadAuthorization(true)

    private fun lastReadAuthorization(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            // A different user's authenticated MLS identity cannot mark our conversations as read.
            withFixture(mls) { fixture ->
                fixture.seedReadState()
                val read = fixture.encrypt("read", MessageContent.LastRead("read", DELETE_TARGET.toModel(), TIME))
                val first = fixture.encrypt("first")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(
                    fixture.receive(
                        listOf(
                            fixture.batch(
                                "notification",
                                fixture.event("read", read),
                    fixture.event("first", first)
                            )
                        ),
                        "notification"
                    )
                )
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                    assertEquals(Instant.fromEpochMilliseconds(Instant.DISTANT_PAST.toEpochMilliseconds()), runtime.database.conversationDAO.getConversationLastReadDate(DELETE_TARGET))
                }
                )
            }
            // A self echo in an ordinary conversation is not a LastRead synchronization signal.
            withFixture(mls, senderId = SELF) { fixture ->
                val read = fixture.encrypt("read", MessageContent.LastRead("read", CONVERSATION_ID.toModel(), TIME + 10.seconds))
                val first = fixture.encrypt("first")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(
                    fixture.receive(
                        listOf(
                            fixture.batch(
                                "notification",
                                fixture.event("read", read),
                    fixture.event("first", first)
                            )
                        ),
                        "notification"
                    )
                )
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                    assertEquals(Instant.fromEpochMilliseconds(Instant.DISTANT_PAST.toEpochMilliseconds()), runtime.database.conversationDAO.getConversationLastReadDate(CONVERSATION_ID))
                }
                )
            }
        }
    }

    @Test
    fun propertyDefaultsAndMutedTextProteus() = propertyDefaultsAndMutedText(false)

    @Test
    fun propertyDefaultsAndMutedTextMLS() = propertyDefaultsAndMutedText(true)

    private fun propertyDefaultsAndMutedText(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.propertyEvent("WIRE_RECEIPT_MODE", 1),
                    fixture.propertyEvent("WIRE_TYPING_INDICATOR_MODE", 0),
                    fixture.plainEvent("receipt-delete", """{"type":"user.properties-delete","key":"WIRE_RECEIPT_MODE"}"""),
                    fixture.plainEvent("typing-delete", """{"type":"user.properties-delete","key":"WIRE_TYPING_INDICATOR_MODE"}"""),
                    fixture.preferenceEvent(muted = 3), fixture.event("first", fixture.encrypt("first")))
                fixture.releasePrepared()
                val received = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value
                assertFalse(checkNotNull(received).shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(runtime.database.userPrefsDAO.areReadReceiptsEnabled().first())
                    assertTrue(runtime.database.userPrefsDAO.isTypingIndicatorEnabled().first())
                    assertEquals(ConversationEntity.MutedStatus.ALL_MUTED,
                        runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)?.mutedStatus)
                    assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                })
            }
        }
    }

    @Test
    fun unknownUserConfigurationDoesNotBlockText() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val payloads = listOf(fixture.event("prefix", fixture.encrypt("prefix")),
                        fixture.configEvent("unknown-feature"), fixture.propertyEvent("unknown-key", 1),
                        fixture.plainEvent("unknown-value", """{"type":"user.properties-set","key":"unknown-key","value":{"future":true}}"""),
                        fixture.plainEvent("unknown-delete", """{"type":"user.properties-delete","key":"unknown-key"}"""),
                        fixture.event("first", fixture.encrypt("first")))
                    // Preserve unknown JSON values instead of round-tripping through the DTO serializer.
                    val event = EventResponseToStore("notification", JsonArray(payloads.flatMap {
                        KtxSerializer.json.parseToJsonElement(checkNotNull(it.payload)).jsonArray
                    }).toString(), false)
                    val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    if (stage != null) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id, stage))
                        if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    }
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event, tail), tail.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                        assertNull(runtime.database.metadataDAO.valueByKey("unknown-key"))
                    })
                    fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
                }
            }
        }
    }

    @Test
    fun adminlessRemindersPersistWithoutReplacingMessages() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (system in listOf(false, true)) withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.adminlessReminder(system, TIME),
                    fixture.event("first", fixture.encrypt("first")))
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(TIME, runtime.database.conversationDAO.getConversationDetailsById(CONVERSATION_ID)
                        ?.adminlessGroupDeletionTimestamp)
                    assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("notification", CONVERSATION_ID))
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                })
            }
        }
    }

    @Test
    fun adminlessReminderWriteFailureKeepsBothProtocolsPendingUntilRetry() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (system in listOf(false, true)) withFixture(mls) { fixture ->
                val event = fixture.batch("notification", fixture.event("first", fixture.encrypt("first")),
                    fixture.adminlessReminder(system, TIME))
                fixture.releasePrepared()
                fixture.sql("CREATE TRIGGER fail_adminless BEFORE INSERT ON AdminlessGroupDelete " +
                    "BEGIN SELECT RAISE(ABORT, 'reminder write failed'); END")
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertNull(runtime.database.conversationDAO.getConversationDetailsById(CONVERSATION_ID)?.adminlessGroupDeletionTimestamp)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                })
                fixture.sql("DROP TRIGGER fail_adminless")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), event.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(TIME, runtime.database.conversationDAO.getConversationDetailsById(CONVERSATION_ID)
                        ?.adminlessGroupDeletionTimestamp)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                })
            }
        }
    }

    @Test
    fun systemMemberRolesProteus() = memberRoles(false, system = true)

    @Test
    fun systemMemberRolesMLS() = memberRoles(true, system = true)

    @Test
    fun systemMLSDeletionWithoutSenderReplaysNativeRollback() =
        mlsConversationDeletionRoundTrip(Interruption.BEFORE_COMMIT, system = true)

    @Test
    fun ordinaryMetadataProteus() = ordinaryMetadata(false)

    @Test
    fun ordinaryMetadataMLS() = ordinaryMetadata(true)

    @Test
    fun systemMetadataIsPersistedWithoutAnAlert() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                fixture.sql("UPDATE Conversation SET type = 'GROUP'")
                val metadata = fixture.batch("notification", *fixture.metadataEvents().toTypedArray())
                fixture.releasePrepared()
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(listOf(metadata), metadata.id)
                ).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    for (id in listOf("notification", "notification:1", "notification:2")) {
                        assertIs<MessageEntity.System>(runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID))
                    }
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(metadata.id)).isProcessed)
                })
            }
        }
    }

    private fun ordinaryMetadata(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.sql("UPDATE Conversation SET type = 'GROUP'")
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                val event = fixture.batch("notification", *fixture.metadataEvents().toTypedArray(), fixture.event("first", first))
                fixture.releasePrepared()
                // The existing replay fixture exercises SQL success followed by native rollback.
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", Interruption.BEFORE_COMMIT))
                var systemDates = emptyList<Instant>()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertEquals("Renamed", conversation.name)
                    assertEquals(ConversationEntity.ReceiptMode.ENABLED, conversation.receiptMode)
                    assertEquals(45_000, conversation.messageTimer)
                    val systems = listOf("notification", "notification:1", "notification:2").map {
                        assertIs<MessageEntity.System>(runtime.database.messageDAO.getMessageById(it, CONVERSATION_ID))
                    }
                    assertEquals(MessageEntityContent.ConversationRenamed("Renamed"), systems[0].content)
                    assertEquals(MessageEntityContent.ConversationReceiptModeChanged(true), systems[1].content)
                    assertEquals(MessageEntityContent.ConversationMessageTimerChanged(45_000), systems[2].content)
                    systemDates = systems.map { it.date }
                    assertTrue(assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        .expectsReadConfirmation)
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    val pending = runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10)
                    runtime.database.pendingDeliveryConfirmationDAO.acknowledge(pending.map { it.id })
                })
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(systemDates, listOf("notification", "notification:1", "notification:2").map {
                        checkNotNull(runtime.database.messageDAO.getMessageById(it, CONVERSATION_ID)).date
                    })
                    assertEquals(TIME, checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).lastModifiedDate)
                })
                fixture.verifyStoredAndNext(next, "first", emptyList())
            }
        }
    }

    @Test
    fun metadataPersistenceFailureProteus() = metadataPersistenceFailure(false)

    @Test
    fun metadataPersistenceFailureMLS() = metadataPersistenceFailure(true)

    private fun metadataPersistenceFailure(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val first = fixture.encrypt("first")
                val event = fixture.batch("notification", fixture.event("first", first), *fixture.metadataEvents().toTypedArray())
                fixture.releasePrepared()
                fixture.sql("CREATE TRIGGER fail_system BEFORE INSERT ON MessageSystemContent " +
                    "WHEN NEW.content_type = 'CONVERSATION_TIMER_CHANGED' BEGIN SELECT RAISE(ABORT, 'system write failed'); END")
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    assertEquals("Fixture", conversation.name)
                    assertEquals(ConversationEntity.ReceiptMode.DISABLED, conversation.receiptMode)
                    assertNull(conversation.messageTimer)
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("notification:1", CONVERSATION_ID))
                    assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                })
                fixture.sql("DROP TRIGGER fail_system")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value?.text)
            }
        }
    }

    @Test
    fun ordinaryActionsProteus() = ordinaryActions(false)

    @Test
    fun ordinaryActionsMLS() = ordinaryActions(true)

    private fun ordinaryActions(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seed(
                    storedText("original"), storedText("delete-me"), storedText("protected", sender = SELF),
                    storedText("self-ephemeral", sender = SELF, expireAfter = 2_000),
                    storedText("local", body = "newer local edit", expireAfter = 7_000, endDate = TIME + 60.seconds).copy(
                        status = MessageEntity.Status.PENDING, editStatus = MessageEntity.EditStatus.Edited(TIME + 30.seconds)
                    )
                )
                val entries = listOf(
                    fixture.event("created", fixture.encrypt("created")),
                    fixture.event("created-edit", fixture.encrypt("created-edit", MessageContent.TextEdited("created", "created-edit", emptyList()))),
                    fixture.event("edited", fixture.encrypt("edited", MessageContent.TextEdited("original", "edited", emptyList()))),
                    fixture.event("reaction", fixture.encrypt("reaction", MessageContent.Reaction("edited", setOf("❤", "👍")))),
                    fixture.event("reaction-update", fixture.encrypt("reaction-update", MessageContent.Reaction("edited", setOf("❤")))),
                    fixture.event("delete", fixture.encrypt("delete", MessageContent.DeleteMessage("delete-me"))),
                    fixture.event("unauthorized-edit", fixture.encrypt("unauthorized-edit", MessageContent.TextEdited("protected", "wrong", emptyList()))),
                    fixture.event("unauthorized-delete", fixture.encrypt("unauthorized-delete", MessageContent.DeleteMessage("protected"))),
                    fixture.event("unauthorized-delete-for-me", fixture.encrypt("unauthorized-delete-for-me",
                        MessageContent.DeleteForMe("protected", ConversationId(CONVERSATION_ID.value, CONVERSATION_ID.domain)))),
                    fixture.event("delete-ephemeral", fixture.encrypt("delete-ephemeral", MessageContent.DeleteMessage("self-ephemeral"))),
                    fixture.event("stale-edit", fixture.encrypt("stale-edit", MessageContent.TextEdited("local", "older remote edit", emptyList()))),
                    fixture.event("ephemeral", fixture.encrypt("ephemeral", expiresAfterMillis = 1_234)),
                    fixture.event("removed", fixture.encrypt("removed", expiresAfterMillis = 1_234)),
                    fixture.event("removed-edit", fixture.encrypt("removed-edit", MessageContent.TextEdited("removed", "removed-edit", emptyList()))),
                    fixture.event("remove-transient", fixture.encrypt("remove-transient", MessageContent.DeleteMessage("removed-edit"))),
                    fixture.event("first", fixture.encrypt("first"), TIME + 1.seconds)
                )
                val event = fixture.batch("notification", *entries.toTypedArray())
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", Interruption.BEFORE_COMMIT))
                var unread = 0L
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertActionState(runtime)
                    unread = runtime.database.messageDAO.getConversationUnreadEventsCount(CONVERSATION_ID)
                    val pending = runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20)
                    runtime.database.pendingDeliveryConfirmationDAO.acknowledge(pending.map { it.id })
                })
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value
                assertEquals("first", result?.text)
                assertTrue(checkNotNull(result).shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertActionState(runtime)
                    assertEquals(unread, runtime.database.messageDAO.getConversationUnreadEventsCount(CONVERSATION_ID))
                    assertEquals(TIME + 1.seconds, checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).lastModifiedDate)
                    assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20).isEmpty())
                })
                fixture.verifyStoredAndNext(next, "first", emptyList())
            }
        }
    }

    private suspend fun assertActionState(runtime: AppleAccountRuntime) {
        val dao = runtime.database.messageDAO
        assertNull(dao.getMessageById("created", CONVERSATION_ID))
        assertEquals("created-edit", assertIs<MessageEntityContent.Text>(checkNotNull(dao.getMessageById("created-edit", CONVERSATION_ID)).content).messageBody)
        assertNull(dao.getMessageById("original", CONVERSATION_ID))
        assertEquals("edited", assertIs<MessageEntityContent.Text>(checkNotNull(dao.getMessageById("edited", CONVERSATION_ID)).content).messageBody)
        assertEquals(setOf("❤️"), runtime.database.reactionDAO.getReaction("edited", CONVERSATION_ID, SENDER.toDao()))
        assertEquals(MessageEntity.Visibility.DELETED, checkNotNull(dao.getMessageById("delete-me", CONVERSATION_ID)).visibility)
        assertEquals(MessageEntity.Visibility.VISIBLE, checkNotNull(dao.getMessageById("protected", CONVERSATION_ID)).visibility)
        assertNull(dao.getMessageById("unauthorized-edit", CONVERSATION_ID))
        assertNull(dao.getMessageById("self-ephemeral", CONVERSATION_ID))
        assertNull(dao.getMessageById("removed", CONVERSATION_ID))
        assertNull(dao.getMessageById("removed-edit", CONVERSATION_ID))
        assertNull(dao.getMessageById("local", CONVERSATION_ID))
        val stale = assertIs<MessageEntity.Regular>(dao.getMessageById("stale-edit", CONVERSATION_ID))
        assertEquals("newer local edit", assertIs<MessageEntityContent.Text>(stale.content).messageBody)
        assertEquals(MessageEntity.EditStatus.Edited(TIME + 30.seconds), stale.editStatus)
        assertEquals(MessageEntity.Status.PENDING, stale.status)
        assertEquals(7_000L, stale.expireAfterMs)
        assertEquals(TIME + 60.seconds, stale.selfDeletionEndDate)
        val ephemeral = checkNotNull(dao.getMessageById("ephemeral", CONVERSATION_ID))
        assertEquals(1_234L, ephemeral.expireAfterMs)
        assertNull(ephemeral.selfDeletionEndDate)
    }

    @Test
    fun actionPersistenceFailureProteus() = actionPersistenceFailure(false)

    @Test
    fun actionPersistenceFailureMLS() = actionPersistenceFailure(true)

    private fun actionPersistenceFailure(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.seed(storedText("original"))
                val event = fixture.batch("notification",
                    fixture.event("edited", fixture.encrypt("edited", MessageContent.TextEdited("original", "edited", emptyList()))),
                    fixture.event("reaction", fixture.encrypt("reaction", MessageContent.Reaction("edited", setOf("❤")))),
                    fixture.event("first", fixture.encrypt("first")),
                )
                fixture.releasePrepared()
                fixture.sql("CREATE TRIGGER fail_reaction BEFORE INSERT ON Reaction BEGIN SELECT RAISE(ABORT, 'reaction write failed'); END")
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("original", CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("edited", CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                })
                fixture.sql("DROP TRIGGER fail_reaction")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value?.text)
            }
        }
    }

    @Test
    fun deleteForMeProteus() = deleteForMe(false)

    @Test
    fun deleteForMeMLS() = deleteForMe(true)

    private fun deleteForMe(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls, SELF) { fixture ->
                fixture.seedDeleteForMeTarget()
                val event = fixture.batch("notification",
                    fixture.event("delete-for-me", fixture.encrypt("delete-for-me", MessageContent.DeleteForMe(
                        "delete-me", ConversationId(DELETE_TARGET.value, DELETE_TARGET.domain)
                    ))),
                    fixture.event("first", fixture.encrypt("first")),
                )
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                fixture.sql("CREATE TRIGGER fail_delete BEFORE DELETE ON Message BEGIN SELECT RAISE(ABORT, 'delete failed'); END")
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("delete-me", DELETE_TARGET))
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                })
                fixture.sql("DROP TRIGGER fail_delete")
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("delete-me", DELETE_TARGET))
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                })
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value?.text)
                fixture.verifyStoredAndNext(next, "first", emptyList())
            }
        }
    }

    @Test
    fun ephemeralOtherProteus() = ephemeralOrigins(false, SENDER)

    @Test
    fun ephemeralOtherMLS() = ephemeralOrigins(true, SENDER)

    @Test
    fun ephemeralSelfProteus() = ephemeralOrigins(false, SELF)

    @Test
    fun ephemeralSelfMLS() = ephemeralOrigins(true, SELF)

    private fun ephemeralOrigins(mls: Boolean, sender: UserId) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls, sender) { fixture ->
                val event = fixture.batch("notification",
                    fixture.event("due", fixture.encrypt("due", expiresAfterMillis = 1)),
                    fixture.event("pending", fixture.encrypt("pending", expiresAfterMillis = 60_000)),
                )
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val beforeReceipt = Clock.System.now()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", Interruption.BEFORE_COMMIT))
                // The app's existing due-message query compares with SQLite's whole-second clock.
                if (sender == SELF) kotlinx.coroutines.delay(1_100)
                var deadlines = emptyList<Instant?>()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val messages = listOf("due", "pending").map { checkNotNull(runtime.database.messageDAO.getMessageById(it, CONVERSATION_ID)) }
                    deadlines = messages.map { it.selfDeletionEndDate }
                    assertEquals(listOf(1L, 60_000L), messages.map { it.expireAfterMs })
                    if (sender == SELF) {
                        assertTrue(checkNotNull(deadlines.first()) >= beforeReceipt)
                        assertEquals(listOf("due"), runtime.database.messageDAO.getAllAlreadyEndedEphemeralMessages().map { it.id })
                        assertEquals(listOf("pending"), runtime.database.messageDAO.getAllPendingEphemeralMessages().map { it.id })
                    } else {
                        assertEquals(listOf(null, null), deadlines)
                        assertTrue(runtime.database.messageDAO.getAllAlreadyEndedEphemeralMessages().isEmpty())
                        assertTrue(runtime.database.messageDAO.getAllPendingEphemeralMessages().isEmpty())
                    }
                })
                val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value
                assertFalse(checkNotNull(result).mayPreview)
                assertEquals(sender != SELF, result.shouldNotify)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(deadlines, listOf("due", "pending").map {
                        val message = checkNotNull(runtime.database.messageDAO.getMessageById(it, CONVERSATION_ID))
                        assertEquals(MessageEntity.Visibility.VISIBLE, message.visibility)
                        message.selfDeletionEndDate
                    })
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun membershipPipelineMLS() = membershipPipeline(false)

    @Test
    fun membershipBatchReplayMLS() = membershipPipeline(true)

    @Test
    fun membershipBatchReplayAfterNativeCommitMLS() = membershipPipeline(true, Interruption.AFTER_COMMIT)

    private fun membershipPipeline(batch: Boolean, interruption: Interruption = Interruption.BEFORE_COMMIT) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                val removal = fixture.commitRemovalProposal()
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                val events = listOf(
                    fixture.memberJoin(),
                    fixture.event("addition", addition),
                    fixture.event("proposal", Base64.decode(REMOVE_THIRD_PROPOSAL)),
                    fixture.memberLeave(),
                    fixture.event("removal", removal),
                    fixture.event("notification", first)
                )
                val page = if (batch) listOf(fixture.batch("notification", *events.toTypedArray())) else events
                fixture.releasePrepared()
                if (batch) assertIs<Either.Left<*>>(fixture.receive(page, "notification", interruption))
                val result = fixture.receive(if (batch) emptyList() else page, "notification")
                assertEquals(
                    if (batch && interruption == Interruption.AFTER_COMMIT) null else "first",
                    assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text
                )
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertNull(runtime.database.memberDAO.getMemberRole(THIRD.toDao(), CONVERSATION_ID))
                        val added = assertIs<MessageEntity.System>(
                            runtime.database.messageDAO.getMessageById(
                                if (batch) "notification" else "join",
                                CONVERSATION_ID
                            )
                        )
                        val removed = assertIs<MessageEntity.System>(
                            runtime.database.messageDAO.getMessageById(
                                if (batch) "notification:3" else "leave",
                                CONVERSATION_ID
                            )
                        )
                        assertEquals(
                            MessageEntityContent.MemberChange(listOf(THIRD.toDao()), MessageEntity.MemberChangeType.ADDED),
                            added.content
                        )
                        assertEquals(
                            MessageEntityContent.MemberChange(listOf(THIRD.toDao()), MessageEntity.MemberChangeType.REMOVED),
                            removed.content
                        )
                        assertEquals(3uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                        assertEquals(
                            3uL,
                            (
                                checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                            as ConversationEntity.ProtocolInfo.MLSCapable
                            ).epoch
                        )
                    }
                )
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun pendingProposalSurvivesCompletionAndReopen() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                fixture.observeRemovalProposal()
                val page = listOf(
                    fixture.memberJoin(),
                    fixture.event("addition", addition),
                    fixture.event("notification", Base64.decode(REMOVE_THIRD_PROPOSAL))
                )
                fixture.releasePrepared()
                // Reaching the queue end must not run the app's live proposal scheduler.
                assertIs<Either.Right<*>>(fixture.receive(page, "notification"))
                var deadline: Instant? = null
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        deadline = runtime.database.conversationDAO.getProposalTimers().first().single().firingDate
                        assertTrue(checkNotNull(deadline) >= TIME)
                        assertEquals(2uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    }
                )
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertEquals(deadline, runtime.database.conversationDAO.getProposalTimers().first().single().firingDate)
                        // The same shared operation used by the app's scheduler, against reopened account state.
                        checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                            assertIs<Either.Right<*>>(
                                com.wire.kalium.logic.data.conversation.commitPendingMLSProposals(
                                context,
                                GroupID(GROUP_ID),
                                runtime.database.conversationDAO
                            )
                            )
                        }
                        assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                    }
                )
                val committed = checkNotNull(fixture.transport.bundle).commit
                checkNotNull(fixture.senderMLS).transaction {
                    assertIs<MLSDecryptResult.Success>(it.decryptMessage(GROUP_ID, committed))
                }
                fixture.decryptAndCheck(fixture.encrypt("after-app-commit"), "after-app-commit")
            }
        }
    }

    @Test
    fun subconversationRequiresOwnedMappingAndProposalConsumer() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                fixture.prepareResetWelcome()
                val welcome = assertIs<EventContentDTO.Conversation.MLSWelcomeDTO>(fixture.welcomeEvent().toEventResponse().payload.orEmpty().single())
                checkNotNull(fixture.receiverMLS).transaction { it.processWelcomeMessage(Base64.decode(welcome.message)) }
                val sub = assertIs<EventContentDTO.Conversation.NewMLSMessageDTO>(
                    fixture.event("sub", fixture.encrypt("sub-text", groupId = RESET_GROUP_ID)).toEventResponse().payload.orEmpty().single()
                ).copy(subconversation = "conference")
                val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.plainEvent("sub", KtxSerializer.json.encodeToString<EventContentDTO>(sub)), fixture.event("first", fixture.encrypt("first")))
                val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                val subNext = fixture.encrypt("sub-next", groupId = RESET_GROUP_ID)
                fixture.releasePrepared()
                repeat(2) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event, tail), tail.id))
                    fixture.verifyPendingTextQueue(event, tail)
                }
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationByGroupID(RESET_GROUP_ID))
                    assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                    val plain = checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertTrue(context.conversationExists(RESET_GROUP_ID))
                        assertIs<DecryptedMessageBundle.Text>(assertIs<MLSDecryptResult.Success>(
                            context.decryptMessage(RESET_GROUP_ID, subNext)).messages.single()).message
                    }
                    assertEquals(MessageContent.Text("sub-next"), assertIs<ProtoContent.Readable>(
                        ProtoContentMapperImpl(selfUserId = SELF).decodeFromProtobuf(com.wire.kalium.logic.data.message.PlainMessageBlob(plain))).messageContent)
                })
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun transientAppBacklogAndCaughtUpTargetReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(null, Interruption.BEFORE_COMMIT,
            Interruption.AFTER_COMMIT, Interruption.MARKER, Interruption.DELIVERY)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val prefix = fixture.event("prefix", fixture.encrypt("prefix"))
                val captured = fixture.batch("captured", prefix, fixture.typingEvent("started", SELF), prefix,
                    fixture.propertyEvent("WIRE_TYPING_INDICATOR_MODE", 0), fixture.typingEvent("started")).copy(transient = true)
                val target = fixture.batch("notification", fixture.propertyEvent("WIRE_TYPING_INDICATOR_MODE", 1),
                    fixture.typingEvent("stopped", SELF), fixture.event("first", fixture.encrypt("first"))).copy(transient = true)
                val anchor = fixture.typingEvent("started", SELF).copy(id = "rest-anchor")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.eventDAO.insertEvents(listOf(com.wire.kalium.persistence.dao.event.NewEventEntity(
                        captured.id, captured.payload, true, true)))
                    runtime.database.metadataDAO.insertValue("anchor", com.wire.kalium.persistence.dao.event.NotificationEventStore.FETCH_CURSOR_KEY)
                })
                if (stage != null) {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(anchor, target), target.id, stage))
                    if (stage == Interruption.MARKER || stage == Interruption.DELIVERY) fixture.sql("DROP TRIGGER interrupt_receive")
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val stored = checkNotNull(runtime.database.eventDAO.getEventById(captured.id))
                        assertTrue(stored.transient)
                        assertFalse(stored.isLive)
                        assertFalse(stored.isProcessed)
                        assertEquals(anchor.id, runtime.database.notificationEventStore.cursor())
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        if (stage == Interruption.DELIVERY) {
                            assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                            assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                        }
                    })
                }
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(listOf(anchor, target), target.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals("rest-anchor", runtime.database.notificationEventStore.cursor())
                    for (id in listOf(captured.id, anchor.id, target.id)) assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(id)).isProcessed)
                    assertTrue(runtime.database.userPrefsDAO.isTypingIndicatorEnabled().first())
                    assertNotNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), target.id)).value)
                fixture.verifyStoredAndNext(next, "first", listOf("prefix", "first"))
            }
        }
    }

    @Test
    fun transientTypingAndUnknownConfigurationDoNotBlockCapturedQueue() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val captured = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                    fixture.configEvent("unknown-feature"), fixture.typingEvent("started"),
                    fixture.event("first", fixture.encrypt("first"))).copy(transient = true)
                val tail = fixture.event("tail-notification", fixture.encrypt("tail"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.eventDAO.insertEvents(listOf(captured, tail).map {
                        com.wire.kalium.persistence.dao.event.NewEventEntity(it.id, it.payload, it.transient, true) })
                })
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), tail.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                    assertNull(runtime.database.notificationEventStore.cursor())
                })
                fixture.verifyStoredAndNext(next, "tail", listOf("prefix", "first", "tail"))
            }
        }
    }

    @Test
    fun allTransientCatchUpReceivesWithoutAdvancingCursor() = runTest {
        for (mls in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val first = fixture.typingEvent("started", SELF).copy(id = "typing", transient = true)
                val target = fixture.event("notification", fixture.encrypt("first")).copy(transient = true)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(listOf(first, target), target.id)).value?.text)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.notificationEventStore.cursor())
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(first.id)).isProcessed)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun dueProposalWaitsForAppAfterFreshCatchUp() = runTest {
        for (unavailable in listOf(false, true)) withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                val page = listOf(fixture.memberJoin(), fixture.event("addition", addition), fixture.batch("notification",
                    fixture.event("proposal", Base64.decode(REMOVE_THIRD_PROPOSAL)), fixture.event("first", fixture.encrypt("first"))))
                fixture.observeRemovalProposal()
                val api = CommitApi(fail = unavailable)
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "notification", commitApi = api)).value?.text)
                assertEquals(0, api.attempts)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val client = checkNotNull(fixture.openMLS(runtime))
                    assertEquals(2uL, client.transaction { it.conversationEpoch(GROUP_ID) })
                    assertNotNull(runtime.database.memberDAO.getMemberRole(THIRD.toDao(), CONVERSATION_ID))
                    assertTrue(runtime.database.conversationDAO.getProposalTimers().first().single().firingDate <= com.wire.kalium.util.DateTimeUtil.currentInstant())
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    // A newly opened native client still has the proposal for the existing app consumer.
                    client.transaction { it.commitPendingProposals(GROUP_ID) }
                    assertEquals(3uL, client.transaction { it.conversationEpoch(GROUP_ID) })
                })
                checkNotNull(fixture.senderMLS).transaction {
                    assertIs<MLSDecryptResult.Success>(it.decryptMessage(GROUP_ID, checkNotNull(fixture.transport.bundle).commit))
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(fixture.encrypt("after-app-commit"), "after-app-commit"))
            }
        }
    }

    @Test
    fun proposalDeadlineAndRequiredSQLFailure() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                val firstTime = Instant.parse("2099-01-02T00:00:00Z")
                val secondTime = Instant.parse("2099-01-01T00:00:00Z")
                val secondProposal = Base64.decode(
                    "AAEAARRyZWNlaXZpbmctdGVzdC1ncm91cAAAAAAAAAACAgAAAAAAAgADAAAAAEBA/yNNcqgEIIea7DHCgHsEojoxOH4rYEcmO/oLbyHkn1Uhfk31qSDDMRxsAGb/obXgM7iCqNa80fhbrqJnfPxsCg=="
                )
                val page = listOf(
                    fixture.memberJoin(),
                    fixture.event("addition", addition),
                    fixture.event("proposal", Base64.decode(REMOVE_THIRD_PROPOSAL), firstTime),
                    fixture.event("notification", secondProposal, secondTime)
                )
                fixture.sql(
                    "CREATE TRIGGER proposal_failure BEFORE UPDATE OF mls_proposal_timer ON Conversation WHEN NEW.mls_proposal_timer IS NOT NULL BEGIN SELECT RAISE(ABORT, 'proposal timer failed'); END"
                )
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(page, "notification"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("proposal")).isProcessed)
                        fixture.sql("DROP TRIGGER proposal_failure")
                    }
                )
                var delay: Long? = null
                assertIs<Either.Right<*>>(
                    fixture.receive(emptyList(), "proposal", decorateMLSContext = { real ->
                    object : MlsCoreCryptoContext by real {
                        override suspend fun decryptMessage(groupId: MLSGroupId, message: ByteArray): MLSDecryptResult =
                            real.decryptMessage(groupId, message).also { result ->
                                if (message.contentEquals(Base64.decode(REMOVE_THIRD_PROPOSAL))) {
                                    delay = assertIs<DecryptedMessageBundle.Proposal>(
                                        assertIs<MLSDecryptResult.Success>(result).messages.single()).commitDelay
                                }
                            }
                    }
                })
                )
                val expected = firstTime + checkNotNull(delay).seconds
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertEquals(expected, runtime.database.conversationDAO.getProposalTimers().first().single().firingDate)
                        assertEquals(2uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    }
                )
            }
        }
    }

    @Test
    fun proposalHandoffDoesNotConsumeResponseAcrossCommitInterruptions() = runTest {
        for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                fixture.prepareResetWelcome()
                val prefix = listOf(fixture.memberJoin(), fixture.event("addition", addition))
                val target = fixture.batch("notification", fixture.event("proposal", Base64.decode(REMOVE_THIRD_PROPOSAL)),
                    fixture.event("first", fixture.encrypt("first")))
                val created = assertIs<EventContentDTO.Conversation.NewConversationDTO>(
                    fixture.creationEvent(response = fixture.creationResponse().copy(id = DELETE_TARGET.toApi(), groupId = RESET_GROUP_ID))
                        .toEventResponse().payload.orEmpty().single()
                ).copy(qualifiedConversation = DELETE_TARGET.toApi())
                val welcome = assertIs<EventContentDTO.Conversation.MLSWelcomeDTO>(fixture.welcomeEvent().toEventResponse().payload.orEmpty().single())
                    .copy(qualifiedConversation = DELETE_TARGET.toApi())
                val returned = listOf(created, welcome) + fixture.event("returned", fixture.encrypt("returned", groupId = RESET_GROUP_ID),
                    conversationId = DELETE_TARGET).toEventResponse().payload.orEmpty()
                val api = CommitApi(returned)
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(prefix, "addition"))
                assertIs<Either.Left<*>>(fixture.receive(listOf(target), target.id, stage,
                    commitApi = api))
                if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                assertEquals(0, api.attempts)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                    assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(RESET_GROUP_ID) })
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(target.id)).isProcessed)
                })
                repeat(2) { assertIs<Either.Right<*>>(fixture.receive(emptyList(), target.id, commitApi = api)) }
                assertEquals(0, api.attempts)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                    assertEquals(2uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    assertEquals(1, runtime.database.conversationDAO.getProposalTimers().first().size)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(target.id)).isProcessed)
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun membershipFailureRollsBackCryptoAndAllDatabaseEffects() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val addition = fixture.addThird()
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                val event = fixture.batch(
                    "notification",
                    fixture.event("commit", addition),
                    fixture.memberJoin(),
                    fixture.event("first", first)
                )
                fixture.sql("CREATE TRIGGER membership_failure BEFORE INSERT ON Member BEGIN SELECT RAISE(ABORT, 'membership failed'); END")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertEquals(1uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertNull(runtime.database.memberDAO.getMemberRole(THIRD.toDao(), CONVERSATION_ID))
                        fixture.sql("DROP TRIGGER membership_failure")
                    }
                )
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification"))
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun verificationChangeAndRequiredFailure() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val commit = fixture.commit()
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                fixture.sql("UPDATE Conversation SET verification_status = 'VERIFIED'")
                fixture.sql(
                    "CREATE TRIGGER verification_failure BEFORE UPDATE OF verification_status ON Conversation BEGIN SELECT RAISE(ABORT, 'verification failed'); END"
                )
                fixture.releasePrepared()
                val event = fixture.batch("notification", fixture.event("commit", commit), fixture.event("first", first))
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertEquals(1uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        fixture.sql("DROP TRIGGER verification_failure")
                    }
                )
                val text = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value
                assertTrue(checkNotNull(text).mayPreview)
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertEquals(
                            ConversationEntity.VerificationStatus.DEGRADED,
                            checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).mlsVerificationStatus
                        )
                        assertEquals(
                            MessageEntityContent.ConversationDegradedMLS,
                            assertIs<MessageEntity.System>(
                                runtime.database.messageDAO.getMessageById("notification:mls-verification", CONVERSATION_ID)
                            ).content
                        )
                        assertFalse(runtime.database.conversationDAO.observeDegradedConversationNotified(CONVERSATION_ID).first())
                    }
                )
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun selfRemovalMLS() = selfRemoval(true)

    @Test
    fun selfRemovalProteus() = selfRemoval(false)

    private fun selfRemoval(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val after = fixture.encrypt("after-removal")
                fixture.releasePrepared()
                val page = listOf(fixture.memberLeave("leave-self", SELF), fixture.event("after", after))
                val result = fixture.receive(page, "after")
                if (mls) assertIs<Either.Left<*>>(result)
                else assertEquals("after-removal", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.messageId)
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                        assertIs<MessageEntity.System>(runtime.database.messageDAO.getMessageById("leave-self", CONVERSATION_ID))
                        assertEquals(!mls, checkNotNull(runtime.database.eventDAO.getEventById("after")).isProcessed)
                        val message = runtime.database.messageDAO.getMessageById("after-removal", CONVERSATION_ID)
                        if (mls) assertNull(message) else assertNotNull(message)
                        assertEquals(if (mls) emptyList() else listOf("after-removal"),
                            runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                        if (mls) assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                    }
                )
            }
        }
    }

    @Test
    fun inactiveRemovalCommitReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (interruption in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT)) withFixture(true) { fixture ->
                checkNotNull(
                    fixture.senderMLS
                ).transaction { it.removeMember(GROUP_ID, listOf(CryptoQualifiedClientId(CLIENT, SELF.toCrypto()))) }
                val removal = checkNotNull(fixture.transport.bundle).commit
                val after = fixture.encrypt("after-removal")
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(
                    fixture.receive(
                    listOf(fixture.event("removal", removal), fixture.event("after", after)),
                    "removal",
                    interruption
                )
                )
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "after"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertNotNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                        assertEquals(
                            ConversationEntity.GroupState.PENDING_JOIN,
                            (
                                checkNotNull(
                            runtime.database.conversationDAO
                            .getConversationById(CONVERSATION_ID)
                        ).protocolInfo as ConversationEntity.ProtocolInfo.MLSCapable
                            ).groupState
                        )
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("removal")).isProcessed)
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("after")).isProcessed)
                        assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                        assertEquals(
                            MessageEntityContent.MLSWrongEpochWarning,
                            assertIs<MessageEntity.System>(
                                runtime.database.messageDAO.getMessageById("removal:mls-removal", CONVERSATION_ID)
                            ).content
                        )
                    }
                )
            }
        }
    }

    @Test
    fun inactiveCommitThenBackendSelfLeave() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                checkNotNull(
                    fixture.senderMLS
                ).transaction { it.removeMember(GROUP_ID, listOf(CryptoQualifiedClientId(CLIENT, SELF.toCrypto()))) }
                val commit = checkNotNull(fixture.transport.bundle).commit
                val page = listOf(fixture.event("inactive", commit), fixture.memberLeave("notification", SELF))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(page, "notification"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                        assertIs<MessageEntity.System>(runtime.database.messageDAO.getMessageById("inactive:mls-removal", CONVERSATION_ID))
                        assertEquals(
                            MessageEntityContent.MemberChange(listOf(SELF.toDao()), MessageEntity.MemberChangeType.REMOVED),
                            assertIs<MessageEntity.System>(runtime.database.messageDAO.getMessageById("notification", CONVERSATION_ID)).content
                        )
                    }
                )
            }
        }
    }

    @Test
    fun terminalMLSRemovalAfterCiphertextBlocksBeforeCommit() = runTest {
        withContext(Dispatchers.Default) {
            for (inactiveCommit in listOf(false, true)) withFixture(true) { fixture ->
                val first = fixture.event("first", fixture.encrypt("first"))
                val removal = if (inactiveCommit) {
                    checkNotNull(
                        fixture.senderMLS
                    ).transaction { it.removeMember(GROUP_ID, listOf(CryptoQualifiedClientId(CLIENT, SELF.toCrypto()))) }
                    fixture.event("removal", checkNotNull(fixture.transport.bundle).commit)
                } else fixture.memberLeave("removal", SELF)
                val event = fixture.batch("notification", first, removal)
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification"))
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertNotNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                        assertEquals(1uL, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    }
                )
            }
        }
    }

    @Test
    fun proteusTextThenSelfLeaveReplay() = runTest {
        withContext(Dispatchers.Default) {
            for (interruption in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT)) withFixture(false) { fixture ->
                val event = fixture.batch(
                    "notification",
                    fixture.event("first", fixture.encrypt("first")),
                    fixture.memberLeave("leave", SELF)
                )
                fixture.releasePrepared()
                assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", interruption))
                // A committed Proteus duplicate has no new plaintext result; its SQL delivery survives.
                val replay = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value
                if (interruption == Interruption.BEFORE_COMMIT) assertEquals("first", replay?.messageId) else assertNull(replay)
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                        assertEquals(listOf("first"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                    }
                )
            }
        }
    }

    @Test
    fun legalHoldProfileHandoffPreservesRawEventAndCrypto() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) for (enabled in listOf(true, false)) {
                withFixture(mls) { fixture ->
                    val event = fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")),
                        fixture.connectionEvent("pending", peer = SENDER), fixture.event("text", fixture.encrypt("first")))
                    val profile = fixture.connectionProfile(SENDER.toApi()).copy(legalHoldStatus =
                        if (enabled) com.wire.kalium.network.api.model.LegalHoldStatusDTO.ENABLED
                        else com.wire.kalium.network.api.model.LegalHoldStatusDTO.DISABLED)
                    fixture.releasePrepared()
                    suspend fun cache(hasLegalHold: Boolean) {
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            runtime.database.clientDAO.deleteClientsOfUserByQualifiedID(SENDER.toDao())
                            if (hasLegalHold) runtime.database.clientDAO.insertClient(com.wire.kalium.persistence.dao.client.InsertClientParam(
                                SENDER.toDao(), "legal-hold", com.wire.kalium.persistence.dao.client.DeviceTypeEntity.LegalHold,
                                com.wire.kalium.persistence.dao.client.ClientTypeEntity.LegalHold,
                                null, null, null, null, null, false, false))
                        })
                    }
                    cache(!enabled)
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id,
                        fetchConnectionPeer = { profile }))
                    assertEquals(NotificationAppWork.LEGAL_HOLD, fixture.receivedAppWork)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    })
                    assertIs<Either.Right<*>>(fixture.receive(emptyList(), event.id,
                        fetchConnectionPeer = { profile }))
                    assertEquals(NotificationAppWork.LEGAL_HOLD, fixture.receivedAppWork)
                    // Model app completion of the client refresh, then retry the same native ciphertext.
                    cache(enabled)
                    assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(
                        fixture.receive(emptyList(), event.id, fetchConnectionPeer = { profile })).value?.text)
                    assertNull(fixture.receivedAppWork)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertNotNull(runtime.database.messageDAO.getMessageById("prefix", CONVERSATION_ID))
                    })
                }
            }
        }
    }

    @Test
    fun legalHoldMembershipPolicyMatrix() = runTest {
        withContext(Dispatchers.Default) {
            for (scenario in listOf("other-leave", "self-leave", "noop", "absent-leave", "degraded", "pending", "pending-user")) {
                withFixture(false) { fixture ->
                    val hold = scenario in listOf("other-leave", "self-leave", "degraded")
                    fixture.sql("UPDATE Conversation SET legal_hold_status = '${if (scenario == "degraded") "DEGRADED" else if (hold) "ENABLED" else "DISABLED"}'")
                    val membership = if (scenario in listOf("other-leave", "self-leave", "absent-leave"))
                        fixture.memberLeave(member = if (scenario == "self-leave") SELF else THIRD)
                    else fixture.memberJoin()
                    val tail = fixture.event("tail", fixture.encrypt("tail"))
                    fixture.releasePrepared()
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        runtime.database.userDAO.upsertUser(user(THIRD))
                        if (scenario == "other-leave") runtime.database.memberDAO.insertMember(
                            com.wire.kalium.persistence.dao.member.MemberEntity(THIRD.toDao(),
                                com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member), CONVERSATION_ID)
                        if (hold) runtime.database.clientDAO.insertClient(com.wire.kalium.persistence.dao.client.InsertClientParam(
                            SENDER.toDao(), "legal-hold", com.wire.kalium.persistence.dao.client.DeviceTypeEntity.LegalHold,
                            com.wire.kalium.persistence.dao.client.ClientTypeEntity.LegalHold,
                            null, null, null, null, null, false, false))
                        if (scenario.startsWith("pending")) runtime.database.metadataDAO.insertValue("[{}]",
                            if (scenario == "pending-user") "legal-hold-user-transitions-v1" else "legal-hold-conversation-transitions-v1")
                    })
                    val result = assertIs<Either.Right<ReceivedMessageResult?>>(
                        fixture.receive(listOf(membership, tail), "tail"))
                    val needsApp = scenario in listOf("self-leave", "degraded", "pending", "pending-user")
                    assertEquals(if (needsApp) NotificationAppWork.LEGAL_HOLD else null, fixture.receivedAppWork, scenario)
                    assertEquals(if (needsApp) null else "tail", result.value?.text, scenario)
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertEquals(!needsApp, checkNotNull(runtime.database.eventDAO.getEventById(membership.id)).isProcessed, scenario)
                        assertEquals(!needsApp, checkNotNull(runtime.database.eventDAO.getEventById("tail")).isProcessed, scenario)
                    })
                }
            }
        }
    }

    @Test
    fun legalHoldMembershipHandoffMLS() = legalHoldMembershipHandoff(true)

    @Test
    fun legalHoldMembershipHandoffProteus() = legalHoldMembershipHandoff(false)

    private fun legalHoldMembershipHandoff(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.sql("UPDATE Conversation SET legal_hold_status = 'ENABLED'")
                val event = fixture.batch("notification", fixture.memberJoin(), fixture.event("text", fixture.encrypt("first")))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime -> runtime.database.userDAO.upsertUser(user(THIRD)) })
                assertIs<Either.Right<*>>(fixture.receive(listOf(event), "notification"))
                assertEquals(NotificationAppWork.LEGAL_HOLD, fixture.receivedAppWork)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    assertNull(runtime.database.memberDAO.getMemberRole(THIRD.toDao(), CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                })
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), "notification"))
                assertEquals(NotificationAppWork.LEGAL_HOLD, fixture.receivedAppWork)
                // Model completion of the app's status work, then prove native rollback left ciphertext replayable.
                fixture.sql("UPDATE Conversation SET legal_hold_status = 'DISABLED'")
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(emptyList(), "notification")).value?.text)
                assertNull(fixture.receivedAppWork)
            }
        }
    }

    @Test
    fun legalHoldNoopsMLS() = legalHoldNoops(true)

    @Test
    fun legalHoldNoopsProteus() = legalHoldNoops(false)

    private fun legalHoldNoops(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                fixture.sql("UPDATE Conversation SET legal_hold_status = 'ENABLED'")
                val text = fixture.encrypt("first", legalHold = Conversation.LegalHoldStatus.ENABLED)
                val next = fixture.encrypt("next")
                val event = fixture.batch(
                    "notification",
                    fixture.plainEvent(
                        "request",
                        """{"type":"user.legalhold-request","id":"${SELF.value}","client":{"id":"123"},"last_prekey":{"id":65535,"key":"key"}}"""
                    ),
                    fixture.plainEvent("enable", """{"type":"user.legalhold-enable","id":"${SELF.value}"}"""),
                    fixture.plainEvent("disable", """{"type":"user.legalhold-disable","id":"${SELF.value}"}"""),
                    fixture.configEvent("legalhold"),
                    fixture.event("text", text)
                )
                fixture.releasePrepared()
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value?.text)
                assertIs<Either.Right<*>>(
                    fixture.open { runtime ->
                        assertEquals(
                            ConversationEntity.LegalHoldStatus.ENABLED,
                            checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).legalHoldStatus
                        )
                    }
                )
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun `MLS commit advances the epoch silently before the following encrypted target`() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val epoch = checkNotNull(fixture.receiverMLS).transaction { it.conversationEpoch(GROUP_ID) }
                val commit = fixture.commit()
                val text = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val page = listOf(fixture.event("commit", commit), fixture.event("notification", text))
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "commit")).value)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(epoch + 1u, checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) })
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("commit")).isProcessed)
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    assertEquals(listOf("first"), runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun `MLS commit only replay works on both sides of native commit using the event queue`() = runTest {
        withContext(Dispatchers.Default) {
            for (interruption in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
                withFixture(true) { fixture ->
                    val before = fixture.encrypt("before")
                    val commit = fixture.commit()
                    val first = fixture.encrypt("first")
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    val page = listOf(fixture.event("before", before), fixture.event("commit", commit), fixture.event("notification", first))
                    assertIs<Either.Right<*>>(fixture.receive(page.take(1), "before"))
                    assertIs<Either.Left<*>>(fixture.receive(page.drop(1), "commit", interruption))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("commit")).isProcessed)
                        if (interruption == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    })
                    assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value?.text)
                    fixture.verifyStoredAndNext(next, "first", listOf("before", "first"))
                }
            }
        }
    }

    @Test
    fun `MLS commit replay reports duplicate then stale and continues later payloads`() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val commit = fixture.commit()
                val first = fixture.encrypt("first")
                val secondCommit = fixture.commit()
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val page = listOf(
                    fixture.event("commit", commit),
                    fixture.batch("notification", fixture.event("duplicate", commit), fixture.event("text", first)),
                    fixture.event("second-commit", secondCommit),
                )
                assertIs<Either.Right<*>>(fixture.receive(page.take(1), "commit"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertEquals(MLSFailure.DuplicateMessage, assertIs<Either.Left<*>>(
                            decryptMLSMessage(context, commit, GroupID(GROUP_ID))
                        ).value)
                    }
                })
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page.drop(1), "notification")).value?.text)
                assertIs<Either.Right<*>>(fixture.receive(emptyList(), "second-commit"))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    checkNotNull(fixture.openMLS(runtime)).transaction { context ->
                        assertEquals(MLSFailure.StaleCommit, assertIs<Either.Left<*>>(
                            decryptMLSMessage(context, commit, GroupID(GROUP_ID))
                        ).value)
                    }
                })
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun `duplicate application payloads do not skip new text later in the notification`() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val first = fixture.encrypt("first")
                val later = fixture.encrypt("later")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val page = listOf(fixture.event("notification", first),
                    fixture.batch("tail", fixture.event("duplicate", first), fixture.event("later", later)))
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(page, "notification")).value?.text)
                fixture.verifyProcessedText(page.last(), "later")
                fixture.verifyStoredAndNext(next, "later", listOf("first", "later"))
            }
        }
    }

    @Test
    fun `MLS exact duplicate result ignores opaque payload and continues fresh text`() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val opaque = byteArrayOf(42)
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val event = fixture.batch("notification", fixture.event("duplicate", opaque), fixture.event("text", first))
                val result = fixture.receive(listOf(event), "notification", decorateMLSContext = { context ->
                    object : MlsCoreCryptoContext by context {
                        override suspend fun decryptMessage(groupId: MLSGroupId, message: ByteArray): MLSDecryptResult {
                            if (message.contentEquals(opaque)) {
                                throw CommonizedMLSException(MLSFailure.DuplicateMessage, IllegalStateException("fixture duplicate"))
                            }
                            return context.decryptMessage(groupId, message)
                        }
                    }
                })
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun `MLS deferred decrypted work rolls back earlier text and remains replayable`() = runTest {
        withContext(Dispatchers.Default) {
            val unsupported = listOf(MLSFailure.WrongEpoch, MLSFailure.UnmergedPendingGroup)
            for (unsupportedResult in unsupported) withFixture(true) { fixture ->
                val first = fixture.encrypt("first")
                val tail = fixture.encrypt("tail")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val event = fixture.batch("notification", fixture.event("text", first), fixture.event("unsupported", tail))
                val result = fixture.receive(listOf(event), "notification", nowEpochMillis = { 1_000L }, decorateMLSContext = { context ->
                    object : MlsCoreCryptoContext by context {
                        override suspend fun decryptMessage(groupId: MLSGroupId, message: ByteArray): MLSDecryptResult {
                            val decrypted = context.decryptMessage(groupId, message)
                            if (message.contentEquals(tail)) {
                                throw CommonizedMLSException(unsupportedResult, IllegalStateException("fixture unsupported recovery"))
                            }
                            return decrypted
                        }
                    }
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertEquals("QUEUE_END", fixture.receivedStop)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                    assertEquals(31_000L, runtime.database.notificationEventStore.deferredUntil("notification"))
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    assertNull(runtime.database.messageDAO.getMessageById("tail", CONVERSATION_ID))
                    assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                })
                // The real decryptions must roll back before deferral and remain replayable after the cooldown.
                assertEquals("tail", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification", nowEpochMillis = { 32_000L })).value?.text)
                fixture.verifyStoredAndNext(next, "tail", listOf("first", "tail"))
            }
        }
    }

    @Test
    fun `MLS commit and text share notification persistence and replay after rollback`() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(true) { fixture ->
                val commit = fixture.commit()
                val first = fixture.encrypt("first")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val page = listOf(fixture.batch("notification", fixture.event("commit", commit), fixture.event("text", first)))
                assertIs<Either.Left<*>>(fixture.receive(page, "notification", Interruption.DELIVERY))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                    fixture.sql("DROP TRIGGER interrupt_receive")
                })
                assertIs<Either.Left<*>>(fixture.receive(emptyList(), "notification", Interruption.BEFORE_COMMIT))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                })
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), "notification")).value?.text)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    @Test
    fun `app stored backlog uses the shared event queue for both protocols`() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val earlier = fixture.event("z-earlier", fixture.encrypt("earlier"))
                val target = fixture.event("notification", fixture.encrypt("first"))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.eventDAO.insertEvents(listOf(NewEventEntity(earlier.id, earlier.payload, false, true)))
                    runtime.database.eventDAO.storeFetchedPage(listOf(NewEventEntity(target.id, target.payload, false, false)))
                })
                val result = fixture.receive(emptyList(), target.id)
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                fixture.verifyStoredAndNext(next, "first", listOf("earlier", "first"))
            }
        }
    }

    @Test
    fun pendingAlertSurvivesAccountCloseAndReopenForBothProtocols() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val target = fixture.event("target", fixture.encrypt("pending"))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.receive(listOf(target), target.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val alerts = UnnotifiedMessageAlerts(runtime.database, SELF) { TIME }
                    assertEquals(listOf("pending"), alerts.take().map { it.text })
                })
                // The first native database/crypto owner has closed, before any native enqueue acknowledgement.
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val alerts = UnnotifiedMessageAlerts(runtime.database, SELF) { TIME }
                    assertEquals(listOf("pending"), alerts.pending.read().map { it.text })
                    runtime.database.userPrefsDAO.persistMessageContentPreference(false)
                    assertNull(alerts.pending.read().single().text)
                    alerts.pending.acknowledge(alerts.pending.read().map {
                        com.wire.kalium.persistence.dao.message.NotificationMessageKey(
                            it.messageId, QualifiedIDEntity(it.conversationValue, it.conversationDomain),
                        )
                    })
                })
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(emptyList(), UnnotifiedMessageAlerts(runtime.database, SELF) { TIME }.pending.read())
                })
            }
        }
    }

    @Test
    fun `unnotified recent messages are taken once and in order for both protocols`() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val old = fixture.event("old", fixture.encrypt("old"))
                val earlier = fixture.event("earlier", fixture.encrypt("earlier"), time = TIME + 1.seconds)
                val target = fixture.event("target", fixture.encrypt("target"), time = TIME + 2.seconds)
                fixture.releasePrepared()

                assertIs<Either.Right<*>>(fixture.receive(listOf(old, earlier, target), target.id))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val now = TIME + UnnotifiedMessageAlerts.MAX_AGE + 500.milliseconds
                    val alerts = UnnotifiedMessageAlerts(runtime.database, SELF) { now }
                    // The oldest message is past the age bound; taking marks it notified with the others.
                    assertEquals(listOf("earlier", "target"), alerts.take().map { it.text })
                    assertEquals(emptyList(), alerts.take())
                    val conversation = runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)
                    assertEquals(TIME + 2.seconds, conversation?.lastNotificationDate)
                })
            }
        }
    }

    @Test
    fun `sync to live processes past the target to the queue end without fetching for both protocols`() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val events = listOf("earlier", "target", "later").mapIndexed { index, text ->
                    fixture.event(text, fixture.encrypt(text), time = TIME + index.seconds)
                }
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    // The bounded catch-up stores the stream before the scan.
                    runtime.database.eventDAO.storeFetchedPage(events.map { NewEventEntity(it.id, it.payload, false, false) })
                })

                val result = fixture.receive(emptyList(), "target", toLive = { false })

                assertEquals("target", assertIs<Either.Right<ReceivedMessageResult?>>(result).value?.text)
                assertEquals("QUEUE_END", fixture.receivedStop)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(events.all { checkNotNull(runtime.database.eventDAO.getEventById(it.id)).isProcessed })
                    assertEquals(listOf("earlier", "target", "later"), UnnotifiedMessageAlerts(runtime.database, SELF) { TIME + 10.seconds }.take().map { it.text })
                })
            }
        }
    }

    @Test
    fun `sync to live stops before the next event at its deadline and keeps the tail for both protocols`() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val events = listOf("earlier", "target", "later").mapIndexed { index, text ->
                    fixture.event(text, fixture.encrypt(text), time = TIME + index.seconds)
                }
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.eventDAO.storeFetchedPage(events.map { NewEventEntity(it.id, it.payload, false, false) })
                })
                var checks = 0

                val result = fixture.receive(emptyList(), "target",
                    toLive = { checks++ >= 1 })

                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertEquals("BUDGET", fixture.receivedStop)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(listOf(true, false, false), events.map { checkNotNull(runtime.database.eventDAO.getEventById(it.id)).isProcessed })
                    assertEquals(listOf("earlier"), UnnotifiedMessageAlerts(runtime.database, SELF) { TIME + 10.seconds }.take().map { it.text })
                })
            }
        }
    }

    @Test
    fun `sync to live without its push in the queue still processes every stored event for both protocols`() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val events = listOf("first", "second").mapIndexed { index, text ->
                    fixture.event(text, fixture.encrypt(text), time = TIME + index.seconds)
                }
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    runtime.database.eventDAO.storeFetchedPage(events.map { NewEventEntity(it.id, it.payload, false, false) })
                })

                val result = fixture.receive(emptyList(), "absent",
                    toLive = { false })

                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(result).value)
                assertEquals("QUEUE_END", fixture.receivedStop)
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertEquals(listOf("first", "second"), UnnotifiedMessageAlerts(runtime.database, SELF) { TIME + 10.seconds }.take().map { it.text })
                })
            }
        }
    }

    @Test
    fun `unknown future event does not block later text for both protocols`() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val unknown = fixture.unknownEvent("unknown", "conversation.future-event")
                val target = fixture.event("target", fixture.encrypt("target"))
                fixture.releasePrepared()

                assertEquals(
                    "target",
                    assertIs<Either.Right<ReceivedMessageResult?>>(
                        fixture.receive(listOf(unknown, target), target.id)
                    ).value?.text
                )
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(unknown.id)).isProcessed)
                    assertNull(runtime.database.messageDAO.getMessageById(unknown.id, CONVERSATION_ID))
                    assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(target.id)).isProcessed)
                    assertNotNull(runtime.database.messageDAO.getMessageById(target.id, CONVERSATION_ID))
                })
            }
        }
    }

    @Test
    fun groupNotificationRetainsConversationType() = runTest {
        withContext(Dispatchers.Default) {
            withFixture(false) { fixture ->
                val event = fixture.event("group-message", fixture.encrypt("hello group"))
                fixture.releasePrepared()
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                    runtime.database.conversationDAO.insertConversation(conversation.copy(type = ConversationEntity.Type.GROUP))
                })
                val result = checkNotNull(assertIs<Either.Right<ReceivedMessageResult?>>(
                    fixture.receive(listOf(event), event.id)).value)
                assertTrue(result.isGroupConversation)
                assertEquals("hello group", result.text)
                assertEquals("Fixture", result.senderName)
                assertEquals("Fixture", result.conversationName)
            }
        }
    }

    @Test
    fun `Proteus multiple payloads commit together and recover selection and quotes`() = multiplePayloads(false)

    @Test
    fun `MLS multiple payloads commit together and recover selection and quotes`() = multiplePayloads(true)

    @Test
    fun duplicateTextPayloadsCompleteOnReplay() = runTest {
        for (mls in listOf(false, true)) for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.MARKER)) {
            withContext(Dispatchers.Default) {
                withFixture(mls) { fixture ->
                    val event = fixture.batch("notification", fixture.event("first", fixture.encrypt("first")),
                        fixture.event("repeated", fixture.encrypt("first")), fixture.event("tail", fixture.encrypt("tail")))
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    if (stage != null) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id, stage))
                        if (stage == Interruption.MARKER) fixture.sql("DROP TRIGGER interrupt_receive")
                    }
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertEquals(1, runtime.database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
                            .count { it.id == "first" })
                    })
                    fixture.verifyStoredAndNext(next, "tail", listOf("first", "tail"))
                }
            }
        }
    }

    @Test
    fun wrongProtocolPayloadUsesExistingFailurePolicy() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) withFixture(mls) { fixture ->
                val first = fixture.encrypt("first")
                val tail = fixture.encrypt("tail")
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                val event = fixture.batch("notification", fixture.event("first", first), fixture.event("tail", tail, protocolMLS = !mls))
                if (mls) {
                    assertIs<Either.Right<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNotNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        val failure = assertIs<MessageEntity.Regular>(runtime.database.messageDAO
                            .getMessageById(event.id, CONVERSATION_ID))
                        assertIs<MessageEntityContent.FailedDecryption>(failure.content)
                        assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                    })
                } else {
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), event.id))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                        assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                    })
                    assertIs<Either.Right<*>>(fixture.decryptAndCheck(first, "first"))
                }
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    @Test
    fun `read multiple payloads remain silent after recovery for both protocols`() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(false, true)) {
                withFixture(mls) { fixture ->
                    fixture.sql("UPDATE Conversation SET last_read_date = ${TIME.toEpochMilliseconds() + 1_000}")
                    val first = fixture.event("first", fixture.encrypt("first"))
                    val last = fixture.event("last", fixture.encrypt("last"))
                    val event = fixture.batch("notification", first, last)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    assertIs<Either.Left<*>>(fixture.receive(listOf(event), "notification", Interruption.BEFORE_COMMIT))
                    val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(event), "notification")).value
                    assertEquals("last", result?.messageId)
                    assertEquals(false, result?.shouldNotify)
                    fixture.verifyStoredAndNext(next, "last", listOf("first", "last"))
                }
            }
        }
    }

    private fun multiplePayloads(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            for (stage in listOf(null, Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.SECOND_MESSAGE, Interruption.MARKER)) {
                withFixture(mls) { fixture ->
                    fixture.sql("UPDATE Conversation SET last_read_date = ${TIME.toEpochMilliseconds() - 1_000}")
                    val first = fixture.event("first", fixture.encrypt("first"))
                    val hash = checkNotNull(MessageContentEncoder().encodeMessageContent(TIME, MessageContent.Text("first"))).sha256Digest
                    val quote = MessageContent.Text("quoted", quotedMessageReference = MessageContent.QuoteReference("first", hash, false))
                    val quoted = fixture.event("quoted", fixture.encrypt("quoted", quote), Instant.fromEpochMilliseconds(TIME.toEpochMilliseconds() - 2_000))
                    val notification = fixture.batch("notification", first, quoted)
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    if (stage != null) {
                        assertIs<Either.Left<*>>(fixture.receive(listOf(notification), "notification", stage))
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                            if (stage == Interruption.SECOND_MESSAGE) {
                                assertNull(runtime.database.messageDAO.getMessageById("first", CONVERSATION_ID))
                                assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                            }
                            fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                        })
                    }
                    val result = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(listOf(notification), "notification")).value
                    if (stage == Interruption.AFTER_COMMIT || stage == Interruption.MARKER) assertNull(result)
                    else {
                        assertEquals("first", result?.messageId)
                        assertEquals(true, result?.shouldNotify)
                    }
                    fixture.verifyStoredAndNext(next, "first", listOf("first", "quoted"))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        val stored = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById("quoted", CONVERSATION_ID))
                        val content = assertIs<MessageEntityContent.Text>(stored.content)
                        assertEquals(true, content.quotedMessage?.isVerified)
                    })
                }
            }
        }
    }

    @Test
    fun `Proteus replays the event queue across crypto commit gaps without repeating required effects`() = recoveryRoundTrip(false)

    @Test
    fun `MLS replays the event queue across crypto commit gaps without repeating required effects`() = recoveryRoundTrip(true)

    private fun recoveryRoundTrip(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            for (stage in listOf(Interruption.BEFORE_COMMIT, Interruption.AFTER_COMMIT, Interruption.DELIVERY, Interruption.MARKER)) {
                withFixture(mls) { fixture ->
                    val first = fixture.encrypt("first")
                    val next = fixture.encrypt("next")
                    fixture.releasePrepared()
                    val events = listOf(fixture.event("notification", first))
                    assertIs<Either.Left<*>>(fixture.receive(events, "notification", stage))
                    assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertFalse(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                        fixture.sql("DROP TRIGGER IF EXISTS interrupt_receive")
                    })
                    val recovered = assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(events, "notification")).value
                    if (stage == Interruption.AFTER_COMMIT || stage == Interruption.MARKER) assertNull(recovered)
                    else assertEquals("first", recovered?.text)
                    fixture.verifyStoredAndNext(next, "first")
                }
            }
        }
    }

    @Test
    fun `Proteus cancellation replays durable effects after reopening crypto`() = cancelledReceiving(false)

    @Test
    fun `MLS cancellation replays durable effects after reopening crypto`() = cancelledReceiving(true)

    private fun cancelledReceiving(mls: Boolean) = runTest {
        withContext(Dispatchers.Default) {
            withFixture(mls) { fixture ->
                val events = listOf(fixture.event("notification", fixture.encrypt("first")))
                val next = fixture.encrypt("next")
                fixture.releasePrepared()
                assertFailsWith<CancellationException> { fixture.receive(events, "notification", Interruption.CANCEL_BEFORE_COMMIT) }
                assertEquals("first", assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(events, "notification")).value?.text)
                fixture.verifyStoredAndNext(next, "first")
            }
        }
    }

    // Invoked by the separate test-only executable, never by ordinary test discovery.
    fun exportPreparedAccountsForSwift(multiple: Boolean = false, mlsCommit: Boolean = false, ordinary: Boolean = false,
        synchronization: Boolean = false,
        membership: Boolean = false,
        creation: Boolean = false,
        recovery: Boolean = false,
        reset: Boolean = false,
        migration: Boolean = false,
        oneOnOne: Boolean = false,
        content: Boolean = false,
        signals: Boolean = false,
        assets: Boolean = false,
        cleanup: Boolean = false,
        multipart: Boolean = false,
        previews: Boolean = false,
        decryption: Boolean = false,
        buffering: Boolean = false,
        mlsIgnores: Boolean = false,
        conversationCleanup: Boolean = false,
        identity: Boolean = false,
        meetings: Boolean = false,
        federation: Boolean = false,
        meetingConversation: Boolean = false,
        connections: Boolean = false,
        sentConnections: Boolean = false,
        acceptedConnections: Boolean = false,
        selfConversations: Boolean = false,
        sessionRefresh: Boolean = false,
        e2eiSettings: Boolean = false,
        mlsSettings: Boolean = false,
        mlsClients: Boolean = false,
        mlsProtocols: Boolean = false,
        mlsMigration: Boolean = false,
        mlsCipher: Boolean = false,
        x509: Boolean = false,
        remoteMute: Boolean = false,
        roomSettings: Boolean = false,
        accessChanges: Boolean = false,
        memberRoles: Boolean = false,
        typing: Boolean = false,
        calling: Boolean = false,
    ) = runTest {
        withContext(Dispatchers.Default) {
            val manifests = mutableListOf<JsonObject>()
            val variants = if (membership || recovery || reset || migration || mlsIgnores || meetingConversation || selfConversations || x509) listOf(
                    false to false,
                    true to false,
                    true to true
                ) else if (synchronization || creation || signals || conversationCleanup || identity || federation || acceptedConnections || e2eiSettings || mlsSettings || roomSettings || accessChanges || memberRoles) listOf(false to false, true to false, false to true, true to true)
                else listOf(false to false, true to false)
            for ((isMLS, readState) in variants) {
                val pendingProposal = membership && isMLS && readState
                val path = (if (isMLS) "mls" else "proteus") + if (mlsIgnores && isMLS) if (readState) "-old" else "-own" else if (pendingProposal) "-pending" else if (creation && readState) "-fetched" else if (readState) "-read" else ""
                val fixture = Fixture(isMLS, "https://localhost:18443/$path/", apiVersion = if (meetings || meetingConversation) 17 else 8,
                    senderId = if (selfConversations || readState && !membership && !creation && !recovery && !reset && !migration && !mlsIgnores && !conversationCleanup && !identity && !federation && !meetingConversation && !acceptedConnections && !e2eiSettings && !mlsSettings && !x509 && !roomSettings && !accessChanges && !memberRoles) SELF else SENDER, newConversation = creation || oneOnOne || meetingConversation || selfConversations || (migration && isMLS && readState), x509 = x509 && isMLS)
                try {
                    fixture.prepare()
                    val commit = if (isMLS && mlsCommit) fixture.event("commit", fixture.commit()) else null
                    var prefix = emptyList<EventResponseToStore>()
                    var bufferNext: ByteArray? = null
                    var appBufferResponse: NotificationResponse? = null
                    var event = if (calling) {
                        prefix = listOf(fixture.callEventForSwift())
                        fixture.event("notification", fixture.encrypt("first"))
                    } else if (typing) fixture.typingNoopEvents() else if (memberRoles) {
                        if (readState) {
                            fixture.sql("DELETE FROM Conversation WHERE qualified_id = '$CONVERSATION_ID'")
                            fixture.batch("notification", fixture.memberRoleEvent("wire_admin"), fixture.memberRoleEvent("wire_member"),
                                fixture.event("first", fixture.encrypt("first")))
                        } else fixture.memberRoleEvents()
                    } else if (accessChanges) {
                        fixture.seedAccess(readState)
                        fixture.accessEvents(enabled = !readState)
                    } else if (roomSettings) {
                        fixture.seedRoomSettings()
                        fixture.roomSettingsEvents(if (readState) "delete" else "fallback")
                    } else if (remoteMute) {
                        fixture.ignoredRemoteMuteEvents()
                    } else if (x509) {
                        if (isMLS) {
                            if (readState) fixture.prepareRevokedX509Credentials()
                            fixture.x509Events()
                        } else fixture.batch("notification", fixture.event("prefix", fixture.encrypt("prefix")), fixture.event("first", fixture.encrypt("first")))
                    } else if (mlsSettings) {
                        fixture.seedMLSSettings()
                        if (mlsProtocols) fixture.seedMLSProtocolResolution() else if (mlsClients) fixture.seedMLSClients()
                        if (mlsCipher) {
                            fixture.installChangedCipherCredential()
                            fixture.mlsCipherEvents(enabled = !readState)
                        } else if (mlsMigration) fixture.mlsMigrationEvents(enabled = !readState)
                        else fixture.mlsSettingsEvents(enabled = !readState, changeTeamProtocols = mlsClients)
                    } else if (e2eiSettings) {
                        // The app identity observer joins the already registered client to its MLS groups.
                        fixture.sql("INSERT INTO Client(user_id, id, is_mls_capable) VALUES ('${SELF.toDao()}', '$CLIENT', ${if (isMLS) 1 else 0})")
                        fixture.e2eiEvents(required = !readState)
                    } else if (sessionRefresh) {
                        fixture.sessionRefreshEvents()
                    } else if (selfConversations) {
                        fixture.seedDeleteForMeTarget()
                        fixture.selfConversationPage(if (readState) "welcome-first" else "together").single()
                    } else if (sentConnections) {
                        prefix = listOf(fixture.pendingConnectionCreation())
                        fixture.sql("DELETE FROM Member WHERE user = '${SELF.toDao()}'")
                        fixture.sql("UPDATE Conversation SET proteus_verification_status = 'DEGRADED', verification_status = 'DEGRADED'")
                        fixture.sentConnectionEvents()
                    } else if (acceptedConnections) {
                        fixture.seedAcceptedConnection(readState)
                        fixture.sql("UPDATE Conversation SET proteus_verification_status = 'DEGRADED', verification_status = 'DEGRADED'")
                        fixture.acceptedConnectionEvents()
                    } else if (connections) {
                        fixture.seedProteusConversation()
                        fixture.seedConversationCleanupFiles(DELETE_TARGET)
                        fixture.connectionEvents()
                    } else if (meetingConversation) {
                        fixture.meetingConversationEvent(welcomeFirst = readState)
                    } else if (federation) {
                        fixture.seedFederationState(deleteRequests = !readState)
                        fixture.federationEvents(deleted = !readState)
                    } else if (meetings) {
                        fixture.meetingEvents()
                    } else if (identity) {
                        fixture.seedIdentityState()
                        fixture.identityEvent(own = readState)
                    } else if (conversationCleanup) {
                        if (readState) fixture.seedProteusConversation()
                        fixture.seedConversationCleanupFiles(if (readState) DELETE_TARGET else CONVERSATION_ID)
                        fixture.conversationCleanupEvent(if (readState) CleanupMode.BACKEND_DELETE else CleanupMode.CLEAR)
                    } else if (mlsIgnores && isMLS) {
                        fixture.batch("notification", fixture.event("ignored", fixture.ignoredMLSCiphertext(readState)),
                            fixture.event("first", fixture.encrypt("first")))
                    } else if (buffering && isMLS) {
                        val missing = fixture.commit()
                        val future = fixture.encrypt("first")
                        bufferNext = fixture.encrypt("next")
                        val appMissing = fixture.commit()
                        appBufferResponse = NotificationResponse(TIME.toString(), false,
                            listOf(fixture.event("app-buffer-trigger", appMissing)))
                        fixture.batch("notification", fixture.event("future", future), fixture.event("missing", missing),
                            fixture.event("app-future", fixture.encrypt("app-buffered")))
                    } else if (cleanup) {
                        fixture.seedMessageFiles()
                        fixture.seedExpiredMessageFiles()
                        fixture.sql("INSERT INTO Asset(key, domain, data_path, data_size, downloaded_date) " +
                            "VALUES('asset', '${SELF.domain}', '${fixture.root}/missing-download.bin', 4, 0)")
                        fixture.batch("notification", fixture.messageFileDeletionEvent(includeText = false),
                            fixture.event("reset", fixture.encrypt("session-reset", MessageContent.ClientAction)),
                            fixture.event("delete-reset", fixture.encrypt("delete-reset", MessageContent.DeleteMessage("session-reset"))),
                            fixture.assetEvent())
                    } else if (decryption) fixture.batch("notification",
                        fixture.event("bad", byteArrayOf(0), protocolMLS = false), fixture.event("first", fixture.encrypt("first")))
                    else if (previews) fixture.previewEvent() else if (multipart) fixture.multipartEvent(edited = true) else if (assets) fixture.assetEvent() else if (signals) fixture.signalingEvent() else if (content) fixture.batch("notification", fixture.compositeEvent(), fixture.regularContentEvent()) else if (oneOnOne) {
                        val response = fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE)
                        if (isMLS) {
                            fixture.seedProteusConversation()
                            fixture.seed(storedText("history").copy(conversationId = DELETE_TARGET))
                            fixture.batch("notification", fixture.event("proteus-first", fixture.encrypt("proteus-first", protocolMLS = false),
                                protocolMLS = false, conversationId = DELETE_TARGET), fixture.welcomeEvent(),
                                fixture.event("text", fixture.encrypt("first")))
                        } else fixture.batch("notification", fixture.creationEvent(response = response),
                            fixture.event("text", fixture.encrypt("first")))
                    } else if (migration && isMLS) {
                        fixture.sql("UPDATE Conversation SET protocol = 'PROTEUS', type = 'GROUP', verification_status = 'DEGRADED'")
                        fixture.migrationEvent(creation = readState)
                    } else if (reset && isMLS) {
                        fixture.prepareResetWelcome()
                        fixture.sql("UPDATE Conversation SET mls_proposal_timer = '$TIME'")
                        val before = if (readState) listOf(fixture.welcomeEvent(), fixture.resetEvent())
                            else listOf(fixture.resetEvent(), fixture.welcomeEvent())
                        fixture.batch("notification", *(before + fixture.event("text",
                            fixture.encrypt("first", groupId = RESET_GROUP_ID))).toTypedArray())
                    } else if (recovery && isMLS) {
                        val inactive = fixture.event("inactive", fixture.prepareRejoinWelcome())
                        prefix = if (readState) listOf(inactive, fixture.memberLeave("leave", SELF)) else emptyList()
                        val before = if (readState) listOf(fixture.memberJoin("self-join", SELF)) else listOf(inactive)
                        fixture.batch("notification", *(before + listOf(fixture.welcomeEvent(),
                            fixture.event("text", fixture.encrypt("first")))).toTypedArray())
                    } else if (creation) {
                        fixture.sql("UPDATE User SET name = NULL WHERE qualified_id = '${SENDER.toDao()}'")
                        if (readState) {
                            prefix = listOf(if (isMLS) fixture.welcomeEvent() else fixture.creationEvent())
                            fixture.event("notification", fixture.encrypt("first"))
                        } else fixture.creationPage(true).single()
                    } else if (membership) {
                        fixture.membershipEvent(pendingProposal)
                    } else if (synchronization) {
                        if (readState) fixture.readStateEvent() else fixture.synchronizationEvent()
                    } else if (ordinary) {
                        fixture.ordinaryEvent()
                    } else if (multiple) {
                        val first = fixture.encrypt("first")
                        fixture.sql("UPDATE Conversation SET last_read_date = ${TIME.toEpochMilliseconds() - 1_000}")
                        val hash = checkNotNull(MessageContentEncoder().encodeMessageContent(TIME, MessageContent.Text("first"))).sha256Digest
                        val content = MessageContent.Text("quoted", quotedMessageReference = MessageContent.QuoteReference("first", hash, false))
                        fixture.batch("notification", fixture.event("first", first), fixture.event(
                            "quoted", fixture.encrypt("quoted", content), Instant.fromEpochMilliseconds(TIME.toEpochMilliseconds() - 2_000)
                        ))
                    } else fixture.event("notification", fixture.encrypt("first"))
                    val mixed = creation && isMLS && !readState
                    val extraProteus = if (conversationCleanup && !readState) mapOf(
                        "deliveryIds" to JsonArray(listOf("remove-asset", "prefix", "first").map { JsonPrimitive(it) }),
                    ) else if (identity) mapOf(
                        "deliveryIds" to JsonArray(listOf("prefix", "first").map { JsonPrimitive(it) }),
                    ) else if (decryption) mapOf(
                        "failureSender" to JsonPrimitive(SENDER.value),
                        "failureClient" to JsonPrimitive(SENDER_CLIENT),
                        "deliveryIds" to JsonArray(listOf(JsonPrimitive("first"))),
                    ) else if (previews) mapOf(
                        "assetText" to JsonPrimitive(ASSET_TEXT),
                        "encryptedAsset" to JsonPrimitive(Base64.encode(fixture.encryptedAsset)),
                        "deliveryIds" to JsonArray(listOf("first", "preview-quoted").map { JsonPrimitive(it) }),
                    ) else if (multipart) mapOf(
                        "deliveryIds" to JsonArray(listOf("first", "multipart", "multipart-empty").map { JsonPrimitive(it) }),
                    ) else if (assets) mapOf(
                        "deliveryIds" to JsonArray(ASSET_MESSAGE_IDS.map { JsonPrimitive(it) }),
                        "encryptedAsset" to JsonPrimitive(Base64.encode(fixture.encryptedAsset)),
                        "assetText" to JsonPrimitive(ASSET_TEXT),
                    ) else if (content) mapOf(
                        "deliveryIds" to JsonArray(listOf("composite", "knock", "unknown", "first", "location").map { JsonPrimitive(it) })
                    ) else if (mixed) {
                        fixture.seedProteusConversation()
                        event = fixture.batch("notification", fixture.event(
                            "proteus-first", fixture.encrypt("proteus-first", protocolMLS = false),
                            protocolMLS = false, conversationId = DELETE_TARGET
                        ), event)
                        mapOf(
                            "proteusNext" to JsonPrimitive(Base64.encode(fixture.encrypt("proteus-next", protocolMLS = false))),
                            "proteusAppNext" to JsonPrimitive(Base64.encode(fixture.encrypt("proteus-app-next", protocolMLS = false))),
                            "deliveryIds" to JsonArray(listOf("proteus-first", "first").map { JsonPrimitive(it) }),
                        )
                    } else if ((migration || oneOnOne) && isMLS) mapOf(
                        "proteusNext" to JsonPrimitive(Base64.encode(fixture.encrypt("proteus-next", protocolMLS = false))),
                        "proteusAppNext" to JsonPrimitive(Base64.encode(fixture.encrypt("proteus-app-next", protocolMLS = false))),
                        "proteusConversationId" to JsonPrimitive("conversation"),
                    ) else emptyMap()
                    val groupId = if (reset && isMLS) RESET_GROUP_ID else GROUP_ID
                    val next = bufferNext ?: fixture.encrypt("next", groupId = groupId)
                    val appNext = fixture.encrypt("app-next", groupId = groupId, protocolMLS = isMLS && !(mlsSettings && readState))
                    if (pendingProposal) fixture.observeRemovalProposal()
                    val transientBacklog = if (typing) {
                        val payload = event.toEventResponse().payload.orEmpty()
                        val captured = EventResponseToStore("captured", KtxSerializer.json.encodeToString(payload.dropLast(1)), true)
                        event = event.copy(payload = KtxSerializer.json.encodeToString(payload.takeLast(1)), transient = true)
                        prefix = listOf(fixture.typingEvent("started", SELF).copy(id = "rest-anchor"))
                        captured
                    } else null
                    fixture.releasePrepared()
                    if (transientBacklog != null) assertIs<Either.Right<*>>(fixture.open { runtime ->
                        runtime.database.eventDAO.insertEvents(listOf(com.wire.kalium.persistence.dao.event.NewEventEntity(
                            transientBacklog.id, transientBacklog.payload, true, true)))
                    })
                    val messageIds = when {
                        conversationCleanup && !readState -> listOf("prefix", "first")
                        memberRoles -> if (readState) listOf("first") else listOf("prefix", "first")
                        acceptedConnections && isMLS -> listOf("prefix", "first")
                        typing || accessChanges || roomSettings || remoteMute || x509 || mlsSettings || e2eiSettings || sessionRefresh || identity || meetings || federation || connections -> listOf("prefix", "first")
                        decryption -> listOf("notification", "first")
                        previews -> listOf("first", "preview-quoted")
                        multipart -> listOf("first", "multipart-edited", "multipart-empty")
                        assets -> ASSET_MESSAGE_IDS
                        content -> listOf("buttons-edited", "knock", "unknown", "first", "location")
                        oneOnOne && isMLS -> listOf("proteus-first", "first")
                        migration && isMLS -> listOf("proteus-first", "first", "final")
                        ordinary -> listOf("edited", "ephemeral", "first")
                        multiple -> listOf("first", "quoted")
                        else -> listOf("first")
                    }
                    manifests += JsonObject(fixture.manifest(event, next, appNext, messageIds, commit, ordinary, synchronization,
                        selfConversations || readState && !membership && !creation && !recovery && !reset && !migration && !mlsIgnores && !conversationCleanup && !identity && !federation && !meetingConversation && !acceptedConnections && !e2eiSettings && !mlsSettings && !x509 && !roomSettings && !accessChanges && !memberRoles,
                        path, membership, pendingProposal, creation, creation && isMLS && readState, prefix)
                        + extraProteus + mapOf("recoveryEvents" to JsonPrimitive(recovery && isMLS),
                            "resetEvents" to JsonPrimitive(reset && isMLS), "groupId" to JsonPrimitive(groupId),
                            "oneOnOneEvents" to JsonPrimitive(oneOnOne),
                            "contentEvents" to JsonPrimitive(content),
                            "signalEvents" to JsonPrimitive(signals),
                            "remoteMuteEvents" to JsonPrimitive(remoteMute),
                            "callingEvents" to JsonPrimitive(calling),
                            "proposalReturnedEvents" to KtxSerializer.json.encodeToJsonElement(
                                if (pendingProposal) fixture.memberLeave().toEventResponse().payload.orEmpty() else emptyList<EventContentDTO>()),
                            "typingEvents" to JsonPrimitive(typing),
                            "memberRoleEvents" to JsonPrimitive(memberRoles),
                            "memberRoleFetched" to JsonPrimitive(memberRoles && readState),
                            "accessChangesEvents" to JsonPrimitive(accessChanges),
                            "appsEnabled" to JsonPrimitive(!readState),
                            "roomSettingsEvents" to JsonPrimitive(roomSettings),
                            "roomLinkDeleted" to JsonPrimitive(roomSettings && readState),
                            "assetEvents" to JsonPrimitive(assets),
                            "cleanupEvents" to JsonPrimitive(cleanup),
                            "meetingConversationEvents" to JsonPrimitive(meetingConversation),
                            "meetingConversationWelcomeFirst" to JsonPrimitive(meetingConversation && readState),
                            "connectionEvents" to JsonPrimitive(connections),
                            "sessionRefreshEvents" to JsonPrimitive(sessionRefresh),
                            "e2eiSettingsEvents" to JsonPrimitive(e2eiSettings),
                            "mlsSettingsEvents" to JsonPrimitive(mlsSettings),
                            "mlsClientEvents" to JsonPrimitive(mlsClients),
                            "mlsProtocolEvents" to JsonPrimitive(mlsProtocols),
                            "mlsMigrationEvents" to JsonPrimitive(mlsMigration),
                            "mlsCipherEvents" to JsonPrimitive(mlsCipher),
                            "x509Events" to JsonPrimitive(x509 && isMLS),
                            "x509PeerRevoked" to JsonPrimitive(x509 && isMLS && readState),
                            "x509SelfRevoked" to JsonPrimitive(x509 && isMLS && readState),
                            "selfClients" to KtxSerializer.json.encodeToJsonElement(fixture.selfClients()),
                            "mlsConfiguredEnabled" to JsonPrimitive(!readState),
                            "e2eiRequired" to JsonPrimitive(!readState),
                            "selfConversationEvents" to JsonPrimitive(selfConversations),
                            "selfConversationWelcomeFirst" to JsonPrimitive(selfConversations && readState),
                            "sentConnectionEvents" to JsonPrimitive(sentConnections),
                            "acceptedConnectionEvents" to JsonPrimitive(acceptedConnections),
                            "acceptedConnectionConsent" to JsonPrimitive(acceptedConnections && readState),
                            "federationEvents" to JsonPrimitive(federation),
                            "federationDeleted" to JsonPrimitive(federation && !readState),
                            "meetingEvents" to JsonPrimitive(meetings),
                            "meetingMetadata" to KtxSerializer.json.encodeToJsonElement(
                                fixture.meetingDTO(com.wire.kalium.network.api.model.MeetingId("meeting", SELF.domain))),
                            "identityEvents" to JsonPrimitive(identity),
                            "identityOwn" to JsonPrimitive(identity && readState),
                            "conversationCleanup" to JsonPrimitive(if (conversationCleanup) if (readState) "delete" else "clear" else ""),
                            "multipartEvents" to JsonPrimitive(multipart),
                            "previewEvents" to JsonPrimitive(previews),
                            "decryptionEvents" to JsonPrimitive(decryption),
                            "mlsIgnoreEvents" to JsonPrimitive(mlsIgnores && isMLS),
                            "bufferEvents" to JsonPrimitive(buffering && isMLS),
                            "appBufferResponse" to KtxSerializer.json.encodeToJsonElement(appBufferResponse),
                            "conversationMetadata" to KtxSerializer.json.encodeToJsonElement(
                                if (selfConversations) fixture.selfConversationResponse()
                                else if (sentConnections) fixture.sentConnectionResponse()
                                else if (meetingConversation) fixture.meetingConversationResponse()
                                else if (oneOnOne) fixture.creationResponse().copy(type = ConversationResponse.Type.ONE_TO_ONE)
                                else fixture.creationResponse()),
                            "migrationEvents" to JsonPrimitive(migration && isMLS),
                            "expectedText" to JsonPrimitive(if (previews) "preview-quoted" else if (migration && isMLS) "final" else "first")))
                } finally {
                    fixture.close(deleteStorage = false)
                }
            }
            val path = "${NSTemporaryDirectory()}kalium-nse-swift-fixture.json"
            FileSystem.SYSTEM.write(path.toPath()) { writeUtf8(JsonArray(manifests).toString()) }
            println("NSE_SWIFT_MANIFEST=$path")
        }
    }

    fun exportAfterAppProposal() = runTest {
        withContext(Dispatchers.Default) {
            val path = "${NSTemporaryDirectory()}kalium-nse-swift-fixture.json"
            val accounts = KtxSerializer.json.parseToJsonElement(FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }).jsonArray
            for (account in accounts.map { it.jsonObject }.filter { it["pendingProposal"]?.jsonPrimitive?.boolean == true }) {
                val root = account.getValue("root").jsonPrimitive.content
                val fixture = Fixture(true, root = root, keychainService = account.getValue("service").jsonPrimitive.content)
                val sender = coreCryptoCentral("$root/sender-mls", KEY)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                try {
                    val client = sender.mlsClient(
                        CryptoQualifiedClientId(SENDER_CLIENT, SENDER.toCrypto()),
                        CIPHER.toCrypto(),
                        CaptureTransport(),
                        object : MLSEpochObserver { override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) = Unit },
                        scope
                    )
                    val commit = FileSystem.SYSTEM.read("$root/app-proposal-commit".toPath()) { readByteArray() }
                    val next = client.transaction {
                        assertIs<MLSDecryptResult.Success>(it.decryptMessage(GROUP_ID, commit))
                        val plain = ProtoContentMapperImpl(selfUserId = SENDER).encodeToProtobuf(
                            ProtoContent.Readable(
                                "after-app-proposal",
                                MessageContent.Text("after-app-proposal"),
                                false,
                                Conversation.LegalHoldStatus.DISABLED,
                                null
                            )
                        ).data
                        it.encryptMessage(GROUP_ID, plain)
                    }
                    FileSystem.SYSTEM.write("$root/after-app-proposal".toPath()) { write(next) }
                } finally {
                    scope.coroutineContext.job.cancelAndJoin()
                    sender.close()
                    fixture.close(deleteStorage = false)
                }
            }
        }
    }

    fun verifyAccountsReceivedFromSwift() = runTest {
        withContext(Dispatchers.Default) {
            val path = "${NSTemporaryDirectory()}kalium-nse-swift-fixture.json"
            val accounts = KtxSerializer.json.parseToJsonElement(FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }).jsonArray
            for (account in accounts.map { it.jsonObject }) {
                val fixture = Fixture(
                    account.getValue("mls").jsonPrimitive.boolean,
                    senderId = UserId(account.getValue("senderValue").jsonPrimitive.content, SELF.domain),
                    root = account.getValue("root").jsonPrimitive.content,
                    keychainService = account.getValue("service").jsonPrimitive.content,
                    assetData = account["encryptedAsset"]?.jsonPrimitive?.content?.let { Base64.decode(it) },
                )
                try {
                    if (account["creationEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(fixture.open {
                            fixture.verifyCreation(it, expectMessages = !account.getValue("fetchedCreation").jsonPrimitive.boolean)
                        })
                    }
                    if (account["assetEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(fixture.open { fixture.verifyAssets(it) })
                    }
                    if (account["previewEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(fixture.open { fixture.verifyPreviewMetadata(it,
                            downloaded = account["linkPreviewEnabled"]?.jsonPrimitive?.boolean == true) })
                    }
                    if (account["multipartEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(fixture.open { fixture.verifyMultipart(it, edited = true) })
                    }
                    if (account["sessionRefreshEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyRefreshedSession(it, 2)
                    })
                    if (account["x509Events"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyX509(it, account.getValue("x509PeerRevoked").jsonPrimitive.boolean)
                    })
                    if (account["mlsMigrationEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyMLSMigration(it)
                    })
                    if (account["mlsProtocolEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyMLSProtocolResolution(it, account.getValue("mlsConfiguredEnabled").jsonPrimitive.boolean)
                    })
                    if (account["mlsClientEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyMLSClients(it, account.getValue("mlsConfiguredEnabled").jsonPrimitive.boolean)
                    })
                    if (account["mlsSettingsEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        val enabled = account.getValue("mlsConfiguredEnabled").jsonPrimitive.boolean
                        if (account["mlsCipherEvents"]?.jsonPrimitive?.boolean == true) fixture.verifyMLSCipher(it, enabled)
                        else fixture.verifyMLSSettings(it, enabled)
                    })
                    if (account["e2eiSettingsEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyE2EISettings(it, account.getValue("e2eiRequired").jsonPrimitive.boolean)
                    })
                    if (account["selfConversationEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifySelfConversation(it)
                    })
                    if (account["sentConnectionEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifySentConnection(it); fixture.verifyPendingConnectionCreation(it)
                    })
                    if (account["acceptedConnectionEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyAcceptedConnection(it, account.getValue("acceptedConnectionConsent").jsonPrimitive.boolean)
                    })
                    if (account["connectionEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyConnectionState(it)
                    })
                    if (account["meetingConversationEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyCreation(it, expectedType = ConversationEntity.Type.MEETING)
                        fixture.verifyMeetingState(it)
                    })
                    if (account["federationEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyFederationState(it, account.getValue("federationDeleted").jsonPrimitive.boolean)
                    })
                    if (account["meetingEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyMeetingState(it)
                    })
                    if (account["identityEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyIdentityState(it, own = account["identityOwn"]?.jsonPrimitive?.boolean == true)
                    })
                    val conversationCleanup = account["conversationCleanup"]?.jsonPrimitive?.content.orEmpty()
                    if (conversationCleanup.isNotEmpty()) assertIs<Either.Right<*>>(fixture.open {
                        if (conversationCleanup == "delete") fixture.verifyConversationCleanup(it, DELETE_TARGET, deleted = true)
                        else fixture.verifyHistoricalCutoffCleanup(it, CONVERSATION_ID)
                    })
                    if (account["cleanupEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(fixture.open { fixture.verifyMessageFiles(it) })
                    }
                    if (account["typingEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open { runtime ->
                        assertTrue(runtime.database.userPrefsDAO.isTypingIndicatorEnabled().first())
                        assertNull(runtime.database.conversationDAO.getConversationById(DELETE_TARGET))
                        assertEquals("rest-anchor", runtime.database.notificationEventStore.cursor())
                        for (id in listOf("captured", "notification")) {
                            val stored = checkNotNull(runtime.database.eventDAO.getEventById(id))
                            assertTrue(stored.transient)
                            assertTrue(stored.isProcessed)
                            assertFalse(stored.isLive)
                        }
                    })
                    if (account["memberRoleEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open { runtime ->
                        if (account["memberRoleFetched"]?.jsonPrimitive?.boolean == true) {
                            fixture.verifyPromotion(runtime, "notification", SENDER)
                            assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member,
                                runtime.database.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
                        } else fixture.verifyMemberRoles(runtime)
                    })
                    if (account["accessChangesEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyAccess(it, account["appsEnabled"]?.jsonPrimitive?.boolean == true)
                    })
                    if (account["roomSettingsEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyRoomSettings(it, if (account["roomLinkDeleted"]?.jsonPrimitive?.boolean == true) "delete" else "fallback")
                    })
                    if (account["remoteMuteEvents"]?.jsonPrimitive?.boolean == true) assertIs<Either.Right<*>>(fixture.open {
                        fixture.verifyIgnoredRemoteMute(it)
                    })
                    if (account["signalEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(fixture.open { fixture.verifySignals(it) })
                    }
                    fixture.verifyStoredAndNext(Base64.decode(account.getValue("next").jsonPrimitive.content),
                        account["expectedText"]?.jsonPrimitive?.content ?: "first",
                        account.getValue("deliveryIds").jsonArray.map { it.jsonPrimitive.content },
                        groupId = account["groupId"]?.jsonPrimitive?.content ?: GROUP_ID)
                    if (account["resetEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            val protocol = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                            assertEquals(RESET_GROUP_ID, (protocol as ConversationEntity.ProtocolInfo.MLS).groupId)
                            assertEquals(1uL, protocol.epoch)
                            assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                            assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                            assertFalse(checkNotNull(fixture.openMLS(runtime)).transaction { it.conversationExists(GROUP_ID) })
                        })
                    }
                    if (account["recoveryEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            val protocol = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID)).protocolInfo
                            assertEquals(ConversationEntity.GroupState.ESTABLISHED,
                                (protocol as ConversationEntity.ProtocolInfo.MLS).groupState)
                            assertEquals(3uL, protocol.epoch)
                            assertNotNull(runtime.database.memberDAO.getMemberRole(SELF.toDao(), CONVERSATION_ID))
                        })
                    }
                    account["proteusNext"]?.let { next ->
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            assertNotNull(runtime.database.messageDAO.getMessageById("proteus-first",
                                account["proteusConversationId"]?.let { QualifiedIDEntity(it.jsonPrimitive.content, SELF.domain) } ?: DELETE_TARGET))
                        })
                        assertIs<Either.Right<*>>(fixture.decryptAndCheck(
                            Base64.decode(next.jsonPrimitive.content), "proteus-next", protocolMLS = false
                        ))
                    }
                    if (account.getValue("synchronization").jsonPrimitive.boolean) {
                        assertIs<Either.Right<*>>(
                            fixture.open { runtime ->
                            if (account.getValue("readState").jsonPrimitive.boolean) fixture.verifyReadState(runtime)
                            else fixture.verifySynchronization(runtime)
                        }
                        )
                    }
                    if (account["membershipEvents"]?.jsonPrimitive?.boolean == true) {
                        assertIs<Either.Right<*>>(
                            fixture.open { runtime ->
                            assertEquals("Third user", runtime.database.userDAO.getUserDetailsByQualifiedID(THIRD.toDao())?.name)
                            assertEquals(
                                account.getValue("pendingProposal").jsonPrimitive.boolean,
                                runtime.database.memberDAO.getMemberRole(THIRD.toDao(), CONVERSATION_ID) != null
                            )
                        }
                        )
                    }
                    if (account.getValue("ordinaryEvents").jsonPrimitive.boolean) {
                        assertIs<Either.Right<*>>(fixture.open { runtime ->
                            val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
                            assertEquals("Renamed", conversation.name)
                            assertEquals(ConversationEntity.ReceiptMode.ENABLED, conversation.receiptMode)
                            assertEquals(45_000, conversation.messageTimer)
                            assertNull(runtime.database.messageDAO.getMessageById("original", CONVERSATION_ID))
                            assertEquals(MessageEntity.Visibility.DELETED,
                                checkNotNull(runtime.database.messageDAO.getMessageById("delete-me", CONVERSATION_ID)).visibility)
                            assertEquals(setOf("❤️"), runtime.database.reactionDAO.getReaction("edited", CONVERSATION_ID, SENDER.toDao()))
                            val ephemeral = checkNotNull(runtime.database.messageDAO.getMessageById("ephemeral", CONVERSATION_ID))
                            assertEquals(60_000L, ephemeral.expireAfterMs)
                            assertNull(ephemeral.selfDeletionEndDate)
                            assertEquals(MessageEntity.Visibility.VISIBLE, ephemeral.visibility)
                        })
                    }
                } finally {
                    fixture.close(deleteStorage = false)
                }
            }
        }
    }

    @Test
    fun invalidApplicationContentIsConsumedForMLSAndProteus() = runTest {
        withContext(Dispatchers.Default) {
            for (mls in listOf(true, false)) withFixture(mls) { fixture ->
                val invalid = listOf(
                    byteArrayOf(0x80.toByte()),
                    GenericMessage(messageId = "bad-history", content = GenericMessage.Content.HistoryClientAvailable(
                        com.wire.kalium.protobuf.messages.HistoryClientAvailable(com.wire.kalium.protobuf.messages.HistoryClient(
                            clientId = "client", createdAt = "not-an-instant", secret = pbandk.ByteArr(byteArrayOf(1, 2, 3)),
                        ))
                    )).encodeToByteArray(),
                    GenericMessage(messageId = "bad-calling", content = GenericMessage.Content.Calling(
                        com.wire.kalium.protobuf.messages.Calling(content = "{")
                    )).encodeToByteArray(),
                )
                val events = invalid.flatMapIndexed { index, bytes ->
                    listOf(
                        fixture.event("invalid-$index", fixture.encryptBytes(bytes)),
                        fixture.event("valid-$index", fixture.encrypt("valid-$index")),
                    )
                }
                val target = events.last().id
                val next = fixture.encrypt("next")
                fixture.releasePrepared()

                assertIs<Either.Right<*>>(fixture.receive(events, target))
                assertIs<Either.Right<*>>(fixture.open { runtime ->
                    for (index in invalid.indices) {
                        assertNotNull(runtime.database.messageDAO.getMessageById("valid-$index", CONVERSATION_ID))
                    }
                    events.forEach { assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(it.id)).isProcessed) }
                    assertNull(runtime.database.notificationEventStore.nextPending())
                })
                assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(fixture.receive(emptyList(), target)).value)
                assertIs<Either.Right<*>>(fixture.decryptAndCheck(next, "next"))
            }
        }
    }

    private suspend fun withFixture(mls: Boolean, senderId: UserId = SENDER, newConversation: Boolean = false, x509: Boolean = false, basicSelf: Boolean = false, deleteStorage: Boolean = true, block: suspend (Fixture) -> Unit) {
        val fixture = Fixture(mls, senderId = senderId, newConversation = newConversation, x509 = x509, basicSelf = basicSelf)
        try {
            fixture.prepare()
            block(fixture)
        } finally {
            fixture.close(deleteStorage)
        }
    }

    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    private class Fixture(
        private val mlsEnabled: Boolean,
        private val apiEndpoint: String? = null,
        private val senderId: UserId = SENDER,
        private val newConversation: Boolean = false,
        val root: String = "${NSTemporaryDirectory()}receiving-text-${NSUUID.UUID().UUIDString}",
        keychainService: String = "com.wire.kalium.test.${NSUUID.UUID().UUIDString}",
        assetData: ByteArray? = null,
        private val apiVersion: Int = 8,
        private val x509: Boolean = false,
        private val basicSelf: Boolean = false,
    ) {
        var receivedCalls: List<ReceivedCallResult> = emptyList()
        var receivedAppWork: NotificationAppWork? = null
        var receivedStop: String? = null
        var receivedCallEvents: List<ReceivedCallEvent> = emptyList()
        private val config = ApplePersistenceConfig(keychainService, keychainAccessibility = null)
        val databaseSecret get() = checkNotNull(GlobalPrefProvider(config).passphraseStorage.existingUserDatabaseSecret(SELF.toDao()))
        private val account = appleAccountPath(root, SELF)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var lease: AccountRuntimeLease? = null
        private lateinit var receiver: CoreCryptoCentral
        private lateinit var sender: CoreCryptoCentral
        private var receiverMLSCentral: CoreCryptoCentral? = null
        private var senderMLSCentral: CoreCryptoCentral? = null
        private lateinit var receiverProteus: ProteusClient
        private lateinit var senderProteus: ProteusClient
        var receiverMLS: MLSClient? = null
        var senderMLS: MLSClient? = null
        val transport = CaptureTransport()
        private var x509Preparation: X509Preparation? = null
        private var receiverPrekey: PreKeyCrypto? = null
        private var firstProteus = true
        private var welcome: ByteArray? = null

        suspend fun prepare() {
            FileSystem.SYSTEM.createDirectories(account.toPath())
            lease = assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(SELF)).value
            val preferences = GlobalPrefProvider(config)
            preferences.authTokenStorage.addOrReplace(AuthTokenEntity(SELF.toDao(), "access", "refresh", "Bearer", "cookie"), null)
            preferences.passphraseStorage.setPassphrase("${PROTEUS_DB_PASSPHRASE_PREFIX_V2}_$SELF", Base64.encode(KEY))
            val databaseKey = if (kotlin.native.Platform.osFamily == kotlin.native.OsFamily.IOS) KEY + ByteArray(16) else KEY
            preferences.passphraseStorage.setPassphrase("${USER_DB_PASSPHRASE_PREFIX_V2}_$SELF", Base64.encode(databaseKey))
            receiver = coreCryptoCentral("$account/proteus", KEY)
            sender = coreCryptoCentral("$root/sender-proteus", KEY)
            receiverProteus = receiver.proteusClient()
            senderProteus = sender.proteusClient()
            receiverPrekey = receiverProteus.newPreKeysAuto(1).single()
            var protocol: ConversationEntity.ProtocolInfo = ConversationEntity.ProtocolInfo.Proteus
            if (mlsEnabled) {
                preferences.passphraseStorage.setPassphrase("${MLS_DB_PASSPHRASE_PREFIX_V2}_$SELF", Base64.encode(KEY))
                receiverMLSCentral = coreCryptoCentral("$account/mls/$CLIENT/keystore", KEY)
                senderMLSCentral = coreCryptoCentral("$root/sender-mls", KEY)
                receiverMLS = mlsClient(checkNotNull(receiverMLSCentral), SELF, CLIENT, scope)
                senderMLS = mlsClient(checkNotNull(senderMLSCentral), senderId, SENDER_CLIENT, scope)
                val receiving = checkNotNull(receiverMLS)
                val sending = checkNotNull(senderMLS)
                if (x509) {
                    val preparation = X509Preparation().also { x509Preparation = it }
                    preparation.control("reset=1")
                    if (basicSelf) {
                        preparation.configure(checkNotNull(receiverMLSCentral))
                        receiving.initializeBasicCredential()
                    } else preparation.install(checkNotNull(receiverMLSCentral), SELF, CLIENT)
                    preparation.install(checkNotNull(senderMLSCentral), senderId, SENDER_CLIENT)
                    checkNotNull(receiverMLSCentral).checkCredentials()
                } else {
                    receiving.initializeBasicCredential()
                    sending.initializeBasicCredential()
                }
                val keyPackage = receiving.transaction { it.generateKeyPackages(1).single() }
                sending.transaction {
                    it.createConversation(GROUP_ID, Base64.decode(PROPOSAL_PUBLIC_KEY))
                    it.addMember(GROUP_ID, listOf(keyPackage))
                }
                welcome = checkNotNull(transport.bundle?.welcome)
                if (!newConversation) receiving.transaction { it.processWelcomeMessage(checkNotNull(welcome)) }
                protocol = ConversationEntity.ProtocolInfo.MLS(
                    GROUP_ID, ConversationEntity.GroupState.ESTABLISHED, sending.transaction { it.conversationEpoch(GROUP_ID) },
                    TIME, ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
                )
            }
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default
            )
            try {
                database.userPrefsDAO.persistReadReceipts(false)
                if (x509) database.userPrefsDAO.setE2EISettings(com.wire.kalium.persistence.config.E2EISettingsEntity(
                    status = !basicSelf, discoverUrl = "${X509Preparation.BASE}/directory", gracePeriodEndMs = null,
                    shouldUseProxy = false, crlProxy = null))
                if (x509) database.userPrefsDAO.setIfAbsentE2EINotificationTime(TIME.toEpochMilliseconds())
                database.userDAO.upsertUser(user(SELF))
                database.userDAO.upsertUser(user(SENDER))
                if (!newConversation) {
                database.conversationDAO.insertConversation(conversation(protocol))
                database.memberDAO.insertMembersWithQualifiedId(
                    listOf(SELF, SENDER).distinct().map {
                        com.wire.kalium.persistence.dao.member.MemberEntity(
                        it.toDao(),
                        com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member
                    )
                    },
                    CONVERSATION_ID
                )
                }

                val registration = ClientRegistrationStorageImpl(database.metadataDAO)
                registration.setRegisteredClientId(CLIENT)
                if (mlsEnabled) {
                    registration.setHasRegisteredMLSClient()
                    database.userPrefsDAO.enableMLS(true)
                    database.userConfigDAO.setDefaultCipherSuite(SupportedCipherSuiteEntity(listOf(CIPHER.tag), CIPHER.tag))
                }
            } finally {
                database.close()
            }
            if (x509) {
                sql("UPDATE User SET name = 'NSE $CLIENT', handle = 'nse$CLIENT' WHERE qualified_id = '${SELF.toDao()}'")
                sql("UPDATE User SET name = 'NSE $SENDER_CLIENT', handle = 'nse$SENDER_CLIENT' WHERE qualified_id = '${senderId.toDao()}'")
                sql("INSERT INTO Client(user_id, id, is_mls_capable) VALUES ('${SELF.toDao()}', '$CLIENT', 1), ('${senderId.toDao()}', '$SENDER_CLIENT', 1)")
                if (!basicSelf) sql("UPDATE Conversation SET verification_status = 'VERIFIED'")
            }
            sql("UPDATE Conversation SET proteus_verification_status = 'NOT_VERIFIED'")
        }

        fun pendingConnectionCreation(): EventResponseToStore {
            val response = creationResponse().copy(id = DELETE_TARGET.toApi(), creator = SELF.value,
                type = ConversationResponse.Type.WAIT_FOR_CONNECTION, name = "Pending connection",
                protocol = ConvProtocol.PROTEUS, groupId = null, epoch = null, mlsCipherSuiteTag = null,
                members = ConversationMembersResponse(ConversationMemberDTO.Self(SELF.toApi(), "wire_admin"), emptyList()),
                receiptMode = ReceiptMode.DISABLED, cellsState = null, accessRole = emptySet())
            return EventResponseToStore("pending-creation", KtxSerializer.json.encodeToString(listOf<EventContentDTO>(
                EventContentDTO.Conversation.NewConversationDTO(response.id, SELF.toApi(), TIME, response))), false)
        }

        suspend fun verifyPendingConnectionCreation(runtime: AppleAccountRuntime, name: String = "Pending connection",
            role: com.wire.kalium.persistence.dao.member.MemberEntity.Role = com.wire.kalium.persistence.dao.member.MemberEntity.Role.Admin) {
            val db = runtime.database
            val conversation = checkNotNull(db.conversationDAO.getConversationById(DELETE_TARGET))
            assertEquals(ConversationEntity.Type.CONNECTION_PENDING, conversation.type)
            assertEquals(name, conversation.name)
            assertIs<ConversationEntity.ProtocolInfo.Proteus>(conversation.protocolInfo)
            assertEquals(listOf(SELF.toDao()), db.memberDAO.getConversationMembers(DELETE_TARGET))
            assertEquals(role, db.memberDAO.getMemberRole(SELF.toDao(), DELETE_TARGET))
            assertNull(db.connectionDAO.getConnection(DELETE_TARGET))
            assertNull(db.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.activeOneOnOneConversationId)
            val message = db.messageDAO.getMessagesByConversationAndVisibility(DELETE_TARGET, 100, 0).first().single()
            assertEquals(MessageEntityContent.ConversationStartedUnverifiedWarning, message.content)
            assertEquals(SELF.toDao(), message.senderUserId)
            assertEquals(TIME, message.date)
        }

        fun creationResponse(): ConversationResponse = ConversationResponse(
            creator = SENDER.value,
            members = ConversationMembersResponse(
                ConversationMemberDTO.Self(SELF.toApi(), "wire_member"),
                listOf(ConversationMemberDTO.Other(SENDER.toApi(), "wire_admin")),
            ),
            name = "Created group",
            id = CONVERSATION_ID.toApi(),
            groupId = if (mlsEnabled) GROUP_ID else null,
            epoch = if (mlsEnabled) 1uL else null,
            type = ConversationResponse.Type.GROUP,
            messageTimer = null,
            teamId = null,
            protocol = if (mlsEnabled) ConvProtocol.MLS else ConvProtocol.PROTEUS,
            lastEventTime = TIME.toString(),
            mlsCipherSuiteTag = if (mlsEnabled) CIPHER.tag else null,
            access = setOf(com.wire.kalium.network.api.model.ConversationAccessDTO.INVITE),
            accessRole = setOf(com.wire.kalium.network.api.model.ConversationAccessRoleDTO.SERVICE),
            receiptMode = ReceiptMode.ENABLED,
            cellsState = "ready",
        )

        fun lifecyclePeer(id: com.wire.kalium.network.api.model.UserId) = connectionProfile(id).copy(
            supportedProtocols = if (mlsEnabled) com.wire.kalium.network.api.model.SupportedProtocolDTO.entries
                else listOf(com.wire.kalium.network.api.model.SupportedProtocolDTO.PROTEUS))

        fun sentConnectionResponse() = creationResponse().copy(members = ConversationMembersResponse(
            self = null, otherMembers = listOf(ConversationMemberDTO.Other(SENDER.toApi(), "wire_admin"),
                ConversationMemberDTO.Other(UserId("sent-incomplete", SELF.domain).toApi(), "wire_member"))))

        suspend fun sentConnectionEvents() = batch("notification", connectionEvent("sent", CONVERSATION_ID.value, SENDER),
            event("first", encrypt("first")))

        suspend fun verifySentConnection(runtime: AppleAccountRuntime, established: Boolean = true,
            expectedPeerRole: com.wire.kalium.persistence.dao.member.MemberEntity.Role = com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member) {
            val db = runtime.database
            val conversation = checkNotNull(db.conversationDAO.getConversationById(CONVERSATION_ID))
            assertEquals(ConversationEntity.Type.CONNECTION_PENDING, conversation.type)
            assertEquals("Created group", conversation.name)
            assertEquals(ConversationEntity.ReceiptMode.ENABLED, conversation.receiptMode)
            // App insertion retains existing roles; newly inserted members use the fetched role.
            assertEquals(expectedPeerRole, db.memberDAO.getMemberRole(SENDER.toDao(), CONVERSATION_ID))
            assertTrue(checkNotNull(db.userDAO.getUserDetailsByQualifiedID(UserId("sent-incomplete", SELF.domain).toDao())).hasIncompleteMetadata)
            assertEquals(ConnectionEntity.State.SENT, db.connectionDAO.getConnection(CONVERSATION_ID)?.status)
            assertEquals(ConnectionEntity.State.SENT, db.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.connectionStatus)
            if (mlsEnabled) {
                val protocol = assertIs<ConversationEntity.ProtocolInfo.MLS>(conversation.protocolInfo)
                assertEquals(if (established) ConversationEntity.GroupState.ESTABLISHED else ConversationEntity.GroupState.PENDING_JOIN, protocol.groupState)
                assertEquals(GROUP_ID, protocol.groupId)
            } else assertIs<ConversationEntity.ProtocolInfo.Proteus>(conversation.protocolInfo)
        }

        suspend fun seedAcceptedConnection(consent: Boolean) {
            val id = if (mlsEnabled) DELETE_TARGET else CONVERSATION_ID
            if (mlsEnabled) {
                seedProteusConversation()
                seedProteusConversation(QualifiedIDEntity("legacy-second", SELF.domain))
                seed(storedText("history").copy(conversationId = DELETE_TARGET),
                    storedText("history-second").copy(conversationId = QualifiedIDEntity("legacy-second", SELF.domain)))
            }
            sql("UPDATE Conversation SET type = 'CONNECTION_PENDING' WHERE qualified_id = '$id'")
            sql("DELETE FROM Member WHERE conversation = '$id' AND user = '${SELF.toDao()}'")
            sql("INSERT INTO Connection(from_id, conversation_id, qualified_conversation, to_id, last_update_date, qualified_to, status) " +
                "VALUES('${SELF.value}', '${id.value}', '$id', '${SENDER.value}', ${TIME.toEpochMilliseconds()}, '${SENDER.toDao()}', " +
                "'${if (consent) "MISSING_LEGALHOLD_CONSENT" else "PENDING"}')")
            if (!mlsEnabled) sql("UPDATE User SET supported_protocols = 'PROTEUS' WHERE qualified_id = '${SENDER.toDao()}'")
        }

        suspend fun acceptedConnectionEvents(): EventResponseToStore {
            val prefix = if (mlsEnabled) listOf(event("prefix", encrypt("prefix", protocolMLS = false),
                protocolMLS = false, conversationId = DELETE_TARGET)) else emptyList()
            return batch("notification", *(prefix + connectionEvent("accepted", if (mlsEnabled) DELETE_TARGET.value else CONVERSATION_ID.value, SENDER) +
                event("first", encrypt("first"))).toTypedArray())
        }

        suspend fun verifyAcceptedConnection(runtime: AppleAccountRuntime, consent: Boolean,
            expectedState: ConnectionEntity.State = if (consent) ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT else ConnectionEntity.State.PENDING,
            expectedUserState: ConnectionEntity.State = if (expectedState == ConnectionEntity.State.IGNORED) expectedState else ConnectionEntity.State.ACCEPTED) {
            val db = runtime.database
            val source = if (mlsEnabled) DELETE_TARGET else CONVERSATION_ID
            assertEquals(ConversationEntity.Type.ONE_ON_ONE, db.conversationDAO.getConversationById(source)?.type)
            assertNotNull(db.memberDAO.getMemberRole(SENDER.toDao(), source))
            assertNull(db.memberDAO.getMemberRole(SELF.toDao(), source))
            assertEquals(expectedState, db.connectionDAO.getConnection(source)?.status)
            assertEquals(expectedUserState,
                db.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.connectionStatus)
            assertEquals(CONVERSATION_ID, db.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.activeOneOnOneConversationId)
            val messages = db.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
            assertEquals((if (consent) 0 else 1) + (if (mlsEnabled) 1 else 0), messages.count {
                it.content == MessageEntityContent.ConversationStartedUnverifiedWarning })
            if (mlsEnabled) {
                for (id in listOf("history", "history-second")) assertNotNull(db.messageDAO.getMessageById(id, CONVERSATION_ID))
                assertTrue(db.messageDAO.getMessagesByConversationAndVisibility(source, 100, 0).first().isEmpty())
                assertEquals(1, messages.count { it.content is MessageEntityContent.ConversationProtocolChanged })
            }
        }

        fun connectionEvent(status: String, id: String = "request", peer: UserId = UserId("peer-$id", SELF.domain)) = plainEvent("connection-$status-$id", """
            {"type":"user.connection","connection":{"conversation":"$id","from":"${SELF.value}","last_update":"$TIME",
            "qualified_conversation":{"id":"$id","domain":"${SELF.domain}"},
            "qualified_to":{"id":"${peer.value}","domain":"${peer.domain}"},"status":"$status","to":"${peer.value}"}}
        """)

        fun connectionProfile(id: com.wire.kalium.network.api.model.UserId) =
            KtxSerializer.json.decodeFromString<com.wire.kalium.network.api.model.UserProfileDTO>("""
            {"qualified_id":{"id":"${id.value}","domain":"${id.domain}"},"id":"${id.value}",
            "name":"Peer ${id.value.removePrefix("peer-")}","handle":"peer","team":null,"accent_id":0,"assets":[],
            "deleted":false,"email":null,"expires_at":null,"service":null,"supported_protocols":["proteus","mls"],
            "legalhold_status":"disabled","type":"regular","app":null}
            """)

        suspend fun connectionEvents() = batch("notification", event("prefix", encrypt("prefix")),
            connectionEvent("pending"), connectionEvent("blocked", "blocked"), connectionEvent("ignored", "ignored"),
            connectionEvent("missing-legalhold-consent", "consent"), connectionEvent("pending", DELETE_TARGET.value),
            connectionEvent("cancelled", DELETE_TARGET.value), event("first", encrypt("first")), connectionEvent("pending"))

        suspend fun verifyConnectionState(runtime: AppleAccountRuntime) {
            val db = runtime.database
            for ((id, status) in mapOf("request" to ConnectionEntity.State.PENDING, "blocked" to ConnectionEntity.State.BLOCKED,
                "ignored" to ConnectionEntity.State.IGNORED, "consent" to ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT)) {
                val connection = checkNotNull(db.connectionDAO.getConnection(QualifiedIDEntity(id, SELF.domain)))
                assertEquals(status, connection.status)
                assertEquals(UserId("peer-$id", SELF.domain).toDao(), connection.qualifiedToId)
                val peer = checkNotNull(db.userDAO.getUserDetailsByQualifiedID(connection.qualifiedToId))
                assertEquals("Peer $id", peer.name)
                assertEquals(status, peer.connectionStatus)
                val conversation = db.conversationDAO.getConversationById(connection.qualifiedConversationId)
                if (id == "request") {
                    assertEquals(ConversationEntity.Type.CONNECTION_PENDING, conversation?.type)
                    assertIs<ConversationEntity.ProtocolInfo.Proteus>(conversation?.protocolInfo)
                    assertTrue(db.memberDAO.getConversationMembers(connection.qualifiedConversationId).isEmpty())
                    assertEquals(TIME, conversation?.lastReadDate)
                } else assertNull(conversation)
            }
            assertEquals(listOf("request"), db.connectionDAO.getConnectionRequestsForNotification().first().map { it.conversationId })
            assertNull(db.conversationDAO.getConversationById(DELETE_TARGET))
            assertNull(db.connectionDAO.getConnection(DELETE_TARGET))
            assertEquals(ConnectionEntity.State.CANCELLED,
                db.userDAO.getUserDetailsByQualifiedID(UserId("peer-${DELETE_TARGET.value}", SELF.domain).toDao())?.connectionStatus)
            assertNull(db.messageDAO.getMessageById("remove-asset", DELETE_TARGET))
            assertFalse(FileSystem.SYSTEM.exists(messageFile("delete-asset").toPath()))
            assertTrue(FileSystem.SYSTEM.exists(messageFile("keep-shared").toPath()))
        }

        suspend fun sessionRefreshEvents() = batch("notification", event("prefix", encrypt("prefix")),
            plainEvent("refresh-one", """{"type":"user.session-refresh-suggested"}"""),
            connectionEvent("ignored", "refresh-one"),
            plainEvent("refresh-two", """{"type":"user.session-refresh-suggested"}"""),
            connectionEvent("ignored", "refresh-two"), event("first", encrypt("first")))

        fun refreshResponse(index: Int) = NetworkResponse.Success(
            AccessTokenDTO(SELF.value, "access-$index", 3600, "Bearer") to RefreshTokenDTO("refresh-$index"), emptyMap(), 200)

        fun refreshHandler(runtime: AppleAccountRuntime, failWrite: () -> Boolean = { false },
            issue: suspend () -> NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>>): SessionRefreshSuggestedEventHandler {
            val tokens = GlobalPrefProvider(config).authTokenStorage
            val storage = object : AuthTokenStorage by tokens {
                override fun updateToken(userId: com.wire.kalium.persistence.dao.UserIDEntity, accessToken: String,
                    tokenType: String, refreshToken: String?): AuthTokenEntity {
                    check(!failWrite()) { "Token persistence failed" }
                    return tokens.updateToken(userId, accessToken, tokenType, refreshToken)
                }
            }
            val manager = ExistingAccountSessionManager(SELF, com.wire.kalium.logic.data.conversation.ClientId(CLIENT),
                runtime.prepared.serverConfig, storage)
            val api = object : AccessTokenApi {
                override suspend fun getToken(refreshToken: String, clientId: String?): NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>> {
                    assertEquals(CLIENT, clientId)
                    assertEquals(checkNotNull(tokens.getToken(SELF.toDao())).refreshToken, refreshToken)
                    return issue()
                }
            }
            val session = object : SessionManager by manager {
                override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String?) = manager.updateToken(api, oldRefreshToken)
            }
            return SessionRefreshSuggestedEventHandlerImpl(runtime.network, session)
        }

        suspend fun verifyRefreshedSession(runtime: AppleAccountRuntime, index: Int) {
            val session = runtime.sessionManager.session()
            assertEquals("access-$index", session.accessToken)
            assertEquals("refresh-$index", session.refreshToken)
            assertEquals("cookie", session.cookieLabel)
            assertEquals(SELF.toApi(), session.userId)
        }

        fun selfConversationResponse() = creationResponse().copy(
            creator = SELF.value, type = ConversationResponse.Type.SELF, name = null,
            members = ConversationMembersResponse(ConversationMemberDTO.Self(SELF.toApi(), "wire_member"), emptyList()),
            access = emptySet(), accessRole = emptySet(), cellsState = null,
        )

        suspend fun selfConversationPage(mode: String): List<EventResponseToStore> {
            val creation = creationEvent(response = selfConversationResponse())
            val before = when (mode) {
                "welcome-only" -> listOf(welcomeEvent())
                "welcome-first" -> listOf(welcomeEvent(), creation)
                else -> listOfNotNull(creation, if (mlsEnabled) welcomeEvent() else null)
            }
            val signal = event("delete-for-me", encrypt("delete-for-me", MessageContent.DeleteForMe("delete-me", DELETE_TARGET.toModel())))
            val text = event("notification", encrypt("first"))
            return if (mode == "ordered") before + batch("notification", signal, text)
                else listOf(batch("notification", *(before + signal + text).toTypedArray()))
        }

        suspend fun verifySelfConversation(runtime: AppleAccountRuntime, expectWarning: Boolean = true) {
            val db = runtime.database
            val conversation = checkNotNull(db.conversationDAO.getConversationById(CONVERSATION_ID))
            assertEquals(ConversationEntity.Type.SELF, conversation.type)
            assertEquals(CONVERSATION_ID, db.conversationDAO.getSelfConversationId(
                if (mlsEnabled) ConversationEntity.Protocol.MLS else ConversationEntity.Protocol.PROTEUS))
            assertEquals(listOf(SELF.toDao()), db.memberDAO.getConversationMembers(CONVERSATION_ID))
            val messages = db.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first()
            assertEquals(if (expectWarning) 1 else 0, messages.count { it.content == MessageEntityContent.ConversationStartedUnverifiedWarning })
            assertFalse(messages.any { it.content == MessageEntityContent.ConversationCreated ||
                it.content is MessageEntityContent.MemberChange || it.content is MessageEntityContent.NewConversationReceiptMode })
            assertNull(db.messageDAO.getMessageById("delete-me", DELETE_TARGET))
            assertTrue(db.pendingDeliveryConfirmationDAO.getPendingAfter(0, 100).isEmpty())
            if (mlsEnabled) {
                val protocol = assertIs<ConversationEntity.ProtocolInfo.MLS>(conversation.protocolInfo)
                assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                assertEquals(checkNotNull(openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) }, protocol.epoch)
            }
        }

        fun meetingConversationResponse() = creationResponse().copy(conversationGroupType = ConversationResponse.GroupType.Meeting)

        fun meetingCreationEvent(id: String = "creation") = EventResponseToStore(
            id, KtxSerializer.json.encodeToString(listOf<EventContentDTO>(EventContentDTO.Conversation.NewMeetingConversationDTO(
                CONVERSATION_ID.toApi(), SENDER.toApi(), TIME, meetingConversationResponse()))), false)

        suspend fun meetingConversationEvent(welcomeFirst: Boolean, creation: Boolean = true): EventResponseToStore {
            val before = if (mlsEnabled && welcomeFirst) listOfNotNull(welcomeEvent(), if (creation) meetingCreationEvent() else null)
                else listOfNotNull(if (creation) meetingCreationEvent() else null, if (mlsEnabled) welcomeEvent() else null)
            return batch("notification", *(before + meetingEvent("create") + event("first", encrypt("first"))).toTypedArray())
        }

        fun meetingConversationProfiles() = KtxSerializer.json.decodeFromString<ListUsersDTO>(
            """{"found":[${listOf(SENDER, THIRD).joinToString(",") { user ->
                """{"qualified_id":{"id":"${user.value}","domain":"${user.domain}"},"id":"${user.value}",
                "name":"Third user","handle":"third","accent_id":0,"assets":[],"deleted":false,
                "legalhold_status":"disabled","supported_protocols":["proteus","mls"]}"""
            }}],"failed":[]}""")

        fun creationEvent(id: String = "creation", response: ConversationResponse = creationResponse()): EventResponseToStore =
            EventResponseToStore(id, KtxSerializer.json.encodeToString(listOf<EventContentDTO>(
                EventContentDTO.Conversation.NewConversationDTO(response.id, senderId.toApi(), TIME, response)
            )), false)

        fun welcomeEvent(id: String = "welcome", message: ByteArray = checkNotNull(welcome)): EventResponseToStore =
            EventResponseToStore(id, KtxSerializer.json.encodeToString(listOf<EventContentDTO>(
                EventContentDTO.Conversation.MLSWelcomeDTO(CONVERSATION_ID.toApi(), senderId.toApi(), Base64.encode(message), senderId.value, TIME)
            )), false)

        suspend fun creationPage(together: Boolean): List<EventResponseToStore> {
            val events = listOfNotNull(creationEvent(), if (mlsEnabled) welcomeEvent() else null, event("notification", encrypt("first")))
            return if (together) listOf(batch("notification", *events.toTypedArray())) else events
        }

        suspend fun verifyCreation(runtime: AppleAccountRuntime, expectMessages: Boolean = true,
            expectedType: ConversationEntity.Type = ConversationEntity.Type.GROUP) {
            val database = runtime.database
            val conversation = checkNotNull(database.conversationDAO.getConversationById(CONVERSATION_ID))
            assertEquals("Created group", conversation.name)
            assertEquals(expectedType, conversation.type)
            assertEquals(ConversationEntity.ReceiptMode.ENABLED, conversation.receiptMode)
            assertEquals(CONVERSATION_ID.toString(), conversation.wireCell)
            assertEquals(setOf(SELF.toDao(), SENDER.toDao()), database.memberDAO.getConversationMembers(CONVERSATION_ID).toSet())
            if (expectMessages) assertTrue(
                database.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first().any {
                    it is MessageEntity.System && it.content == MessageEntityContent.ConversationCreated
                }
            )
            assertNotNull(database.userDAO.getUserDetailsByQualifiedID(SENDER.toDao())?.name)
            if (mlsEnabled) {
                val protocol = assertIs<ConversationEntity.ProtocolInfo.MLS>(conversation.protocolInfo)
                assertEquals(ConversationEntity.GroupState.ESTABLISHED, protocol.groupState)
                assertEquals(checkNotNull(openMLS(runtime)).transaction { it.conversationEpoch(GROUP_ID) }, protocol.epoch)
            }
        }

        suspend fun seed(vararg messages: MessageEntity.Regular) {
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default
            )
            try {
                messages.forEach { database.messageDAO.insertOrIgnoreMessage(it) }
            } finally {
                database.close()
            }
        }

        suspend fun previewEvent(image: Boolean = true, complete: Boolean = true): EventResponseToStore {
            val content = MessageContent.Text("first",
                mentions = listOf(com.wire.kalium.logic.data.message.mention.MessageMention(0, 4, SELF, true)),
                linkPreviews = listOf(com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview(
                    "https://example.com", 0, "https://example.com/permanent", "preview", "summary",
                    if (image) com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset(
                        mimeType = "image/png", assetDataPath = null, assetDataSize = 4, assetHeight = 20, assetWidth = 10,
                        assetKey = "preview-image", assetToken = "token-preview-image", assetDomain = SELF.domain,
                        otrKey = if (complete) KEY else byteArrayOf(), sha256Key = assetHash,
                    ) else null),
                    com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview("https://example.org", 5, title = "second")))
            val hash = checkNotNull(MessageContentEncoder().encodeMessageContent(TIME, content)).sha256Digest
            return batch("notification", event("first", encrypt("first", content)), event("quote", encrypt("preview-quoted",
                MessageContent.Text("preview-quoted", quotedMessageReference = MessageContent.QuoteReference("first", hash, false)))))
        }

        suspend fun verifyPreviewMetadata(runtime: AppleAccountRuntime, image: Boolean = true, complete: Boolean = true, downloaded: Boolean = false, includeTail: Boolean = false) {
            val database = runtime.database
            val content = assertIs<MessageEntityContent.Text>(
                assertIs<MessageEntity.Regular>(database.messageDAO.getMessageById("first", CONVERSATION_ID)).content)
            val preview = content.linkPreview.single { it.urlOffset == 0 }
            assertEquals(2, content.linkPreview.size)
            assertEquals("preview", preview.title)
            assertEquals("summary", preview.summary)
            assertEquals("https://example.com/permanent", preview.permanentUrl)
            assertEquals(SELF.toDao(), content.mentions.single().userId)
            if (downloaded) {
                val path = checkNotNull(preview.imageLocalPath).toPath()
                assertEquals(ASSET_TEXT, FileSystem.SYSTEM.read(path) { readUtf8() })
                val asset = checkNotNull(database.assetDAO.getAssetByKey("preview-image").first())
                assertEquals(path.toString(), asset.dataPath)
                assertEquals(ASSET_TEXT.encodeToByteArray().size.toLong(), asset.dataSize)
            } else assertTrue(preview.imageLocalPath.isNullOrEmpty())
            if (image) {
                assertEquals("preview-image", preview.imageAssetKey)
                assertEquals("token-preview-image", preview.imageAssetToken)
                assertEquals(SELF.domain, preview.imageAssetDomain)
                assertContentEquals(if (complete) KEY else byteArrayOf(), preview.imageOtrKey?.hexToByteArray())
                assertContentEquals(assetHash, preview.imageSha256?.hexToByteArray())
            }
            if (!downloaded) assertTrue(database.assetDAO.getAssets().isEmpty())
            val quote = assertIs<MessageEntityContent.Text>(
                assertIs<MessageEntity.Regular>(database.messageDAO.getMessageById("preview-quoted", CONVERSATION_ID)).content)
            assertEquals(true, quote.quotedMessage?.isVerified)
            assertEquals(listOf("first", "preview-quoted") + if (includeTail) listOf("tail") else emptyList(), database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
        }

        suspend fun multipartEvent(edited: Boolean = false): EventResponseToStore {
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default
            )
            try {
                database.userConfigDAO.setWireCellsConfig(com.wire.kalium.persistence.config.WireCellsConfigEntity(
                    "${apiEndpoint ?: "https://localhost:18443/"}cells", "", null))
            } finally {
                database.close()
            }
            val hash = checkNotNull(MessageContentEncoder().encodeMessageContent(TIME, MessageContent.Text("first"))).sha256Digest
            val content = MessageContent.Multipart(
                value = "multipart",
                mentions = listOf(com.wire.kalium.logic.data.message.mention.MessageMention(0, 4, SELF, true)),
                linkPreviews = listOf(com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview(
                    "https://example.com", 0, title = "preview")),
                quotedMessageReference = MessageContent.QuoteReference("first", hash, false),
                attachments = listOf("cell-two", "cell-one").map { id ->
                    com.wire.kalium.logic.data.message.CellAssetContent(
                        id = id, versionId = "", mimeType = "image/png", assetPath = "remote/$id.png", assetSize = 4,
                        metadata = com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image(10, 20),
                        transferStatus = com.wire.kalium.logic.data.asset.AssetTransferStatus.NOT_DOWNLOADED)
                },
            )
            val original = batch("notification", event("first", encrypt("first")), event("multipart", encrypt("multipart", content)),
                event("multipart-empty", encrypt("multipart-empty", MessageContent.Multipart("",
                    quotedMessageReference = MessageContent.QuoteReference("missing", hash, false)))))
            return if (edited) batch("notification", original, event("multipart-edit", encrypt("multipart-edited",
                MessageContent.MultipartEdited("multipart", "multipart-edited", newAttachments = emptyList())), TIME + 5.seconds)) else original
        }

        suspend fun verifyMultipart(runtime: AppleAccountRuntime, edited: Boolean = false, includeTail: Boolean = false) {
            val id = if (edited) "multipart-edited" else "multipart"
            val database = runtime.database
            val message = assertIs<MessageEntity.Regular>(database.messageDAO.getMessageById(id, CONVERSATION_ID))
            val content = assertIs<MessageEntityContent.Multipart>(message.content)
            assertEquals(id, content.messageBody)
            assertEquals(true, content.quotedMessage?.isVerified)
            assertEquals("first", content.quotedMessageId)
            if (edited) assertTrue(content.mentions.isEmpty()) else assertEquals(SELF.toDao(), content.mentions.single().userId)
            // The app currently omits multipart previews when mapping rows back to the model.
            assertTrue(content.linkPreview.isEmpty())
            assertEquals(if (edited) null else "preview", sqlValue("SELECT title FROM MessageLinkPreview WHERE message_id = '$id'"))
            assertEquals(listOf("cell-two", "cell-one"), content.attachments.map { it.assetId })
            assertEquals(listOf("cell-two", "cell-one"), database.messageAttachments.getAttachments(id, CONVERSATION_ID).map { it.assetId })
            assertTrue(database.messageDAO.needsToBeNotified(id, CONVERSATION_ID))
            assertEquals(listOf("first", "multipart", "multipart-empty") + if (includeTail) listOf("tail") else emptyList(),
                database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
            if (edited) {
                assertNull(database.messageDAO.getMessageById("multipart", CONVERSATION_ID))
                assertEquals(MessageEntity.EditStatus.Edited(TIME + 5.seconds), message.editStatus)
            }
            val empty = assertIs<MessageEntityContent.Multipart>(
                assertIs<MessageEntity.Regular>(database.messageDAO.getMessageById("multipart-empty", CONVERSATION_ID)).content)
            assertNull(empty.quotedMessage)
            assertEquals("0", sqlValue("SELECT is_quote_verified FROM MessageTextContent WHERE message_id = 'multipart-empty'"))
            assertTrue(empty.attachments.isEmpty())
        }

        suspend fun seedConversationCleanupFiles(target: QualifiedIDEntity) {
            seedProteusConversation(FILE_KEEP_CONVERSATION)
            seedMessageFiles(target)
            sql("UPDATE Message SET conversation_id = '$FILE_KEEP_CONVERSATION' WHERE id IN ('keep-asset', 'keep-multipart')")
            sql("INSERT INTO PendingDeliveryConfirmations(conversation_id, message_id) VALUES('$target', 'remove-asset')")
        }

        fun adminlessReminder(system: Boolean, deletionTime: Instant) = plainEvent("reminder", """{
            "type":"conversation.${if (system) "system." else ""}adminless-reminder",
            "conversation":"${CONVERSATION_ID.value}","via":"system",
            ${if (system) "" else """"from":"system","""}
            "qualified_conversation":{"id":"${CONVERSATION_ID.value}","domain":"${CONVERSATION_ID.domain}"},
            "qualified_from":${if (system) "null" else """{"id":"${SENDER.value}","domain":"${SENDER.domain}"}"""},
            "time":"$TIME","data":{"deletion_scheduled_for":"$deletionTime"}
        }""")

        fun deletedConversationEvent(target: QualifiedIDEntity = DELETE_TARGET, meeting: Boolean = false, sender: UserId = SENDER) = plainEvent("delete-conversation",
            """{"type":"conversation.${if (meeting) "delete-meeting" else "delete"}",
            "qualified_conversation":{"id":"${target.value}","domain":"${target.domain}"},
            "qualified_from":{"id":"${sender.value}","domain":"${sender.domain}"},"time":"$TIME"}"""
        )

        suspend fun conversationCleanupEvent(mode: CleanupMode, target: QualifiedIDEntity = if (mode == CleanupMode.CLEAR) CONVERSATION_ID else DELETE_TARGET): EventResponseToStore {
            if (mode == CleanupMode.BACKEND_DELETE) return batch("notification", event("first", encrypt("first")), deletedConversationEvent())
            val prefix = if (mode == CleanupMode.CLEAR) listOf(event("prefix", encrypt("prefix"))) else emptyList()
            // Clear uses a historical cutoff; local deletion removes the target through this signal.
            val cutoff = if (mode == CleanupMode.CLEAR) TIME - 1_000.seconds else TIME
            val clear = event("clear", encrypt("clear", MessageContent.Cleared(target.toModel(), cutoff, mode == CleanupMode.LOCAL_DELETE)))
            return batch("notification", *(prefix + clear + event("first", encrypt("first"))).toTypedArray())
        }

        suspend fun verifyPendingConversationCleanup(runtime: AppleAccountRuntime, target: QualifiedIDEntity, deleted: Boolean = false) {
            val type = if (deleted) PendingActionType.DELETE_CONVERSATION_LOCALLY else PendingActionType.CLEAR_CONVERSATION_CONTENT
            val otherType = if (deleted) PendingActionType.CLEAR_CONVERSATION_CONTENT else PendingActionType.DELETE_CONVERSATION_LOCALLY
            assertTrue(runtime.database.pendingActionDAO.getByActionType(type).isEmpty())
            assertTrue(runtime.database.pendingActionDAO.getByActionType(otherType).isEmpty())
        }

        suspend fun verifyHistoricalCutoffCleanup(runtime: AppleAccountRuntime, target: QualifiedIDEntity) {
            val db = runtime.database
            verifyPendingConversationCleanup(runtime, target)
            assertNotNull(db.conversationDAO.getConversationById(target))
            for (id in MESSAGE_FILE_IDS) assertNotNull(db.messageDAO.getMessageById(id, target))
            assertNotNull(db.messageDAO.getMessageById("keep-asset", FILE_KEEP_CONVERSATION))
            assertNotNull(db.messageDAO.getMessageById("keep-multipart", FILE_KEEP_CONVERSATION))
            for (name in listOf("delete-asset", "delete-preview", "delete-attachment", "keep-shared", "keep-offline", "keep-avatar", "keep-draft")) {
                assertTrue(FileSystem.SYSTEM.exists(messageFile(name).toPath()))
            }
            for (key in listOf("delete-asset", "delete-preview", "shared-asset", "avatar-asset")) {
                assertNotNull(db.assetDAO.getAssetByKey(key).first())
            }
            assertTrue(db.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20).any {
                it.conversationId == target && it.messageId == "remove-asset"
            })
        }

        suspend fun verifyConversationCleanup(runtime: AppleAccountRuntime, target: QualifiedIDEntity, deleted: Boolean) {
            val db = runtime.database
            assertEquals(deleted, db.conversationDAO.getConversationById(target) == null)
            for (id in MESSAGE_FILE_IDS + listOf("keep-asset", "keep-multipart", "delete-me")) {
                assertNull(db.messageDAO.getMessageById(id, target))
            }
            assertNotNull(db.messageDAO.getMessageById("keep-asset", FILE_KEEP_CONVERSATION))
            assertNotNull(db.messageDAO.getMessageById("keep-multipart", FILE_KEEP_CONVERSATION))
            for (name in listOf("delete-asset", "delete-preview", "delete-attachment")) {
                assertFalse(FileSystem.SYSTEM.exists(messageFile(name).toPath()))
            }
            for (name in listOf("keep-shared", "keep-offline", "keep-avatar")) {
                assertTrue(FileSystem.SYSTEM.exists(messageFile(name).toPath()))
            }
            assertEquals(!deleted, FileSystem.SYSTEM.exists(messageFile("keep-draft").toPath()))
            assertNull(db.assetDAO.getAssetByKey("delete-asset").first())
            assertNull(db.assetDAO.getAssetByKey("delete-preview").first())
            assertNotNull(db.assetDAO.getAssetByKey("shared-asset").first())
            assertNotNull(db.assetDAO.getAssetByKey("avatar-asset").first())
            assertFalse(db.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20).any {
                it.conversationId == target && it.messageId == "remove-asset"
            })
        }

        fun messageFile(name: String): String = "$root/message-files/$name.bin"

        suspend fun seedMessageFiles(conversationId: QualifiedIDEntity = CONVERSATION_ID) {
            FileSystem.SYSTEM.createDirectories("$root/message-files".toPath())
            val fileNames = listOf("delete-asset", "delete-preview", "delete-attachment", "keep-shared", "keep-offline", "keep-draft", "keep-avatar")
            fileNames.forEach { name -> FileSystem.SYSTEM.write(messageFile(name).toPath()) { writeUtf8(name) } }
            val mapper = com.wire.kalium.logic.data.message.RegularMessageMapper(SELF)
            fun asset(messageId: String, assetId: String) = storedText(messageId).copy(
                conversationId = conversationId, content = mapper.toMessageEntityContent(assetContent(assetId)))
            fun preview(messageId: String, path: String, key: String? = null) = storedText(messageId).copy(
                conversationId = conversationId, content = MessageEntityContent.Text(messageId, listOf(
                    MessageEntity.LinkPreview("https://example.com", 0, "https://example.com", "preview", "summary",
                        imageLocalPath = messageFile(path), imageAssetKey = key))))
            fun attachment(id: String, path: String) = com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity(
                assetId = id, cellAsset = true, mimeType = "image/png", assetPath = "remote/$id", assetSize = 4,
                localPath = messageFile(path), assetWidth = 10, assetHeight = 20, assetDuration = null,
                assetTransferStatus = "SAVED_INTERNALLY", isEditSupported = false)
            fun multipart(id: String, vararg items: com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity) =
                storedText(id).copy(conversationId = conversationId, content = MessageEntityContent.Multipart(id, attachments = items.toList()))
            seed(
                asset("remove-asset", "delete-asset"),
                preview("remove-preview", "delete-preview", "delete-preview"),
                multipart("remove-multipart", attachment("cell-delete", "delete-attachment"), attachment("cell-shared", "keep-shared")),
                asset("remove-asset-shared", "shared-asset"),
                asset("remove-alias", "alias-delete"),
                multipart("remove-offline", attachment("cell-offline", "keep-offline")),
                preview("remove-draft-ref", "keep-draft"),
                asset("remove-avatar-ref", "avatar-asset"),
                asset("keep-asset", "shared-asset"),
                multipart("keep-multipart", attachment("cell-shared", "keep-shared")),
            )
            mapOf("delete-asset" to "delete-asset", "delete-preview" to "delete-preview", "shared-asset" to "keep-shared",
                "alias-delete" to "keep-shared", "avatar-asset" to "keep-avatar").forEach { (key, path) ->
                sql("INSERT INTO Asset(key, domain, data_path, data_size, downloaded_date) VALUES('$key', '${SELF.domain}', '${messageFile(path)}', 4, 0)")
            }
            sql("UPDATE CellFile SET isOffline = 1 WHERE uuid = 'cell-offline'")
            sql("INSERT INTO MessageAttachmentDraft(attachment_id, version_id, conversation_id, mime_type, file_name, file_size, " +
                "data_path, node_path, upload_status) VALUES('draft-ref', 'version', '${conversationId.value}@${conversationId.domain}', " +
                "'image/png', 'draft.png', 4, '${messageFile("keep-draft")}', 'node', 'NOT_UPLOADED')")
            sql("UPDATE User SET complete_asset_id = 'avatar-asset@${SELF.domain}' WHERE qualified_id = '${SELF.value}@${SELF.domain}'")
        }

        suspend fun seedExpiredMessageFiles() {
            val mapper = com.wire.kalium.logic.data.message.RegularMessageMapper(SELF)
            val expiration = storedText("expired-asset", sender = SELF, expireAfter = 1_000, endDate = TIME).copy(date = TIME - 10.seconds)
            FileSystem.SYSTEM.createDirectories(messageFile("expired-asset").toPath())
            FileSystem.SYSTEM.write("${messageFile("expired-asset")}/busy".toPath()) { writeUtf8("prevent deletion") }
            for (name in listOf("expired-preview", "expired-attachment")) {
                FileSystem.SYSTEM.write(messageFile(name).toPath()) { writeUtf8(name) }
            }
            seed(
                expiration.copy(content = mapper.toMessageEntityContent(assetContent("expired-asset"))),
                expiration.copy(id = "expired-preview", content = MessageEntityContent.Text("preview", listOf(
                    MessageEntity.LinkPreview("https://example.com", 0, "https://example.com", "preview", "summary",
                        imageLocalPath = messageFile("expired-preview"))))),
                expiration.copy(id = "expired-multipart", content = MessageEntityContent.Multipart("multipart", attachments = listOf(
                    com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity(
                        assetId = "expired-cell", cellAsset = true, mimeType = "image/png", assetPath = "remote/expired-cell", assetSize = 4,
                        localPath = messageFile("expired-attachment"), assetWidth = 10, assetHeight = 20, assetDuration = null,
                        assetTransferStatus = "SAVED_INTERNALLY", isEditSupported = false))))
            )
            sql("INSERT INTO Asset(key, domain, data_path, data_size, downloaded_date) " +
                "VALUES('expired-asset', '${SELF.domain}', '${messageFile("expired-asset")}', 4, 0)")
        }

        suspend fun messageFileDeletionEvent(includeText: Boolean = true): EventResponseToStore {
            val events = MESSAGE_FILE_IDS.map { id -> event("delete-$id", encrypt("delete-$id", MessageContent.DeleteMessage(id))) }
            return batch("notification", *(events + if (includeText) listOf(event("first", encrypt("first"))) else emptyList()).toTypedArray())
        }

        suspend fun verifyMessageFiles(runtime: AppleAccountRuntime, conversationId: QualifiedIDEntity = CONVERSATION_ID, removed: Boolean = false) {
            val db = runtime.database
            for (id in MESSAGE_FILE_IDS) {
                val message = db.messageDAO.getMessageById(id, conversationId)
                if (removed) assertNull(message) else assertEquals(MessageEntity.Visibility.DELETED, message?.visibility)
            }
            for (id in listOf("keep-asset", "keep-multipart")) assertEquals(MessageEntity.Visibility.VISIBLE,
                db.messageDAO.getMessageById(id, conversationId)?.visibility)
            for (name in listOf("delete-asset", "delete-preview", "delete-attachment")) assertFalse(FileSystem.SYSTEM.exists(messageFile(name).toPath()))
            for (name in listOf("keep-shared", "keep-offline", "keep-draft", "keep-avatar")) assertTrue(FileSystem.SYSTEM.exists(messageFile(name).toPath()))
            assertNull(db.assetDAO.getAssetByKey("delete-asset").first())
            assertNull(db.assetDAO.getAssetByKey("delete-preview").first())
            assertNotNull(db.assetDAO.getAssetByKey("shared-asset").first())
            assertNotNull(db.assetDAO.getAssetByKey("avatar-asset").first())
            assertTrue(db.messageAttachments.getAttachments("remove-multipart", conversationId).isEmpty())
        }

        val meetingStart = com.wire.kalium.util.DateTimeUtil.currentInstant() - 86400.seconds

        fun meetingDTO(id: com.wire.kalium.network.api.model.MeetingId) =
            com.wire.kalium.network.api.authenticated.meeting.MeetingDTO(
                meetingId = id,
                conversationId = CONVERSATION_ID.toApi(),
                creatorId = THIRD.toApi(),
                createdAt = TIME,
                updatedAt = TIME,
                title = "Fixture meeting",
                startTime = meetingStart,
                endTime = meetingStart + 3600.seconds,
                tzid = "America/New_York",
                recurrence = com.wire.kalium.network.api.authenticated.meeting.MeetingRecurrenceDTO(
                    com.wire.kalium.network.api.authenticated.meeting.MeetingFrequencyDTO.DAILY, 1, meetingStart + 604800.seconds))

        fun meetingEvent(type: String, id: String = "meeting") = EventResponseToStore("meeting-$type-$id", """
            [{"type":"meeting.$type","qualified_id":{"id":"$id","domain":"${SELF.domain}"},"time":"$TIME"}]
        """.trimIndent(), false)

        suspend fun meetingEvents() = batch("notification", event("prefix", encrypt("prefix")),
            meetingEvent("create"), meetingEvent("update"), meetingEvent("member-add"),
            meetingEvent("create", "removed-meeting"), meetingEvent("delete", "removed-meeting"),
            event("first", encrypt("first")))

        suspend fun verifyMeetingState(runtime: AppleAccountRuntime): String {
            val db = runtime.database
            val id = QualifiedIDEntity("meeting", SELF.domain)
            val meeting = checkNotNull(db.meetingDao.getMeeting(id))
            assertEquals("Fixture meeting", meeting.title)
            assertEquals("America/New_York", meeting.tzid)
            assertEquals(THIRD.toDao(), meeting.creatorId)
            assertNotNull(db.userDAO.getUserDetailsByQualifiedID(THIRD.toDao()))
            assertNotNull(db.conversationDAO.getConversationById(CONVERSATION_ID))
            assertNull(db.meetingDao.getMeeting(QualifiedIDEntity("removed-meeting", SELF.domain)))
            assertNull(db.meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(
                QualifiedIDEntity("removed-meeting", SELF.domain), meeting.startTime))
            val occurrence = checkNotNull(db.meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(id, meeting.startTime))
            val details = checkNotNull(db.meetingDao.getMeetingOccurrenceDetailsFlow(occurrence).first())
            assertEquals(meeting.startTime, details.occurrence.occurrenceStart)
            assertEquals(meeting.endTime, details.occurrence.occurrenceEnd)
            return occurrence
        }

        suspend fun seedFederationState(deleteRequests: Boolean = false) {
            val db = userDatabaseBuilder(PlatformDatabaseData(StorageData.FileBacked("$account/storage")),
                SELF.toDao(), databaseSecret, Dispatchers.Default)
            try {
                val members = listOf(SELF, SENDER, THIRD.copy(domain = "federated.example"), THIRD.copy(domain = "other.example"))
                members.forEach { db.userDAO.upsertUser(user(it)) }
                db.conversationDAO.updateConversationType(CONVERSATION_ID, ConversationEntity.Type.GROUP)
                for (type in listOf(ConversationEntity.Type.GROUP, ConversationEntity.Type.CHANNEL, ConversationEntity.Type.MEETING)) {
                    for (domain in listOf(SELF.domain, "federated.example", "other.example")) {
                        val id = QualifiedIDEntity(if (type == ConversationEntity.Type.GROUP) "conversation" else if (type == ConversationEntity.Type.CHANNEL) "CHANNEL" else "MEETING", domain)
                        if (id != CONVERSATION_ID) db.conversationDAO.insertConversation(
                            conversation(ConversationEntity.ProtocolInfo.Proteus).copy(id = id, type = type))
                        db.memberDAO.insertMembersWithQualifiedId(members.map {
                            com.wire.kalium.persistence.dao.member.MemberEntity(it.toDao(),
                                com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member)
                        }, id)
                    }
                }
                for (already in listOf(false, true)) {
                    val id = QualifiedIDEntity(if (already) "already-defederated" else "federation-one", SELF.domain)
                    val peer = THIRD.copy(value = if (already) "already-defederated" else THIRD.value, domain = "federated.example")
                    db.userDAO.upsertUser(user(peer))
                    if (already) db.userDAO.markUserAsDefederated(peer.toDao())
                    db.conversationDAO.insertConversation(conversation(ConversationEntity.ProtocolInfo.Proteus)
                        .copy(id = id, type = ConversationEntity.Type.ONE_ON_ONE))
                    db.memberDAO.insertMembersWithQualifiedId(listOf(SELF, peer).map {
                        com.wire.kalium.persistence.dao.member.MemberEntity(it.toDao(),
                            com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member)
                    }, id)
                    db.messageDAO.insertOrIgnoreMessage(storedText("history").copy(conversationId = id))
                }
                for (name in listOf("PENDING", "SENT", "IGNORED", "ACCEPTED", "BLOCKED", "unknown", "foreign")) {
                    val id = if (name == "PENDING") DELETE_TARGET else QualifiedIDEntity("request-$name", SELF.domain)
                    val peer = UserId("request-$name", if (name == "foreign") "other.example" else "federated.example")
                    val state = if (name in listOf("unknown", "foreign")) ConnectionEntity.State.PENDING else ConnectionEntity.State.valueOf(name)
                    if (name != "unknown") db.userDAO.upsertUser(user(peer).copy(connectionStatus = state))
                    db.conversationDAO.insertConversation(conversation(ConversationEntity.ProtocolInfo.Proteus).copy(id = id))
                    db.connectionDAO.insertConnection(ConnectionEntity(id.value, SELF.value, TIME, id, peer.toDao(), state, peer.value))
                }
            } finally { db.close() }
            seedConversationCleanupFiles(DELETE_TARGET)
            if (!deleteRequests) sql("DELETE FROM PendingDeliveryConfirmations")
        }

        fun federationEvent(deleted: Boolean, domains: List<String> = listOf("federated.example", "other.example")) = plainEvent(
            "federation", if (deleted) """{"type":"federation.delete","domain":"federated.example"}"""
            else """{"type":"federation.connectionRemoved","domains":${JsonArray(domains.map { JsonPrimitive(it) })}}""")

        suspend fun federationEvents(deleted: Boolean, clear: Boolean = false): EventResponseToStore {
            val prefix = event("prefix", encrypt("prefix"))
            val clearing = if (clear) listOf(event("clear", encrypt("clear", MessageContent.Cleared(CONVERSATION_ID.toModel(), TIME, false))))
                else emptyList()
            return batch("notification", *(listOf(prefix) + clearing + federationEvent(deleted) + event("first", encrypt("first"))).toTypedArray())
        }

        suspend fun verifyFederationState(runtime: AppleAccountRuntime, deleted: Boolean): List<MessageEntity> {
            val db = runtime.database
            val warnings = mutableListOf<MessageEntity>()
            val local = setOf(SELF.toDao(), SENDER.toDao())
            val fed = THIRD.copy(domain = "federated.example").toDao()
            val other = THIRD.copy(domain = "other.example").toDao()
            for (type in listOf("conversation", "CHANNEL", "MEETING")) {
                for (domain in listOf(SELF.domain, "federated.example", "other.example")) {
                    val id = QualifiedIDEntity(type, domain)
                    val expected = if (deleted) when (domain) {
                        SELF.domain -> local + other
                        "federated.example" -> setOf(fed, other)
                        else -> setOf(other)
                    } else when (domain) {
                        "federated.example" -> local + fed
                        "other.example" -> local + other
                        else -> local
                    }
                    assertEquals(expected, db.memberDAO.getConversationMembers(id).toSet(), "Members of $id")
                    val messages = db.messageDAO.getMessagesByConversationAndVisibility(id, 100, 0).first()
                    val stopped = messages.single { it.content is MessageEntityContent.Federation }
                    assertEquals(MessageEntityContent.Federation(if (deleted) listOf("federated.example") else
                        listOf("federated.example", "other.example"), if (deleted) MessageEntity.FederationType.DELETE else
                        MessageEntity.FederationType.CONNECTION_REMOVED), stopped.content)
                    val removed = messages.single { (it.content as? MessageEntityContent.MemberChange)?.memberChangeType ==
                        MessageEntity.MemberChangeType.FEDERATION_REMOVED }
                    assertEquals((local + fed + other) - expected, (removed.content as MessageEntityContent.MemberChange).memberUserIdList.toSet())
                    assertEquals(MessageEntity.Status.READ, stopped.status)
                    assertEquals(MessageEntity.Status.READ, removed.status)
                    warnings += listOf(stopped, removed)
                }
            }
            for (already in listOf(false, true)) {
                val id = QualifiedIDEntity(if (already) "already-defederated" else "federation-one", SELF.domain)
                val peer = THIRD.copy(value = if (already) "already-defederated" else THIRD.value, domain = "federated.example")
                assertEquals(deleted || already, checkNotNull(db.userDAO.getUserDetailsByQualifiedID(peer.toDao())).defederated)
                assertNotNull(db.messageDAO.getMessageById("history", id))
                assertEquals(setOf(SELF.toDao(), peer.toDao()), db.memberDAO.getConversationMembers(id).toSet())
                val messages = db.messageDAO.getMessagesByConversationAndVisibility(id, 100, 0).first()
                    .filter { it.content is MessageEntityContent.Federation }
                assertEquals(if (deleted && !already) 1 else 0, messages.size)
                warnings += messages
            }
            for (name in listOf("PENDING", "SENT", "IGNORED", "ACCEPTED", "BLOCKED", "unknown", "foreign")) {
                val id = if (name == "PENDING") DELETE_TARGET else QualifiedIDEntity("request-$name", SELF.domain)
                val removed = deleted && name in listOf("PENDING", "SENT", "IGNORED")
                assertEquals(removed, db.connectionDAO.getConnection(id) == null)
                assertEquals(removed, db.conversationDAO.getConversationById(id) == null)
                if (removed) assertEquals(ConnectionEntity.State.CANCELLED, db.userDAO.getUserDetailsByQualifiedID(
                    QualifiedIDEntity("request-$name", "federated.example"))?.connectionStatus)
            }
            if (deleted) verifyConversationCleanup(runtime, DELETE_TARGET, deleted = true)
            else assertTrue(FileSystem.SYSTEM.exists(messageFile("delete-asset").toPath()))
            return warnings
        }

        suspend fun seedIdentityState() {
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default)
            try {
                for (id in listOf(SELF, SENDER, THIRD, THIRD.copy(domain = "foreign.example"))) {
                    database.userDAO.upsertUser(user(id).copy(team = "fixture-team"))
                }
                database.conversationDAO.updateConversationType(CONVERSATION_ID, ConversationEntity.Type.GROUP)
                for (type in listOf(ConversationEntity.Type.CHANNEL, ConversationEntity.Type.MEETING, ConversationEntity.Type.ONE_ON_ONE)) {
                    val id = if (type == ConversationEntity.Type.ONE_ON_ONE) DELETE_TARGET else QualifiedIDEntity(if (type == ConversationEntity.Type.CHANNEL) "CHANNEL" else "MEETING", SELF.domain)
                    database.conversationDAO.insertConversation(conversation(ConversationEntity.ProtocolInfo.Proteus).copy(id = id, type = type))
                }
                for (id in listOf(CONVERSATION_ID, DELETE_TARGET, QualifiedIDEntity("CHANNEL", SELF.domain), QualifiedIDEntity("MEETING", SELF.domain))) {
                    database.memberDAO.insertMembersWithQualifiedId(listOf(SELF, SENDER, THIRD, THIRD.copy(domain = "foreign.example")).map {
                        com.wire.kalium.persistence.dao.member.MemberEntity(it.toDao(), com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member)
                    }, id)
                }
                database.messageDAO.insertOrIgnoreMessage(storedText("history").copy(conversationId = DELETE_TARGET))
            } finally { database.close() }
        }

        fun newClient(id: String = "new-client") = plainEvent("client-add",
            """{"type":"user.client-add","client":{"id":"$id","time":"$TIME","type":"permanent","class":"phone",
            "model":"Fixture phone","label":"New device","capabilities":{"capabilities":[]},"mls_public_keys":{"ed25519":"key"}}}""")

        fun clientRemove(id: String = "new-client") = plainEvent("client-remove",
            """{"type":"user.client-remove","client":{"id":"$id"}}""")

        fun userDelete(user: UserId) = plainEvent("user-delete",
            """{"type":"user.delete","id":"${user.value}","qualified_id":{"id":"${user.value}","domain":"${user.domain}"}}""")

        fun teamLeave(user: UserId = THIRD) = plainEvent("team-leave",
            """{"type":"team.member-leave","team":"fixture-team","time":"$TIME","data":{"user":"${user.value}"}}""")

        suspend fun identityEvent(own: Boolean = false) = if (own) {
            batch("notification", newClient(), event("prefix", encrypt("prefix")), teamLeave(SELF), event("first", encrypt("first")))
        } else batch("notification", newClient(CLIENT), newClient(), clientRemove(),
            event("prefix", encrypt("prefix")), teamLeave(), memberLeave(member = THIRD, reason = "user-deleted"),
            userDelete(THIRD), event("first", encrypt("first")))

        suspend fun verifyIdentityState(runtime: AppleAccountRuntime, client: Boolean = true, team: Boolean = true, own: Boolean = false) {
            val database = runtime.database
            if (client) {
                val new = database.newClientDAO.observeNewClients().first().single()
                assertEquals("new-client", new.id)
                assertEquals("Fixture phone", new.model)
                assertEquals(TIME, new.registrationDate)
                val stored = database.clientDAO.getClientsOfUserByQualifiedID(SELF.toDao()).single { it.id == new.id }
                assertTrue(stored.isValid)
                assertTrue(stored.isMLSCapable)
                assertEquals("New device", stored.label)
            }
            val removedId = if (own) SELF else THIRD
            val removed = checkNotNull(database.userDAO.getUserDetailsByQualifiedID(removedId.toDao()))
            assertTrue(removed.deleted)
            assertEquals(UserTypeEntity.NONE, removed.userType)
            assertFalse(checkNotNull(database.userDAO.getUserDetailsByQualifiedID(THIRD.copy(domain = "foreign.example").toDao())).deleted)
            for (id in listOf(CONVERSATION_ID, QualifiedIDEntity("CHANNEL", SELF.domain), QualifiedIDEntity("MEETING", SELF.domain))) {
                assertNull(database.memberDAO.getMemberRole(removedId.toDao(), id))
                assertNotNull(database.memberDAO.getMemberRole(THIRD.copy(domain = "foreign.example").toDao(), id))
                val warnings = database.messageDAO.getMessagesByConversationAndVisibility(id, 100, 0).first()
                    .filter { it.content is MessageEntityContent.MemberChange }
                assertEquals(if (team) 1 else 0, warnings.size)
                if (team) assertEquals(MessageEntityContent.MemberChange(listOf(removedId.toDao()), MessageEntity.MemberChangeType.REMOVED_FROM_TEAM), warnings.single().content)
            }
            assertNotNull(database.memberDAO.getMemberRole(removedId.toDao(), DELETE_TARGET))
            assertNotNull(database.messageDAO.getMessageById("history", DELETE_TARGET))
        }

        suspend fun seedPendingIsolatedRecovery(eventId: String, id: QualifiedIDEntity) {
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default
            )
            try {
                val source = checkNotNull(database.conversationDAO.getConversationById(CONVERSATION_ID))
                val protocol = source.protocolInfo as ConversationEntity.ProtocolInfo.MLS
                database.conversationDAO.insertConversation(source.copy(id = id, protocolInfo = protocol.copy(groupId = "blocked-group")))
                MLSReceiveRecoveryStore(database.metadataDAO, database.notificationEventStore, CLIENT)
                    .begin(eventId, "blocked-group", byteArrayOf(1))
            } finally { database.close() }
        }

        fun unavailableRecovery(runtime: AppleAccountRuntime, onFetch: () -> Unit = {}): MLSReceiveRecovery = MLSReceiveRecovery(
            MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT),
            object : ConversationApi by runtime.network.conversationApi {
                override suspend fun fetchConversationDetails(conversationId: com.wire.kalium.network.api.model.ConversationId)
                    : NetworkResponse<ConversationResponse> {
                    onFetch()
                    return NetworkResponse.Error(com.wire.kalium.network.exceptions.FederationError(
                        com.wire.kalium.network.api.model.FederationErrorResponse.Generic(
                            533, "backend unavailable", "federation-remote-error", null)))
                }
            }, SELF,
        ) { _, _, _ -> error("Unavailable federation must not send a commit") }

        suspend fun seedProteusConversation(id: QualifiedIDEntity = DELETE_TARGET) {
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default
            )
            try {
                database.conversationDAO.insertConversation(conversation(ConversationEntity.ProtocolInfo.Proteus).copy(id = id))
                database.memberDAO.insertMembersWithQualifiedId(listOf(SELF, SENDER).map {
                    com.wire.kalium.persistence.dao.member.MemberEntity(
                        it.toDao(), com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member
                    )
                }, id)
            } finally { database.close() }
        }

        suspend fun seedDeleteForMeTarget() {
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default
            )
            try {
                database.conversationDAO.getConversationById(CONVERSATION_ID)?.let { current ->
                    database.conversationDAO.insertConversation(current.copy(type = ConversationEntity.Type.SELF))
                }
                database.conversationDAO.insertConversation(conversation(ConversationEntity.ProtocolInfo.Proteus).copy(id = DELETE_TARGET))
                if (mlsEnabled) {
                    database.conversationDAO.insertConversation(conversation(ConversationEntity.ProtocolInfo.Proteus).copy(
                        id = QualifiedIDEntity("proteus-self", SELF.domain), type = ConversationEntity.Type.SELF
                    ))
                }
                database.messageDAO.insertOrIgnoreMessage(storedText("delete-me").copy(conversationId = DELETE_TARGET))
            } finally {
                database.close()
            }
        }

        suspend fun seedMLSDeleteForMeTarget() {
            check(mlsEnabled)
            seedDeleteForMeTarget()
            val receiving = checkNotNull(receiverMLS)
            val sending = checkNotNull(senderMLS)
            val keyPackage = receiving.transaction { it.generateKeyPackages(1).single() }
            sending.transaction {
                it.createConversation(RESET_GROUP_ID, Base64.decode(PROPOSAL_PUBLIC_KEY))
                it.addMember(RESET_GROUP_ID, listOf(keyPackage))
            }
            receiving.transaction { it.processWelcomeMessage(checkNotNull(transport.bundle?.welcome)) }
            val protocol = ConversationEntity.ProtocolInfo.MLS(
                RESET_GROUP_ID,
                ConversationEntity.GroupState.ESTABLISHED,
                sending.transaction { it.conversationEpoch(RESET_GROUP_ID) },
                TIME,
                ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
            )
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default
            )
            try {
                database.conversationDAO.insertConversation(conversation(protocol).copy(id = DELETE_TARGET))
            } finally {
                database.close()
            }
        }

        suspend fun addThird(seedProfile: Boolean = true): ByteArray {
            val thirdCentral = coreCryptoCentral("$root/third-mls", KEY)
            try {
                val third = mlsClient(thirdCentral, THIRD, "112233", scope)
                third.initializeBasicCredential()
                val keyPackage = third.transaction { it.generateKeyPackages(1).single() }
                checkNotNull(senderMLS).transaction { it.addMember(GROUP_ID, listOf(keyPackage)) }
            } finally {
                thirdCentral.close()
            }
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")),
                SELF.toDao(),
                databaseSecret,
                Dispatchers.Default
            )
            try {
                if (seedProfile) database.userDAO.upsertUser(user(THIRD))
                database.conversationDAO.updateConversationType(CONVERSATION_ID, ConversationEntity.Type.GROUP)
            } finally { database.close() }
            return checkNotNull(transport.bundle).commit
        }

        suspend fun membershipEvent(pending: Boolean): EventResponseToStore {
            val events = mutableListOf<EventResponseToStore>()
            if (mlsEnabled) {
                val addition = addThird(seedProfile = false)
                events += memberJoin()
                events += event("addition", addition)
                events += event("proposal", Base64.decode(REMOVE_THIRD_PROPOSAL))
                if (!pending) {
                    val removal = commitRemovalProposal()
                    events += memberLeave()
                    events += event("removal", removal)
                }
            } else {
                val database = userDatabaseBuilder(
                    PlatformDatabaseData(StorageData.FileBacked("$account/storage")),
                    SELF.toDao(),
                    databaseSecret,
                    Dispatchers.Default
                )
                try {
                    database.conversationDAO.updateConversationType(CONVERSATION_ID, ConversationEntity.Type.GROUP)
                } finally { database.close() }
                events += memberJoin()
                events += memberLeave()
            }
            sql("UPDATE Conversation SET legal_hold_status = 'ENABLED'")
            events += configEvent("legalhold")
            events += event("text", encrypt("first", legalHold = Conversation.LegalHoldStatus.ENABLED))
            return batch("notification", *events.toTypedArray())
        }

        suspend fun observeRemovalProposal() {
            checkNotNull(senderMLS).transaction {
                assertIs<MLSDecryptResult.Success>(it.decryptMessage(GROUP_ID, Base64.decode(REMOVE_THIRD_PROPOSAL)))
            }
        }

        suspend fun commitRemovalProposal(): ByteArray {
            observeRemovalProposal()
            checkNotNull(senderMLS).transaction { it.commitPendingProposals(GROUP_ID) }
            return checkNotNull(transport.bundle).commit
        }

        fun memberJoin(id: String = "join", member: UserId = THIRD) = plainEvent(
            id,
            """{"type":"conversation.member-join","qualified_conversation":{"id":"conversation","domain":"example.com"},
            "qualified_from":{"id":"${SENDER.value}","domain":"example.com"},"from":"${SENDER.value}","time":"$TIME",
            "data":{"user_ids":["${member.value}"],"users":[{"id":"${member.value}","qualified_id":{"id":"${member.value}","domain":"${member.domain}"},"conversation_role":"wire_member"}]}}"""
        )

        fun memberLeave(id: String = "leave", member: UserId = THIRD, reason: String = "removed",
            target: QualifiedIDEntity = CONVERSATION_ID) = plainEvent(
            id,
            """{"type":"conversation.member-leave","qualified_conversation":{"id":"${target.value}","domain":"${target.domain}"},
            "qualified_from":{"id":"${SENDER.value}","domain":"example.com"},"from":"${SENDER.value}","time":"$TIME",
            "data":{"qualified_user_ids":[{"id":"${member.value}","domain":"example.com"}],"reason":"$reason"}}"""
        )

        suspend fun ignoredMLSCiphertext(old: Boolean): ByteArray {
            val receiving = checkNotNull(receiverMLS)
            if (old) {
                val message = encrypt("old")
                // CoreCrypto 10.4 retains three previous epochs.
                repeat(4) {
                    val commit = commit()
                    receiving.transaction { it.decryptMessage(GROUP_ID, commit) }
                }
                sql("UPDATE Conversation SET mls_epoch = 5")
                return message
            }
            // Retain an own commit whose local transaction rolled back. The native
            // group is at that epoch but has no pending commit left to merge.
            var rolledBack = false
            try {
                receiving.transaction<Unit> {
                    it.updateKeyingMaterial(GROUP_ID)
                    error("Discard fixture own commit")
                }
            } catch (_: Exception) { rolledBack = true }
            check(rolledBack)
            return checkNotNull(transport.bundle).commit
        }

        suspend fun commit(): ByteArray {
            checkNotNull(senderMLS).transaction { it.updateKeyingMaterial(GROUP_ID) }
            return checkNotNull(transport.bundle).commit
        }

        suspend fun prepareRejoinWelcome(): ByteArray {
            // All key material is generated by preparation, before releasing the account to NSE.
            val keyPackage = checkNotNull(receiverMLS).transaction { it.generateKeyPackages(1).single() }
            val sending = checkNotNull(senderMLS)
            sending.transaction { it.removeMember(GROUP_ID, listOf(CryptoQualifiedClientId(CLIENT, SELF.toCrypto()))) }
            val removal = checkNotNull(transport.bundle).commit
            sending.transaction { it.addMember(GROUP_ID, listOf(keyPackage)) }
            welcome = checkNotNull(transport.bundle?.welcome)
            sql("UPDATE Conversation SET type = 'GROUP', verification_status = 'DEGRADED'")
            return removal
        }

        suspend fun prepareResetWelcome() {
            val keyPackage = checkNotNull(receiverMLS).transaction { it.generateKeyPackages(1).single() }
            checkNotNull(senderMLS).transaction {
                it.createConversation(RESET_GROUP_ID, Base64.decode(PROPOSAL_PUBLIC_KEY))
                it.addMember(RESET_GROUP_ID, listOf(keyPackage))
            }
            welcome = checkNotNull(transport.bundle?.welcome)
            sql("UPDATE Conversation SET type = 'GROUP', verification_status = 'DEGRADED'")
        }

        fun resetEvent(id: String = "reset") = plainEvent(id,
            """{"type":"conversation.mls-reset","qualified_conversation":{"id":"conversation","domain":"example.com"},
            "qualified_from":{"id":"${SENDER.value}","domain":"example.com"},
            "data":{"group_id":"$GROUP_ID","new_group_id":"$RESET_GROUP_ID"}}"""
        )

        fun protocolEvent(protocol: ConvProtocol) = plainEvent("protocol-${protocol.name}",
            """{"type":"conversation.protocol-update","qualified_conversation":{"id":"conversation","domain":"example.com"},
            "qualified_from":{"id":"${SENDER.value}","domain":"example.com"},"data":{"protocol":"${protocol.name.lowercase()}"}}"""
        )

        suspend fun migrationEvent(creation: Boolean = false) = batch("notification", *listOfNotNull(
            creationEvent(response = creationResponse().copy(protocol = ConvProtocol.PROTEUS, groupId = null)).takeIf { creation },
            event("proteus-text", encrypt("proteus-first", protocolMLS = false), protocolMLS = false),
            protocolEvent(ConvProtocol.MIXED),
            welcomeEvent(),
            event("first-text", encrypt("first")),
            protocolEvent(ConvProtocol.MLS),
            event("final-text", encrypt("final")),
        ).toTypedArray())

        suspend fun missingSessionSequence(): Pair<ByteArray, ByteArray> {
            receiverPrekey = receiverProteus.newLastResortPreKey()
            val prekey = encrypt("prekey", protocolMLS = false)
            val receiverSession = CryptoSessionId(senderId.toCrypto(), CryptoClientId(SENDER_CLIENT))
            val senderSession = CryptoSessionId(SELF.toCrypto(), CryptoClientId(CLIENT))
            val reply = receiverProteus.transaction("fixture-receive-prekey") { context ->
                context.decryptMessage(receiverSession, prekey) { it }
                context.encrypt("ack".encodeToByteArray(), receiverSession)
            }
            senderProteus.transaction("fixture-ack-prekey") { context -> context.decryptMessage(senderSession, reply) { it } }
            val message = encrypt("recoverable", protocolMLS = false)
            receiverProteus.transaction("fixture-lose-session") { it.deleteSession(receiverSession) }
            return prekey to message
        }

        suspend fun encrypt(text: String, content: MessageContent.FromProto = MessageContent.Text(text), expiresAfterMillis: Long? = null,
            legalHold: Conversation.LegalHoldStatus = Conversation.LegalHoldStatus.DISABLED, protocolMLS: Boolean = mlsEnabled,
            groupId: String = GROUP_ID,
        ): ByteArray {
            val bytes = ProtoContentMapperImpl(selfUserId = senderId).encodeToProtobuf(
                ProtoContent.Readable(text, content, false, legalHold, expiresAfterMillis)
            ).data
            return encryptBytes(bytes, protocolMLS, groupId)
        }

        suspend fun encryptBytes(bytes: ByteArray, protocolMLS: Boolean = mlsEnabled, groupId: String = GROUP_ID): ByteArray =
            if (protocolMLS) checkNotNull(senderMLS).transaction { it.encryptMessage(groupId, bytes) } else {
                senderProteus.transaction("fixture-encrypt") {
                    if (firstProteus) {
                        firstProteus = false
                        it.encryptWithPreKey(bytes, checkNotNull(receiverPrekey), CryptoSessionId(SELF.toCrypto(), CryptoClientId(CLIENT)))
                    } else it.encrypt(bytes, CryptoSessionId(SELF.toCrypto(), CryptoClientId(CLIENT)))
                }
            }

        val encryptedAsset: ByteArray = assetData ?: com.wire.kalium.cryptography.utils.encryptDataWithAES256(
            com.wire.kalium.cryptography.utils.PlainData(ASSET_TEXT.encodeToByteArray()),
            com.wire.kalium.cryptography.utils.AES256Key(KEY)).data
        private val assetHash = checkNotNull(com.wire.kalium.cryptography.utils.calcFileSHA256(okio.Buffer().write(encryptedAsset)))

        fun assetContent(id: String, name: String? = "$id.txt", mime: String = "text/plain", complete: Boolean = true,
            size: Long = ASSET_TEXT.encodeToByteArray().size.toLong(),
            metadata: com.wire.kalium.logic.data.message.AssetContent.AssetMetadata? = null,
        ) = MessageContent.Asset(com.wire.kalium.logic.data.message.AssetContent(
            size, name, mime, metadata, com.wire.kalium.logic.data.message.AssetContent.RemoteData(
                if (complete) KEY else byteArrayOf(), if (complete) assetHash else byteArrayOf(), if (complete) id else "",
                if (complete) "token-$id" else null, if (complete) SELF.domain else null,
                if (complete) com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm.AES_CBC else null)))

        suspend fun assetEvent(): EventResponseToStore = batch("notification", configEvent("fileSharing"),
            event("preview", encrypt("asset", assetContent("asset", complete = false))),
            event("keys", encrypt("asset", assetContent("asset", name = null, mime = "*/*", size = 0))),
            event("incomplete-repeat", encrypt("asset", assetContent("asset", name = "wrong.txt", complete = false))),
            event("image", encrypt("image", assetContent("image", mime = "image/png", metadata =
                com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image(640, 480)))),
            event("audio", encrypt("audio", assetContent("audio", mime = "audio/mpeg", metadata =
                com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio(1234, byteArrayOf(1, 2, 3))))),
            event("video", encrypt("video", assetContent("video", mime = "video/mp4", metadata =
                com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video(1280, 720, 12000)))),
            event("hidden", encrypt("hidden", assetContent("hidden", complete = false))),
            event("image-preview", encrypt("image-preview", assetContent("image-preview", mime = "image/png", complete = false,
                metadata = com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image(20, 30)))),
            event("text", encrypt("first")),
            event("last-image", encrypt("last-image", assetContent("last-image", mime = "image/png", metadata =
                com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image(64, 48)))))

        suspend fun verifyAssets(runtime: AppleAccountRuntime, includeTail: Boolean = false) {
            val db = runtime.database
            for (id in listOf("asset", "image", "audio", "video", "last-image")) {
                val stored = assertIs<MessageEntity.Regular>(db.messageDAO.getMessageById(id, CONVERSATION_ID))
                val asset = assertIs<MessageEntityContent.Asset>(stored.content)
                assertEquals(MessageEntity.Visibility.VISIBLE, stored.visibility)
                assertEquals("$id.txt", asset.assetName)
                assertEquals(ASSET_TEXT.encodeToByteArray().size.toLong(), asset.assetSizeInBytes)
                assertEquals(id, asset.assetId)
                assertEquals("token-$id", asset.assetToken)
                assertEquals(SELF.domain, asset.assetDomain)
                assertContentEquals(KEY, asset.assetOtrKey)
                assertContentEquals(assetHash, asset.assetSha256Key)
                assertEquals("AES_CBC", asset.assetEncryptionAlgorithm)
                assertEquals(TIME, stored.date)
            }
            val image = assertIs<MessageEntityContent.Asset>(checkNotNull(db.messageDAO.getMessageById("image", CONVERSATION_ID)).content)
            assertEquals(640, image.assetWidth)
            assertEquals(480, image.assetHeight)
            val video = assertIs<MessageEntityContent.Asset>(checkNotNull(db.messageDAO.getMessageById("video", CONVERSATION_ID)).content)
            assertEquals(12000L, video.assetDurationMs)
            val audio = assertIs<MessageEntityContent.Asset>(checkNotNull(db.messageDAO.getMessageById("audio", CONVERSATION_ID)).content)
            assertEquals(1234L, audio.assetDurationMs)
            assertContentEquals(byteArrayOf(1, 2, 3), audio.assetNormalizedLoudness)
            assertEquals(MessageEntity.Visibility.HIDDEN, db.messageDAO.getMessageById("hidden", CONVERSATION_ID)?.visibility)
            assertEquals(MessageEntity.Visibility.VISIBLE, db.messageDAO.getMessageById("image-preview", CONVERSATION_ID)?.visibility)
            assertEquals(if (senderId == SELF) emptySet() else ASSET_MESSAGE_IDS.toSet() + if (includeTail) setOf("tail") else emptySet(),
                db.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20).map { it.messageId }.toSet())
        }

        suspend fun signalingEvent(): EventResponseToStore {
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default)
            try {
                database.userConfigDAO.setTrackingIdentifier("initial")
                database.userConfigDAO.setPreviousTrackingIdentifier("earlier")
            } finally { database.close() }
            val historyClient = com.wire.kalium.logic.data.history.HistoryClient("history-client", TIME,
                com.wire.kalium.logic.data.history.HistoryClient.Secret(byteArrayOf(1, 2, 3)))
            val signals = listOf(
                MessageContent.History.ClientsRequest,
                MessageContent.History.ClientsResponse(listOf(historyClient)),
                MessageContent.History.NewClientAvailable(historyClient),
                MessageContent.DataTransfer(),
                MessageContent.DataTransfer(MessageContent.DataTransfer.TrackingIdentifier("initial")),
                MessageContent.DataTransfer(MessageContent.DataTransfer.TrackingIdentifier("middle")),
                MessageContent.DataTransfer(MessageContent.DataTransfer.TrackingIdentifier("received")),
                MessageContent.DataTransfer(MessageContent.DataTransfer.TrackingIdentifier("received")),
            )
            return batch("notification", *(listOf(
                event("reset", encrypt("session-reset", MessageContent.ClientAction), TIME + 5.seconds)
            ) + signals.mapIndexed { index, content -> event("signal-$index", encrypt("signal-$index", content)) } +
                event("text", encrypt("first"))).toTypedArray())
        }

        suspend fun verifySignals(runtime: AppleAccountRuntime, includeTail: Boolean = false) {
            val db = runtime.database
            val reset = assertIs<MessageEntity.System>(db.messageDAO.getMessageById("session-reset", CONVERSATION_ID))
            assertEquals(MessageEntityContent.CryptoSessionReset, reset.content)
            assertEquals(senderId.toDao(), reset.senderUserId)
            assertEquals(TIME + 5.seconds, reset.date)
            assertEquals(MessageEntity.Status.SENT, reset.status)
            assertNull(reset.expireAfterMs)
            assertEquals(if (senderId == SELF) "received" else "initial", db.userConfigDAO.getTrackingIdentifier())
            assertEquals(if (senderId == SELF) "middle" else "earlier", db.userConfigDAO.getPreviousTrackingIdentifier())
            for (index in 0..7) assertNull(db.messageDAO.getMessageById("signal-$index", CONVERSATION_ID))
            assertEquals(if (senderId == SELF) 0L else if (includeTail) 2L else 1L, db.messageDAO.getConversationUnreadEventsCount(CONVERSATION_ID))
            assertEquals(if (includeTail) TIME + 1.seconds else TIME, checkNotNull(db.conversationDAO.getConversationById(CONVERSATION_ID)).lastModifiedDate)
            assertEquals(if (senderId == SELF) emptyList() else if (includeTail) listOf("first", "tail") else listOf("first"),
                db.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
        }

        suspend fun compositeEvent(): EventResponseToStore = batch("notification",
            event("composite", encrypt("composite", MessageContent.Composite(MessageContent.Text("original"),
                listOf(com.wire.kalium.logic.data.message.composite.Button("first", "first", false))))),
            event("selected", encrypt("selected", MessageContent.ButtonActionConfirmation("composite", "first"))),
            event("reset", encrypt("reset", MessageContent.ButtonActionConfirmation("composite", null))),
            event("edit", encryptCompositeEdit("composite-edited", "composite", "replacement", listOf("first", "second"))),
            // A button-only edit must still rekey the message, even if its timestamp is older.
            event("buttons-edit", encryptCompositeEdit("buttons-edited", "composite-edited", null, listOf("second", "third")), TIME - 1.seconds),
            event("unknown-button", encrypt("unknown-button", MessageContent.ButtonActionConfirmation("buttons-edited", "missing"))),
            event("final-selection", encrypt("final-selection", MessageContent.ButtonActionConfirmation("buttons-edited", "second"))),
            // Actions from another user do not select a button on this device.
            event("other-action", encrypt("other-action", MessageContent.ButtonAction("buttons-edited", "third"))))

        suspend fun encryptCompositeEdit(id: String, original: String, text: String?, buttons: List<String>): ByteArray {
            val items = listOfNotNull(text?.let {
                com.wire.kalium.protobuf.messages.Composite.Item(
                    com.wire.kalium.protobuf.messages.Composite.Item.Content.Text(com.wire.kalium.protobuf.messages.Text(it)))
            }) + buttons.map {
                com.wire.kalium.protobuf.messages.Composite.Item(
                    com.wire.kalium.protobuf.messages.Composite.Item.Content.Button(com.wire.kalium.protobuf.messages.Button(it, it)))
            }
            return encryptBytes(GenericMessage(messageId = id, content = GenericMessage.Content.Edited(
                com.wire.kalium.protobuf.messages.MessageEdit(original,
                    com.wire.kalium.protobuf.messages.MessageEdit.Content.Composite(com.wire.kalium.protobuf.messages.Composite(items)))
            )).encodeToByteArray())
        }

        suspend fun regularContentEvent(): EventResponseToStore = batch("notification",
            event("knock", encrypt("knock", MessageContent.Knock(true))),
            event("unknown", encryptUnknown("unknown", GenericMessage.UnknownStrategy.WARN_USER_ALLOW_RETRY)),
            event("ignored", encryptUnknown("ignored", GenericMessage.UnknownStrategy.IGNORE)),
            event("text", encrypt("first")),
            event("location", encrypt("location", MessageContent.Location(52f, 13f, "Berlin", 10))))

        suspend fun encryptUnknown(id: String, strategy: GenericMessage.UnknownStrategy): ByteArray =
            encryptBytes(GenericMessage(messageId = id, unknownStrategy = strategy).encodeToByteArray())

        suspend fun releasePrepared() {
            receiverMLSCentral?.close()
            receiver.close()
            ApplePreparedAccountStore(root, SELF).publish(checkNotNull(lease), PreparedAccount(
                SELF, CLIENT, ServerConfig(
                    "test",
                    // Ktor's legacy socket uses the WSS default port on the endpoint's host and path.
                    apiEndpoint?.let { ServerConfig.DUMMY.copy(api = it, webSocket = it.replaceFirst(LOCAL_API, "wss://localhost")) }
                        ?: ServerConfig.DUMMY,
                    ServerConfig.MetaData(false, CommonApiVersionType.Valid(apiVersion), SELF.domain)
                ),
                if (mlsEnabled) CIPHER else null,
            ))
            lease?.release()
            lease = null
        }

        fun event(id: String, bytes: ByteArray, time: Instant = TIME, protocolMLS: Boolean = mlsEnabled,
            conversationId: QualifiedIDEntity = CONVERSATION_ID,
        ): EventResponseToStore {
            val content: EventContentDTO = if (protocolMLS) EventContentDTO.Conversation.NewMLSMessageDTO(
                conversationId.toApi(), SELF.toApi(), time, Base64.encode(bytes), null // claimed sender deliberately wrong; MLS authenticates it
            ) else EventContentDTO.Conversation.NewMessageDTO(
                conversationId.toApi(), senderId.toApi(), time = time,
                data = MessageEventData(Base64.encode(bytes), SENDER_CLIENT, CLIENT),
            )
            return EventResponseToStore(id, KtxSerializer.json.encodeToString(listOf(content)), false)
        }

        fun unknownEvent(id: String, type: String) = EventResponseToStore(
            id,
            JsonArray(listOf(JsonObject(mapOf("type" to JsonPrimitive(type))))).toString(),
            false,
        )

        fun metadataEvents(target: QualifiedIDEntity = CONVERSATION_ID): List<EventResponseToStore> = listOf(
            EventContentDTO.Conversation.ConversationRenameDTO(
                target.toApi(), senderId.toApi(), TIME,
                com.wire.kalium.network.api.authenticated.conversation.ConversationNameUpdateEvent("Renamed")
            ),
            EventContentDTO.Conversation.ReceiptModeUpdate(
                target.toApi(),
                com.wire.kalium.network.api.authenticated.conversation.model.ConversationReceiptModeDTO(
                    com.wire.kalium.network.api.authenticated.conversation.ReceiptMode.ENABLED
                ), senderId.toApi()
            ),
            EventContentDTO.Conversation.MessageTimerUpdate(
                target.toApi(),
                com.wire.kalium.network.api.authenticated.conversation.messagetimer.ConversationMessageTimerDTO(45_000),
                senderId.toApi(), TIME
            )
        ).mapIndexed { index, event ->
            EventResponseToStore("metadata-$index", KtxSerializer.json.encodeToString(listOf(event)), false)
        }

        fun batch(id: String, vararg events: EventResponseToStore) = EventResponseToStore(
            id, KtxSerializer.json.encodeToString(events.flatMap { it.toEventResponse().payload.orEmpty() }), false
        )

        suspend fun receive(
            page: List<EventResponseToStore>,
            target: String,
            interruption: Interruption? = null,
            commitApi: com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi? = null,
            decorateMLSContext: (MlsCoreCryptoContext) -> MlsCoreCryptoContext = { it },
            decorateProteusContext: (ProteusCoreCryptoContext) -> ProteusCoreCryptoContext = { it },
            fetchConversation: (suspend () -> ConversationResponse)? = null,
            fetchProfiles: (suspend () -> ListUsersDTO)? = null,
            fetchConnectionPeer: (suspend (com.wire.kalium.network.api.model.UserId) -> com.wire.kalium.network.api.model.UserProfileDTO)? = null,
            fetchTeamMembers: (suspend () -> com.wire.kalium.network.api.authenticated.teams.TeamMemberListNonPaginated)? = null,
            fetchMeeting: (suspend (com.wire.kalium.network.api.model.MeetingId) ->
                com.wire.kalium.network.api.authenticated.meeting.MeetingDTO)? = null,
            receivingAPIVersion: Int = 8,
            fetchSelfClients: (suspend () -> List<com.wire.kalium.network.api.authenticated.client.ClientDTO>)? = null,
            uploadSelfProtocols: (suspend (List<com.wire.kalium.network.api.model.SupportedProtocolDTO>) -> Unit)? = null,
            fetchProfilesRequest: (suspend (ListUserRequest) -> ListUsersDTO)? = null,
            refreshSession: ((AppleAccountRuntime) -> SessionRefreshSuggestedEventHandler)? = null,
            allowedFileExtensions: List<String>? = null,
            linkPreviewEnabled: Boolean = false,
            downloadPreview: (suspend (okio.Sink) -> Unit)? = null,
            deleteFile: (String) -> Unit = { FileSystem.SYSTEM.delete(it.toPath(), mustExist = false) },
            hasCredential: (suspend (com.wire.kalium.cryptography.MLSCiphersuite) -> Boolean)? = null,
            transactionCheckpoint: suspend (String) -> Unit = {},
            diagnostics: ReceivingDiagnostics? = null,
            recoveryFactory: ((AppleAccountRuntime) -> MLSReceiveRecovery)? = null,
            nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
            mlsTransport: MLSTransporter? = null,
            toLive: () -> Boolean = { false },
        ) = open { runtime ->
            // Catch-up owns network fetching. These native tests start from the stored queue;
            // NotificationCatchUpTest and the Swift harness exercise the socket/transport boundary.
            val store = runtime.database.notificationEventStore
            val unseen = page.filter { runtime.database.eventDAO.getEventById(it.id) == null }
            if (unseen.isNotEmpty()) {
                store.storePage(store.cursor(), unseen.map { NewEventEntity(it.id, it.payload, it.transient, false) })
            }
            if (interruption in listOf(Interruption.DELIVERY, Interruption.MARKER, Interruption.SECOND_MESSAGE)) {
                val trigger = when (interruption) {
                    Interruption.DELIVERY -> "INSERT ON PendingDeliveryConfirmations"
                    Interruption.SECOND_MESSAGE -> "INSERT ON MessageTextContent WHEN NEW.message_id = 'quoted'"
                    else -> "UPDATE ON Events WHEN NEW.is_processed = 1"
                }
                sql("CREATE TRIGGER interrupt_receive BEFORE $trigger BEGIN SELECT RAISE(ABORT, 'interrupted'); END")
            }
            val realProteus = runtime.proteusCoreCrypto.proteusClient()
            val proteus = object : ProteusClient by realProteus {
                override suspend fun <R> transaction(name: String, block: suspend (ProteusCoreCryptoContext) -> R): R =
                    realProteus.transaction(name) { context ->
                        block(decorateProteusContext(context)).also { if (name == "nse-notification") interruption?.beforeCommit() }
                    }.also { if (name == "nse-notification") interruption?.afterCommit() }
            }
            val conversationApi = object : ConversationApi by runtime.network.conversationApi {
                override suspend fun fetchConversationDetails(conversationId: com.wire.kalium.network.api.model.ConversationId) = try {
                    if (fetchConversation == null) NetworkResponse.Error(KaliumException.NoNetwork())
                    else NetworkResponse.Success(fetchConversation(), emptyMap(), 200)
                } catch (failure: KaliumException) { NetworkResponse.Error(failure) }
                override suspend fun fetchGroupInfo(conversationId: com.wire.kalium.network.api.model.ConversationId) =
                    NetworkResponse.Error(KaliumException.NoNetwork())
            }
            val rejoin = ReceivedMLSRejoin(commitApi ?: CommitApi(), runtime.database.metadataDAO, diagnostics)
            val mls = (mlsTransport?.let { openMLS(runtime, it) } ?: runtime.mlsClient(rejoin))?.let { realMLS ->
                object : MLSClient by realMLS {
                    override suspend fun <R> transaction(name: String, block: suspend (MlsCoreCryptoContext) -> R): R =
                        realMLS.transaction(name) { context ->
                            block(decorateMLSContext(context)).also {
                                if (name == "nse-local-conversation-cleanup") transactionCheckpoint("cleanup-before-commit")
                                if (name == "nse-notification") {
                                    transactionCheckpoint("before-commit")
                                    interruption?.beforeCommit()
                                }
                            }
                        }.also {
                            if (name == "nse-local-conversation-cleanup") transactionCheckpoint("cleanup-after-commit")
                            if (name == "nse-notification") {
                                transactionCheckpoint("after-commit")
                                interruption?.afterCommit()
                            }
                        }
                }
            }
            NotificationProcessor(
                runtime.database, CLIENT, SELF, proteus, mls,
                diagnostics = diagnostics,
                invalidateReceiving = { runtime.invalidateReceiving() },
                nowEpochMillis = nowEpochMillis,
                recovery = if (mls == null) null else recoveryFactory?.invoke(runtime) ?: MLSReceiveRecovery(
                    MLSReceiveRecoveryStore(runtime.database.metadataDAO, runtime.database.notificationEventStore, CLIENT),
                    conversationApi, SELF, rejoin::join,
                ),
                evaluateCalls = { events ->
                    receivedCallEvents = events
                    evaluateCallingEvents(events, SELF, CLIENT, runtime.database)
                },
                serverConfigLinks = runtime.prepared.serverConfig.links,
                sessionRefresh = refreshSession?.invoke(runtime),
                apiVersion = receivingAPIVersion,
                hasMLSCredential = hasCredential ?: { cipher ->
                    checkNotNull(runtime.mlsCoreCrypto).hasCredential(CryptoQualifiedClientId(CLIENT, SELF.toCrypto()), cipher)
                },
                selfApi = object : com.wire.kalium.network.api.base.authenticated.self.SelfApi by runtime.network.selfApi {
                    override suspend fun updateSupportedProtocols(protocols: List<com.wire.kalium.network.api.model.SupportedProtocolDTO>) = try {
                        if (uploadSelfProtocols == null) NetworkResponse.Error(KaliumException.NoNetwork())
                        else { uploadSelfProtocols(protocols); NetworkResponse.Success(Unit, emptyMap(), 200) }
                    } catch (failure: KaliumException) { NetworkResponse.Error(failure) }
                },
                clientApi = object : com.wire.kalium.network.api.base.authenticated.client.ClientApi by runtime.network.clientApi {
                    override suspend fun fetchSelfUserClient() = try {
                        if (fetchSelfClients == null) NetworkResponse.Error(KaliumException.NoNetwork())
                        else NetworkResponse.Success(fetchSelfClients(), emptyMap(), 200)
                    } catch (failure: KaliumException) { NetworkResponse.Error(failure) }
                },
                deleteFile = deleteFile,
                assetDirectory = runtime.assetDirectory,
                assetApi = object : com.wire.kalium.network.api.base.authenticated.asset.AssetApi by runtime.network.assetApi {
                    override suspend fun downloadAsset(assetId: String, assetDomain: String?, assetToken: String?, tempFileSink: okio.Sink)
                        : NetworkResponse<Unit> {
                        assertEquals("preview-image", assetId)
                        assertEquals(SELF.domain, assetDomain)
                        assertEquals("token-preview-image", assetToken)
                        if (downloadPreview == null) return NetworkResponse.Error(KaliumException.NoNetwork())
                        downloadPreview(tempFileSink)
                        return NetworkResponse.Success(Unit, emptyMap(), 200)
                    }
                },
                options = ReceivingOptions(linkPreviewEnabled = linkPreviewEnabled, fileRestrictionState = allowedFileExtensions?.let {
                    com.wire.kalium.logic.featureFlags.BuildFileRestrictionState.AllowSome(it)
                } ?: com.wire.kalium.logic.featureFlags.BuildFileRestrictionState.NoRestriction),
                conversationApi = conversationApi,
                meetingApi = object : com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi by runtime.network.meetingApi {
                    override suspend fun fetchMeeting(meetingId: com.wire.kalium.network.api.model.MeetingId) = try {
                        if (fetchMeeting == null) NetworkResponse.Error(KaliumException.NoNetwork())
                        else NetworkResponse.Success(fetchMeeting(meetingId), emptyMap(), 200)
                    } catch (failure: KaliumException) { NetworkResponse.Error(failure) }
                },
                teamsApi = object : com.wire.kalium.network.api.base.authenticated.TeamsApi by runtime.network.teamsApi {
                    override suspend fun getTeamMembersByIds(teamId: String,
                        teamMemberIdList: com.wire.kalium.network.api.authenticated.teams.TeamMemberIdList) = try {
                        if (fetchTeamMembers == null) NetworkResponse.Error(KaliumException.NoNetwork())
                        else NetworkResponse.Success(fetchTeamMembers(), emptyMap(), 200)
                    } catch (failure: KaliumException) { NetworkResponse.Error(failure) }
                },
                userDetailsApi = object : UserDetailsApi by runtime.network.userDetailsApi {
                    override suspend fun getUserInfo(userId: com.wire.kalium.network.api.model.UserId) = try {
                        if (fetchConnectionPeer == null) NetworkResponse.Error(KaliumException.NoNetwork())
                        else NetworkResponse.Success(fetchConnectionPeer(userId), emptyMap(), 200)
                    } catch (failure: KaliumException) { NetworkResponse.Error(failure) }
                    override suspend fun getMultipleUsers(users: ListUserRequest) = try {
                        if (fetchProfilesRequest != null) NetworkResponse.Success(fetchProfilesRequest(users), emptyMap(), 200)
                        else if (fetchProfiles == null) NetworkResponse.Error(KaliumException.NoNetwork())
                        else NetworkResponse.Success(fetchProfiles(), emptyMap(), 200)
                    } catch (failure: KaliumException) { NetworkResponse.Error(failure) }
                },
            ).let { processor ->
                processor.processToLive(target, toLive)
            }.also { receivedCalls = it.calls; receivedAppWork = it.appWorkPending; receivedStop = it.stoppedReason }.message
        }

        suspend fun decryptAndCheck(cipher: ByteArray, value: String, protocolMLS: Boolean = mlsEnabled) = open { runtime ->
            val plain = if (protocolMLS) checkNotNull(openMLS(runtime)).transaction { context ->
                val decrypted = assertIs<MLSDecryptResult.Success>(context.decryptMessage(GROUP_ID, cipher))
                assertIs<DecryptedMessageBundle.Text>(decrypted.messages.single()).message
            } else runtime.proteusCoreCrypto.proteusClient().transaction("fixture-decrypt") { context ->
                context.decryptMessage(CryptoSessionId(senderId.toCrypto(), CryptoClientId(SENDER_CLIENT)), cipher) { it }
            }
            val decoded = assertIs<ProtoContent.Readable>(ProtoContentMapperImpl(selfUserId = SELF).decodeFromProtobuf(
                com.wire.kalium.logic.data.message.PlainMessageBlob(plain)
            ))
            assertEquals(value, (decoded.messageContent as MessageContent.Text).value)
        }

        suspend fun <T> open(block: suspend (AppleAccountRuntime) -> T): Either<AccountRuntimeFailure, T> {
            var assertionFailure: AssertionError? = null
            val result =
            AppleAccountRuntime.use(root, SELF, config, "receiver-fixture", emptyMap()) { runtime ->
                try {
                    block(runtime)
                } catch (failure: Throwable) {
                    if (failure is AssertionError) assertionFailure = failure
                    else println("NSE_FIXTURE_FAILURE: " + if (failure is com.wire.kalium.logic.sync.KaliumSyncException) {
                        failure.coreFailureCause.toString()
                    } else failure.toString())
                    throw failure
                }
            }
            assertionFailure?.let { throw it }
            return result
        }

        /** Test-only failure injection connection; caller already holds the account runtime lease. */
        fun sql(statement: String) {
            val driver = databaseDriver("$account/storage", FileNameUtil.userDBName(SELF.toDao()), UserDatabase.Schema.synchronous(), databaseSecret.value) {
                isWALEnabled = true
                useGradleSafeSqliterLogging = true
            }
            try {
                driver.execute(null, statement, 0)
            } finally {
                driver.close()
            }
        }

        fun sqlValue(statement: String): String? {
            val driver = databaseDriver("$account/storage", FileNameUtil.userDBName(SELF.toDao()), UserDatabase.Schema.synchronous(), databaseSecret.value) {
                isWALEnabled = true
                useGradleSafeSqliterLogging = true
            }
            try {
                return driver.executeQuery(null, statement, { cursor ->
                    app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
                }, 0).value
            } finally {
                driver.close()
            }
        }

        suspend fun openMLS(runtime: AppleAccountRuntime, transporter: MLSTransporter = transport): MLSClient? = runtime.mlsCoreCrypto?.let {
            mlsClient(it, SELF, CLIENT, runtime.processingScope, transporter, checkNotNull(runtime.mlsCipherSuite))
        }

        private suspend fun mlsClient(central: CoreCryptoCentral, user: UserId, client: String, owner: CoroutineScope,
            transporter: MLSTransporter = transport, cipher: CipherSuite = CIPHER) = central.mlsClient(
            CryptoQualifiedClientId(client, user.toCrypto()),
            cipher.toCrypto(),
            transporter,
            object : MLSEpochObserver {
                override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) = Unit
            }, owner,
        )

        suspend fun installChangedCipherCredential() {
            if (mlsEnabled) mlsClient(checkNotNull(receiverMLSCentral), SELF, CLIENT, scope,
                cipher = CipherSuite.fromTag(3)).initializeBasicCredential()
        }

        fun seedTyping(enabled: Boolean) = sql("INSERT OR REPLACE INTO Metadata(key, stringValue) VALUES ('enable_typing_indicator', '$enabled')")

        fun typingEvent(status: String, sender: UserId = SENDER) = plainEvent("typing",
            """{"type":"conversation.typing","qualified_conversation":{"id":"${DELETE_TARGET.value}","domain":"${DELETE_TARGET.domain}"},
            "qualified_from":{"id":"${sender.value}","domain":"${sender.domain}"},"from":"${sender.value}","time":"$TIME",
            "data":{"status":"$status"}}""")

        suspend fun typingNoopEvents() = batch("notification", event("prefix", encrypt("prefix")),
            typingEvent("started", SELF), typingEvent("stopped", SELF), propertyEvent("WIRE_TYPING_INDICATOR_MODE", 0),
            typingEvent("started"), propertyEvent("WIRE_TYPING_INDICATOR_MODE", 1), typingEvent("started", SELF), typingEvent("stopped", SELF),
            event("first", encrypt("first")))

        suspend fun verifyPendingTextQueue(event: EventResponseToStore, tail: EventResponseToStore) {
            assertIs<Either.Right<*>>(open { runtime ->
                assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                assertFalse(checkNotNull(runtime.database.eventDAO.getEventById(tail.id)).isProcessed)
                for (id in listOf("prefix", "first", "tail")) assertNull(runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID))
                assertTrue(runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
            })
        }

        fun memberRoleEvent(role: String?, member: UserId = SENDER, target: QualifiedIDEntity = CONVERSATION_ID,
            system: Boolean = false) = plainEvent("role",
            """{"type":"conversation.${if (system) "system." else ""}member-update",
            "qualified_conversation":{"id":"${target.value}","domain":"${target.domain}"},
            "qualified_from":${if (system) "null" else """{"id":"${SENDER.value}","domain":"${SENDER.domain}"}"""},
            ${if (system) "" else """"from":"${SENDER.value}","""}"time":"$TIME",
            "data":{"target":"${member.value}","qualified_target":{"id":"${member.value}","domain":"${member.domain}"},
            "conversation_role":${role?.let { "\"$it\"" } ?: "null"},"otr_muted_ref":null,"otr_muted_status":null,"otr_archived":null,"otr_archived_ref":null}}""")

        fun memberMutedEvent(target: QualifiedIDEntity = CONVERSATION_ID) = plainEvent("member-muted",
            """{"type":"conversation.member-update","qualified_conversation":{"id":"${target.value}","domain":"${target.domain}"},
            "qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},"from":"${SENDER.value}","time":"$TIME",
            "data":{"target":"${SELF.value}","qualified_target":{"id":"${SELF.value}","domain":"${SELF.domain}"},
            "conversation_role":null,"otr_muted_ref":null,"otr_muted_status":3,"otr_archived":null,"otr_archived_ref":null}}""")

        suspend fun memberRoleEvents(member: UserId = SENDER, target: QualifiedIDEntity = CONVERSATION_ID,
            system: Boolean = false) = batch("notification",
            event("prefix", encrypt("prefix")), memberRoleEvent("custom-role", member, target, system),
            memberRoleEvent("wire_admin", member, target, system), memberRoleEvent("wire_admin", member, target, system),
            memberRoleEvent("wire_member", member, target, system), memberRoleEvent("wire_admin", member, target, system),
            memberRoleEvent("custom-final", member, target, system), event("first", encrypt("first")))

        suspend fun verifyPromotion(runtime: AppleAccountRuntime, id: String, member: UserId, target: QualifiedIDEntity = CONVERSATION_ID) {
            val message = assertIs<MessageEntity.System>(runtime.database.messageDAO.getMessageById(id, target))
            assertEquals(MessageEntityContent.MemberChange(listOf(member.toDao()), MessageEntity.MemberChangeType.USER_PROMOTED_TO_ADMIN), message.content)
            assertEquals(SELF.toDao(), message.senderUserId)
            assertEquals(TIME, message.date)
            assertEquals(MessageEntity.Status.SENT, message.status)
            assertEquals(MessageEntity.Visibility.VISIBLE, message.visibility)
        }

        suspend fun verifyMemberRoles(runtime: AppleAccountRuntime, member: UserId = SENDER, target: QualifiedIDEntity = CONVERSATION_ID,
            absentMember: Boolean = false) {
            val role = runtime.database.memberDAO.getMemberRole(member.toDao(), target)
            if (absentMember) assertNull(role) else assertEquals(com.wire.kalium.persistence.dao.member.MemberEntity.Role.Unknown("custom-final"), role)
            val promotions = if (absentMember) listOf(2, 3, 5) else listOf(2, 5)
            for (index in 1..6) {
                if (index in promotions) verifyPromotion(runtime, "notification:$index", member, target)
                else assertNull(runtime.database.messageDAO.getMessageById("notification:$index", target))
            }
        }

        suspend fun seedAccess(enabled: Boolean) {
            val db = userDatabaseBuilder(PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default)
            try {
                db.conversationDAO.updateAccess(CONVERSATION_ID, emptyList(), if (enabled) listOf(ConversationEntity.AccessRole.SERVICE) else emptyList())
            } finally { db.close() }
        }

        fun accessEvent(enabled: Boolean, privateAccess: Boolean = false, target: QualifiedIDEntity = CONVERSATION_ID) = plainEvent("access",
            """{"type":"conversation.access-update","qualified_conversation":{"id":"${target.value}","domain":"${target.domain}"},
            "qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},"data":{"access":${if (privateAccess) "[\"private\"]" else "[\"code\",\"invite\"]"},
            "access_role_v2":["team_member","guest"${if (enabled) ",\"service\"" else ""}]}}""")

        suspend fun accessEvents(enabled: Boolean = true) = batch("notification", event("prefix", encrypt("prefix")), accessEvent(enabled),
            accessEvent(enabled, privateAccess = true), accessEvent(!enabled), accessEvent(enabled), event("first", encrypt("first")))

        suspend fun verifyAccess(runtime: AppleAccountRuntime, enabled: Boolean = true): List<MessageEntity> {
            val conversation = checkNotNull(runtime.database.conversationDAO.getConversationById(CONVERSATION_ID))
            assertEquals(listOf(ConversationEntity.Access.CODE, ConversationEntity.Access.INVITE), conversation.access)
            assertEquals(listOf(ConversationEntity.AccessRole.TEAM_MEMBER, ConversationEntity.AccessRole.GUEST) +
                if (enabled) listOf(ConversationEntity.AccessRole.SERVICE) else emptyList(), conversation.accessRole)
            assertNull(runtime.database.messageDAO.getMessageById("notification:2", CONVERSATION_ID))
            return listOf(1 to enabled, 3 to !enabled, 4 to enabled).map { (index, value) ->
                val message = assertIs<MessageEntity.System>(runtime.database.messageDAO.getMessageById("notification:$index", CONVERSATION_ID))
                assertEquals(MessageEntityContent.ConversationAppsAccessChanged(value), message.content)
                assertEquals(SENDER.toDao(), message.senderUserId)
                assertEquals(MessageEntity.Status.SENT, message.status)
                assertEquals(MessageEntity.Visibility.VISIBLE, message.visibility)
                message
            }
        }

        suspend fun seedRoomSettings(target: QualifiedIDEntity = CONVERSATION_ID, incomplete: Boolean = false) {
            if (target != CONVERSATION_ID) seedProteusConversation(target)
            sql("UPDATE Conversation SET type = 'CHANNEL', channel_access = 'PUBLIC', channel_add_permission = 'ADMINS', " +
                "guest_room_link = 'https://initial.example', is_guest_password_protected = 1, incomplete_metadata = ${if (incomplete) 1 else 0} " +
                "WHERE qualified_id = '$target'")
        }

        fun roomCodeEvent(id: String = "code", target: QualifiedIDEntity = CONVERSATION_ID, deleted: Boolean = false,
            uri: Boolean = false, password: Boolean = false) = plainEvent(id,
            """{"type":"conversation.code-${if (deleted) "delete" else "update"}","qualified_conversation":{"id":"${target.value}","domain":"${target.domain}"},
            "qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"}${if (deleted) "" else ",\"data\":{\"uri\":${if (uri) "\"https://invite.example/final?hello=world\"" else "null"},\"key\":\"room key/?&\",\"code\":\"code=+#\",\"has_password\":$password}"}}""")

        fun roomPermissionEvent(id: String = "permission", target: QualifiedIDEntity = CONVERSATION_ID,
            everyone: Boolean = true) = plainEvent(id,
            """{"type":"conversation.add-permission-update","conversation":"${target.value}","qualified_conversation":{"id":"${target.value}","domain":"${target.domain}"},
            "from":"${SENDER.value}","qualified_from":{"id":"${SENDER.value}","domain":"${SENDER.domain}"},"time":"$TIME",
            "data":{"add_permission":"${if (everyone) "everyone" else "admins"}"}}""")

        suspend fun roomSettingsEvents(final: String = "fallback", target: QualifiedIDEntity = CONVERSATION_ID): EventResponseToStore {
            val last = if (final == "fallback") emptyList() else listOf(roomCodeEvent("final-code", target,
                deleted = final == "delete", uri = final == "uri", password = true))
            return batch("notification", *(listOf(event("prefix", encrypt("prefix")), roomPermissionEvent(target = target),
                roomCodeEvent(target = target, uri = true, password = true), roomPermissionEvent("admins", target, everyone = false),
                roomCodeEvent("deleted-code", target, deleted = true), roomCodeEvent("fallback-code", target),
                roomPermissionEvent("everyone", target)) + last + event("first", encrypt("first"))).toTypedArray())
        }

        suspend fun verifyRoomSettings(runtime: AppleAccountRuntime, final: String = "fallback",
            target: QualifiedIDEntity = CONVERSATION_ID, absent: Boolean = false) {
            val db = runtime.database
            val conversation = db.conversationDAO.getConversationById(target)
            if (absent) assertNull(conversation) else assertEquals(ConversationEntity.ChannelAddPermission.EVERYONE,
                checkNotNull(conversation).channelAddPermission)
            val link = db.conversationDAO.observeGuestRoomLinkByConversationId(target).first()
            if (absent || final == "delete") assertNull(link) else {
                assertEquals(final == "uri", checkNotNull(link).isPasswordProtected)
                if (final == "uri") assertEquals("https://invite.example/final?hello=world", link.link) else {
                    val url = io.ktor.http.Url(link.link)
                    assertTrue(link.link.startsWith(ServerConfig.DUMMY.accounts))
                    assertEquals("room key/?&", url.parameters["key"])
                    assertEquals("code=+#", url.parameters["code"])
                }
            }
        }

        suspend fun seedRemoteMute(mode: String, own: Boolean) {
            if (mode != "non-admin" && (mode != "target" || own)) {
                sql("UPDATE Member SET role = 'wire_admin' WHERE user = '${senderId.toDao()}' AND conversation = '$CONVERSATION_ID'")
            }
            if (mode == "target") {
                seedProteusConversation()
                val role = if (own) "wire_member" else "wire_admin"
                sql("INSERT OR REPLACE INTO Member(user, conversation, role) VALUES ('${senderId.toDao()}', '$DELETE_TARGET', '$role')")
            }
        }

        fun remoteMute(mode: String = "non-admin"): MessageContent.Calling {
            val user = if (mode == "other-user") THIRD.value else SELF.value
            val client = if (mode == "other-client") "another-client" else CLIENT
            val domain = if (mode == "other-domain") "different.example" else SELF.domain
            val data = if (mode == "null") "" else ",\"data\":{\"targets\":{\"$domain\":{\"$user\":[\"$client\"]}}}"
            return MessageContent.Calling("{\"type\":\"REMOTEMUTE\"$data,\"extra\":true}",
                if (mode == "target") DELETE_TARGET.toModel() else null)
        }

        fun callContent(type: String = "GROUPSTART", session: String = "call"): MessageContent.Calling =
            MessageContent.Calling("""{"type":"$type","version":"3.0","sessid":"$session","resp":false}""")

        suspend fun callEventForSwift(): EventResponseToStore {
            // A separate real conversation gives the CallKit host its required UUID while the
            // existing app-reopen fixture continues to verify text in the original conversation.
            val callId = QualifiedIDEntity("11111111-1111-4111-8111-111111111111", SELF.domain)
            val callGroup = Base64.encode("swift-calling-group".encodeToByteArray())
            var protocol: ConversationEntity.ProtocolInfo = ConversationEntity.ProtocolInfo.Proteus
            if (mlsEnabled) {
                val receiver = checkNotNull(receiverMLS)
                val sender = checkNotNull(senderMLS)
                val keyPackage = receiver.transaction { it.generateKeyPackages(1).single() }
                sender.transaction {
                    it.createConversation(callGroup, Base64.decode(PROPOSAL_PUBLIC_KEY))
                    it.addMember(callGroup, listOf(keyPackage))
                }
                receiver.transaction { it.processWelcomeMessage(checkNotNull(transport.bundle?.welcome)) }
                protocol = ConversationEntity.ProtocolInfo.MLS(
                    callGroup, ConversationEntity.GroupState.ESTABLISHED, sender.transaction { it.conversationEpoch(callGroup) },
                    TIME, ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
                )
            }
            val database = userDatabaseBuilder(
                PlatformDatabaseData(StorageData.FileBacked("$account/storage")), SELF.toDao(), databaseSecret, Dispatchers.Default
            )
            try {
                database.conversationDAO.insertConversation(conversation(protocol).copy(id = callId, name = "Calling fixture"))
                database.memberDAO.insertMembersWithQualifiedId(listOf(SELF, SENDER).map {
                    com.wire.kalium.persistence.dao.member.MemberEntity(it.toDao(), com.wire.kalium.persistence.dao.member.MemberEntity.Role.Member)
                }, callId)
            } finally { database.close() }
            // Member insertion updates verification via SQL triggers; match prepare()'s initial state.
            sql("UPDATE Conversation SET proteus_verification_status = 'NOT_VERIFIED' WHERE qualified_id = '$callId'")
            val now = Clock.System.now()
            return event("call-prefix", encrypt("call", callContent(), groupId = callGroup), now, conversationId = callId)
        }

        suspend fun ignoredRemoteMuteEvents(): EventResponseToStore {
            val mute = encrypt("mute", remoteMute())
            return batch("notification", event("prefix", encrypt("prefix")), event("mute", mute),
                event("mute-duplicate", mute), event("null-targets", encrypt("null-targets", remoteMute("null"))),
                event("other-client", encrypt("other-client", remoteMute("other-client"))), event("first", encrypt("first")))
        }

        suspend fun verifyIgnoredRemoteMute(runtime: AppleAccountRuntime) {
            for (id in listOf("mute", "null-targets", "other-client")) {
                assertNull(runtime.database.messageDAO.getMessageById(id, CONVERSATION_ID))
            }
            assertNull(runtime.database.callDAO.getCallerIdByConversationId(CONVERSATION_ID))
        }

        suspend fun prepareRevokedX509Credentials() {
            x509Control("revoke=all")
            checkNotNull(receiverMLSCentral).checkCredentials()
        }

        suspend fun x509Control(query: String) = checkNotNull(x509Preparation).control(query)

        suspend fun x509Events() = batch("notification", event("prefix", encrypt("prefix")),
            event("commit", commit()), event("first", encrypt("first")))

        suspend fun configureX509Proxy(runtime: AppleAccountRuntime, proxy: Boolean) {
            val settings = checkNotNull(runtime.database.userPrefsDAO.getE2EISettings())
            runtime.database.userPrefsDAO.setE2EISettings(settings.copy(shouldUseProxy = proxy,
                crlProxy = "${X509Preparation.BASE}/proxy"))
        }

        suspend fun verifyX509(runtime: AppleAccountRuntime, revoked: Boolean = false) {
            val client = checkNotNull(openMLS(runtime))
            assertEquals(!basicSelf, client.getCredentialRef(com.wire.kalium.cryptography.CredentialType.X509) != null)
            val identity = checkNotNull(client.getUserIdentities(GROUP_ID, listOf(senderId.toCrypto()))[senderId.value]).single()
            assertEquals(com.wire.kalium.cryptography.CredentialType.X509, identity.credentialType)
            assertEquals(if (revoked) com.wire.kalium.cryptography.CryptoCertificateStatus.REVOKED
                else com.wire.kalium.cryptography.CryptoCertificateStatus.VALID, identity.status)
            assertEquals("NSE $SENDER_CLIENT", checkNotNull(identity.x509Identity).displayName)
            assertEquals(!basicSelf && !revoked, client.getGroupState(GROUP_ID) == com.wire.kalium.cryptography.E2EIConversationState.VERIFIED)
            assertEquals(1, checkNotNull(runtime.mlsCoreCrypto).getPkiTrustAnchors().size)
        }

        suspend fun probeMissingPki(ciphertext: ByteArray) {
            val nativeLease = assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(SELF)).value
            val central = coreCryptoCentral("$account/mls/$CLIENT/keystore", KEY)
            try {
                val client = mlsClient(central, SELF, CLIENT, scope)
                val failure = assertIs<Either.Left<*>>(com.wire.kalium.common.error.wrapMLSRequest {
                    client.transaction { it.decryptMessage(GROUP_ID, ciphertext) }
                }).value
                println("NSE_X509_WITHOUT_PKI: $failure")
                assertTrue(failure.toString().contains("PKI", ignoreCase = true))
            } finally {
                central.close()
                nativeLease.release()
            }
        }

        suspend fun mlsCipherEvents(enabled: Boolean = true) = batch("notification", event("prefix", encrypt("prefix")),
            mlsSettingsEvent(enabled, defaultCipher = 3), mlsSettingsEvent(enabled, defaultCipher = 1),
            mlsSettingsEvent(enabled, defaultCipher = 3), event("first", encrypt("first")))

        suspend fun verifyMLSCipher(runtime: AppleAccountRuntime, enabled: Boolean = true, defaultCipher: Int = 3) {
            verifyMLSSettings(runtime, enabled, defaultCipher)
            if (mlsEnabled) {
                assertEquals(CIPHER, runtime.prepared.mlsCipherSuite)
                assertEquals(defaultCipher, runtime.mlsCipherSuite?.tag)
                val client = checkNotNull(openMLS(runtime))
                assertEquals(CipherSuite.fromTag(defaultCipher).toCrypto(), client.getDefaultCipherSuite())
                assertTrue(client.getPublicKey().first.isNotEmpty())
                client.transaction { assertTrue(it.generateKeyPackages(1).single().isNotEmpty()) }
            }
        }

        suspend fun verifyProcessedText(event: EventResponseToStore, messageId: String) {
            assertIs<Either.Right<*>>(open { runtime ->
                assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(event.id)).isProcessed)
                val message = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById(messageId, CONVERSATION_ID))
                assertEquals(messageId, assertIs<MessageEntityContent.Text>(message.content).messageBody)
                assertEquals(if (senderId == SELF) 0 else 1,
                    runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 20).count { it.messageId == messageId })
            })
            assertNull(assertIs<Either.Right<ReceivedMessageResult?>>(receive(emptyList(), event.id)).value)
        }

        suspend fun verifyStoredAndNext(next: ByteArray, messageId: String, deliveries: List<String> = listOf(messageId),
            groupId: String = GROUP_ID,
        ) {
            assertIs<Either.Right<*>>(open { runtime ->
                val message = assertIs<MessageEntity.Regular>(runtime.database.messageDAO.getMessageById(messageId, CONVERSATION_ID))
                assertEquals(messageId, (message.content as MessageEntityContent.Text).messageBody)
                assertEquals(senderId.toDao(), message.senderUserId)
                assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                assertEquals(deliveries, runtime.database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                val plain = if (mlsEnabled) checkNotNull(openMLS(runtime)).transaction {
                    val result = assertIs<MLSDecryptResult.Success>(it.decryptMessage(groupId, next))
                    assertIs<DecryptedMessageBundle.Text>(result.messages.single()).message
                } else runtime.proteusCoreCrypto.proteusClient().transaction("fixture-next") {
                    it.decryptMessage(CryptoSessionId(senderId.toCrypto(), CryptoClientId(SENDER_CLIENT)), next) { bytes -> bytes }
                }
                val decoded = assertIs<ProtoContent.Readable>(ProtoContentMapperImpl(selfUserId = SELF).decodeFromProtobuf(
                    com.wire.kalium.logic.data.message.PlainMessageBlob(plain)
                ))
                assertEquals("next", (decoded.messageContent as MessageContent.Text).value)
            })
        }

        fun plainEvent(id: String, payload: String) = EventResponseToStore(id, "[$payload]", false)

        fun propertyEvent(key: String, value: Int) = plainEvent(
            "property",
            """{"type":"user.properties-set","key":"$key","value":$value}"""
        )

        fun mlsMigrationEvent(phase: String) = configEvent("mlsMigration", if (phase == "disabled") "disabled" else "enabled",
            when (phase) {
                "missing-times" -> """{"startTime":null,"finaliseRegardlessAfter":null}"""
                "future" -> """{"startTime":"2099-01-01T00:00:00Z","finaliseRegardlessAfter":"2099-01-02T00:00:00Z"}"""
                "started" -> """{"startTime":"2000-01-01T00:00:00Z","finaliseRegardlessAfter":"2099-01-02T00:00:00Z"}"""
                else -> """{"startTime":"2000-01-01T00:00:00Z","finaliseRegardlessAfter":"2000-01-02T00:00:00Z"}"""
            })

        suspend fun mlsMigrationEvents(enabled: Boolean = true, phase: String = "ended") = batch("notification",
            event("prefix", encrypt("prefix")), mlsSettingsEvent(enabled), mlsMigrationEvent("disabled"),
            mlsMigrationEvent("future"), mlsMigrationEvent("started"), mlsMigrationEvent(phase),
            mlsMigrationEvent(phase), event("first", encrypt("first")))

        suspend fun verifyMLSMigration(runtime: AppleAccountRuntime, phase: String = "ended") {
            val migration = checkNotNull(runtime.database.userConfigDAO.getMigrationConfiguration())
            assertEquals(phase != "disabled", migration.status)
            assertEquals(if (phase == "missing-times") null else Instant.parse(if (phase == "future") "2099-01-01T00:00:00Z"
                else "2000-01-01T00:00:00Z"), migration.startTime)
            assertEquals(if (phase == "missing-times") null else Instant.parse(if (phase in listOf("future", "started")) "2099-01-02T00:00:00Z"
                else "2000-01-02T00:00:00Z"), migration.endTime)
        }

        fun seedMLSSettings() = sql("INSERT OR REPLACE INTO Metadata(key, stringValue) VALUES ('SUPPORTED_PROTOCOLS', '[\"PROTEUS\",\"MLS\"]')")

        fun mlsSettingsEvent(enabled: Boolean = true, defaultProtocol: String = "mls", defaultCipher: Int = 1,
            protocols: String = "[\"proteus\",\"mls\"]") = configEvent("mls", if (enabled) "enabled" else "disabled",
            """{"defaultProtocol":"$defaultProtocol","supportedProtocols":$protocols,"allowedCipherSuites":[1,3],"defaultCipherSuite":$defaultCipher}""")

        suspend fun mlsSettingsEvents(enabled: Boolean = true, changeTeamProtocols: Boolean = false) = batch("notification", event("prefix", encrypt("prefix")),
            mlsSettingsEvent(!enabled, "proteus", protocols = if (changeTeamProtocols) "[\"proteus\"]" else "[\"proteus\",\"mls\"]"), mlsSettingsEvent(enabled), event("first", encrypt("first")))

        fun seedMLSClients() {
            seedMLSSettings()
            sql("INSERT INTO Client(user_id, id, is_verified, is_valid) VALUES ('${SELF.toDao()}', '$CLIENT', 1, 0)")
            sql("INSERT INTO Client(user_id, id) VALUES ('${SELF.toDao()}', 'obsolete')")
        }

        suspend fun seedMLSProtocolResolution() {
            seedMLSClients()
            sql("UPDATE User SET supported_protocols = 'PROTEUS' WHERE qualified_id = '${SELF.toDao()}'")
            if (mlsEnabled) {
                seedProteusConversation()
                seedProteusConversation(QualifiedIDEntity("legacy-second", SELF.domain))
                seed(storedText("history").copy(conversationId = DELETE_TARGET),
                    storedText("history-second").copy(conversationId = QualifiedIDEntity("legacy-second", SELF.domain)))
            }
        }

        fun protocolProfiles() = ListUsersDTO(usersFound = listOf(connectionProfile(SENDER.toApi()).copy(
            name = "Protocol peer",
            supportedProtocols = if (mlsEnabled) com.wire.kalium.network.api.model.SupportedProtocolDTO.entries.toList()
                else listOf(com.wire.kalium.network.api.model.SupportedProtocolDTO.PROTEUS)
        )))

        suspend fun verifyMLSProtocolResolution(runtime: AppleAccountRuntime, enabled: Boolean = true) {
            verifyMLSClients(runtime, enabled)
            val db = runtime.database
            val peer = checkNotNull(db.userDAO.getUserDetailsByQualifiedID(SENDER.toDao()))
            assertEquals("Protocol peer", peer.name)
            assertEquals(CONVERSATION_ID, peer.activeOneOnOneConversationId)
            if (mlsEnabled) {
                listOf("history", "history-second").forEach {
                    assertNotNull(db.messageDAO.getMessageById(it, CONVERSATION_ID))
                    assertNull(db.messageDAO.getMessageById(it, DELETE_TARGET))
                    assertNull(db.messageDAO.getMessageById(it, QualifiedIDEntity("legacy-second", SELF.domain)))
                }
                val warnings = db.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION_ID, 100, 0).first().filterIsInstance<MessageEntity.System>()
                assertEquals(1, warnings.count { it.content == MessageEntityContent.ConversationStartedUnverifiedWarning })
                assertEquals(1, warnings.count { it.content == MessageEntityContent.ConversationProtocolChanged(ConversationEntity.Protocol.MLS) })
            }
        }

        fun selfClients(): List<com.wire.kalium.network.api.authenticated.client.ClientDTO> = listOf(
            com.wire.kalium.network.api.authenticated.client.ClientDTO(
                cookie = null, registrationTime = TIME.toString(), lastActive = TIME.toString(), model = "refreshed",
                clientId = CLIENT, type = com.wire.kalium.network.api.authenticated.client.ClientTypeDTO.Permanent,
                deviceType = com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO.Phone,
                capabilities = listOf(com.wire.kalium.network.api.authenticated.client.ClientCapabilityDTO.ConsumableNotifications),
                label = "Current client", mlsPublicKeys = mapOf("ed25519" to "public-key")
            )
        )

        suspend fun verifyMLSClients(runtime: AppleAccountRuntime, enabled: Boolean = true) {
            verifyMLSSettings(runtime, enabled)
            val client = runtime.database.clientDAO.getClientsOfUserByQualifiedID(SELF.toDao()).single()
            assertEquals(CLIENT, client.id)
            assertEquals("refreshed", client.model)
            assertEquals("Current client", client.label)
            assertEquals(mapOf("ed25519" to "public-key"), client.mlsPublicKeys)
            assertTrue(client.isMLSCapable)
            assertTrue(client.isProteusVerified)
            assertFalse(client.isValid)
            // The existing app DAO retains the current row's async-notification capability.
            assertFalse(client.isAsyncNotificationsCapable)
            assertEquals(SupportedProtocolEntity.entries.toSet(), checkNotNull(runtime.database.userDAO.getUserDetailsByQualifiedID(SELF.toDao())).supportedProtocols)
        }

        suspend fun verifyMLSSettings(runtime: AppleAccountRuntime, enabled: Boolean = true, defaultCipher: Int = 1) {
            val config = runtime.database.userConfigDAO
            assertEquals(enabled, runtime.database.userPrefsDAO.isMLSEnabled())
            assertEquals(if (enabled) "MLS" else "PROTEUS", runtime.database.metadataDAO.valueByKey("default_protocol"))
            assertEquals(setOf(com.wire.kalium.persistence.dao.SupportedProtocolEntity.PROTEUS,
                com.wire.kalium.persistence.dao.SupportedProtocolEntity.MLS), config.getSupportedProtocols())
            assertEquals(SupportedCipherSuiteEntity(listOf(1, 3), defaultCipher), config.getDefaultCipherSuite())
            assertEquals(mlsEnabled, ClientRegistrationStorageImpl(runtime.database.metadataDAO).hasRegisteredMLSClient())
        }

        fun e2eiEvent(required: Boolean = true, url: String = "https://discovery.example/e2ei", proxy: Boolean = true,
            expiration: Long = 3600, crl: String = "https://crl.example/") = configEvent("mlsE2EId",
            status = if (required) "enabled" else "disabled",
            config = """{"acmeDiscoveryUrl":"$url","verificationExpiration":$expiration,"useProxyOnMobile":$proxy,"crlProxy":"$crl"}""")

        suspend fun e2eiEvents(required: Boolean = true) = batch("notification", event("prefix", encrypt("prefix")),
            e2eiEvent(required), e2eiEvent(required, proxy = false, expiration = 7200, crl = "https://ignored.example/"),
            event("first", encrypt("first")))

        suspend fun verifyE2EISettings(runtime: AppleAccountRuntime, required: Boolean = true): Pair<Long?, Long?> {
            val settings = checkNotNull(runtime.database.userPrefsDAO.getE2EISettings())
            assertEquals(required, settings.status)
            assertEquals("https://discovery.example/e2ei", settings.discoverUrl)
            assertEquals(true, settings.shouldUseProxy)
            assertEquals("https://crl.example/", settings.crlProxy)
            val now = com.wire.kalium.util.DateTimeUtil.currentInstant().toEpochMilliseconds()
            assertTrue(checkNotNull(settings.gracePeriodEndMs) in (now + 3_000_000)..(now + 3_600_000))
            val notification = runtime.database.userPrefsDAO.getE2EINotificationTime()
            assertTrue(checkNotNull(notification) <= now)
            assertFalse(ClientRegistrationStorageImpl(runtime.database.metadataDAO).isBlockedByE2EI())
            return settings.gracePeriodEndMs to notification
        }

        fun configEvent(name: String, status: String = "enabled", config: String? = null) = plainEvent(
            "config",
            """
            {"type":"feature-config.update","name":"$name","data":{"status":"$status"${config?.let { ",\"config\":$it" } ?: ""}}}
        """.trimIndent()
        )

        fun preferenceEvent(muted: Int? = null, archived: Boolean? = null) = plainEvent(
            "preference",
            """
            {"type":"conversation.member-update","qualified_conversation":{"id":"${CONVERSATION_ID.value}","domain":"${SELF.domain}"},
             "from":"${SELF.value}","qualified_from":{"id":"${SELF.value}","domain":"${SELF.domain}"},"time":"$TIME",
             "data":{"target":"${SELF.value}","qualified_target":{"id":"${SELF.value}","domain":"${SELF.domain}"},
             "otr_muted_status":$muted,"otr_muted_ref":"$TIME","otr_archived":$archived,"otr_archived_ref":"$TIME"}}
        """.trimIndent()
        )

        suspend fun synchronizationEvent(): EventResponseToStore {
            seed(storedText("receipt-target", sender = SELF))
            // Establish acknowledged baseline settings so repeated changes must retain their pending notification flags.
            sql("INSERT INTO Metadata(key, stringValue) VALUES('lastSlowSyncInstant', '$TIME')")
            val delivery =
                event("delivery", encrypt("delivery", MessageContent.Receipt(ReceiptType.DELIVERED, listOf("receipt-target", "missing"))))
            val read = event("read", encrypt("read", MessageContent.Receipt(ReceiptType.READ, listOf("receipt-target"))))
            val laterDelivery =
                event("later-delivery", encrypt("delivery", MessageContent.Receipt(ReceiptType.DELIVERED, listOf("receipt-target"))))
            val availability = event("availability", encrypt("availability", MessageContent.Availability(UserAvailabilityStatus.BUSY)))
            val first = event("first", encrypt("first"), TIME + 1.seconds)
            val profile =
                plainEvent(
                    "profile",
                    """{"type":"user.update","user":{"id":"${SENDER.value}","name":"Updated profile","handle":"updated","email":"updated@example.com","accent_id":5,"supported_protocols":["proteus","mls"],"assets":[{"type":"image","size":"preview","key":"preview"},{"type":"image","size":"complete","key":"complete"}]}}"""
                )
            val selfProfile = plainEvent("self-profile", """{"type":"user.update","user":{"id":"${SELF.value}","name":"Updated self"}}""")
            val unknownProfile = plainEvent("unknown-profile", """{"type":"user.update","user":{"id":"unknown","name":"Do not create"}}""")
            val folder =
                plainEvent(
                    "folders",
                    """{"type":"user.properties-set","key":"labels","value":{"labels":[{"id":"folder","name":"Important","type":0,"conversations":[],"qualified_conversations":[{"id":"${CONVERSATION_ID.value}","domain":"${SELF.domain}"}]}]}}"""
                )
            return batch(
                "notification", delivery, delivery, read, laterDelivery, availability, profile, selfProfile, unknownProfile,
                propertyEvent("WIRE_RECEIPT_MODE", 1), propertyEvent("WIRE_TYPING_INDICATOR_MODE", 0), folder,
                preferenceEvent(muted = 3), preferenceEvent(archived = true), preferenceEvent(muted = 0), preferenceEvent(archived = false),
                configEvent("fileSharing", "disabled"), configEvent("fileSharing"),
                configEvent("classifiedDomains", config = """{"domains":["trusted.example"]}"""),
                configEvent("conferenceCalling", config = """{"useSFTForOneToOneCalls":true}"""),
                configEvent("conversationGuestLinks", "disabled"), configEvent("conversationGuestLinks"),
                configEvent("selfDeletingMessages", "disabled", """{"enforcedTimeoutSeconds":0}"""),
                configEvent("selfDeletingMessages", config = """{"enforcedTimeoutSeconds":60}"""),
                configEvent("appLock", config = """{"enforceAppLock":true,"inactivityTimeoutSecs":45}"""),
                configEvent("allowedGlobalOperations", config = """{"mlsConversationReset":true}"""),
                configEvent(
                    "cells"
                ), configEvent(
                    "cellsInternal",
                    config = """{"backend":{"url":"https://cells.example"},"collabora":{"edition":"CODE"},"storage":{"perUserQuotaBytes":2048}}"""
                ),
                configEvent("simplifiedUserConnectionRequestQRCode"), configEvent("assetAuditLog"),
                configEvent("preventAdminlessGroups"), configEvent("meetings"), first
            )
        }

        suspend fun verifySynchronization(runtime: AppleAccountRuntime) {
            val db = runtime.database
            assertEquals(MessageEntity.Status.READ, db.messageDAO.getMessageById("receipt-target", CONVERSATION_ID)?.status)
            for (type in listOf(ReceiptTypeEntity.READ, ReceiptTypeEntity.DELIVERY)) {
                val receipt = db.receiptDAO.observeDetailedReceiptsForMessage(CONVERSATION_ID, "receipt-target", type).first().single()
                assertEquals(SENDER.toDao(), receipt.userId)
                assertEquals(TIME, receipt.date)
            }
            val profile = checkNotNull(db.userDAO.getUserDetailsByQualifiedID(SENDER.toDao()))
            assertEquals("Updated profile", profile.name)
            assertEquals("updated", profile.handle)
            assertEquals("updated@example.com", profile.email)
            assertEquals(5, profile.accentId)
            assertEquals(UserAvailabilityStatusEntity.BUSY, profile.availabilityStatus)
            assertEquals(QualifiedIDEntity("preview", SELF.domain), profile.previewAssetId)
            assertEquals(QualifiedIDEntity("complete", SELF.domain), profile.completeAssetId)
            assertEquals("Updated self", db.userDAO.getUserDetailsByQualifiedID(SELF.toDao())?.name)
            assertNull(db.userDAO.getUserDetailsByQualifiedID(QualifiedIDEntity("unknown", SELF.domain)))
            assertTrue(db.userPrefsDAO.areReadReceiptsEnabled().first())
            assertFalse(db.userPrefsDAO.isTypingIndicatorEnabled().first())
            assertEquals(listOf(CONVERSATION_ID), db.conversationFolderDAO.getFoldersWithConversations().single().conversationIdList)
            assertTrue(checkNotNull(db.userPrefsDAO.isFileSharingEnabled()).isStatusChanged == true)
            assertTrue(checkNotNull(db.userPrefsDAO.isGuestRoomLinkEnabled()).isStatusChanged == true)
            assertEquals(listOf("trusted.example"), db.userPrefsDAO.isClassifiedDomainsEnabledFlow().first()?.trustedDomains)
            assertTrue(db.userPrefsDAO.isConferenceCallingEnabled())
            assertTrue(db.userPrefsDAO.shouldUseSftForOneOnOneCalls())
            val timer = checkNotNull(db.userConfigDAO.getTeamSettingsSelfDeletionStatus())
            assertEquals(SelfDeletionTimerEntity.Enforced(60.seconds), timer.selfDeletionTimerEntity)
            assertTrue(timer.isStatusChanged == true)
            assertTrue(checkNotNull(db.userPrefsDAO.appLockStatus()).isStatusChanged == true)
            assertEquals(45, checkNotNull(db.userPrefsDAO.appLockStatus()).inactivityTimeoutSecs)
            assertTrue(db.userConfigDAO.getMlsConversationsResetEnabled())
            assertTrue(db.userConfigDAO.isCellsEnabled())
            assertEquals("https://cells.example", db.userConfigDAO.getWireCellsConfig()?.backendUrl)
            assertTrue(db.userConfigDAO.isProfileQRCodeEnabled())
            assertTrue(db.userConfigDAO.isAssetAuditLogEnabled())
            assertTrue(db.userConfigDAO.isPreventAdminlessGroupsEnabled())
            assertTrue(db.userConfigDAO.isMeetingsEnabled())
            assertNull(db.metadataDAO.valueByKey("lastSlowSyncInstant"))
            assertFalse(checkNotNull(db.conversationDAO.getConversationById(CONVERSATION_ID)).archived)
            assertEquals(ConversationEntity.MutedStatus.ALL_ALLOWED, db.conversationDAO.getConversationById(CONVERSATION_ID)?.mutedStatus)
        }

        suspend fun seedReadState() {
            seedDeleteForMeTarget()
            val db =
                userDatabaseBuilder(
                    PlatformDatabaseData(StorageData.FileBacked("$account/storage")),
                    SELF.toDao(),
                    databaseSecret,
                    Dispatchers.Default
                )
            try {
                db.messageDAO.insertOrIgnoreMessage(storedText("read-me", expireAfter = 60_000).copy(conversationId = DELETE_TARGET))
                db.messageDAO.insertOrIgnoreMessage(
                    storedText("started", expireAfter = 60_000, endDate = TIME + 60.seconds).copy(conversationId = DELETE_TARGET)
                )
                db.messageDAO.insertOrIgnoreMessage(storedText("receipt-target", sender = SELF))
            } finally {
                db.close()
            }
        }

        suspend fun readStateEvent(): EventResponseToStore {
            seedReadState()
            val read = event("read", encrypt("read", MessageContent.LastRead("read-me", DELETE_TARGET.toModel(), TIME)))
            val older = event("older", encrypt("older", MessageContent.LastRead("read-me", DELETE_TARGET.toModel(), TIME - 1.seconds)))
            val receipt = event("self-receipt", encrypt("self-receipt", MessageContent.Receipt(ReceiptType.READ, listOf("receipt-target"))))
            val first = event("first", encrypt("first"))
            return batch("notification", read, older, receipt, first, propertyEvent("WIRE_RECEIPT_MODE", 1))
        }

        suspend fun verifyReadState(runtime: AppleAccountRuntime) {
            val db = runtime.database
            assertEquals(
                listOf(NotificationMessageKey("read-me", DELETE_TARGET)),
                db.messageDAO.observeNotificationRemovalCandidates(listOf("read-me")).first()
            )
            assertEquals(TIME, db.conversationDAO.getConversationLastReadDate(DELETE_TARGET))
            assertEquals(0L, db.messageDAO.getConversationUnreadEventsCount(DELETE_TARGET))
            assertNull(db.messageDAO.getMessageById("read-me", DELETE_TARGET)?.selfDeletionEndDate)
            assertEquals(TIME + 60.seconds, db.messageDAO.getMessageById("started", DELETE_TARGET)?.selfDeletionEndDate)
            assertEquals(MessageEntity.Status.SENT, db.messageDAO.getMessageById("receipt-target", CONVERSATION_ID)?.status)
            assertTrue(
                db.receiptDAO.observeDetailedReceiptsForMessage(CONVERSATION_ID, "receipt-target", ReceiptTypeEntity.READ).first().isEmpty()
            )
            assertTrue(db.userPrefsDAO.areReadReceiptsEnabled().first())
        }

        suspend fun ordinaryEvent(): EventResponseToStore {
            seed(storedText("original"), storedText("delete-me"))
            sql("UPDATE Conversation SET type = 'GROUP'")
            return batch("notification", *metadataEvents().toTypedArray(),
                event("edited", encrypt("edited", MessageContent.TextEdited("original", "edited", emptyList()))),
                event("reaction", encrypt("reaction", MessageContent.Reaction("edited", setOf("❤")))),
                event("delete", encrypt("delete", MessageContent.DeleteMessage("delete-me"))),
                event("ephemeral", encrypt("ephemeral", expiresAfterMillis = 60_000)),
                event("first", encrypt("first"), TIME + 1.seconds),
            )
        }

        fun manifest(
            event: EventResponseToStore,
            next: ByteArray,
            appNext: ByteArray,
            messageIds: List<String>,
            commit: EventResponseToStore? = null,
            ordinary: Boolean = false,
            synchronization: Boolean = false,
            readState: Boolean = false,
            route: String = if (mlsEnabled) "mls" else "proteus",
            membership: Boolean = false,
            pendingProposal: Boolean = false,
            creation: Boolean = false,
            fetchedCreation: Boolean = false,
            prefix: List<EventResponseToStore> = emptyList(),
        ): JsonObject = JsonObject(mapOf(
            "root" to JsonPrimitive(root), "service" to JsonPrimitive(config.serviceName),
            "userValue" to JsonPrimitive(SELF.value), "userDomain" to JsonPrimitive(SELF.domain), "clientId" to JsonPrimitive(CLIENT),
            "mls" to JsonPrimitive(mlsEnabled), "next" to JsonPrimitive(Base64.encode(next)),
            "appNext" to JsonPrimitive(Base64.encode(appNext)),
            "senderValue" to JsonPrimitive(senderId.value), "route" to JsonPrimitive(route),
            "synchronization" to JsonPrimitive(synchronization), "readState" to JsonPrimitive(readState),
            "messageIds" to JsonArray(messageIds.map { JsonPrimitive(it) }),
            "deliveryIds" to JsonArray((if (readState) emptyList() else if (ordinary) listOf("ephemeral", "first") else messageIds).map { JsonPrimitive(it) }),
            "ordinaryEvents" to JsonPrimitive(ordinary),
            "membershipEvents" to JsonPrimitive(membership), "pendingProposal" to JsonPrimitive(pendingProposal),
            "creationEvents" to JsonPrimitive(creation), "fetchedCreation" to JsonPrimitive(fetchedCreation),
            "conversationMetadata" to KtxSerializer.json.encodeToJsonElement(creationResponse()),
            "response" to KtxSerializer.json.encodeToJsonElement(
                NotificationResponse(TIME.toString(), false, prefix + listOfNotNull(commit, event))
            ),
        ))

        suspend fun close(deleteStorage: Boolean = true) {
            scope.coroutineContext.job.cancelAndJoin()
            receiverMLSCentral?.close()
            if (::receiver.isInitialized) receiver.close()
            senderMLSCentral?.close()
            if (::sender.isInitialized) sender.close()
            x509Preparation?.close()
            lease?.release()
            assertEquals(emptyList(), openFixtureFiles(root))
            val finalLease = assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(SELF)).value
            finalLease.release()
            if (deleteStorage) FileSystem.SYSTEM.deleteRecursively(root.toPath())
        }
    }

    private class CommitApi(private val events: List<EventContentDTO> = emptyList(), private val fail: Boolean = false) :
        com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi {
        var attempts = 0
        override suspend fun sendMessage(message: ByteArray) = error("Unexpected send")
        override suspend fun sendCommitBundle(bundle: com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi.CommitBundle):
            NetworkResponse<com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse> {
            attempts++
            check(!fail) { "Required commit transport failed" }
            return NetworkResponse.Success(
                com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse(TIME, events),
                emptyMap(),
                201
            )
        }
    }

    private class CaptureTransport : MLSTransporter {
        var bundle: CommitBundle? = null
        override suspend fun sendCommitBundle(commitBundle: CommitBundle) { bundle = commitBundle }
    }

    private enum class Interruption {
        BEFORE_COMMIT, AFTER_COMMIT, DELIVERY, MARKER, SECOND_MESSAGE, CANCEL_BEFORE_COMMIT;

        fun beforeCommit() {
            if (this == BEFORE_COMMIT) error("Interrupted before crypto commit")
            if (this == CANCEL_BEFORE_COMMIT) throw CancellationException("Cancelled before crypto commit")
        }

        fun afterCommit() {
            if (this == AFTER_COMMIT) error("Interrupted after crypto commit")
        }

    }

    private companion object {
        val SELF = UserId("fcbe2b38-c66a-4fbe-9e54-6fab39c017a4", "example.com")
        val SENDER = UserId("eef049aa-c081-4520-9e60-3fbe90809ee9", "example.com")
        val CONVERSATION_ID = QualifiedIDEntity("conversation", "example.com")
        const val CLIENT = "abc123"
        const val SENDER_CLIENT = "def456"
        val THIRD = UserId("bcc012da-0478-46ac-aac7-b02c4ab7b144", "example.com")
        const val PROPOSAL_PUBLIC_KEY = "GX9rI+FshTLGq8g4+s1ep4m+DHaykgM0A5v6iz02jWE="

        // OpenMLS ExternalProposal::new_remove, group receiving-test-group, epoch 2, leaf 2,
        // external sender index 0, Ed25519 test seed [42; 32].
        const val REMOVE_THIRD_PROPOSAL =
            "AAEAARRyZWNlaXZpbmctdGVzdC1ncm91cAAAAAAAAAACAgAAAAAAAgADAAAAAkBA1JOnlaFniPnGxIZ8Wz5/frz1CcnecBDWZtdXCqBOwWOQ86RNjDxap8kuPkdz4guajy3r6Hlp27imWNrVovBmDA=="
        val FILE_KEEP_CONVERSATION = QualifiedIDEntity("file-keeper", "example.com")
        val GROUP_ID = Base64.encode("receiving-test-group".encodeToByteArray())
        val RESET_GROUP_ID = Base64.encode("receiving-reset-group".encodeToByteArray())
        const val ASSET_TEXT = "Asset received by the NSE and downloaded through the real app."
        val ASSET_MESSAGE_IDS = listOf("asset", "image", "audio", "video", "hidden", "image-preview", "first", "last-image")
        val MESSAGE_FILE_IDS = listOf("remove-asset", "remove-preview", "remove-multipart", "remove-asset-shared", "remove-alias",
            "remove-offline", "remove-draft-ref", "remove-avatar-ref")
        val KEY = ByteArray(32) { it.toByte() }
        val TIME = Instant.parse("2026-09-05T12:00:00Z")
        val CIPHER = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

        fun user(id: UserId) = UserEntity(
            id.toDao(), "Fixture", null, null, null, 1, null, ConnectionEntity.State.ACCEPTED, null, null,
            UserAvailabilityStatusEntity.NONE, UserTypeEntity.STANDARD, null, false, false, null, false,
            setOf(SupportedProtocolEntity.PROTEUS, SupportedProtocolEntity.MLS), null,
        )

        val DELETE_TARGET = QualifiedIDEntity("delete-target", "example.com")

        fun storedText(
            id: String, body: String = id, sender: UserId = SENDER, expireAfter: Long? = null, endDate: Instant? = null,
        ) = MessageEntity.Regular(
            id, CONVERSATION_ID, TIME, sender.toDao(), MessageEntity.Status.SENT,
            content = MessageEntityContent.Text(body), readCount = 0, senderName = null, senderClientId = SENDER_CLIENT,
            editStatus = MessageEntity.EditStatus.NotEdited, expireAfterMs = expireAfter, selfDeletionEndDate = endDate,
            isSelfMessage = sender == SELF,
        )

        fun conversation(protocol: ConversationEntity.ProtocolInfo) = ConversationEntity(
            CONVERSATION_ID, "Fixture", ConversationEntity.Type.ONE_ON_ONE, null, protocol,
            creatorId = SENDER.value, lastNotificationDate = null, lastModifiedDate = Instant.DISTANT_PAST,
            lastReadDate = Instant.DISTANT_PAST, access = emptyList(), accessRole = emptyList(),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED, messageTimer = null, userMessageTimer = null,
            archivedInstant = null, mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED, channelAccess = null,
            channelAddPermission = null, wireCell = null, historySharingRetentionSeconds = 0,
        )
    }
}

/** Entry point of the test-only simulator executable used by the Swift integration harness. */
private const val LOCAL_API = "https://localhost:18443"

fun runSwiftReceivingFixture(args: Array<String>) {
    val fixture = NotificationProcessorNativeTest()
    when (args.first()) {
        "stress" -> fixture.runMLSStress(args.drop(1).toTypedArray())
        "export" -> fixture.exportPreparedAccountsForSwift()
        "export-multiple" -> fixture.exportPreparedAccountsForSwift(multiple = true)
        "export-mls-commit" -> fixture.exportPreparedAccountsForSwift(mlsCommit = true)
        "export-ordinary" -> fixture.exportPreparedAccountsForSwift(ordinary = true)
        "export-membership" -> fixture.exportPreparedAccountsForSwift(membership = true)
        "export-creation" -> fixture.exportPreparedAccountsForSwift(creation = true)
        "export-recovery" -> fixture.exportPreparedAccountsForSwift(recovery = true)
        "export-reset" -> fixture.exportPreparedAccountsForSwift(reset = true)
        "export-content" -> fixture.exportPreparedAccountsForSwift(content = true)
        "export-signals" -> fixture.exportPreparedAccountsForSwift(signals = true)
        "export-typing" -> fixture.exportPreparedAccountsForSwift(typing = true)
        "export-calling" -> fixture.exportPreparedAccountsForSwift(calling = true)
        "export-member-roles" -> fixture.exportPreparedAccountsForSwift(memberRoles = true)
        "export-access-changes" -> fixture.exportPreparedAccountsForSwift(accessChanges = true)
        "export-room-settings" -> fixture.exportPreparedAccountsForSwift(roomSettings = true)
        "export-remote-mute" -> fixture.exportPreparedAccountsForSwift(remoteMute = true)
        "export-x509" -> fixture.exportPreparedAccountsForSwift(x509 = true)
        "export-mls-cipher" -> fixture.exportPreparedAccountsForSwift(mlsSettings = true, mlsCipher = true)
        "export-mls-migration" -> fixture.exportPreparedAccountsForSwift(mlsSettings = true, mlsClients = true, mlsProtocols = true, mlsMigration = true)
        "export-mls-protocols" -> fixture.exportPreparedAccountsForSwift(mlsSettings = true, mlsClients = true, mlsProtocols = true)
        "export-mls-clients" -> fixture.exportPreparedAccountsForSwift(mlsSettings = true, mlsClients = true)
        "export-mls-settings" -> fixture.exportPreparedAccountsForSwift(mlsSettings = true)
        "export-e2ei-settings" -> fixture.exportPreparedAccountsForSwift(e2eiSettings = true)
        "export-session-refresh" -> fixture.exportPreparedAccountsForSwift(sessionRefresh = true)
        "export-self-conversations" -> fixture.exportPreparedAccountsForSwift(selfConversations = true)
        "export-sent-connections" -> fixture.exportPreparedAccountsForSwift(sentConnections = true)
        "export-accepted-connections" -> fixture.exportPreparedAccountsForSwift(acceptedConnections = true)
        "export-connections" -> fixture.exportPreparedAccountsForSwift(connections = true)
        "export-meeting-conversations" -> fixture.exportPreparedAccountsForSwift(meetingConversation = true)
        "export-federation" -> fixture.exportPreparedAccountsForSwift(federation = true)
        "export-meetings" -> fixture.exportPreparedAccountsForSwift(meetings = true)
        "export-identity" -> fixture.exportPreparedAccountsForSwift(identity = true)
        "export-conversation-cleanup" -> fixture.exportPreparedAccountsForSwift(conversationCleanup = true)
        "export-mls-ignores" -> fixture.exportPreparedAccountsForSwift(mlsIgnores = true)
        "export-buffering" -> fixture.exportPreparedAccountsForSwift(buffering = true)
        "export-decryption" -> fixture.exportPreparedAccountsForSwift(decryption = true)
        "export-previews" -> fixture.exportPreparedAccountsForSwift(previews = true)
        "export-multipart" -> fixture.exportPreparedAccountsForSwift(multipart = true)
        "export-assets" -> fixture.exportPreparedAccountsForSwift(assets = true)
        "export-cleanup" -> fixture.exportPreparedAccountsForSwift(assets = true, cleanup = true)
        "export-one-on-one" -> fixture.exportPreparedAccountsForSwift(oneOnOne = true)
        "export-migration" -> fixture.exportPreparedAccountsForSwift(migration = true)
        "after-app-proposal" -> fixture.exportAfterAppProposal()
        "export-synchronization" -> fixture.exportPreparedAccountsForSwift(synchronization = true)
        "verify" -> fixture.verifyAccountsReceivedFromSwift()
        else -> error("Expected export or verify")
    }
}

private fun openFixtureFiles(root: String): List<String> = memScoped {
    val path = allocArray<ByteVar>(PATH_MAX)
    (0 until getdtablesize()).mapNotNull { descriptor ->
        if (fcntl(descriptor, F_GETPATH, path) == 0) {
            path.toKString().takeIf { it.startsWith(root.replace("//", "/")) }
        } else null
    }
}
