/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationCommentType
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GetNotificationsUseCaseTest {

    @Test
    fun givenSyncStateChangedToLive_thenRepositoriesAreUsedToFetchNotifications() = runTest {
        val syncStatusFlow = MutableSharedFlow<IncrementalSyncStatus>(1)
        val expectedMessages = listOf(notificationMessageText(), notificationMessageComment())
        val expectedConversations = listOf(localNotificationConversation(messages = expectedMessages))
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withIncrementalSyncState(syncStatusFlow)
            .withConnectionList(listOf())
            .withConversationsForNotifications(flowOf(expectedConversations)).arrange()

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
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withIncrementalSyncState(syncStatusFlow)
            .withConnectionList(listOf())
            .withConversationsForNotifications(flowOf(expectedConversations)).arrange()

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
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withIncrementalSyncState(syncStatusFlow)
            .withConnectionList(listOf())
            .withConversationsForNotifications(flowOf(expectedConversations)).arrange()

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
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification(
                LocalNotificationConversation(
                    conversationId(1), "some convo", listOf(), false
                )
            )
            .withConnectionList(listOf())
            .withConversationsForNotifications(flowOf(listOf()))
            .arrange()

        getNotifications().test {
            expectNoEvents()
        }
    }

    @Test
    fun givenConversationWithEmptyMessageList_thenNoItemsAreEmitted() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification(
                LocalNotificationConversation(
                    conversationId(1), "some convo", listOf(), false
                )
            )
            .withConnectionList(listOf())
            .withConversationsForNotifications(flowOf(listOf(localNotificationConversation())))
            .arrange()

        getNotifications().test {
            expectNoEvents()
        }
    }

    @Test
    fun givenConversationWithOnlyMyMessageList_thenNoItemsAreEmitted() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withConversationsForNotifications(flowOf(listOf(localNotificationConversation(messages = emptyList()))))
            .arrange()

        getNotifications().test {
            expectNoEvents()
        }
    }

    @Test
    fun givenSelfUserWithStatusAway_whenNewMessageCome_thenNoNotificationsAreEmitted() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withConversationsForNotifications(flowOf(listOf(localNotificationConversation(messages = emptyList()))))
            .arrange()

        getNotifications().test {
            expectNoEvents()
        }
    }

    @Test
    fun givenConnectionRequests_thenNotificationListWithConnectionRequestMessage() = runTest {
        val (_, getNotifications) = Arrangement()
            .withConnectionList(listOf(connectionRequest()))
            .withConversationsForNotifications(emptyFlow())
            .withEphemeralNotification()
            .arrange()

        getNotifications().test {
            val actualToCheck = awaitItem()

            assertEquals(1, actualToCheck.size)
            assertEquals(
                listOf(
                    notificationMessageConnectionRequest(authorName = otherUserName(otherUserId()))
                ),
                actualToCheck.first { message -> message.messages.any { it is LocalNotificationMessage.ConnectionRequest } }.messages
            )
            awaitComplete()
        }
    }

    @Test
    fun givenNoNewNotifications_thenShouldNotEmitAnything() = runTest {
        val (_, getNotifications) = Arrangement()
            .withConnectionList(listOf())
            .withConversationsForNotifications(emptyFlow())
            .withEphemeralNotification(null)
            .arrange()

        getNotifications().test {
            expectNoEvents()
        }
    }

    private class Arrangement {
        @Mock
        val connectionRepository = mock(classOf<ConnectionRepository>())

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val ephemeralNotifications = mock(classOf<DeleteConversationNotificationsManager>())

        @Mock
        private val incrementalSyncRepository = mock(classOf<IncrementalSyncRepository>())

        val getNotificationsUseCase: GetNotificationsUseCase = GetNotificationsUseCaseImpl(
            connectionRepository = connectionRepository,
            messageRepository = messageRepository,
            deleteConversationNotificationsManager = ephemeralNotifications,
            incrementalSyncRepository = incrementalSyncRepository
        )

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

        fun withConversationsForNotifications(list: Flow<List<LocalNotificationConversation>> = emptyFlow()): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::getNotificationMessage)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(list))

            return this
        }

        fun withIncrementalSyncState(statusFlow: Flow<IncrementalSyncStatus>): Arrangement {
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::incrementalSyncState)
                .whenInvoked()
                .then { statusFlow }

            return this
        }

        fun withConnectionList(connections: List<ConversationDetails>?) = apply {
            given(connectionRepository)
                .suspendFunction(connectionRepository::observeConnectionRequestsForNotification)
                .whenInvoked()
                .thenReturn(connections?.let { flowOf(it) } ?: flowOf())
        }

        fun withEphemeralNotification(ephemeral: LocalNotificationConversation? = null) = apply {
            given(ephemeralNotifications)
                .suspendFunction(ephemeralNotifications::observeEphemeralNotifications)
                .whenInvoked()
                .thenReturn(ephemeral?.let { flowOf(it) } ?: flowOf())
        }

        fun arrange() = this to getNotificationsUseCase
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
        ) = LocalNotificationConversation(
            conversationId(conversationIdSeed),
            conversationName = "conversation_$conversationIdSeed",
            messages = messages,
            isOneToOneConversation = isOneOnOne
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
                status = Message.Status.SENT,
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
                        ),
                        downloadStatus = Message.DownloadStatus.NOT_DOWNLOADED
                    )
                ),
                conversationId = conversationId,
                date = TIME,
                senderUserId = senderId,
                senderClientId = ClientId("client_1"),
                status = Message.Status.SENT,
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
                status = Message.Status.SENT,
                expirationData = null
            )

        private fun notificationMessageText(
            authorName: String = "Author Name",
            time: Instant = TIME_INSTANCE,
            text: String = "test text"
        ) =
            LocalNotificationMessage.Text(
                LocalNotificationMessageAuthor(authorName, null),
                time,
                text
            )

        private fun notificationMessageComment(
            authorName: String = "Author Name",
            time: Instant = TIME_INSTANCE,
            commentType: LocalNotificationCommentType = LocalNotificationCommentType.PICTURE
        ) =
            LocalNotificationMessage.Comment(
                LocalNotificationMessageAuthor(authorName, null),
                time,
                commentType
            )

        private fun notificationMessageConnectionRequest(
            authorName: String = "Author Name",
            time: Instant = TIME_INSTANCE
        ) =
            LocalNotificationMessage.ConnectionRequest(
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
