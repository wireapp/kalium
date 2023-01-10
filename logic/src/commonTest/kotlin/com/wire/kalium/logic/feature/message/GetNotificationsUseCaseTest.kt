package com.wire.kalium.logic.feature.message

import app.cash.turbine.test
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
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GetNotificationsUseCaseTest {

    @Ignore
    @Test
    fun givenEmptyConversationList_thenEmptyNotificationList() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification(
                LocalNotificationConversation(
                    conversationId(1), "some convo", listOf(), false
                )
            )
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf())
            .arrange()

        getNotifications().test {
            val actual1 = awaitItem()
            val actual2 = awaitItem()
            val actual3 = awaitItem()
            val actualToCheck = if (actual2.size > actual1.size) {
                if (actual2.size > actual3.size) actual2 else actual3
            } else {
                if (actual3.size > actual1.size) actual3 else actual1
            }

            assertEquals(1, actualToCheck.size)
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithEmptyMessageList_thenEmptyNotificationList() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification(
                LocalNotificationConversation(
                    conversationId(1), "some convo", listOf(), false
                )
            )
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf(localNotificationConversation()))
            .arrange()

        getNotifications().test {
            val actual1 = awaitItem()
            val actual2 = awaitItem()
            val actual3 = awaitItem()
            val actualToCheck = if (actual2.size > actual1.size) {
                if (actual2.size > actual3.size) actual2 else actual3
            } else {
                if (actual3.size > actual1.size) actual3 else actual1
            }

            assertEquals(1, actualToCheck.size)

            awaitComplete()
        }
    }

    @Ignore
    @Test
    fun givenConversationWithOnlyMyMessageList_thenEmptyNotificationList() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf(localNotificationConversation()))
            .arrange()

        getNotifications().test {
            val actualToCheck = awaitItem()

            assertEquals(0, actualToCheck.size)

            awaitComplete()
        }
    }

    @Ignore
    @Test
    fun givenSelfUserWithStatusAway_whenNewMessageCome_thenNoNotificationsAndAllConversationNotificationDateUpdated() = runTest {
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus(UserAvailabilityStatus.AWAY))
            .withKnownUser()
            .withConversationsForNotifications(listOf(localNotificationConversation()))
            .arrange()

        getNotifications().test {
            val actualToCheck = awaitItem()

            assertEquals(0, actualToCheck.size)
            verify(arrange.conversationRepository)
                .suspendFunction(arrange.conversationRepository::updateAllConversationsNotificationDate)
                .with(any())
                .wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Ignore
    @Test
    fun givenSelfUserWithStatusBusy_whenNewMessageCome_thenNotificationsWithMentionComesAndNotificationDateUpdated() = runTest {
        val mentionMessageText = "@handle message with Mention"
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus(UserAvailabilityStatus.BUSY))
            .withKnownUser()
            .withConversationsForNotifications(listOf(localNotificationConversation()))
            .arrange()

        getNotifications().test {
            val expected = listOf(notificationMessageText(authorName = otherUserName(otherUserId()), text = mentionMessageText))
            val actual1 = awaitItem()
            val actual2 = awaitItem()
            val actualToCheck = if (actual2.size > actual1.size) actual2 else actual1

            assertEquals(1, actualToCheck.size)
            assertEquals(expected, actualToCheck[0].messages)
            verify(arrange.conversationRepository)
                .suspendFunction(arrange.conversationRepository::updateConversationNotificationDate)
                .with(any(), any())
                .wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Test
    fun givenConnectionRequests_thenNotificationListWithConnectionRequestMessage() = runTest {
        val (_, getNotifications) = Arrangement()
            .withConnectionList(listOf(connectionRequest()))
            .withSelfUser(selfUserWithStatus())
            .withKnownUser()
            .withConversationsForNotifications(null)
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

    private class Arrangement {
        @Mock
        val connectionRepository = mock(classOf<ConnectionRepository>())

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val ephemeralNotifications = mock(classOf<EphemeralNotificationsMgr>())

        val getNotificationsUseCase: GetNotificationsUseCase = GetNotificationsUseCaseImpl(
            connectionRepository = connectionRepository,
            messageRepository = messageRepository,
            userRepository = userRepository,
            conversationRepository = conversationRepository,
            selfUserId = SELF_USER_ID,
            ephemeralNotificationsManager = ephemeralNotifications
        )

        init {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationNotificationDate)
                .whenInvokedWith(any(), any())
                .then { _, _ -> Either.Right(Unit) }
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateAllConversationsNotificationDate)
                .whenInvokedWith(any())
                .then { Either.Right(Unit) }
        }

        fun withSelfUser(user: SelfUser = selfUserWithStatus()): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .then { flowOf(user) }

            return this
        }

        fun withConversationsForNotifications(list: List<LocalNotificationConversation>?): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::getNotificationMessage)
                .whenInvokedWith(any())
                .thenReturn(list?.let { flowOf(it) } ?: flowOf())

            return this
        }

        fun withKnownUser(): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .whenInvokedWith(any())
                .then { id -> flowOf(otherUser(id)) }

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
        private const val TIME = "2000-01-23T01:23:35.678+09:00"
        private const val TIME_EARLIER = "2000-01-23T01:23:30.678+09:00"

        private fun conversationId(number: Int = 0) =
            QualifiedID("conversation_id_${number}_value", "conversation_id_${number}_domain")

        private fun localNotificationConversation(
            number: Int = 0,
            isOneOnOne: Boolean = true,
        ) = LocalNotificationConversation(
            conversationId(number),
            conversationName = "conversation_$number",
            messages = emptyList(),
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
                visibility = visibility
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
                editStatus = Message.EditStatus.NotEdited
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
                status = Message.Status.SENT
            )

        private fun notificationMessageText(
            authorName: String = "Author Name",
            time: String = TIME,
            text: String = "test text"
        ) =
            LocalNotificationMessage.Text(
                LocalNotificationMessageAuthor(authorName, null),
                time,
                text
            )

        private fun notificationMessageComment(
            authorName: String = "Author Name",
            time: String = TIME,
            commentType: LocalNotificationCommentType = LocalNotificationCommentType.PICTURE
        ) =
            LocalNotificationMessage.Comment(
                LocalNotificationMessageAuthor(authorName, null),
                time,
                commentType
            )

        private fun notificationMessageConnectionRequest(
            authorName: String = "Author Name",
            time: String = TIME
        ) =
            LocalNotificationMessage.ConnectionRequest(
                LocalNotificationMessageAuthor(authorName, null),
                time,
                otherUserId()
            )

        private fun notificationConversationDeleted(
            authorName: String = "Author Name",
            time: String = TIME
        ) = LocalNotificationMessage.ConversationDeleted(
            LocalNotificationMessageAuthor(authorName, null),
            time
        )

        private fun selfUserWithStatus(status: UserAvailabilityStatus = UserAvailabilityStatus.NONE) =
            TestUser.SELF.copy(availabilityStatus = status)

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
