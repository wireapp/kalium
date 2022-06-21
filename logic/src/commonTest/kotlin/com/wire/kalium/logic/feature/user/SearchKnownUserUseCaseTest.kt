package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.feature.publicuser.SearchKnownUsersUseCase
import com.wire.kalium.logic.feature.publicuser.SearchKnownUsersUseCaseImpl
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
import com.wire.kalium.logic.feature.publicuser.Result
import com.wire.kalium.logic.framework.TestUser
import kotlinx.coroutines.flow.flowOf
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
            .with(eq(handleSearchQuery))
            .wasInvoked()

        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(anything())
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
                .with(anything())
                .wasNotInvoked()

            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .with(eq(searchQuery))
                .wasInvoked()
        }
    }

    @Test
    fun givenFederatedInput_whenSearchingUsers_thenSearchByNameOrHandleOrEmail() = runTest {
        //given
        val searchQuery = "someSearchQuery@wire.com"

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
                .with(anything())
                .wasNotInvoked()

            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .with(eq(searchQuery))
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
            availabilityStatus = UserAvailabilityStatus.NONE
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

}

class Arrangement {

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
                flowOf(selfUser)
            )

        return this
    }

    fun withSearchByHandle(searchQuery: String? = null): Arrangement {
        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
            .whenInvokedWith(if (searchQuery == null) any() else eq(searchQuery))
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
                            availabilityStatus = UserAvailabilityStatus.NONE
                        )
                    )
                )
            )

        return this
    }

    fun withSearchKnownUsersByNameOrHandleOrEmail(
        searchQuery: String? = null,
        extraOtherUser: OtherUser? = null
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
                availabilityStatus = UserAvailabilityStatus.NONE
            ),
        )

        if (extraOtherUser != null) {
            otherUsers.plus(extraOtherUser)
        }

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .whenInvokedWith(if (searchQuery == null) any() else eq(searchQuery))
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
