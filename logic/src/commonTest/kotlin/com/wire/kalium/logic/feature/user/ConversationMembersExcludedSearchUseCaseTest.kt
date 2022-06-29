package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.publicuser.search.ConversationMembersExcludedSearchUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersUseCase
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import com.wire.kalium.logic.feature.publicuser.search.Result
import com.wire.kalium.logic.functional.Either
import io.mockative.anything
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConversationMembersExcludedSearchUseCaseTest {

    @Test
    fun givenSearchResultContainsConversationMembers_whenSearchingForUser_ThenSearchResultDoesNotContainMembers() = runTest {
        //given
        val extraOtherUser = OtherUser(
            id = QualifiedID(
                "commonValue",
                "commonDomain"
            ),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            team = null,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            userType = UserType.INTERNAL
        )

        val (_, searchUsersNotPartOfConversation) = Arrangement()
            .withSuccessFullGetConversationMembers(
                conversationMembers = Arrangement.mockConversationMembers,
                extraConversationMember = UserId("commonValue", "commonDomain")
            )
            .withSuccessFullSearchUsers(
                searchResult = UserSearchResult(result = Arrangement.mockOtherUsers),
                extraOtherUser = extraOtherUser,
            ).arrange()

        //when
        val result = searchUsersNotPartOfConversation(ConversationId("someValue", "someDomain"), "someSearchQuery")

        //then
        assertIs<Result.Success>(result)
        assertTrue { !result.userSearchResult.result.contains(extraOtherUser) }
    }

    @Test
    fun givenAllSearchResultExistInConversation_whenSearchForUser_ThenSearchResultIsEmpty() = runTest {
        //given
        val searchResult = UserSearchResult(
            listOf(
                OtherUser(
                    id = QualifiedID(
                        "someValue1",
                        "someDomain1"
                    ),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    team = null,
                    connectionStatus = ConnectionState.NOT_CONNECTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL
                ),
                OtherUser(
                    id = QualifiedID(
                        "someValue2",
                        "someDomain2"
                    ),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    team = null,
                    connectionStatus = ConnectionState.NOT_CONNECTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL
                )
            )
        )

        val conversationMembers = listOf(
            UserId("someValue1", "someDomain1"),
            UserId("someValue2", "someDomain2")
        )

        val (_, searchUsersNotPartOfConversation) = Arrangement()
            .withSuccessFullGetConversationMembers(
                conversationMembers = conversationMembers,
            )
            .withSuccessFullSearchUsers(
                searchResult = searchResult,
            ).arrange()

        //when
        val result = searchUsersNotPartOfConversation(ConversationId("someValue", "someId"), "someSearchQuery")

        //then
        assertIs<Result.Success>(result)
        assertTrue { result.userSearchResult.result.isEmpty() }
    }

    @Test
    fun givenUserSearchFails_WhenSearchForUser_ThenPropagateTheFailureCorrectly() = runTest {
        //given
        val (_, searchUsersNotPartOfConversation) = Arrangement()
            .withFailingSearchUsers()
            .withSuccessFullGetConversationMembers()
            .arrange()

        //when
        val result = searchUsersNotPartOfConversation(ConversationId("someValue", "someDomain"), "someQuery")

        //then
        assertIs<Result.Failure>(result)
    }

    @Test
    fun givenGetConversationMembersFails_WhenSearchForUser_ThenPropagateTheFailureCorrectly() = runTest {
        //given
        val (_, searchUsersNotPartOfConversation) = Arrangement()
            .withFailingGetConversationMembers()
            .withSuccessFullSearchUsers()
            .arrange()

        //when
        val result = searchUsersNotPartOfConversation(ConversationId("someValue", "someDomain"), "someQuery")

        //then
        assertIs<Result.Failure>(result)
    }

    private class Arrangement {
        companion object {

            val mockConversationMembers = listOf(
                UserId("someConversationMemberValue1", "someConversationMemberDomain1"),
                UserId("someConversationMemberValue2", "someConversationMemberDomain2"),
                UserId("someConversationMemberValue3", "someConversationMemberDomain3"),
            )

            val mockOtherUsers = listOf(
                OtherUser(
                    id = QualifiedID(
                        "someOtherUserValue1",
                        "someOtherUserDomain1"
                    ),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    team = null,
                    connectionStatus = ConnectionState.NOT_CONNECTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL
                ),
                OtherUser(
                    id = QualifiedID(
                        "someOtherUserValue2",
                        "someOtherUserDomain2"
                    ),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    team = null,
                    connectionStatus = ConnectionState.NOT_CONNECTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL
                ),
                OtherUser(
                    id = QualifiedID(
                        "someOtherUserValue3",
                        "someOtherUserDomain3"
                    ),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    team = null,
                    connectionStatus = ConnectionState.NOT_CONNECTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL
                )
            )

            private val mockUserSearchResult = UserSearchResult(result = listOf())
        }

        @Mock
        private val searchUsersUseCase: SearchUsersUseCase = mock(classOf<SearchUsersUseCase>())

        @Mock
        private val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        fun withSuccessFullSearchUsers(
            searchResult: UserSearchResult = mockUserSearchResult,
            extraOtherUser: OtherUser? = null
        ): Arrangement {
            given(searchUsersUseCase)
                .suspendFunction(searchUsersUseCase::invoke)
                .whenInvokedWith(anything(), anything())
                .thenReturn(
                    Result.Success(
                        if (extraOtherUser != null) searchResult.copy(result = searchResult.result + extraOtherUser)
                        else searchResult
                    )
                )

            return this
        }

        fun withSuccessFullGetConversationMembers(
            conversationMembers: List<UserId> = mockConversationMembers,
            extraConversationMember: UserId? = null
        ): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(anything())
                .thenReturn(
                    Either.Right(if (extraConversationMember != null) conversationMembers + extraConversationMember else conversationMembers)
                )

            return this
        }

        fun withFailingSearchUsers(
        ): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(anything())
                .thenReturn(
                    Either.Left(StorageFailure.DataNotFound)
                )

            return this
        }

        fun withFailingGetConversationMembers(
        ): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(anything())
                .thenReturn(
                    Either.Left(StorageFailure.DataNotFound)
                )
            return this
        }

        fun arrange() = this to ConversationMembersExcludedSearchUseCase(searchUsersUseCase, conversationRepository)
    }
}
