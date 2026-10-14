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

@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class, com.wire.kalium.util.DelicateKaliumApi::class, com.wire.kalium.util.DebugKaliumApi::class)

package com.wire.kalium.logic

import com.wire.kalium.persistence.db.existingUserDatabaseSecret

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.DecryptedMessageBundle
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSDecryptResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventStreamData
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import com.wire.kalium.logic.sync.receiving.AccountRuntimeLease
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntimeOwner
import com.wire.kalium.logic.sync.receiving.ApplePreparedAccountStore
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import kotlin.io.encoding.Base64
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path.Companion.toPath

/** Test-only executable: the actual app scope sees Swift's message and uses its advanced crypto state. */
fun runSwiftReceivingAppVerifier(args: Array<String>) = runTest {
    withContext(Dispatchers.Default) {
        val stress = args.getOrNull(1) == "stress"
        val parsed = KtxSerializer.json.parseToJsonElement(FileSystem.SYSTEM.read(args.first().toPath()) { readUtf8() })
        val accounts = if (stress) kotlinx.serialization.json.JsonArray(listOf(parsed)) else parsed.jsonArray
        val afterProposal = args.getOrNull(1) == "after-proposal"
        val selected = accounts.map { it.jsonObject }.filter { !afterProposal || it["pendingProposal"]?.jsonPrimitive?.boolean == true }
        for (account in selected) {
            val root = account.getValue("root").jsonPrimitive.content
            val user = UserId(account.getValue("userValue").jsonPrimitive.content, account.getValue("userDomain").jsonPrimitive.content)
            val keychain = ApplePersistenceConfig(account.getValue("service").jsonPrimitive.content)
            // Fixture app metadata is created here, under ownership, before app construction.
            // This belongs to app fixture bootstrap; the NSE never opens this global database.
            if (!afterProposal) {
                val lease = assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(user)).value
                try {
                    val prepared = checkNotNull(ApplePreparedAccountStore(root, user).read(lease))
                    val configuration = prepared.serverConfig
                    val links = configuration.links
                    val database = globalDatabaseProvider(PlatformDatabaseData(StorageData.FileBacked("$root/global-storage")), Dispatchers.Default, null)
                    try {
                        database.serverConfigurationDAO.insert(ServerConfigurationDAO.InsertData(
                            id = configuration.id, apiBaseUrl = links.api, accountBaseUrl = links.accounts,
                            webSocketBaseUrl = links.webSocket, blackListUrl = links.blackList, teamsUrl = links.teams,
                            websiteUrl = links.website, title = links.title, isOnPremises = links.isOnPremises,
                            federation = configuration.metaData.federation, domain = user.domain, commonApiVersion = 8,
                            apiProxyHost = null, apiProxyNeedsAuthentication = null, apiProxyPort = null,
                        ))
                        database.accountsDAO.insertOrReplace(user.toDao(), null, null, configuration.id, false, null, null)
                        if (account["identityEvents"]?.jsonPrimitive?.boolean == true) database.accountsDAO.setCurrentAccount(user.toDao())
                    } finally {
                        database.close()
                    }
                } finally {
                    lease.release()
                }
            }
            val logic = CoreLogic(
                rootPath = root, keychainConfig = keychain, userAgent = "Swift-handoff-app-fixture",
                kaliumConfigs = KaliumConfigs(
                    enableCalling = false,
                    mockedRequests = if (account["pendingProposal"]?.jsonPrimitive?.boolean == true ||
                        account["assetEvents"]?.jsonPrimitive?.boolean == true ||
                        account["multipartEvents"]?.jsonPrimitive?.boolean == true ||
                        account["x509Events"]?.jsonPrimitive?.boolean == true ||
                        account["sessionRefreshEvents"]?.jsonPrimitive?.boolean == true) null else emptyList()
                ),
                useInMemoryStorage = false,
            )
            try {
                var session = logic.openSessionScope(user)
                if (account["recoveryTakeover"]?.jsonPrimitive?.boolean == true) {
                    val gatherer = object : EventGatherer {
                        override suspend fun gatherEvents() = flow {
                            val pending = session.eventRepository.observeEvents().first()
                            assertTrue(pending.isNotEmpty())
                            emit(EventStreamData.NewEvents(pending))
                        }
                    }
                    session.createIncrementalSyncWorker(gatherer).processEventsFlow().collect()
                    assertTrue(session.eventRepository.observeEvents().first().isEmpty())
                    val conversationId = ConversationId("conversation", user.domain)
                    suspend fun verifyMessages() {
                        for (id in account.getValue("messageIds").jsonArray.map { it.jsonPrimitive.content }) {
                            val stored = assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(conversationId, id)).message
                            assertEquals(id, assertIs<MessageContent.Text>(assertIs<Message.Regular>(stored).content).value)
                        }
                        assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(
                            conversationId, account.getValue("recoveryWarningId").jsonPrimitive.content))
                        val members = assertIs<Either.Right<List<UserId>>>(
                            session.conversations.conversationRepository.getConversationMembers(conversationId)).value
                        assertEquals(setOf(user, UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain)), members.toSet())
                    }
                    verifyMessages()
                    logic.releaseSessionScope(user)
                    assertIs<Either.Right<*>>(AppleAccountRuntime.use(root, user, keychain, "recovery-takeover-check", emptyMap()) { runtime ->
                        for (id in listOf("notification", "following")) {
                            assertTrue(checkNotNull(runtime.database.eventDAO.getEventById(id)).isProcessed)
                        }
                        assertTrue(runtime.database.metadataDAO.valuesByKeyRange("mls_receive_recovery_v2:", "mls_receive_recovery_v2;").isEmpty())
                    })
                    session = logic.openSessionScope(user)
                    verifyMessages()
                    println("APP_RECOVERY_DURABLE_WORKER_TAKEOVER_PASSED")
                    continue
                }
                if (stress) {
                    val gate = args[2]
                    val resumed = args.getOrNull(3) == "app-resume"
                    if (resumed) logic.releaseSessionScope(user)
                    FileSystem.SYSTEM.write("$gate.ready".toPath()) { writeUtf8("ready") }
                    withTimeout(60.seconds) {
                        while (!FileSystem.SYSTEM.exists("$gate.release".toPath())) delay(10)
                    }
                    if (resumed) session = logic.openSessionScope(user)
                    // In NSE-first mode, prove public app reads work before any replay/sync.
                    if (args.getOrNull(3) == "nse-first" || resumed) {
                        for (id in account.getValue("messageIds").jsonArray.map { it.jsonPrimitive.content }) {
                            val stored = assertIs<GetMessageByIdUseCase.Result.Success>(
                                session.messages.getMessageById(ConversationId("conversation", "example.com"), id)).message
                            assertEquals(id, assertIs<MessageContent.Text>(assertIs<Message.Regular>(stored).content).value)
                        }
                        println("MLS_STRESS_APP_VISIBLE_BEFORE_SYNC")
                    }
                    assertIs<com.wire.kalium.logic.feature.debug.SynchronizeExternalDataResult.Success>(
                        session.debug.synchronizeExternalData(account.getValue("response").toString()))
                    for (id in account.getValue("messageIds").jsonArray.map { it.jsonPrimitive.content }) {
                        val message = assertIs<GetMessageByIdUseCase.Result.Success>(
                            session.messages.getMessageById(ConversationId("conversation", "example.com"), id)).message
                        assertEquals(id, assertIs<MessageContent.Text>(assertIs<Message.Regular>(message).content).value)
                    }
                    println("MLS_STRESS_APP_RECEIVED")
                    return@withContext
                }
                for (messageId in account.getValue("messageIds").jsonArray.map { it.jsonPrimitive.content }) {
                    val result = assertIs<GetMessageByIdUseCase.Result.Success>(
                        session.messages.getMessageById(ConversationId("conversation", "example.com"), messageId)
                    )
                    val content = assertIs<Message.Regular>(result.message).content
                    when (messageId) {
                        "notification" -> {
                            val failure = assertIs<MessageContent.FailedDecryption>(content)
                            assertFalse(failure.isDecryptionResolved)
                            assertEquals(account.getValue("failureSender").jsonPrimitive.content, failure.senderUserId.value)
                            assertEquals(account.getValue("failureClient").jsonPrimitive.content, checkNotNull(failure.clientId).value)
                            assertEquals(com.wire.kalium.cryptography.exceptions.ProteusException.Code.DECODE_ERROR,
                                com.wire.kalium.cryptography.exceptions.ProteusException.fromProteusCode(checkNotNull(failure.errorCode)))
                            println("APP_DECRYPTION_FAILURE_PASSED")
                        }
                        // The existing app DAO stores the knock type but does not retain hotKnock.
                        "knock" -> assertEquals(false, assertIs<MessageContent.Knock>(content).hotKnock)
                        "location" -> assertEquals("Berlin", assertIs<MessageContent.Location>(content).name)
                        "asset", "image", "audio", "video", "hidden", "image-preview", "last-image" -> {
                            val asset = assertIs<MessageContent.Asset>(content).value
                            assertEquals("$messageId.txt", asset.name)
                            if (messageId !in listOf("hidden", "image-preview")) assertEquals(messageId, asset.remoteData.assetId)
                        }
                        "multipart-edited", "multipart-empty" -> {
                            val multipart = assertIs<MessageContent.Multipart>(content)
                            assertEquals(messageId == "multipart-edited", multipart.quotedMessageReference?.isVerified)
                            if (messageId == "multipart-edited") assertEquals("multipart-edited", multipart.value)
                            assertEquals(if (messageId == "multipart-edited") listOf("cell-two", "cell-one") else emptyList(),
                                multipart.attachments.map { assertIs<com.wire.kalium.logic.data.message.CellAssetContent>(it).id })
                        }
                        "buttons-edited" -> {
                            val composite = assertIs<MessageContent.Composite>(content)
                            assertEquals("replacement", composite.textContent?.value)
                            assertEquals(listOf("second", "third"), composite.buttonList.map { it.id }.sorted())
                            assertEquals(listOf("second"), composite.buttonList.filter { it.isSelected }.map { it.id })
                        }
                        "unknown" -> assertTrue(checkNotNull(assertIs<MessageContent.Unknown>(content).encodedData).isNotEmpty())
                        else -> {
                            assertEquals(messageId, assertIs<MessageContent.Text>(content).value)
                            if (messageId in listOf("quoted", "preview-quoted")) assertEquals(true, content.quotedMessageReference?.isVerified)
                        }
                    }
                }
                if (account["previewEvents"]?.jsonPrimitive?.boolean == true) {
                    val message = assertIs<GetMessageByIdUseCase.Result.Success>(
                        session.messages.getMessageById(ConversationId("conversation", user.domain), "first")).message
                    val content = assertIs<MessageContent.Text>(assertIs<Message.Regular>(message).content)
                    assertEquals(2, content.linkPreviews.size)
                    val preview = content.linkPreviews.single { it.urlOffset == 0 }
                    assertEquals("preview", preview.title)
                    assertEquals("summary", preview.summary)
                    assertEquals("https://example.com/permanent", preview.permanentUrl)
                    val image = checkNotNull(preview.image)
                    assertEquals("preview-image", image.assetKey)
                    assertEquals("token-preview-image", image.assetToken)
                    assertEquals(user.domain, image.assetDomain)
                    assertEquals(10, image.assetWidth)
                    assertEquals(20, image.assetHeight)
                    if (account["linkPreviewEnabled"]?.jsonPrimitive?.boolean == true) {
                        assertEquals(account["assetText"]?.jsonPrimitive?.content,
                            FileSystem.SYSTEM.read(checkNotNull(image.assetDataPath)) { readUtf8() })
                        println("APP_PREVIEW_IMAGE_PASSED")
                    } else assertNull(image.assetDataPath)
                    println("APP_PREVIEW_METADATA_PASSED")
                }
                if (account["multipartEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversationId = ConversationId("conversation", user.domain)
                    val notifications = assertIs<Either.Right<List<com.wire.kalium.logic.data.notification.LocalNotification>>>(
                        session.messages.messageRepository.getNotificationMessage()).value
                        .filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>().flatMap { it.messages }
                    assertTrue(notifications.none { it.messageId.startsWith("multipart") })
                    val attachments = assertIs<Either.Right<List<com.wire.kalium.logic.data.message.MessageAttachment>>>(
                        session.cells.getMessageAttachmentsUseCase("multipart-edited", conversationId)).value
                    for (attachment in attachments) {
                        val cell = assertIs<com.wire.kalium.logic.data.message.CellAssetContent>(attachment)
                        assertEquals("remote/${cell.id}.png", cell.assetPath)
                        assertEquals(com.wire.kalium.logic.data.asset.AssetTransferStatus.NOT_DOWNLOADED, cell.transferStatus)
                        val path = "$root/${cell.id}.png".toPath()
                        assertIs<Either.Right<*>>(session.cells.downloadCellFile(cell.id, conversationId.value, path,
                            checkNotNull(cell.assetSize), onProgressUpdate = {}))
                        assertEquals("cell", FileSystem.SYSTEM.read(path) { readUtf8() })
                    }
                    val stored = assertIs<Either.Right<List<com.wire.kalium.logic.data.message.MessageAttachment>>>(
                        session.cells.getMessageAttachmentsUseCase("multipart-edited", conversationId)).value
                    assertTrue(stored.all { assertIs<com.wire.kalium.logic.data.message.CellAssetContent>(it).transferStatus ==
                        com.wire.kalium.logic.data.asset.AssetTransferStatus.SAVED_INTERNALLY })
                    println("APP_MULTIPART_DOWNLOAD_PASSED")
                }
                if (account["assetEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversationId = ConversationId("conversation", user.domain)
                    val notifications = assertIs<Either.Right<List<com.wire.kalium.logic.data.notification.LocalNotification>>>(
                        session.messages.messageRepository.getNotificationMessage()).value
                        .filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>()
                        .flatMap { it.messages }.associateBy { it.messageId }
                    assertEquals(com.wire.kalium.logic.data.notification.LocalNotificationCommentType.FILE,
                        assertIs<com.wire.kalium.logic.data.notification.LocalNotificationMessage.Comment>(notifications["asset"]).type)
                    assertEquals(com.wire.kalium.logic.data.notification.LocalNotificationCommentType.PICTURE,
                        assertIs<com.wire.kalium.logic.data.notification.LocalNotificationMessage.Comment>(notifications["image"]).type)
                    assertFalse(notifications.containsKey("hidden"))
                    for (id in listOf("asset", "image", "audio", "video")) {
                        val downloaded = assertIs<com.wire.kalium.logic.feature.asset.MessageAssetResult.Success>(
                            session.messages.getAssetMessage(conversationId, id).await())
                        assertEquals(account.getValue("assetText").jsonPrimitive.content,
                            FileSystem.SYSTEM.read(downloaded.decodedAssetPath) { readUtf8() })
                        assertEquals(com.wire.kalium.logic.data.asset.AssetTransferStatus.SAVED_INTERNALLY,
                            session.messages.getAssetMessageTransferStatus(conversationId, id))
                        val cached = assertIs<com.wire.kalium.logic.feature.asset.MessageAssetResult.Success>(
                            session.messages.getAssetMessage(conversationId, id).await())
                        assertEquals(downloaded.decodedAssetPath, cached.decodedAssetPath)
                    }
                    println("APP_ASSET_STATE_PASSED")
                }
                if (account["sentConnectionEvents"]?.jsonPrimitive?.boolean == true ||
                    account["acceptedConnectionEvents"]?.jsonPrimitive?.boolean == true ||
                    account["identityOwn"]?.jsonPrimitive?.boolean == true) {
                    // Actual app query: peer-only request metadata and removed self membership
                    // do not discard eligible text notifications.
                    val notifications = assertIs<Either.Right<List<com.wire.kalium.logic.data.notification.LocalNotification>>>(
                        session.messages.messageRepository.getNotificationMessage()).value
                        .filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>()
                    assertTrue(notifications.any { it.id == ConversationId("conversation", user.domain) &&
                        it.messages.any { message -> message.messageId == "first" } })
                    println("APP_NOTIFICATION_WITHOUT_SELF_MEMBERSHIP_PASSED")
                }
                if (account["sentConnectionEvents"]?.jsonPrimitive?.boolean == true) {
                    val id = ConversationId("conversation", user.domain)
                    val conversation = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.getConversationById(id)).value
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.Type.ConnectionPending, conversation.type)
                    assertEquals("Created group", conversation.name)
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.ReceiptMode.ENABLED, conversation.receiptMode)
                    if (account.getValue("mls").jsonPrimitive.boolean) {
                        val protocol = assertIs<com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLS>(conversation.protocol)
                        assertEquals(com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED, protocol.groupState)
                    } else assertIs<com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.Proteus>(conversation.protocol)
                    val request = session.conversations.observePendingConnectionRequests().first().single { it.conversationId == id }
                    assertEquals(com.wire.kalium.logic.data.user.ConnectionState.SENT, request.connection.status)
                    assertTrue(session.conversations.observeConversationMembers(id).first().any { it.user.id.value == "sent-incomplete" })
                    val pendingId = ConversationId("delete-target", user.domain)
                    val pending = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.getConversationById(pendingId)).value
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.Type.ConnectionPending, pending.type)
                    assertEquals("Pending connection", pending.name)
                    assertIs<com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.Proteus>(pending.protocol)
                    assertEquals(listOf(user), session.conversations.observeConversationMembers(pendingId).first().map { it.user.id })
                    val creationTime = account.getValue("response").jsonObject.getValue("time").jsonPrimitive.content
                    val warning = assertIs<Message.System>(assertIs<GetMessageByIdUseCase.Result.Success>(
                        session.messages.getMessageById(pendingId, "$pendingId:$creationTime:creation:0")).message)
                    assertEquals(MessageContent.ConversationStartedUnverifiedWarning, warning.content)
                    println("APP_PENDING_CONNECTION_CREATION_PASSED")
                    println("APP_SENT_CONNECTION_PASSED")
                }
                if (account["acceptedConnectionEvents"]?.jsonPrimitive?.boolean == true) {
                    val id = ConversationId("conversation", user.domain)
                    val mlsConnection = account.getValue("mls").jsonPrimitive.boolean
                    val consent = account.getValue("acceptedConnectionConsent").jsonPrimitive.boolean
                    val source = if (mlsConnection) ConversationId("delete-target", user.domain) else id
                    val peer = UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain)
                    assertEquals(com.wire.kalium.logic.data.user.ConnectionState.ACCEPTED,
                        checkNotNull(session.users.getKnownUser(peer).first()).connectionStatus)
                    val active = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.observeOneToOneConversationWithOtherUser(peer).first()).value
                    assertEquals(id, active.id)
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.Type.OneOnOne, active.type)
                    assertFalse(session.conversations.observeConversationMembers(source).first().any { it.user.id == user })
                    val warningId = (if (mlsConnection) "notification:1" else "notification") + ":accepted"
                    if (consent) assertIs<GetMessageByIdUseCase.Result.Failure>(session.messages.getMessageById(id, warningId))
                    else assertEquals(MessageContent.ConversationStartedUnverifiedWarning,
                        assertIs<Message.System>(assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(id, warningId)).message).content)
                    if (mlsConnection) {
                        for (message in listOf("history", "history-second")) assertIs<GetMessageByIdUseCase.Result.Success>(
                            session.messages.getMessageById(id, message))
                        assertIs<GetMessageByIdUseCase.Result.Failure>(session.messages.getMessageById(source, warningId))
                    }
                    // Acceptance retains the app's previous connection row, independently of User status.
                    val retained = session.conversations.observePendingConnectionRequests().first().find { it.conversationId == source }
                    if (consent) assertNull(retained) else assertEquals(com.wire.kalium.logic.data.user.ConnectionState.PENDING,
                        checkNotNull(retained).connection.status)
                    println("APP_ACCEPTED_CONNECTION_PASSED")
                }
                if (account["connectionEvents"]?.jsonPrimitive?.boolean == true) {
                    val id = ConversationId("request", user.domain)
                    for ((name, status) in mapOf("request" to "PENDING", "blocked" to "BLOCKED", "ignored" to "IGNORED",
                        "consent" to "MISSING_LEGALHOLD_CONSENT", "delete-target" to "CANCELLED")) {
                        val peer = checkNotNull(session.users.getKnownUser(UserId("peer-$name", user.domain)).first())
                        assertEquals(status, peer.connectionStatus.name)
                        assertEquals("Peer $name", peer.name)
                    }
                    session.incrementalSyncRepository.updateIncrementalSyncState(com.wire.kalium.logic.data.sync.IncrementalSyncStatus.Live)
                    val notifications = withTimeout(5_000) { session.messages.getNotifications().first { list ->
                        list.any { it.conversationId == id }
                    } }
                    val request = notifications.filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>()
                        .single { it.id == id }
                    val content = assertIs<com.wire.kalium.logic.data.notification.LocalNotificationMessage.ConnectionRequest>(request.messages.single())
                    assertEquals("", content.messageId)
                    assertEquals("Peer request", content.author.name)
                    assertEquals(UserId("peer-request", user.domain), content.authorId)
                    session.conversations.markConnectionRequestAsNotified(
                        if (account.getValue("mls").jsonPrimitive.boolean) null else content.authorId)
                    assertFalse(FileSystem.SYSTEM.exists("$root/message-files/delete-asset.bin".toPath()))
                    assertTrue(FileSystem.SYSTEM.exists("$root/message-files/keep-shared.bin".toPath()))
                    println("APP_CONNECTION_NOTIFICATION_PASSED")
                }
                if (account["federationEvents"]?.jsonPrimitive?.boolean == true) {
                    val deleted = account.getValue("federationDeleted").jsonPrimitive.boolean
                    val fed = UserId("bcc012da-0478-46ac-aac7-b02c4ab7b144", "federated.example")
                    val other = fed.copy(domain = "other.example")
                    val local = setOf(user, UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain))
                    for (name in listOf("conversation", "CHANNEL", "MEETING")) {
                        for (domain in listOf(user.domain, "federated.example", "other.example")) {
                            val expected = if (deleted) when (domain) {
                                user.domain -> local + other
                                "federated.example" -> setOf(fed, other)
                                else -> setOf(other)
                            } else when (domain) {
                                "federated.example" -> local + fed
                                "other.example" -> local + other
                                else -> local
                            }
                            assertEquals(expected, session.conversations.observeConversationMembers(ConversationId(name, domain))
                                .first().map { it.user.id }.toSet())
                        }
                    }
                    val peer = assertIs<com.wire.kalium.logic.data.user.OtherUser>(session.users.getKnownUser(fed).first())
                    assertEquals(deleted, peer.defederated)
                    assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(
                        ConversationId("federation-one", user.domain), "history"))
                    for (name in listOf("PENDING", "SENT", "IGNORED")) {
                        val request = assertIs<com.wire.kalium.logic.data.user.OtherUser>(session.users.getKnownUser(
                            UserId("request-$name", "federated.example")).first())
                        assertEquals(if (deleted) "CANCELLED" else name, request.connectionStatus.name)
                    }
                    println("APP_FEDERATION_STATE_PASSED")
                }
                if (account["meetingEvents"]?.jsonPrimitive?.boolean == true ||
                    account["meetingConversationEvents"]?.jsonPrimitive?.boolean == true) {
                    val meetingId = com.wire.kalium.logic.data.id.MeetingId("meeting", user.domain)
                    val start = kotlin.time.Instant.parse(account.getValue("meetingMetadata").jsonObject.getValue("start_time").jsonPrimitive.content)
                    val occurrence = checkNotNull(session.meetings.getNextUnfinishedMeetingOccurrence(meetingId, start))
                    assertEquals("Fixture meeting", occurrence.meeting.title)
                    assertEquals("America/New_York", occurrence.meeting.tzid)
                    assertEquals(ConversationId("conversation", user.domain), occurrence.meeting.conversationId)
                    assertEquals(start.toEpochMilliseconds(), occurrence.occurrenceStartTime.toEpochMilliseconds())
                    assertEquals(occurrence, session.meetings.observeMeetingOccurrence.invoke(occurrence.occurrenceId).first())
                    assertNull(session.meetings.getNextUnfinishedMeetingOccurrence(
                        com.wire.kalium.logic.data.id.MeetingId("removed-meeting", user.domain), start))
                    val creator = checkNotNull(session.users.getKnownUser(occurrence.meeting.creatorId).first())
                    assertEquals("Third user", creator.name)
                    println("APP_MEETING_STATE_PASSED")
                }
                if (account["identityEvents"]?.jsonPrimitive?.boolean == true) {
                    val global = logic.getGlobalScope()
                    val observed = assertIs<com.wire.kalium.logic.feature.client.NewClientResult.InCurrentAccount>(
                        withTimeout(10.seconds) { global.observeNewClientsUseCase().first {
                            it is com.wire.kalium.logic.feature.client.NewClientResult.InCurrentAccount
                        } })
                    assertEquals(user, observed.userId)
                    assertEquals("new-client", observed.newClients.single().id.value)
                    assertEquals("Fixture phone", observed.newClients.single().model)
                    global.clearNewClientsForUser(user)
                    assertIs<com.wire.kalium.logic.feature.client.NewClientResult.Empty>(
                        withTimeout(10.seconds) { global.observeNewClientsUseCase().first {
                            it is com.wire.kalium.logic.feature.client.NewClientResult.Empty
                        } })
                    println("APP_NEW_CLIENT_CONSUMED")
                    val own = account["identityOwn"]?.jsonPrimitive?.boolean == true
                    val third = UserId("bcc012da-0478-46ac-aac7-b02c4ab7b144", user.domain)
                    val removed = if (own) user else third
                    if (own) assertEquals(user, session.users.observeSelfUser().first().id)
                    else assertTrue(checkNotNull(session.users.getKnownUser(removed).first()).deleted)
                    assertFalse(checkNotNull(session.users.getKnownUser(third.copy(domain = "foreign.example")).first()).deleted)
                    for (id in listOf("conversation", "CHANNEL", "MEETING")) {
                        val members = session.conversations.observeConversationMembers(ConversationId(id, user.domain)).first()
                        assertFalse(members.any { it.user.id == removed })
                        assertTrue(members.any { it.user.id == third.copy(domain = "foreign.example") })
                    }
                    val history = session.messages.getMessageById(ConversationId("delete-target", user.domain), "history")
                    assertIs<GetMessageByIdUseCase.Result.Success>(history)
                    println("APP_IDENTITY_STATE_PASSED")
                }
                val conversationCleanup = account["conversationCleanup"]?.jsonPrimitive?.content.orEmpty()
                if (conversationCleanup.isNotEmpty()) {
                    val deleted = conversationCleanup == "delete"
                    val target = ConversationId(if (deleted) "delete-target" else "conversation", user.domain)
                    for (id in listOf("remove-asset", "remove-preview", "remove-multipart", "remove-asset-shared", "remove-alias",
                        "remove-offline", "remove-draft-ref", "remove-avatar-ref")) {
                        assertIs<GetMessageByIdUseCase.Result.Failure>(session.messages.getMessageById(target, id))
                    }
                    val details = session.conversations.getConversationProtocolInfo(target)
                    if (deleted) assertIs<com.wire.kalium.logic.feature.conversation.GetConversationProtocolInfoUseCase.Result.Failure>(details)
                    else assertIs<com.wire.kalium.logic.feature.conversation.GetConversationProtocolInfoUseCase.Result.Success>(details)
                    val shared = ConversationId("file-keeper", user.domain)
                    assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(shared, "keep-asset"))
                    assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(shared, "keep-multipart"))
                    for (file in listOf("delete-asset", "delete-preview", "delete-attachment")) {
                        assertFalse(FileSystem.SYSTEM.exists("$root/message-files/$file.bin".toPath()))
                    }
                    for (file in listOf("keep-shared", "keep-offline", "keep-avatar")) {
                        assertTrue(FileSystem.SYSTEM.exists("$root/message-files/$file.bin".toPath()))
                    }
                    assertEquals(!deleted, FileSystem.SYSTEM.exists("$root/message-files/keep-draft.bin".toPath()))
                    println("APP_CONVERSATION_CLEANUP_PASSED")
                }
                if (account["cleanupEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversationId = ConversationId("conversation", user.domain)
                    for (id in listOf("remove-asset", "remove-preview", "remove-multipart", "remove-asset-shared", "remove-alias",
                        "remove-offline", "remove-draft-ref", "remove-avatar-ref", "session-reset")) {
                        val deleted = assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(conversationId, id)).message
                        assertEquals(Message.Visibility.DELETED, assertIs<Message.Regular>(deleted).visibility)
                    }
                    assertIs<MessageContent.Multipart>(assertIs<GetMessageByIdUseCase.Result.Success>(
                        session.messages.getMessageById(conversationId, "keep-multipart")).message.content)
                    // Exercise the existing app end-date consumer with real SQL/files and a retryable filesystem failure.
                    session.messages.deleteEphemeralMessageEndDate()
                    val expirationRepository = session.messages.messageRepository
                    val retained = assertIs<Either.Right<Message.Regular>>(
                        expirationRepository.getMessageById(conversationId, "expired-asset")).value
                    assertEquals(Message.Visibility.VISIBLE, retained.visibility)
                    assertIs<MessageContent.Asset>(retained.content)
                    val expiredPending = assertIs<Either.Right<List<Message>>>(expirationRepository.getAllAlreadyEndedEphemeralMessages()).value
                    assertTrue(expiredPending.any { it.id == "expired-asset" })
                    FileSystem.SYSTEM.delete("$root/message-files/expired-asset.bin/busy".toPath())
                    session.messages.deleteEphemeralMessageEndDate()
                    for ((id, file) in listOf("expired-asset" to "expired-asset", "expired-preview" to "expired-preview",
                        "expired-multipart" to "expired-attachment")) {
                        val expired = assertIs<Either.Right<Message.Regular>>(expirationRepository.getMessageById(conversationId, id)).value
                        assertEquals(Message.Visibility.DELETED, expired.visibility)
                        assertFalse(FileSystem.SYSTEM.exists("$root/message-files/$file.bin".toPath()))
                    }
                    assertTrue(FileSystem.SYSTEM.exists("$root/message-files/keep-shared.bin".toPath()))
                    println("APP_EXPIRATION_FILE_CLEANUP_PASSED")
                    println("APP_FILE_CLEANUP_STATE_PASSED")
                }
                if (account["typingEvents"]?.jsonPrimitive?.boolean == true) {
                    assertTrue(session.users.observeTypingIndicatorEnabled().first())
                    assertTrue(session.conversations.observeUsersTyping(ConversationId("delete-target", user.domain)).first().isEmpty())
                    val notifications = assertIs<Either.Right<List<com.wire.kalium.logic.data.notification.LocalNotification>>>(
                        session.messages.messageRepository.getNotificationMessage()).value
                        .filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>().flatMap { it.messages }
                    assertEquals(setOf("prefix", "first"), notifications.map { it.messageId }.toSet())
                    println("APP_TYPING_POLICY_PASSED")
                }
                if (account["memberRoleEvents"]?.jsonPrimitive?.boolean == true) {
                    val id = ConversationId("conversation", user.domain)
                    val member = UserId("eef049aa-c081-4520-9e60-3fbe90809ee9", user.domain)
                    val fetched = account["memberRoleFetched"]?.jsonPrimitive?.boolean == true
                    val role = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation.Member.Role?>>(
                        session.conversations.conversationRepository.getConversationMemberRole(id, member)).value
                    assertEquals(if (fetched) com.wire.kalium.logic.data.conversation.Conversation.Member.Role.Member
                        else com.wire.kalium.logic.data.conversation.Conversation.Member.Role.Unknown("custom-final"), role)
                    val messageIds = if (fetched) listOf("notification") else listOf("notification:2", "notification:5")
                    for (messageId in messageIds) {
                        val message = assertIs<Message.System>(assertIs<GetMessageByIdUseCase.Result.Success>(
                            session.messages.getMessageById(id, messageId)).message)
                        assertEquals(MessageContent.MemberChange.UserPromotedToAdmin(listOf(member)), message.content)
                        assertEquals(user, message.senderUserId)
                        assertEquals(Message.Status.Sent, message.status)
                        assertEquals(Message.Visibility.VISIBLE, message.visibility)
                    }
                    assertIs<GetMessageByIdUseCase.Result.Failure>(session.messages.getMessageById(id, if (fetched) "notification:1" else "notification:3"))
                    val notifications = assertIs<Either.Right<List<com.wire.kalium.logic.data.notification.LocalNotification>>>(
                        session.messages.messageRepository.getNotificationMessage()).value
                        .filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>().flatMap { it.messages }
                    assertEquals(if (fetched) setOf("first") else setOf("prefix", "first"), notifications.map { it.messageId }.toSet())
                    println("APP_MEMBER_ROLE_STATE_PASSED")
                }
                if (account["accessChangesEvents"]?.jsonPrimitive?.boolean == true) {
                    val id = ConversationId("conversation", user.domain)
                    val enabled = account["appsEnabled"]?.jsonPrimitive?.boolean == true
                    val conversation = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.getConversationById(id)).value
                    assertEquals(listOf(com.wire.kalium.logic.data.conversation.Conversation.Access.CODE,
                        com.wire.kalium.logic.data.conversation.Conversation.Access.INVITE), conversation.access)
                    assertEquals(enabled, com.wire.kalium.logic.data.conversation.Conversation.AccessRole.SERVICE in conversation.accessRole)
                    for ((index, value) in listOf(1 to enabled, 3 to !enabled, 4 to enabled)) {
                        val message = assertIs<Message.System>(assertIs<GetMessageByIdUseCase.Result.Success>(
                            session.messages.getMessageById(id, "notification:$index")).message)
                        assertEquals(MessageContent.ConversationAppsEnabledChanged(value), message.content)
                        assertEquals(Message.Status.Sent, message.status)
                        assertEquals(Message.Visibility.VISIBLE, message.visibility)
                    }
                    assertIs<GetMessageByIdUseCase.Result.Failure>(session.messages.getMessageById(id, "notification:2"))
                    val notifications = assertIs<Either.Right<List<com.wire.kalium.logic.data.notification.LocalNotification>>>(
                        session.messages.messageRepository.getNotificationMessage()).value
                        .filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>().flatMap { it.messages }
                    assertEquals(setOf("prefix", "first"), notifications.map { it.messageId }.toSet())
                    println("APP_ACCESS_CHANGES_STATE_PASSED")
                }
                if (account["roomSettingsEvents"]?.jsonPrimitive?.boolean == true) {
                    val id = ConversationId("conversation", user.domain)
                    val details = assertIs<com.wire.kalium.logic.feature.conversation.ObserveConversationDetailsUseCase.Result.Success>(
                        session.conversations.observeConversationDetails(id).first()).conversationDetails
                    val channel = assertIs<com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel>(details)
                    assertEquals(com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission.EVERYONE,
                        channel.permission)
                    val link = assertIs<com.wire.kalium.logic.feature.conversation.guestroomlink.ObserveGuestRoomLinkResult.Success>(
                        session.conversations.observeGuestRoomLink(id).first()).link
                    if (account["roomLinkDeleted"]?.jsonPrimitive?.boolean == true) assertNull(link) else {
                        assertFalse(checkNotNull(link).isPasswordProtected)
                        val uri = io.ktor.http.Url(link.link)
                        assertEquals("room key/?&", uri.parameters["key"])
                        assertEquals("code=+#", uri.parameters["code"])
                    }
                    println("APP_ROOM_SETTINGS_STATE_PASSED")
                }
                if (account["remoteMuteEvents"]?.jsonPrimitive?.boolean == true) {
                    val notifications = assertIs<Either.Right<List<com.wire.kalium.logic.data.notification.LocalNotification>>>(
                        session.messages.messageRepository.getNotificationMessage()).value
                        .filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>().flatMap { it.messages }
                    assertEquals(setOf("prefix", "first"), notifications.map { it.messageId }.toSet())
                    for (id in listOf("mute", "null-targets", "other-client")) {
                        assertEquals(com.wire.kalium.common.error.StorageFailure.DataNotFound,
                            assertIs<GetMessageByIdUseCase.Result.Failure>(session.messages.getMessageById(
                                ConversationId("conversation", user.domain), id)).cause)
                    }
                    println("APP_REMOTE_MUTE_STATE_PASSED")
                }
                if (account["signalEvents"]?.jsonPrimitive?.boolean == true) {
                    val reset = assertIs<Message.System>(assertIs<GetMessageByIdUseCase.Result.Success>(
                        session.messages.getMessageById(ConversationId("conversation", user.domain), "session-reset")
                    ).message)
                    assertEquals(MessageContent.CryptoSessionReset, reset.content)
                    assertEquals(UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain), reset.senderUserId)
                    val fromSelf = account.getValue("readState").jsonPrimitive.boolean
                    assertEquals(if (fromSelf) "received" else "initial", assertIs<Either.Right<String>>(
                        session.userConfigRepository.observeCurrentTrackingIdentifier().first()).value)
                    assertEquals(if (fromSelf) "middle" else "earlier", session.userConfigRepository.getPreviousTrackingIdentifier())
                    println("APP_SIGNAL_STATE_PASSED")
                }
                if (account["contentEvents"]?.jsonPrimitive?.boolean == true) println("APP_CONTENT_STATE_PASSED")
                if (account["oneOnOneEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversationId = ConversationId("conversation", user.domain)
                    val peer = UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain)
                    val active = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.observeOneToOneConversationWithOtherUser(peer).first()
                    ).value
                    val resolved = active.id
                    assertEquals(conversationId, resolved)
                    val conversation = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.getConversationById(resolved)
                    ).value
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.Type.OneOnOne, conversation.type)
                    if (account.getValue("mls").jsonPrimitive.boolean) {
                        assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(resolved, "history"))
                    }
                    println("APP_ONE_ON_ONE_STATE_PASSED")
                }
                if (account["mlsClientEvents"]?.jsonPrimitive?.boolean == true) {
                    val clients = assertIs<Either.Right<List<com.wire.kalium.logic.data.client.Client>>>(
                        session.clientRepository.observeClientsByUserId(user).first()
                    ).value
                    val client = clients.single()
                    assertEquals("abc123", client.id.value)
                    assertEquals("refreshed", client.model)
                    assertEquals("Current client", client.label)
                    assertEquals(mapOf("ed25519" to "public-key"), client.mlsPublicKeys)
                    assertTrue(client.isMLSCapable)
                    assertTrue(client.isVerified)
                    assertFalse(client.isValid)
                    assertFalse(client.isAsyncNotificationsCapable)
                    val self = session.users.observeSelfUser().first()
                    assertEquals(com.wire.kalium.logic.data.user.SupportedProtocol.entries.toSet(), self.supportedProtocols)
                    println("APP_MLS_CLIENT_REFRESH_CONSUMED")
                }
                if (account["mlsMigrationEvents"]?.jsonPrimitive?.boolean == true) {
                    val migration = assertIs<Either.Right<com.wire.kalium.logic.data.featureConfig.MLSMigrationModel>>(
                        session.userConfigRepository.getMigrationConfiguration()).value
                    assertEquals(com.wire.kalium.logic.data.featureConfig.Status.ENABLED, migration.status)
                    assertEquals(kotlin.time.Instant.parse("2000-01-01T00:00:00Z"), migration.startTime)
                    assertEquals(kotlin.time.Instant.parse("2000-01-02T00:00:00Z"), migration.endTime)
                    println("APP_MLS_MIGRATION_SETTINGS_CONSUMED")
                }
                if (account["mlsProtocolEvents"]?.jsonPrimitive?.boolean == true) {
                    val peer = UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain)
                    val active = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.observeOneToOneConversationWithOtherUser(peer).first()
                    ).value
                    assertEquals(ConversationId("conversation", user.domain), active.id)
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.Type.OneOnOne, active.type)
                    val profile = checkNotNull(session.users.getKnownUser(peer).first())
                    assertEquals("Protocol peer", profile.name)
                    if (account.getValue("mls").jsonPrimitive.boolean) {
                        listOf("history", "history-second").forEach { id ->
                            assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(active.id, id))
                        }
                    }
                    println("APP_MLS_PROTOCOL_RESOLUTION_CONSUMED")
                }
                if (account["x509Events"]?.jsonPrimitive?.boolean == true) {
                    val client = assertIs<Either.Right<MLSClient>>(session.mlsClientProvider.getMLSClient()).value
                    session.checkCrlRevocationList(forceUpdate = true)
                    val sender = UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain)
                    val identity = checkNotNull(client.getUserIdentities(GROUP_ID, listOf(sender.toCrypto()))[sender.value]).single()
                    val revoked = account.getValue("x509PeerRevoked").jsonPrimitive.boolean
                    assertEquals(com.wire.kalium.cryptography.CredentialType.X509, identity.credentialType)
                    assertEquals(if (revoked) com.wire.kalium.cryptography.CryptoCertificateStatus.REVOKED
                        else com.wire.kalium.cryptography.CryptoCertificateStatus.VALID, identity.status)
                    assertEquals("NSE def456", checkNotNull(identity.x509Identity).displayName)
                    assertEquals(!revoked, client.getGroupState(GROUP_ID) == com.wire.kalium.cryptography.E2EIConversationState.VERIFIED)
                    if (account.getValue("x509SelfRevoked").jsonPrimitive.boolean) {
                        session.users.observeCertificateRevocationForSelfClient()
                        assertTrue(withTimeout(5_000) { session.observeShouldNotifyForRevokedCertificate().first() })
                        session.markNotifyForRevokedCertificateAsNotified()
                        assertFalse(withTimeout(5_000) { session.observeShouldNotifyForRevokedCertificate().first() })
                        println("APP_X509_SELF_REVOCATION_CONSUMED")
                    } else {
                        val renewal = assertIs<com.wire.kalium.logic.feature.user.E2EIRequiredResult.WithGracePeriod.Renew>(
                            withTimeout(5_000) { session.observeE2EIRequired().first() })
                        assertTrue(renewal.timeLeft.inWholeSeconds in 1..86_400)
                        session.markE2EIRequiredAsNotified(renewal.timeLeft)
                        assertTrue(assertIs<Either.Right<kotlin.time.Instant>>(
                            session.userConfigRepository.observeE2EINotificationTime().first()).value >
                            com.wire.kalium.util.DateTimeUtil.currentInstant())
                    }
                    println("APP_X509_PKI_AND_CRL_CONSUMED")
                }
                if (account["mlsSettingsEvents"]?.jsonPrimitive?.boolean == true) {
                    val enabled = account.getValue("mlsConfiguredEnabled").jsonPrimitive.boolean
                    val repository = session.userConfigRepository
                    assertEquals(Either.Right(enabled), repository.isMLSEnabled())
                    assertEquals(Either.Right(if (enabled) com.wire.kalium.logic.data.user.SupportedProtocol.MLS
                        else com.wire.kalium.logic.data.user.SupportedProtocol.PROTEUS), repository.getDefaultProtocol())
                    assertEquals(Either.Right(com.wire.kalium.logic.data.user.SupportedProtocol.entries.toSet()), repository.getSupportedProtocols())
                    val cipher = assertIs<Either.Right<com.wire.kalium.logic.data.mls.SupportedCipherSuite>>(repository.getSupportedCipherSuite()).value
                    assertEquals(listOf(1, 3), cipher.supported.map { it.tag })
                    val changedCipher = account["mlsCipherEvents"]?.jsonPrimitive?.boolean == true
                    assertEquals(if (changedCipher) 3 else 1, cipher.default.tag)
                    if (changedCipher && enabled && account.getValue("mls").jsonPrimitive.boolean) {
                        val client = assertIs<Either.Right<MLSClient>>(session.mlsClientProvider.getMLSClient()).value
                        val expected = com.wire.kalium.cryptography.MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
                        assertEquals(expected, client.getDefaultCipherSuite())
                        val publicKey = client.getPublicKey()
                        assertEquals(expected, publicKey.second)
                        assertTrue(publicKey.first.isNotEmpty())
                        client.transaction { assertTrue(it.generateKeyPackages(1).single().isNotEmpty()) }
                        println("APP_MLS_CHANGED_CIPHER_USABLE")
                    }
                    if (!enabled && account.getValue("mls").jsonPrimitive.boolean) {
                        assertEquals(Either.Left(com.wire.kalium.common.error.MLSFailure.Disabled), session.mlsClientProvider.getMLSClient())
                        println("APP_DISABLED_MLS_POLICY_PASSED")
                    }
                    println("APP_MLS_SETTINGS_CONSUMED")
                }
                if (account["e2eiSettingsEvents"]?.jsonPrimitive?.boolean == true) {
                    val required = account.getValue("e2eiRequired").jsonPrimitive.boolean
                    val settings = assertIs<Either.Right<com.wire.kalium.logic.configuration.E2EISettings>>(
                        session.userConfigRepository.getE2EISettings()).value
                    assertEquals(required, settings.isRequired)
                    assertEquals("https://discovery.example/e2ei", settings.discoverUrl)
                    assertTrue(settings.shouldUseProxy)
                    assertEquals("https://crl.example/", settings.crlProxy)
                    val result = kotlinx.coroutines.withTimeout(5_000) { session.observeE2EIRequired().first() }
                    if (required && account.getValue("mls").jsonPrimitive.boolean) {
                        val create = assertIs<com.wire.kalium.logic.feature.user.E2EIRequiredResult.WithGracePeriod.Create>(result)
                        assertTrue(create.timeLeft.inWholeSeconds in 3000..3600)
                        session.markE2EIRequiredAsNotified(create.timeLeft)
                        val snoozed = assertIs<Either.Right<kotlin.time.Instant>>(
                            session.userConfigRepository.observeE2EINotificationTime().first()).value
                        assertTrue(snoozed > com.wire.kalium.util.DateTimeUtil.currentInstant())
                        println("APP_E2EI_CREATE_REQUIRED")
                    } else assertIs<com.wire.kalium.logic.feature.user.E2EIRequiredResult.NotRequired>(result)
                    println("APP_E2EI_SETTINGS_CONSUMED")
                }
                if (account["sessionRefreshEvents"]?.jsonPrimitive?.boolean == true) {
                    val peer = assertIs<com.wire.kalium.logic.feature.user.GetUserInfoResult.Success>(
                        session.users.getUserInfo(UserId("app-refreshed", user.domain))).otherUser
                    assertEquals("Peer app-refreshed", peer.name)
                    println("APP_REFRESHED_SESSION_REQUEST_PASSED")
                }
                if (account["selfConversationEvents"]?.jsonPrimitive?.boolean == true) {
                    val repository = session.conversations.conversationRepository
                    val id = ConversationId("conversation", user.domain)
                    assertEquals(id, assertIs<Either.Right<ConversationId>>(
                        if (account.getValue("mls").jsonPrimitive.boolean) repository.getMLSSelfConversationId()
                        else repository.getProteusSelfConversationId()).value)
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.Type.Self,
                        assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(repository.getConversationById(id)).value.type)
                    assertEquals(listOf(user), assertIs<Either.Right<List<UserId>>>(repository.getConversationMembers(id)).value)
                    assertFalse(session.messages.getMessageById(ConversationId("delete-target", user.domain), "delete-me")
                        is GetMessageByIdUseCase.Result.Success)
                    val notifications = assertIs<Either.Right<List<com.wire.kalium.logic.data.notification.LocalNotification>>>(
                        session.messages.messageRepository.getNotificationMessage()).value
                        .filterIsInstance<com.wire.kalium.logic.data.notification.LocalNotification.Conversation>()
                    assertTrue(notifications.none { it.id == id && it.messages.isNotEmpty() })
                    println("APP_SELF_CONVERSATION_PASSED")
                }
                if (account["creationEvents"]?.jsonPrimitive?.boolean == true ||
                    account["meetingConversationEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversationId = ConversationId("conversation", "example.com")
                    val conversation = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.getConversationById(conversationId)
                    ).value
                    assertEquals("Created group", conversation.name)
                    assertEquals(if (account["meetingConversationEvents"]?.jsonPrimitive?.boolean == true)
                        com.wire.kalium.logic.data.conversation.Conversation.Type.Group.Meeting
                        else com.wire.kalium.logic.data.conversation.Conversation.Type.Group.Regular, conversation.type)
                    assertEquals(2, assertIs<Either.Right<List<com.wire.kalium.logic.data.user.UserId>>>(
                        session.conversations.conversationRepository.getConversationMembers(conversationId)
                    ).value.size)
                    println("APP_CREATION_STATE_PASSED")
                }
                if (account["recoveryEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversation = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.getConversationById(ConversationId("conversation", user.domain))
                    ).value
                    val protocol = conversation.protocol as com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable
                    assertEquals(3uL, protocol.epoch)
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                        protocol.groupState)
                    println("APP_RECOVERY_STATE_PASSED")
                }
                if (account["migrationEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversation = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.getConversationById(ConversationId("conversation", user.domain))
                    ).value
                    val protocol = assertIs<com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLS>(conversation.protocol)
                    assertEquals(1uL, protocol.epoch)
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                        protocol.groupState)
                    println("APP_MIGRATION_STATE_PASSED")
                }
                if (account["resetEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversation = assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation>>(
                        session.conversations.conversationRepository.getConversationById(ConversationId("conversation", user.domain))
                    ).value
                    val protocol = conversation.protocol as com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable
                    assertEquals(account.getValue("groupId").jsonPrimitive.content, protocol.groupId.value)
                    assertEquals(1uL, protocol.epoch)
                    assertEquals(com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                        protocol.groupState)
                    println("APP_RESET_STATE_PASSED")
                }
                if (account.getValue("synchronization").jsonPrimitive.boolean) {
                    assertEquals(true, assertIs<Either.Right<Boolean>>(session.userConfigRepository.isReadReceiptsEnabled().first()).value)
                    if (!account.getValue("readState").jsonPrimitive.boolean) {
                        val receipt = assertIs<Either.Right<Message.Regular>>(
                            session.messages.messageRepository.getMessageById(
                            ConversationId("conversation", "example.com"),
                            "receipt-target"
                        )
                        ).value
                        assertEquals(1L, assertIs<Message.Status.Read>(receipt.status).readCount)
                        assertEquals(
                            false,
                            assertIs<Either.Right<Boolean>>(session.userConfigRepository.isTypingIndicatorEnabled().first()).value
                        )
                        assertTrue(session.userConfigRepository.isCellsEnabled())
                        assertTrue(session.userConfigRepository.isMeetingsEnabled())
                    }
                }
                if (account.getValue("ordinaryEvents").jsonPrimitive.boolean ||
                    (account.getValue("synchronization").jsonPrimitive.boolean && account.getValue("readState").jsonPrimitive.boolean)) {
                    val readState = account.getValue("readState").jsonPrimitive.boolean
                    val conversationId = ConversationId(if (readState) "delete-target" else "conversation", "example.com")
                    val messageId = if (readState) "read-me" else "ephemeral"
                    val repository = session.messages.messageRepository
                    val before = assertIs<Either.Right<Message.Regular>>(repository.getMessageById(conversationId, messageId)).value
                    assertEquals(60.seconds, checkNotNull(before.expirationData).expireAfter)
                    assertIs<Message.ExpirationData.SelfDeletionStatus.NotStarted>(checkNotNull(before.expirationData).selfDeletionStatus)
                    session.messages.ephemeralMessageDeletionHandler.startSelfDeletion(conversationId, messageId)
                    withTimeout(5_000) {
                        while (true) {
                            val current = assertIs<Either.Right<Message.Regular>>(repository.getMessageById(conversationId, messageId)).value
                            if (current.expirationData?.selfDeletionStatus is Message.ExpirationData.SelfDeletionStatus.Started) break
                            delay(10)
                        }
                    }
                    val pending = assertIs<Either.Right<List<Message.Regular>>>(repository.getAllPendingEphemeralMessages()).value
                    assertTrue(pending.any { it.id == messageId })
                    // Stop the app below before its timer ends. NSE has stored the message without deleting it.
                }
                if (account["bufferEvents"]?.jsonPrimitive?.boolean == true) {
                    val conversation = ConversationId("conversation", user.domain)
                    assertIs<GetMessageByIdUseCase.Result.Failure>(session.messages.getMessageById(conversation, "app-buffered"))
                    // Existing debug input enters the actual app EventProcessor, MLS unpacker
                    // and application handler under the app's crypto transaction provider.
                    assertIs<com.wire.kalium.logic.feature.debug.SynchronizeExternalDataResult.Success>(
                        session.debug.synchronizeExternalData(account.getValue("appBufferResponse").toString())
                    )
                    val message = assertIs<GetMessageByIdUseCase.Result.Success>(
                        session.messages.getMessageById(conversation, "app-buffered")
                    ).message
                    assertEquals("app-buffered", assertIs<MessageContent.Text>(assertIs<Message.Regular>(message).content).value)
                    assertEquals(UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain), message.senderUserId)
                    println("APP_NATIVE_BUFFER_CONSUMED")
                }
                val cipher = if (afterProposal) FileSystem.SYSTEM.read("$root/after-app-proposal".toPath()) { readByteArray() }
                    else Base64.decode(account.getValue("appNext").jsonPrimitive.content)
                val mls = account.getValue("mls").jsonPrimitive.boolean
                val useMLS = mls && !(account["mlsSettingsEvents"]?.jsonPrimitive?.boolean == true &&
                    !account.getValue("mlsConfiguredEnabled").jsonPrimitive.boolean)
                val plain = if (useMLS) {
                    val client = assertIs<Either.Right<MLSClient>>(session.mlsClientProvider.getMLSClient()).value
                    client.transaction {
                        val groupId = account["groupId"]?.jsonPrimitive?.content ?: GROUP_ID
                        val messages = assertIs<MLSDecryptResult.Success>(it.decryptMessage(groupId, cipher)).messages
                        assertIs<DecryptedMessageBundle.Text>(messages.single()).message
                    }
                } else session.proteusClientProvider.getOrCreate().transaction("app-after-swift") {
                    it.decryptMessage(CryptoSessionId(
                            UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain).toCrypto(), CryptoClientId("def456")), cipher) { bytes -> bytes }
                }
                val decoded = assertIs<ProtoContent.Readable>(ProtoContentMapperImpl(selfUserId = user).decodeFromProtobuf(PlainMessageBlob(plain)))
                assertEquals(if (afterProposal) "after-app-proposal" else "app-next", (decoded.messageContent as MessageContent.Text).value)
                account["proteusAppNext"]?.let { next ->
                    val stored = assertIs<GetMessageByIdUseCase.Result.Success>(session.messages.getMessageById(
                        ConversationId(account["proteusConversationId"]?.jsonPrimitive?.content ?: "delete-target", user.domain), "proteus-first"
                    )).message
                    assertEquals("proteus-first", (assertIs<Message.Regular>(stored).content as MessageContent.Text).value)
                    val bytes = session.proteusClientProvider.getOrCreate().transaction("app-mixed-after-swift") {
                        it.decryptMessage(CryptoSessionId(
                            UserId(account.getValue("senderValue").jsonPrimitive.content, user.domain).toCrypto(), CryptoClientId("def456")
                        ), Base64.decode(next.jsonPrimitive.content)) { it }
                    }
                    val content = assertIs<ProtoContent.Readable>(
                        ProtoContentMapperImpl(selfUserId = user).decodeFromProtobuf(PlainMessageBlob(bytes))
                    ).messageContent
                    assertEquals("proteus-app-next", (content as MessageContent.Text).value)
                    println("APP_MIXED_PROTOCOLS_PASSED")
                }
                if (!afterProposal && account["pendingProposal"]?.jsonPrimitive?.boolean == true) {
                    // Drive the existing app scheduler's real live-sync gate after fixture catch-up.
                    session.incrementalSyncRepository.updateIncrementalSyncState(com.wire.kalium.logic.data.sync.IncrementalSyncStatus.Live)
                    val client = assertIs<Either.Right<MLSClient>>(session.mlsClientProvider.getMLSClient()).value
                    withTimeout(15_000) {
                        while (client.transaction { it.conversationEpoch(GROUP_ID) } != 3uL) delay(20)
                        val id = ConversationId("conversation", user.domain)
                        val removed = UserId("bcc012da-0478-46ac-aac7-b02c4ab7b144", user.domain)
                        while (assertIs<Either.Right<com.wire.kalium.logic.data.conversation.Conversation.Member.Role?>>(
                            session.conversations.conversationRepository.getConversationMemberRole(id, removed)).value != null) delay(20)

                    }
                    println("APP_PROPOSAL_SCHEDULER_PASSED")
                    println("APP_PROPOSAL_RESPONSE_CONSUMED")
                }
                println("APP_AFTER_SWIFT_PASSED ${if (mls) "MLS" else "Proteus"}")
            } finally {
                logic.getGlobalScope().coroutineContext.job.cancelAndJoin()
                logic.releaseSessionScope(user)
            }
            val disabledMLS = account["mlsSettingsEvents"]?.jsonPrimitive?.boolean == true &&
                account.getValue("mls").jsonPrimitive.boolean && !account.getValue("mlsConfiguredEnabled").jsonPrimitive.boolean
            if (disabledMLS) {
                // The app's ordinary release cannot re-certify a disabled MLS client.
                repeat(2) {
                    assertEquals(Either.Left(com.wire.kalium.logic.sync.receiving.AccountRuntimeFailure.NOT_PREPARED),
                        AppleAccountRuntime.use(root, user, keychain, "disabled-mls-reopen", emptyMap()) {
                            kotlin.test.fail("Disabled MLS readiness unexpectedly survived app release")
                        })
                }
                val lease = assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(user)).value
                try {
                    assertNull(ApplePreparedAccountStore(root, user).read(lease))
                    val database = com.wire.kalium.persistence.db.existingUserDatabaseBuilder(
                        "${com.wire.kalium.logic.sync.receiving.appleAccountPath(root, user)}/storage", user.toDao(), Dispatchers.Default, com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider(keychain).passphraseStorage.existingUserDatabaseSecret(user.toDao()))
                    try {
                        assertTrue(checkNotNull(database.eventDAO.getEventById("notification")).isProcessed)
                        assertFalse(database.userPrefsDAO.isMLSEnabled())
                        assertEquals("abc123", com.wire.kalium.persistence.client.ClientRegistrationStorageImpl(database.metadataDAO)
                            .getRegisteredClientId())
                        assertTrue(com.wire.kalium.persistence.client.ClientRegistrationStorageImpl(database.metadataDAO).hasRegisteredMLSClient())
                    } finally {
                        database.close()
                    }
                } finally {
                    lease.release()
                }
                println("APP_DISABLED_MLS_READINESS_REVOKED")
            } else assertIs<Either.Right<*>>(AppleAccountRuntime.use(root, user, keychain, "app-after-swift-check", emptyMap()) { runtime ->
                assertTrue(checkNotNull(runtime.database.eventDAO.getEventById("notification")).isProcessed)
                if (account["connectionEvents"]?.jsonPrimitive?.boolean == true) {
                    assertTrue(runtime.database.connectionDAO.getConnectionRequestsForNotification().first().isEmpty())
                    assertEquals(false, runtime.database.connectionDAO.getConnection(ConversationId("request", user.domain).toDao())?.shouldNotify)
                    println("APP_CONNECTION_ACKNOWLEDGEMENT_PASSED")
                }
                if (account["assetEvents"]?.jsonPrimitive?.boolean == true) {
                    for (id in listOf("asset", "image", "audio", "video")) {
                        val asset = checkNotNull(runtime.database.assetDAO.getAssetByKey(id).first())
                        assertEquals(account.getValue("assetText").jsonPrimitive.content,
                            FileSystem.SYSTEM.read(asset.dataPath.toPath()) { readUtf8() })
                        assertEquals(com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity.SAVED_INTERNALLY,
                            runtime.database.messageDAO.getMessageAssetTransferStatus(id, ConversationId("conversation", user.domain).toDao()))
                    }
                }
                if (account["typingEvents"]?.jsonPrimitive?.boolean == true) {
                    assertEquals("rest-anchor", runtime.database.notificationEventStore.cursor())
                    println("APP_TRANSIENT_QUEUE_PASSED")
                }
                if (account["pendingProposal"]?.jsonPrimitive?.boolean == true) {
                    assertTrue(runtime.database.conversationDAO.getProposalTimers().first().isEmpty())
                    val id = ConversationId("conversation", user.domain).toDao()
                    val removed = UserId("bcc012da-0478-46ac-aac7-b02c4ab7b144", user.domain).toDao()
                    assertNull(runtime.database.memberDAO.getMemberRole(removed, id))
                    assertTrue(runtime.database.messageDAO.getMessagesByConversationAndVisibility(id, 100, 0).first().any {
                        it.content == com.wire.kalium.persistence.dao.message.MessageEntityContent.MemberChange(
                            listOf(removed), com.wire.kalium.persistence.dao.message.MessageEntity.MemberChangeType.REMOVED)
                    })

                }
            })
        }
    }
}

private val GROUP_ID = Base64.encode("receiving-test-group".encodeToByteArray())
