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
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationCommentType
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.notification.LocalNotificationUpdateMessageAction
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.IncrementalSyncRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.IncrementalSyncRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.EphemeralEventsNotificationManagerArrangement
import com.wire.kalium.logic.util.arrangement.usecase.EphemeralEventsNotificationManagerArrangementImpl
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.any
import io.mockative.given
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GetNotificationsUseCaseTest {

    @Test
    fun givenSyncStateChangedToLive_thenRepositoriesAreUsedToFetchNotifications() = runTest {
        val syncStatusFlow = MutableSharedFlow<IncrementalSyncStatus>(1)
        val expectedMessages = listOf(notificationMessageText(), notificationMessageComment())
        val expectedConversations = listOf(localNotificationConversation(messages = expectedMessages))
        val (arrange, getNotifications) = arrange {
            withLocalNotifications(Either.Right(flowOf(expectedConversations)))
            withConnectionList(flowOf(listOf()))
            withIncrementalSyncState(syncStatusFlow)
            withEphemeralNotification()
        }

        getNotifications().test {
            syncStatusFlow.emit(IncrementalSyncStatus.FetchingPendingEvents)

            verify(arrange.messageRepository)
                .suspendFunction(arrange.messageRepository::getNotificationMessage)
                .with(any())
                .wasNotInvoked()

            verify(arrange.connectionRepository)
                .suspendFunction(arrange.connectionRepository::observeConnectionRequestsForNotification)
                .wasNotInvoked()

            verify(arrange.ephemeralNotifications)
                .suspendFunction(arrange.ephemeralNotifications::observeEphemeralNotifications)
                .wasInvoked(exactly = once)

            syncStatusFlow.emit(IncrementalSyncStatus.Live)

            verify(arrange.messageRepository)
                .suspendFunction(arrange.messageRepository::getNotificationMessage)
                .with(any())
                .wasInvoked(exactly = once)

            verify(arrange.connectionRepository)
                .suspendFunction(arrange.connectionRepository::observeConnectionRequestsForNotification)
                .wasInvoked(exactly = once)

            verify(arrange.ephemeralNotifications)
                .suspendFunction(arrange.ephemeralNotifications::observeEphemeralNotifications)
                .wasInvoked(atLeast = once)

            val result = awaitItem()
            assertContentEquals(expectedConversations, result)
        }
    }

    @Test
    fun givenSyncStateChangedToPending_thenRepositoriesAreUsedToFetchNotifications() = runTest {
        val syncStatusFlow = MutableSharedFlow<IncrementalSyncStatus>(1)
        val expectedMessages = listOf(notificationMessageText(), notificationMessageComment())
        val expectedConversations = listOf(localNotificationConversation(messages = expectedMessages))
        val (arrange, getNotifications) = arrange {
            withLocalNotifications(Either.Right(flowOf(expectedConversations)))
            withConnectionList(flowOf(listOf()))
            withIncrementalSyncState(syncStatusFlow)
            withEphemeralNotification()
        }

        getNotifications().test {
            syncStatusFlow.emit(IncrementalSyncStatus.FetchingPendingEvents)

            verify(arrange.messageRepository)
                .suspendFunction(arrange.messageRepository::getNotificationMessage)
                .with(any())
                .wasNotInvoked()

            verify(arrange.connectionRepository)
                .suspendFunction(arrange.connectionRepository::observeConnectionRequestsForNotification)
                .wasNotInvoked()

            verify(arrange.ephemeralNotifications)
                .suspendFunction(arrange.ephemeralNotifications::observeEphemeralNotifications)
                .wasInvoked(exactly = once)

            syncStatusFlow.emit(IncrementalSyncStatus.Pending)

            verify(arrange.messageRepository)
                .suspendFunction(arrange.messageRepository::getNotificationMessage)
                .with(any())
                .wasInvoked(exactly = once)

            verify(arrange.connectionRepository)
                .suspendFunction(arrange.connectionRepository::observeConnectionRequestsForNotification)
                .wasInvoked(exactly = once)

            verify(arrange.ephemeralNotifications)
                .suspendFunction(arrange.ephemeralNotifications::observeEphemeralNotifications)
                .wasInvoked(atLeast = once)

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
            withLocalNotifications(Either.Right(flowOf(expectedConversations)))
            withConnectionList(flowOf(listOf()))
            withIncrementalSyncState(syncStatusFlow)
            withEphemeralNotification()
        }

        getNotifications().test {
            syncStatusFlow.emit(IncrementalSyncStatus.FetchingPendingEvents)

            verify(arrange.messageRepository)
                .suspendFunction(arrange.messageRepository::getNotificationMessage)
                .with(any())
                .wasNotInvoked()

            verify(arrange.connectionRepository)
                .suspendFunction(arrange.connectionRepository::observeConnectionRequestsForNotification)
                .wasNotInvoked()

            verify(arrange.ephemeralNotifications)
                .suspendFunction(arrange.ephemeralNotifications::observeEphemeralNotifications)
                .wasInvoked(exactly = once)

            syncStatusFlow.emit(IncrementalSyncStatus.Failed(CoreFailure.Unknown(null)))

            verify(arrange.messageRepository)
                .suspendFunction(arrange.messageRepository::getNotificationMessage)
                .with(any())
                .wasInvoked(exactly = once)

            verify(arrange.connectionRepository)
                .suspendFunction(arrange.connectionRepository::observeConnectionRequestsForNotification)
                .wasInvoked(exactly = once)

            verify(arrange.ephemeralNotifications)
                .suspendFunction(arrange.ephemeralNotifications::observeEphemeralNotifications)
                .wasInvoked(atLeast = once)

            val result = awaitItem()
            assertContentEquals(expectedConversations, result)
        }
    }

    @Test
    fun givenEmptyConversationList_thenNoItemsAreEmitted() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(flowOf(listOf())))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification(
                flowOf(
                    LocalNotification.Conversation(
                        conversationId(1), "some convo", listOf(), false
                    )
                )
            )
        }

        getNotifications().test {
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithEmptyMessageList_thenNoItemsAreEmitted() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(flowOf(listOf(localNotificationConversation()))))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification(
                flowOf(
                    LocalNotification.Conversation(
                        conversationId(1), "some convo", listOf(), false
                    )
                )
            )
        }

        getNotifications().test {
            awaitComplete()
        }
    }

    @Test
    fun givenUpdateMessageFromEphemeralManager_thenCorrespondingItemsAreEmitted() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(flowOf(listOf())))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification(flowOf(localNotificationUpdateMessage()))
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
            withLocalNotifications(Either.Right(flowOf()))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification(flowOf(ephemeralNotification))
        }

        getNotifications().test {
            assertEquals(listOf(ephemeralNotification), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenConnectionRequests_thenNotificationListWithConnectionRequestMessage() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(emptyFlow()))
            withConnectionList(flowOf(listOf(connectionRequest())))
            withEphemeralNotification()
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
    fun givenNoNewNotifications_thenShouldNotEmitAnything() = runTest {
        val (_, getNotifications) = arrange {
            withLocalNotifications(Either.Right(emptyFlow()))
            withConnectionList(flowOf(listOf()))
            withEphemeralNotification()
        }

        getNotifications().test {
            awaitComplete()
        }
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        IncrementalSyncRepositoryArrangement by IncrementalSyncRepositoryArrangementImpl(),
        EphemeralEventsNotificationManagerArrangement by EphemeralEventsNotificationManagerArrangementImpl(),
        ConnectionRepositoryArrangement by ConnectionRepositoryArrangementImpl() {

        init {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationNotificationDate)
                .whenInvokedWith(any())
                .then { _ -> Either.Right(Unit) }

            given(conversationRepository)
                .suspendFunction(conversationRepository::updateAllConversationsNotificationDate)
                .whenInvoked()
                .then { Either.Right(Unit) }
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::incrementalSyncState)
                .whenInvoked()
                .then { flowOf(IncrementalSyncStatus.Live) }
        }

        fun arrange() = block().run {
            this@Arrangement to GetNotificationsUseCaseImpl(
                connectionRepository = connectionRepository,
                messageRepository = messageRepository,
                deleteConversationNotificationsManager = ephemeralNotifications,
                incrementalSyncRepository = incrementalSyncRepository
            )
        }
    }

    companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
        private val MY_ID = TestUser.USER_ID
        private val TIME = Instant.fromEpochMilliseconds(948558215).toIsoDateTimeString()
        private val TIME_INSTANCE = Instant.fromEpochMilliseconds(948558215)
        private const val TIME_EARLIER = "2000-01-23T01:23:30.678+09:00"

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
        ) =
            Message.Regular(
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
                date = "some_time",
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
            userType = UserType.EXTERNAL,
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
