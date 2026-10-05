package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreatorImpl
import com.wire.kalium.logic.data.conversation.PersistConversationUseCase
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class ConversationAppsHistoryTest {
    @Test
    fun delayedCreationAppsMessageKeepsOriginalEventDateInsteadOfRestartTime() = runTest {
        val event = Event.Conversation.NewConversation(
            id = "historical-creation",
            conversationId = TestConversation.ID,
            dateTime = Instant.parse("2001-01-01T00:00:00Z"),
            conversation = TestConversation.CONVERSATION_RESPONSE.copy(
                type = ConversationResponse.Type.GROUP,
                accessRole = setOf(ConversationAccessRoleDTO.SERVICE),
            ),
            senderUserId = TestUser.SELF.id,
        )
        val persistMessage = mock<PersistMessageUseCase>()
        val repository = mock<ConversationRepository>()
        val users = mock<UserRepository>()
        val teamIdProvider = mock<SelfTeamIdProvider>()
        val persistConversation = mock<PersistConversationUseCase>()
        everySuspend { persistMessage(any()) } returns Unit.right()
        everySuspend { repository.updateConversationModifiedDate(any(), any()) } returns Unit.right()
        everySuspend { users.fetchUsersIfUnknownByIds(any()) } returns Unit.right()
        everySuspend { teamIdProvider() } returns null.right()
        everySuspend { persistConversation(any(), any(), any()) } returns true.right()
        val creator = NewGroupConversationSystemMessagesCreatorImpl(
            persistMessage,
            teamIdProvider,
            mock<QualifiedIdMapper>(),
            TestUser.SELF.id,
        )
        val handler = NewConversationEventHandlerImpl(
            repository,
            users,
            teamIdProvider,
            creator,
            mock<OneOnOneResolver>(),
            persistConversation,
        )

        handler.handle(mock<CryptoTransactionContext>(), event)

        verifySuspend(VerifyMode.exactly(1)) {
            persistMessage(matches { message ->
                message is Message.System && message.id == event.id &&
                    message.date == event.dateTime && message.senderUserId == event.senderUserId &&
                    message.content == MessageContent.NewConversationAppsEnabled
            })
        }
    }

    @Test
    fun delayedAccessChangeKeepsOriginalEventDateAndActualActor() = runTest {
        val event = TestEvent.accessUpdate().copy(dateTime = Instant.parse("2001-01-01T00:01:00Z"))
        val persistMessage = mock<PersistMessageUseCase>()
        val conversations = mock<ConversationDAO>()
        everySuspend { persistMessage(any()) } returns Unit.right()
        everySuspend { conversations.updateAccess(any(), any(), any()) } returns Unit
        everySuspend { conversations.getConversationById(any()) } returns TestConversation.ENTITY.copy(
            accessRole = listOf(ConversationEntity.AccessRole.TEAM_MEMBER),
        )
        val handler = AccessUpdateEventHandler(
            selfUserId = TestUser.SELF.id,
            conversationDAO = conversations,
            systemMessageInserter = SystemMessageInserterImpl(TestUser.SELF.id, persistMessage),
        )

        handler.handle(event)

        verifySuspend(VerifyMode.exactly(1)) {
            persistMessage(matches { message ->
                message is Message.System && message.id == event.id &&
                    message.date == event.dateTime && message.senderUserId == event.qualifiedFrom &&
                    message.content == MessageContent.ConversationAppsEnabledChanged(true)
            })
        }
    }
}
