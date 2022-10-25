package com.wire.kalium.logic.feature.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageMention
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationCommentType
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.TimeParserImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GetNotificationsUseCaseTest {

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
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate { listOf() }
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
    fun givenConversationWithOnlyMyMessageList_thenEmptyNotificationList() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId),
                    entityAssetMessage(conversationId, assetId = "test_asset")
                )
            }
            .arrange()

        getNotifications().test {
            val actualToCheck = awaitItem()

            assertEquals(0, actualToCheck.size)

            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithMessageListIncludingMyMessages_thenNotificationListWithoutMyMessages() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withKnownUser()
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId),
                    entityAssetMessage(conversationId, assetId = "test_asset"),
                    entityTextMessage(conversationId, otherUserId()),
                    entityAssetMessage(conversationId, otherUserId(), assetId = "test_asset")
                )
            }
            .arrange()

        getNotifications().test {
            val actual1 = awaitItem()
            val actual2 = awaitItem()
            val actualToCheck = if (actual2.size > actual1.size) actual2 else actual1

            assertEquals(1, actualToCheck.size)
            assertEquals(
                listOf(
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message message_id"),
                    notificationMessageComment(authorName = otherUserName(otherUserId()))
                ),
                actualToCheck[0].messages
            )
            awaitComplete()
        }
    }

    @Test
    fun givenFewConversationWithMessageLists_thenListOfFewNotifications() = runTest {
        val (_, getNotifications) = Arrangement()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withKnownUser()
            .withConversationsForNotifications(
                listOf(entityConversation(0), entityConversation(1))
            )
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId, otherUserId(), "0"),
                    entityTextMessage(conversationId, otherUserId(), "1"),
                    entityAssetMessage(conversationId, otherUserId(), messageId = "2", assetId = "test_asset")
                )
            }
            .withEphemeralNotification()
            .arrange()

        getNotifications().test {
            val actual1 = awaitItem()
            val actual2 = awaitItem()
            val actualToCheck = if (actual2.size > actual1.size) actual2 else actual1

            assertEquals(2, actualToCheck.size)
            assertEquals(
                listOf(
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 0"),
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 1"),
                    notificationMessageComment(authorName = otherUserName(otherUserId()))
                ),
                actualToCheck[0].messages
            )
            assertEquals(
                listOf(
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 0"),
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 1"),
                    notificationMessageComment(authorName = otherUserName(otherUserId()))
                ),
                actualToCheck[1].messages
            )
            awaitComplete()
        }
    }

    @Test
    fun givenFewConversationWithMessageListsButSameAuthor_thenAuthorInfoRequestedOnce() = runTest {
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withKnownUser()
            .withConversationsForNotifications(
                listOf(entityConversation(0), entityConversation(1))
            )
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId, otherUserId(), "0"),
                    entityTextMessage(conversationId, otherUserId(), "1"),
                    entityTextMessage(conversationId, otherUserId(), "2")
                )
            }
            .arrange()

        getNotifications().test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        verify(arrange.userRepository).coroutine { getKnownUser(otherUserId()) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfUserWithStatusAway_whenNewMessageCome_thenNoNotificationsAndAllConversationNotificationDateUpdated() = runTest {
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus(UserAvailabilityStatus.AWAY))
            .withKnownUser()
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId, otherUserId(), "0"),
                    entityTextMessage(conversationId, otherUserId(), "1"),
                    entityTextMessage(conversationId, otherUserId(), "2", MessageContent.Text("@handle Mention"))
                )
            }
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

    @Test
    fun givenSelfUserWithStatusBusy_whenNewMessageCome_thenNotificationsWithMentionComesAndNotificationDateUpdated() = runTest {
        val mentionMessageText = "@handle message with Mention"
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus(UserAvailabilityStatus.BUSY))
            .withKnownUser()
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId, otherUserId(), "0"),
                    entityTextMessage(conversationId, otherUserId(), "1"),
                    entityTextMessage(
                        conversationId, otherUserId(), "2",
                        MessageContent.Text(mentionMessageText, listOf(MessageMention(0, 7, MY_ID)))
                    )
                )
            }
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
    fun givenMutedConversation_whenNewMessageCome_thenNoNotificationsAndNotificationDateUpdated() = runTest {
        val (arrange, getNotifications) = Arrangement()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf(entityConversation(mutedStatus = MutedConversationStatus.AllMuted)))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId, otherUserId(), "0"),
                    entityTextMessage(conversationId, otherUserId(), "1"),
                    entityTextMessage(conversationId, otherUserId(), "2", MessageContent.Text("@handle text"))
                )
            }
            .withKnownUser()
            .withEphemeralNotification()
            .arrange()

        getNotifications().test {
            val actual = awaitAll<List<LocalNotificationConversation>>()

            assertEquals(listOf(), actual)
            verify(arrange.conversationRepository)
                .suspendFunction(arrange.conversationRepository::updateConversationNotificationDate)
                .with(any(), any())
                .wasInvoked(exactly = once)

            awaitItem()
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithOnlyMentionMuteStatus_whenNewMessageCome_thenNotificationsWithMentionComes(): TestResult = runTest {
        val mentionMessageText = "@handle message with Mention"
        val (arrange, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(listOf())
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf(entityConversation(mutedStatus = MutedConversationStatus.OnlyMentionsAllowed)))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId, otherUserId(), "0"),
                    entityTextMessage(conversationId, otherUserId(), "1"),
                    entityTextMessage(
                        conversationId, otherUserId(), "2",
                        MessageContent.Text(mentionMessageText, listOf(MessageMention(0, 7, MY_ID)))
                    )
                )
            }
            .withKnownUser()
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
    fun givenConversationWithMessageListIncludingNotAllowedMessages_thenNotificationListWithoutTheseMessages() = runTest {
        val (_, getNotifications) = Arrangement()
            .withEphemeralNotification()
            .withConnectionList(null)
            .withSelfUser(selfUserWithStatus())
            .withKnownUser()
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate {
                listOf(
                    entityTextMessage(conversationId(), otherUserId(), "0"),
                    entityServerMessage(conversationId(), otherUserId(), "1"),
                    entityTextMessage(conversationId(), otherUserId(), "2", visibility = Message.Visibility.HIDDEN)
                )
            }
            .arrange()

        getNotifications().test {
            val actualToCheck = awaitItem()

            assertEquals(1, actualToCheck.size)
            assertEquals(
                listOf(notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 0")),
                actualToCheck[0].messages
            )
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
        val connectionRepository: ConnectionRepository = mock(classOf<ConnectionRepository>())

        @Mock
        val messageRepository: MessageRepository = mock(classOf<MessageRepository>())

        @Mock
        val userRepository: UserRepository = mock(classOf<UserRepository>())

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val ephemeralNotifications = mock(classOf<EphemeralNotificationsMgr>())

        val timeParser = TimeParserImpl()

        val getNotificationsUseCase: GetNotificationsUseCase = GetNotificationsUseCaseImpl(
            connectionRepository = connectionRepository,
            messageRepository = messageRepository,
            userRepository = userRepository,
            conversationRepository = conversationRepository,
            timeParser = timeParser,
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

        fun withConversationsForNotifications(list: List<Conversation>?): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationsForNotifications)
                .whenInvoked()
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

        fun withMessagesByConversationAfterDate(messagesFun: (ConversationId) -> List<Message>): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessagesByConversationIdAndVisibilityAfterDate)
                .whenInvokedWith(any(), any(), any())
                .then { conversationId, _, _ -> flowOf(messagesFun(conversationId)) }

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

        private val MY_ID = TestUser.USER_ID
        private const val TIME = "2000-01-23T01:23:35.678+09:00"
        private const val TIME_EARLIER = "2000-01-23T01:23:30.678+09:00"

        private fun conversationId(number: Int = 0) =
            QualifiedID("conversation_id_${number}_value", "conversation_id_${number}_domain")

        private fun entityConversation(
            number: Int = 0,
            isOneOnOne: Boolean = true,
            mutedStatus: MutedConversationStatus = MutedConversationStatus.AllAllowed,
        ) = Conversation(
            conversationId(number),
            "conversation_$number",
            if (isOneOnOne) Conversation.Type.ONE_ON_ONE else Conversation.Type.GROUP,
            null,
            ProtocolInfo.Proteus,
            mutedStatus,
            null,
            TIME_EARLIER,
            TIME_EARLIER,
            lastReadDate = "2000-01-01T12:00:00.000Z",
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
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
