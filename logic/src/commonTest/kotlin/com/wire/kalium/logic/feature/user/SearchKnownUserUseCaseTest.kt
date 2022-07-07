package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.ConversationMemberExcludedOptions
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.publicuser.search.SearchKnownUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchKnownUsersUseCaseImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.publicuser.search.Result
import com.wire.kalium.logic.framework.TestUser
import io.mockative.once
import kotlin.test.assertFalse

class SearchKnownUserUseCaseTest {

    @Test
    fun givenAnInputStartingWithAtSymbol_whenSearchingUsers_thenSearchOnlyByHandle() = runTest {
        //given
        val handleSearchQuery = "@someHandle"

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchByHandle(handleSearchQuery).arrange()

        //when
        searchKnownUsersUseCase(handleSearchQuery)
        //then
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByHandle)
            .with(eq(handleSearchQuery), anything())
            .wasInvoked()

        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(anything(), anything())
            .wasNotInvoked()
    }

    @Test
    fun givenNormalInput_whenSearchingUsers_thenSearchByNameOrHandleOrEmail() = runTest {
        //given
        val searchQuery = "someSearchQuery"

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchKnownUsersByNameOrHandleOrEmail(searchQuery)
            .arrange()
        //when
        searchKnownUsersUseCase(searchQuery)
        //then
        with(arrangement) {
            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
                .with(anything(), anything())
                .wasNotInvoked()

            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .with(eq(searchQuery), anything())
                .wasInvoked()
        }
    }

    @Test
    fun givenFederatedInput_whenSearchingUsers_thenSearchByNameOrHandleOrEmail() = runTest {
        //given
        val searchQuery = "someSearchQuery@wire.com"

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchKnownUsersByNameOrHandleOrEmail()
            .arrange()
        //when
        searchKnownUsersUseCase(searchQuery)
        //then
        with(arrangement) {
            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
                .with(anything(), anything())
                .wasNotInvoked()

            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .with(anything(), anything())
                .wasInvoked()
        }
    }

    @Test
    fun test() = runTest {
        //given
        val searchQuery = "someSearchQuery"

        val selfUserId = QualifiedID(
            value = "selfUser",
            domain = "wire.com",
        )

        val otherUserContainingSelfUserId = OtherUser(
            id = selfUserId,
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            team = null,
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.NONE,
            userType = UserType.EXTERNAL
        )

        val (_, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve(selfUserId)
            .withSearchKnownUsersByNameOrHandleOrEmail(searchQuery, otherUserContainingSelfUserId)
            .arrange()
        //when
        val result = searchKnownUsersUseCase(searchQuery)
        //then
        assertIs<Result.Success>(result)
        assertFalse(result.userSearchResult.result.contains(otherUserContainingSelfUserId))
    }

    @Test
    fun givenSearchingForHandleWithConversationExcluded_whenSearchingUsers_ThenPropagateTheSearchOption() = runTest {
        //given
        val searchQuery = "@someHandle"

        val searchUsersOptions = SearchUsersOptions(
            ConversationMemberExcludedOptions.ConversationExcluded(
                ConversationId("someValue", "someDomain")
            )
        )

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchByHandle(
                searchQuery = searchQuery,
                searchUsersOptions = searchUsersOptions
            ).withSearchKnownUsersByNameOrHandleOrEmail(
                searchQuery = searchQuery,
                searchUsersOptions = searchUsersOptions
            )
            .arrange()

        //when
        val result = searchKnownUsersUseCase(
            searchQuery = searchQuery,
            searchUsersOptions = searchUsersOptions
        )

        //then
        assertIs<Result.Success>(result)
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByHandle)
            .with(anything(), eq(searchUsersOptions))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSearchingForNameOrHandleOrEmailWithConversationExcluded_whenSearchingUsers_ThenPropagateTheSearchOption() = runTest {
        //given
        val searchQuery = "someSearchQuery"

        val searchUsersOptions = SearchUsersOptions(
            ConversationMemberExcludedOptions.ConversationExcluded(
                ConversationId("someValue", "someDomain")
            )
        )

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchKnownUsersByNameOrHandleOrEmail(
                searchQuery = searchQuery,
                searchUsersOptions = searchUsersOptions
            ).withSearchByHandle(
                searchQuery = searchQuery,
                searchUsersOptions = searchUsersOptions
            )
            .arrange()

        //when
        val result = searchKnownUsersUseCase(
            searchQuery = searchQuery,
            searchUsersOptions = searchUsersOptions
        )

        //then
        assertIs<Result.Success>(result)
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(anything(), eq(searchUsersOptions))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val searchUserRepository = mock(classOf<SearchUserRepository>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        fun withSuccessFullSelfUserRetrieve(
            id: QualifiedID = QualifiedID(
                value = "selfUser",
                domain = "wire.com",
            )
        ): Arrangement {
            val selfUser = TestUser.SELF.copy(id = id)

            given(userRepository)
                .suspendFunction(userRepository::getSelfUser)
                .whenInvoked()
                .thenReturn(
                    selfUser
                )

            return this
        }

        fun withSearchByHandle(
            searchQuery: String? = null,
            searchUsersOptions: SearchUsersOptions? = null
        ): Arrangement {
            given(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
                .whenInvokedWith(
                    if (searchQuery == null) any() else eq(searchQuery),
                    if (searchUsersOptions == null) any() else eq(searchUsersOptions)
                )
                .thenReturn(
                    UserSearchResult(
                        listOf(
                            OtherUser(
                                id = QualifiedID(
                                    value = "someValue",
                                    domain = "someDomain",
                                ),
                                name = null,
                                handle = null,
                                email = null,
                                phone = null,
                                accentId = 0,
                                team = null,
                                connectionStatus = ConnectionState.ACCEPTED,
                                previewPicture = null,
                                completePicture = null,
                                availabilityStatus = UserAvailabilityStatus.NONE,
                                userType = UserType.EXTERNAL
                            )
                        )
                    )
                )

            return this
        }

        fun withSearchKnownUsersByNameOrHandleOrEmail(
            searchQuery: String? = null,
            extraOtherUser: OtherUser? = null,
            searchUsersOptions: SearchUsersOptions? = null
        ): Arrangement {
            val otherUsers = listOf(
                OtherUser(
                    id = QualifiedID(
                        value = "someSearchQuery",
                        domain = "wire.com",
                    ),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    team = null,
                    connectionStatus = ConnectionState.ACCEPTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.NONE,
                    userType = UserType.FEDERATED
                )
            )

            if (extraOtherUser != null) {
                otherUsers.plus(extraOtherUser)
            }

            given(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .whenInvokedWith(
                    if (searchQuery == null) any() else eq(searchQuery),
                    if (searchUsersOptions == null) any() else eq(searchUsersOptions)
                )
                .thenReturn(
                    UserSearchResult(
                        otherUsers
                    )
                )

            return this
        }

        fun arrange(): Pair<Arrangement, SearchKnownUsersUseCase> {
            return this to SearchKnownUsersUseCaseImpl(searchUserRepository, userRepository)
        }
    }
}

