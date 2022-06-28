package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetAllContactsNotInTheConversationUseCaseTest {

    @Test
    fun givenAllContactsAndConversationMembers_whenGettingContacstNotInTheConversation_ThenResultDoesNotContaintTheConversationMembers() =
        runTest {
            val conversationMemberId = UserId("someCommonValue", "someCommonDomain")

            val contactWithConversationMemberId = OtherUser(
                id = conversationMemberId,
                name = null,
                handle = null,
                email = null,
                phone = null,
                accentId = 0,
                team = null,
                connectionStatus = ConnectionState.ACCEPTED,
                previewPicture = null,
                completePicture = null,
                availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                userType = UserType.INTERNAL
            )

            val (_, getAllContactsNotInTheConversation) = Arrangement()
                .withSuccessFullGetAllContacts(
                    allContacts = Arrangement.mockAllContacts,
                    additionalContact = contactWithConversationMemberId
                )
                .withSuccessFullGetConversationMembers(
                    conversationMembers = Arrangement.mockConversationMembers,
                    additionalConversationMember = conversationMemberId
                )
                .arrange()

            val result = getAllContactsNotInTheConversation(ConversationId("someId", "someDomain"))

            assertIs<Result.Success>(result)
            assertTrue(!result.contactNotInTheConversation.contains(contactWithConversationMemberId))
        }

    @Test
    fun givenAllContactsAndConversationMembers_WhenGettingContacstNotInTheConversation_ThenResultContainsAllTheContact() = runTest {
        val (_, getAllContactsNotInTheConversation) = Arrangement()
            .withSuccessFullGetAllContacts(allContacts = Arrangement.mockAllContacts)
            .withSuccessFullGetConversationMembers(conversationMembers = Arrangement.mockConversationMembers)
            .arrange()

        val result = getAllContactsNotInTheConversation(ConversationId("someId", "someDomain"))

        assertIs<Result.Success>(result)
        assertEquals(Arrangement.mockAllContacts, result.contactNotInTheConversation)
    }


    @Test
    fun givenAllContactsAreConversationMembers_WhenGettingContacstNotInTheConversation_ThenResultIsEmpty() = runTest {
        val allContacts = listOf(
            OtherUser(
                id = UserId("user1Value", "user1Domain"),
                name = null,
                handle = null,
                email = null,
                phone = null,
                accentId = 0,
                team = null,
                connectionStatus = ConnectionState.ACCEPTED,
                previewPicture = null,
                completePicture = null,
                availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                userType = UserType.INTERNAL
            ),
            OtherUser(
                id = UserId("user2Value", "user2Domain"),
                name = null,
                handle = null,
                email = null,
                phone = null,
                accentId = 0,
                team = null,
                connectionStatus = ConnectionState.ACCEPTED,
                previewPicture = null,
                completePicture = null,
                availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                userType = UserType.INTERNAL
            )
        )

        val conversationMembers = listOf(
            UserId("user1Value", "user1Domain"),
            UserId("user2Value", "user2Domain")
        )

        val (_, getAllContactsNotInTheConversation) = Arrangement()
            .withSuccessFullGetAllContacts(allContacts = allContacts)
            .withSuccessFullGetConversationMembers(conversationMembers = conversationMembers)
            .arrange()

        val result = getAllContactsNotInTheConversation(ConversationId("someId", "someDomain"))

        assertIs<Result.Success>(result)
        assertTrue(result.contactNotInTheConversation.isEmpty())
    }

    @Test
    fun givenGettingAllContactsFails_whenGettingContatcstNotInTheConversation_PropagateTheFailureAsExpected() = runTest {
        val (arrangement, getAllContactsNotInTheConversation) = Arrangement()
            .withFailureGetAllContact()
            .withSuccessFullGetConversationMembers()
            .arrange()

        val result = getAllContactsNotInTheConversation(ConversationId("someId", "someDomain"))

        assertIs<Result.Failure>(result)
    }

    @Test
    fun givenAllContactsAndConversationMembers_WheGettingContact_ThenResultContainsAllTheContact() = runTest {
        val (_, getAllContactsNotInTheConversation) = Arrangement()
            .withSuccessFullGetAllContacts(allContacts = Arrangement.mockAllContacts)
            .withSuccessFullGetConversationMembers(conversationMembers = Arrangement.mockConversationMembers)
            .arrange()

        val result = getAllContactsNotInTheConversation(ConversationId("someId", "someDomain"))

        assertIs<Result.Success>(result)
        assertEquals(Arrangement.mockAllContacts, result.contactNotInTheConversation)
    }

    private class Arrangement() {
        companion object {
            val mockAllContacts = listOf(
                OtherUser(
                    id = QualifiedID("someAllContactsValue", "someAllContactsDomain"),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    team = null,
                    connectionStatus = ConnectionState.ACCEPTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL
                ),
                OtherUser(
                    id = QualifiedID("someAllContactsValue1", "someAllContactsDomain1"),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    team = null,
                    connectionStatus = ConnectionState.ACCEPTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL
                )
            )

            val mockConversationMembers = listOf(
                UserId("someConversationMemberValue", "someConversationMemberDomain"),
                UserId("someConversationMemberValue1", "someConversationMemberValue1")
            )
        }

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        fun withSuccessFullGetConversationMembers(
            conversationMembers: List<UserId> = emptyList(),
            additionalConversationMember: UserId? = null
        ): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(any())
                .thenReturn(
                    Either.Right(
                        if (additionalConversationMember == null) conversationMembers
                        else conversationMembers + additionalConversationMember
                    )
                )

            return this
        }

        fun withSuccessFullGetAllContacts(allContacts: List<OtherUser> = emptyList(), additionalContact: OtherUser? = null): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::getAllContacts)
                .whenInvoked()
                .thenReturn(
                    Either.Right(
                        if (additionalContact == null)
                            allContacts
                        else allContacts + additionalContact
                    )
                )
            return this
        }

        fun withFailureGetAllContact(): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::getAllContacts)
                .whenInvoked()
                .thenReturn(
                    Either.Left(StorageFailure.DataNotFound)
                )

            return this
        }

        fun withFailureGetConversationMembers(): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(any())
                .thenReturn(
                    Either.Left(StorageFailure.DataNotFound)
                )

            return this
        }


        fun arrange() = this to GetAllContactsNotInTheConversationUseCase(conversationRepository, userRepository)
    }

}
