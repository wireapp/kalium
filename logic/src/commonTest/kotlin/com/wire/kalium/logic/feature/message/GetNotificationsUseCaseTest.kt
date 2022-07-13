package com.wire.kalium.logic.feature.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationCommentType
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GetNotificationsUseCaseTest {

    @Test
    fun givenEmptyConversationList_thenEmptyNotificationList() = runTest {
        val (_, getNotifications) = Arrangement()
            .withSelfUserId(MY_ID)
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf())
            .arrange()

        getNotifications().test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithEmptyMessageList_thenEmptyNotificationList() = runTest {
        val (_, getNotifications) = Arrangement()
            .withSelfUserId(MY_ID)
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate { listOf() }
            .arrange()

        getNotifications().test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithOnlyMyMessageList_thenEmptyNotificationList() = runTest {
        val (_, getNotifications) = Arrangement()
            .withSelfUserId(MY_ID)
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
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithMessageListIncludingMyMessages_thenNotificationListWithoutMyMessages() = runTest {
        val (_, getNotifications) = Arrangement()
            .withSelfUserId(MY_ID)
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
            val actual = awaitItem()

            assertEquals(1, actual.size)
            assertEquals(
                listOf(
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message message_id"),
                    notificationMessageComment(authorName = otherUserName(otherUserId()))
                ),
                actual[0].messages
            )
            awaitComplete()
        }
    }

    @Test
    fun givenFewConversationWithMessageLists_thenListOfFewNotifications() = runTest {
        val (_, getNotifications) = Arrangement()
            .withSelfUserId(MY_ID)
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
            .arrange()

        getNotifications().test {
            val actual = awaitItem()

            assertEquals(2, actual.size)
            assertEquals(
                listOf(
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 0"),
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 1"),
                    notificationMessageComment(authorName = otherUserName(otherUserId()))
                ),
                actual[0].messages
            )
            assertEquals(
                listOf(
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 0"),
                    notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 1"),
                    notificationMessageComment(authorName = otherUserName(otherUserId()))
                ),
                actual[1].messages
            )
            awaitComplete()
        }
    }

    @Test
    fun givenFewConversationWithMessageListsButSameAuthor_thenAuthorInfoRequestedOnce() = runTest {
        val (arrange, getNotifications) = Arrangement()
            .withSelfUserId(MY_ID)
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
            awaitComplete()
        }

        verify(arrange.userRepository).coroutine { getKnownUser(otherUserId()) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfUserWithStatusAway_whenNewMessageCome_thenNoNotificationsAndAllConversationNotificationDateUpdated() = runTest {
        val (arrange, getNotifications) = Arrangement()
            .withSelfUserId(MY_ID)
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
            val actual = awaitItem()

            assertTrue { actual.isEmpty() }
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
            .withSelfUserId(MY_ID)
            .withSelfUser(selfUserWithStatus(UserAvailabilityStatus.BUSY))
            .withKnownUser()
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId, otherUserId(), "0"),
                    entityTextMessage(conversationId, otherUserId(), "1"),
                    entityTextMessage(conversationId, otherUserId(), "2", MessageContent.Text(mentionMessageText))
                )
            }
            .arrange()

        getNotifications().test {
            val actual = awaitItem()
            val expected = listOf(notificationMessageText(authorName = otherUserName(otherUserId()), text = mentionMessageText))

            assertEquals(1, actual.size)
            assertEquals(expected, actual[0].messages)
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
            .withSelfUserId(MY_ID)
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
            .arrange()

        getNotifications().test {
            val actual = awaitItem()

            assertEquals(listOf(), actual)
            verify(arrange.conversationRepository)
                .suspendFunction(arrange.conversationRepository::updateConversationNotificationDate)
                .with(any(), any())
                .wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithOnlyMentionMuteStatus_whenNewMessageCome_thenNotificationsWithMentionComes(): TestResult = runTest {
        val mentionMessageText = "@handle message with Mention"
        val (arrange, getNotifications) = Arrangement()
            .withSelfUserId(MY_ID)
            .withSelfUser(selfUserWithStatus())
            .withConversationsForNotifications(listOf(entityConversation(mutedStatus = MutedConversationStatus.OnlyMentionsAllowed)))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId, otherUserId(), "0"),
                    entityTextMessage(conversationId, otherUserId(), "1"),
                    entityTextMessage(conversationId, otherUserId(), "2", MessageContent.Text(mentionMessageText))
                )
            }
            .withKnownUser()
            .arrange()

        getNotifications().test {
            val actual = awaitItem()
            val expected = listOf(notificationMessageText(authorName = otherUserName(otherUserId()), text = mentionMessageText))

            assertEquals(1, actual.size)
            assertEquals(expected, actual[0].messages)
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
            .withSelfUserId(MY_ID)
            .withSelfUser(selfUserWithStatus())
            .withKnownUser()
            .withConversationsForNotifications(listOf(entityConversation()))
            .withMessagesByConversationAfterDate { conversationId ->
                listOf(
                    entityTextMessage(conversationId(), otherUserId(), "0"),
                    entityServerMessage(conversationId(), otherUserId(), "1"),
                    entityTextMessage(conversationId(), otherUserId(), "2", visibility = Message.Visibility.HIDDEN)
                )
            }
            .arrange()

        getNotifications().test {
            val actual = awaitItem()

            assertTrue(actual.size == 1)
            assertEquals(
                actual[0].messages,
                listOf(notificationMessageText(authorName = otherUserName(otherUserId()), text = "test message 0"))
            )
            awaitComplete()
        }
    }

    private class Arrangement {
        @Mock
        val messageRepository: MessageRepository = mock(classOf<MessageRepository>())

        @Mock
        val userRepository: UserRepository = mock(classOf<UserRepository>())

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        val timeParser = TimeParserImpl()

        val getNotificationsUseCase: GetNotificationsUseCase = GetNotificationsUseCaseImpl(
            messageRepository = messageRepository,
            userRepository = userRepository,
            conversationRepository = conversationRepository,
            timeParser = timeParser
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

        fun withSelfUserId(id: QualifiedID = MY_ID): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::getSelfUserId)
                .whenInvoked()
                .then { id }

            return this
        }

        fun withSelfUser(user: SelfUser = selfUserWithStatus()): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .then { flowOf(user) }

            return this
        }

        fun withConversationsForNotifications(list: List<Conversation> = listOf(entityConversation())): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationsForNotifications)
                .whenInvoked()
                .then { flowOf(list) }

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
            "conversation_${number}",
            if (isOneOnOne) Conversation.Type.ONE_ON_ONE else Conversation.Type.GROUP,
            null,
            ProtocolInfo.Proteus,
            mutedStatus,
            TIME_EARLIER,
            TIME_EARLIER,
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST)
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

        private fun selfUserWithStatus(status: UserAvailabilityStatus = UserAvailabilityStatus.NONE) =
            TestUser.SELF.copy(availabilityStatus = status)

        private fun otherUser(id: QualifiedID) = TestUser.OTHER.copy(id = id, name = otherUserName(id))

        private fun otherUserId(number: Int = 0) =
            QualifiedID("other_user_id_${number}_value", "other_user_id_${number}_domain")

        private fun otherUserName(id: QualifiedID) = "Other User Name ${id.value}"

    }
}
