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

package com.wire.kalium.logic.feature.message

import app.cash.turbine.test
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationCommentType
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.notification.LocalNotificationUpdateMessageAction
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class GetNotificationsUseCaseTest {

    @Test
    fun givenSyncStateChangedToLive_thenRepositoriesAreUsedToFetchNotifications() = runTest {
        val syncStatusFlow = MutableSharedFlow<IncrementalSyncStatus>(1)
        val expectedMessages = listOf(notificationMessageText(), notificationMessageComment())
        val expectedConversations = listOf(localNotificationConversation(messages = expectedMessages))
        val (arrange, getNotifications) = arrange {
            withLocalNotifications(Either.Right(expectedConversations))
            withConnectionList(flowOf(listOf()))
            withIncrementalSyncState(syncStatusFlow)
            withEphemeralNotification()
            withRegularNotificationsChecking(flowOf(Unit))
        }

        getNotifications().test {
            syncStatusFlow.emit(IncrementalSyncStatus.FetchingPendingEvents)

            verifySuspend(VerifyMode.not) {
                arrange.messageRepository.getNotificationMessage(any())
            }

            verifySuspend(VerifyMode.not) {
                arrange.connectionRepository.observeConnectionRequestsForNotification()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrange.notificationEventsManager.observeEphemeralNotifications()
            }

            syncStatusFlow.emit(IncrementalSyncStatus.Live)

            verifySuspend(VerifyMode.exactly(2)) {
                arrange.messageRepository.getNotificationMessage(any())
            } // first onStart

            verifySuspend(VerifyMode.exactly(1)) {
                arrange.connectionRepository.observeConnectionRequestsForNotification()
            }

            verifySuspend(VerifyMode.atLeast(1)) {
                arrange.notificationEventsManager.observeEphemeralNotifications()
            }

            val result = awaitItem()
            assertContentEquals(expectedConversations, result)

            awaitItem()
        }
    }

    @Test
    fun givenSyncStateChangedToPending_thenRepositoriesAreUsedToFetchNotifications() = runTest {
        val syncStatusFlow = MutableSharedFlow<IncrementalSyncStatus>(1)
        val expectedMessages = listOf(notificationMessageText(), notificationMessageComment())
        val expectedConversations = listOf(localNotificationConversation(messages = expectedMessages))
        val (arrange, getNotifications) = arrange {
            withLocalNotifications(Either.Right(expectedConversations))
            withConnectionList(flowOf(listOf()))
            withIncrementalSyncState(syncStatusFlow)
            withEphemeralNotification()
            withRegularNotificationsChecking(flowOf())
        }

        getNotifications().test {
            syncStatusFlow.emit(IncrementalSyncStatus.FetchingPendingEvents)

            verifySuspend(VerifyMode.not) {
                arrange.messageRepository.getNotificationMessage(any())
            }

            verifySuspend(VerifyMode.not) {
                arrange.connectionRepository.observeConnectionRequestsForNotification()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrange.notificationEventsManager.observeEphemeralNotifications()
            }

            syncStatusFlow.emit(IncrementalSyncStatus.Pending)

            verifySuspend(VerifyMode.exactly(1)) {
                arrange.messageRepository.getNotificationMessage(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrange.connectionRepository.observeConnectionRequestsForNotification()
            }

            verifySuspend(VerifyMode.atLeast(1)) {
                arrange.notificationEventsManager.observeEphemeralNotifications()
            }

            val result = awaitItem()
            assertContentEquals(expectedConversations, result)
        }
    }

    @Test
    fun givenSyncStateChangedToFailure_thenRepositoriesAreUsedToFetchNotifications() = runTest {
        val syncStatusFlow = MutableSharedFlow<IncrementalSyncStatus>(1)
        val expectedMessages = listOf(notificationMessageText(), notificationMessageComment())
        val expectedConversations = listOf(localNotificationConversation(messages = expectedMessages))
        val (arrange, getNotifications) = arrange {
            withLocalNotifications(Either.Right(expectedConversations))
            withConnectionList(flowOf(listOf()))
            withIncrementalSyncState(syncStatusFlow)
            withEphemeralNotification()
            withRegularNotificationsChecking(flowOf(Unit))
        }

        getNotifications().test {
            syncStatusFlow.emit(IncrementalSyncStatus.FetchingPendingEvents)

            verifySuspend(VerifyMode.not) {
                arrange.messageRepository.getNotificationMessage(any())
            }

            verifySuspend(VerifyMode.not) {
                arrange.connectionRepository.observeConnectionRequestsForNotification()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrange.notificationEventsManager.observeEphemeralNotifications()
            }

            syncStatusFlow.emit(IncrementalSyncStatus.Failed(CoreFailure.Unknown(null), Duration.ZERO))

            verifySuspend(VerifyMode.exactly(2)) {
                arrange.messageRepository.getNotificationMessage(any())
            } // first onStart

            verifySuspend(VerifyMode.exactly(1)) {
                arrange.connectionRepository.observeConnectionRequestsForNotification()
            }

            verifySuspend(VerifyMode.atLeast(1)) {
                arrange.notificationEventsManager.observeEphemeralNotifications()
            }

            val result = awaitItem()
            assertContentEquals(expectedConversations, result)

            awaitItem()
        }
    }

    @Test
    fun givenEmptyConversationList_thenNoItemsAreEmitted() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(listOf()))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification(
                flowOf(
                    LocalNotification.Conversation(
                        conversationId(1), "some convo", listOf(), false
                    )
                )
            )
            withRegularNotificationsChecking(flowOf())
        }

        getNotifications().test {
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithEmptyMessageList_thenNoItemsAreEmitted() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(listOf(localNotificationConversation())))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification(
                flowOf(
                    LocalNotification.Conversation(
                        conversationId(1), "some convo", listOf(), false
                    )
                )
            )
            withRegularNotificationsChecking(flowOf(Unit))
        }

        getNotifications().test {
            awaitComplete()
        }
    }

    @Test
    fun givenUpdateMessageFromEphemeralManager_thenCorrespondingItemsAreEmitted() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(listOf()))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification(flowOf(localNotificationUpdateMessage()))
            withRegularNotificationsChecking(flowOf(Unit))
        }

        getNotifications().test {
            assertEquals(listOf(localNotificationUpdateMessage()), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenUpdateMessageFromEphemeralManager_2_thenCorrespondingItemsAreEmitted() = runTest {
        val ephemeralNotification = localNotificationUpdateMessage(
            action = LocalNotificationUpdateMessageAction.Edit("updated text", "newId")
        )
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(listOf()))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification(flowOf(ephemeralNotification))
            withRegularNotificationsChecking(flowOf())
        }

        getNotifications().test {
            assertEquals(listOf(ephemeralNotification), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenConnectionRequests_thenNotificationListWithConnectionRequestMessage() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(listOf()))
            withConnectionList(flowOf(listOf(connectionRequest())))
            withEphemeralNotification()
            withRegularNotificationsChecking(flowOf())
        }

        getNotifications().test {
            val actualToCheck = awaitItem()

            assertEquals(1, actualToCheck.size)
            assertEquals(
                listOf(
                    notificationMessageConnectionRequest(authorName = otherUserName(otherUserId()))
                ),
                (actualToCheck.first { notification ->
                    notification is LocalNotification.Conversation
                            && notification.messages.any { it is LocalNotificationMessage.ConnectionRequest }
                } as LocalNotification.Conversation).messages
            )
            awaitComplete()
        }
    }

    @Test
    fun givenNoNewNotifications_thenShouldNotEmitAny() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(listOf()))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification()
            withRegularNotificationsChecking(flowOf())
        }

        getNotifications().test {
            awaitComplete()
        }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) {
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val connectionRepository = mock<ConnectionRepository>(mode = MockMode.autoUnit)
        val incrementalSyncRepository = mock<IncrementalSyncRepository>(mode = MockMode.autoUnit)
        val notificationEventsManager = mock<NotificationEventsManager>(mode = MockMode.autoUnit)

        suspend fun arrange() = run {
            this@Arrangement to GetNotificationsUseCaseImpl(
                connectionRepository = connectionRepository,
                messageRepository = messageRepository,
                notificationEventsManager = notificationEventsManager,
                incrementalSyncRepository = incrementalSyncRepository
            )
        }.also {
            every { incrementalSyncRepository.incrementalSyncState }
                .returns(flowOf(IncrementalSyncStatus.Live))

            block()
        }

        suspend fun withLocalNotifications(list: Either<CoreFailure, List<LocalNotification>>) {
            everySuspend { messageRepository.getNotificationMessage(any()) } returns list
        }

        suspend fun withConnectionList(connectionsFlow: Flow<List<ConversationDetails>>) {
            everySuspend { connectionRepository.observeConnectionRequestsForNotification() } returns connectionsFlow
        }

        fun withIncrementalSyncState(statusFlow: Flow<IncrementalSyncStatus>) {
            every { incrementalSyncRepository.incrementalSyncState } returns statusFlow
        }

        suspend fun withEphemeralNotification(ephemeralsFlow: Flow<LocalNotification> = flowOf()) {
            everySuspend { notificationEventsManager.observeEphemeralNotifications() } returns ephemeralsFlow
        }

        suspend fun withRegularNotificationsChecking(flow: Flow<Unit>) {
            everySuspend { notificationEventsManager.observeRegularNotificationsChecking() } returns flow
        }
    }

    companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
        private val MY_ID = TestUser.USER_ID
        private val TIME = Instant.fromEpochMilliseconds(948558215)
        private val TIME_INSTANCE = Instant.fromEpochMilliseconds(948558215)
        private val TIME_EARLIER = TIME - 10.days

        private fun conversationId(number: Int = 0) =
            QualifiedID("conversation_id_${number}_value", "conversation_id_${number}_domain")

        private fun localNotificationConversation(
            messages: List<LocalNotificationMessage> = emptyList(),
            conversationIdSeed: Int = 0,
            isOneOnOne: Boolean = true,
        ) = LocalNotification.Conversation(
            conversationId(conversationIdSeed),
            conversationName = "conversation_$conversationIdSeed",
            messages = messages,
            isOneToOneConversation = isOneOnOne
        )

        private fun localNotificationUpdateMessage(
            action: LocalNotificationUpdateMessageAction = LocalNotificationUpdateMessageAction.Delete,
            conversationIdSeed: Int = 0,
            messageId: String = "message_id",
        ) = LocalNotification.UpdateMessage(
            conversationId(conversationIdSeed),
            messageId = messageId,
            action = action
        )

        private fun entityTextMessage(
            conversationId: QualifiedID,
            senderId: QualifiedID = TestUser.USER_ID,
            messageId: String = "message_id",
            content: MessageContent.Regular = MessageContent.Text("test message $messageId"),
            visibility: Message.Visibility = Message.Visibility.VISIBLE
        ) = Message.Regular(
            id = messageId,
            content = content,
            conversationId = conversationId,
            date = TIME,
            senderUserId = senderId,
            senderClientId = ClientId("client_1"),
            status = Message.Status.Sent,
            editStatus = Message.EditStatus.NotEdited,
            visibility = visibility,
            isSelfMessage = false
        )

        private fun entityAssetMessage(
            conversationId: QualifiedID,
            senderId: QualifiedID = TestUser.USER_ID,
            messageId: String = "message_id",
            assetId: String
        ) =
            Message.Regular(
                id = messageId,
                content = MessageContent.Asset(
                    AssetContent(
                        sizeInBytes = 1000,
                        name = "some_asset.jpg",
                        mimeType = "image/jpeg",
                        metadata = AssetContent.AssetMetadata.Image(width = 100, height = 100),
                        remoteData = AssetContent.RemoteData(
                            otrKey = ByteArray(0),
                            sha256 = ByteArray(16),
                            assetId = assetId,
                            assetToken = "==some-asset-token",
                            assetDomain = "some-asset-domain.com",
                            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
                        )
                    )
                ),
                conversationId = conversationId,
                date = TIME,
                senderUserId = senderId,
                senderClientId = ClientId("client_1"),
                status = Message.Status.Sent,
                editStatus = Message.EditStatus.NotEdited,
                isSelfMessage = false
            )

        private fun entityServerMessage(
            conversationId: QualifiedID,
            senderId: QualifiedID = TestUser.USER_ID,
            messageId: String = "message_id"
        ) =
            Message.System(
                id = messageId,
                content = MessageContent.MemberChange.Removed(listOf(senderId)),
                conversationId = conversationId,
                date = Instant.DISTANT_PAST,
                senderUserId = senderId,
                status = Message.Status.Sent,
                expirationData = null
            )

        private fun notificationMessageText(
            authorName: String = "Author Name",
            time: Instant = TIME_INSTANCE,
            text: String = "test text",
            messageId: String = "message_id"
        ) =
            LocalNotificationMessage.Text(
                messageId,
                LocalNotificationMessageAuthor(authorName, null),
                time,
                text
            )

        private fun notificationMessageComment(
            authorName: String = "Author Name",
            time: Instant = TIME_INSTANCE,
            commentType: LocalNotificationCommentType = LocalNotificationCommentType.PICTURE,
            messageId: String = "message_id"
        ) =
            LocalNotificationMessage.Comment(
                messageId,
                LocalNotificationMessageAuthor(authorName, null),
                time,
                commentType
            )

        private fun notificationMessageConnectionRequest(
            authorName: String = "Author Name",
            time: Instant = TIME_INSTANCE,
            messageId: String = ""
        ) =
            LocalNotificationMessage.ConnectionRequest(
                messageId,
                LocalNotificationMessageAuthor(authorName, null),
                time,
                otherUserId()
            )

        private fun otherUser(id: QualifiedID) = TestUser.OTHER.copy(id = id, name = otherUserName(id))

        private fun otherUserId(number: Int = 0) =
            QualifiedID("other_user_id_${number}_value", "domain")

        private fun otherUserName(id: QualifiedID) = "Other User Name ${id.value}"

        private fun connectionRequest() = ConversationDetails.Connection(
            conversationId = conversationId(0),
            otherUser = otherUser(TestUser.USER_ID.copy(value = "other_user_id_0_value")),
            userType = UserTypeInfo.Regular(UserType.EXTERNAL),
            lastModifiedDate = TIME,
            connection = Connection(
                "conversationId",
                "",
                TIME,
                conversationId(0),
                TestUser.USER_ID.copy(value = "other_user_id_0_value"),
                ConnectionState.SENT,
                "told",
                null
            ),
            ProtocolInfo.Proteus,
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST)
        )

    }
}
