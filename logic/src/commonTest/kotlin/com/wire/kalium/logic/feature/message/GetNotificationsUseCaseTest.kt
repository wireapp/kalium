package com.wire.kalium.logic.feature.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GetNotificationsUseCaseTest {
    @Mock
    val messageRepository: MessageRepository = mock(classOf<MessageRepository>())

    @Mock
    val userRepository: UserRepository = mock(classOf<UserRepository>())

    @Mock
    val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

    private lateinit var getNotificationsUseCase: GetNotificationsUseCase

    @BeforeTest
    fun setup() {
        getNotificationsUseCase = GetNotificationsUseCaseImpl(messageRepository, userRepository, conversationRepository)
    }

    @Test
    fun givenEmptyConversationList_thenEmptyNotificationList() = runTest {
        given(userRepository).coroutine { getSelfUserId() }.then { MY_ID }
        given(conversationRepository).coroutine { getConversationsForNotifications() }.then { flowOf(listOf()) }

        val notificationsListFlow = getNotificationsUseCase()

        notificationsListFlow.test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithEmptyMessageList_thenEmptyNotificationList() = runTest {
        given(userRepository).coroutine { getSelfUserId() }.then { MY_ID }
        given(conversationRepository).coroutine { getConversationsForNotifications() }.then { flowOf(listOf(entityConversation())) }
        given(messageRepository)
            .suspendFunction(messageRepository::getMessagesByConversationAfterDate)
            .whenInvokedWith(anything(), anything())
            .then { _, _ -> flowOf(listOf()) }

        val notificationsListFlow = getNotificationsUseCase()

        notificationsListFlow.test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithOnlyMyMessageList_thenEmptyNotificationList() = runTest {
        given(userRepository).coroutine { getSelfUserId() }.then { MY_ID }
        given(conversationRepository).coroutine { getConversationsForNotifications() }.then { flowOf(listOf(entityConversation())) }
        given(messageRepository)
            .suspendFunction(messageRepository::getMessagesByConversationAfterDate)
            .whenInvokedWith(anything(), anything())
            .then { conversationId, _ -> flowOf(listOf(entityMessage(conversationId))) }

        val notificationsListFlow = getNotificationsUseCase()

        notificationsListFlow.test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun givenConversationWithMessageListIncludingMyMessages_thenNotificationListWithoutMyMessages() = runTest {
        given(userRepository).coroutine { getSelfUserId() }.then { MY_ID }
        given(conversationRepository).coroutine { getConversationsForNotifications() }.then { flowOf(listOf(entityConversation())) }
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .then { id -> flowOf(otherUser(id)) }
        given(messageRepository)
            .suspendFunction(messageRepository::getMessagesByConversationAfterDate)
            .whenInvokedWith(eq(conversationId()), anything())
            .then { _, _ ->
                flowOf(
                    listOf(
                        entityMessage(conversationId()),
                        entityMessage(conversationId(), otherUserId())
                    )
                )
            }

        val notificationsListFlow = getNotificationsUseCase()

        notificationsListFlow.test {
            val actual = awaitItem()

            assertTrue(actual.size == 1)
            assertEquals(
                actual[0].messages,
                listOf(notificationMessage(authorName = otherUserName(otherUserId()), text = "test message message_id"))
            )
            awaitComplete()
        }
    }

    @Test
    fun givenFewConversationWithMessageLists_thenListOfFewNotifications() = runTest {
        given(userRepository).coroutine { getSelfUserId() }.then { MY_ID }
        given(conversationRepository)
            .coroutine { getConversationsForNotifications() }
            .then {
                flowOf(
                    listOf(
                        entityConversation(0),
                        entityConversation(1)
                    )
                )
            }
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .then { id -> flowOf(otherUser(id)) }
        given(messageRepository)
            .suspendFunction(messageRepository::getMessagesByConversationAfterDate)
            .whenInvokedWith(anything(), anything())
            .then { conversationId, _ ->
                flowOf(
                    listOf(
                        entityMessage(conversationId, otherUserId(), "0"),
                        entityMessage(conversationId, otherUserId(), "1")
                    )
                )
            }

        val notificationsListFlow = getNotificationsUseCase()

        notificationsListFlow.test {
            val actual = awaitItem()

            assertTrue(actual.size == 2)
            assertEquals(
                actual[0].messages, listOf(
                    notificationMessage(authorName = otherUserName(otherUserId()), text = "test message 0"),
                    notificationMessage(authorName = otherUserName(otherUserId()), text = "test message 1")
                )
            )
            assertEquals(
                actual[1].messages, listOf(
                    notificationMessage(authorName = otherUserName(otherUserId()), text = "test message 0"),
                    notificationMessage(authorName = otherUserName(otherUserId()), text = "test message 1")
                )
            )
            awaitComplete()
        }
    }

    @Test
    fun givenFewConversationWithMessageListsButSameAuthor_thenAuthorInfoRequestedOnce() = runTest {
        given(userRepository).coroutine { getSelfUserId() }.then { MY_ID }
        given(conversationRepository)
            .coroutine { getConversationsForNotifications() }
            .then {
                flowOf(
                    listOf(
                        entityConversation(0),
                        entityConversation(1)
                    )
                )
            }
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .then { id -> flowOf(otherUser(id)) }
        given(messageRepository)
            .suspendFunction(messageRepository::getMessagesByConversationAfterDate)
            .whenInvokedWith(anything(), anything())
            .then { conversationId, _ ->
                flowOf(
                    listOf(
                        entityMessage(conversationId, otherUserId(), "0"),
                        entityMessage(conversationId, otherUserId(), "1")
                    )
                )
            }

        getNotificationsUseCase().test {
            awaitItem()
            awaitComplete()
        }

        verify(userRepository).coroutine { getKnownUser(otherUserId()) }
            .wasInvoked(exactly = once)
    }

    companion object {

        private val MY_ID = QualifiedID("my_id_value", "my_id_domain")

        private fun conversationId(number: Int = 0) = QualifiedID("conversation_id_${number}_value", "conversation_id_${number}_domain")

        private fun entityConversation(number: Int = 0, isOneOnOne: Boolean = true) = Conversation(
            conversationId(number),
            "conversation_${number}",
            if (isOneOnOne) Conversation.Type.ONE_ON_ONE else Conversation.Type.GROUP,
            null,
            MutedConversationStatus.AllAllowed,
            "2000-01-23T01:23:35.678+09:00",
            "2000-01-23T01:23:45.678+09:00"
        )

        private fun otherUserId(number: Int = 0) = QualifiedID("other_user_id_${number}_value", "other_user_id_${number}_domain")

        private fun entityMessage(conversationId: QualifiedID, senderId: QualifiedID = MY_ID, messageId: String = "message_id") =
            Message.Client(
                id = messageId,
                content = MessageContent.Text("test message $messageId"),
                conversationId = conversationId,
                date = "some_time",
                senderUserId = senderId,
                senderClientId = ClientId("client_1"),
                status = Message.Status.SENT
            )

        private fun notificationMessage(authorName: String = "Author Name", time: String = "some_time", text: String = "test text") =
            LocalNotificationMessage.Text(
                LocalNotificationMessageAuthor(authorName, null),
                time,
                text
            )

        private fun otherUser(id: QualifiedID) =
            OtherUser(
                id,
                otherUserName(id),
                handle = null,
                accentId = 0,
                previewPicture = null,
                completePicture = null,
                team = null
            )

        private fun otherUserName(id: QualifiedID) = "Other User Name ${id.value}"

    }

}
