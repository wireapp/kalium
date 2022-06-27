package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlin.test.Test

class GetAllContactsNotInTheConversationUseCaseTest {

    @Test
    fun test() {
        val test = Arrangement().withGetAllContacts()
    }


    private val CONTACTS = listOf(
        OtherUser(
            id = QualifiedID(),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            team = null,
            connectionStatus =,
            previewPicture = null,
            completePicture = null,
            availabilityStatus =,
            userType =
        ),
    )

    private class Arrangement() {

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        fun withGetConversationMembers(conversationMembers: List<UserId> = emptyList()) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(conversationMembers))
        }

        fun withGetAllContacts(allContacts: List<OtherUser> = emptyList()) = apply {
            given(userRepository)
                .suspendFunction(userRepository::getAllContacts)
                .whenInvoked()
                .thenReturn(Either.Right(allContacts))
        }

        fun arrange() = this to GetAllContactsNotInTheConversationUseCase(conversationRepository, userRepository)
    }


}
