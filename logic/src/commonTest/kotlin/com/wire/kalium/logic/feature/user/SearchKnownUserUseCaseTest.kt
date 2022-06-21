package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.publicuser.SearchKnownUsersUseCase
import com.wire.kalium.logic.feature.publicuser.SearchKnownUsersUseCaseImpl
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SearchKnownUserUseCaseTest {

    @Mock
    private val searchUserRepository = mock(classOf<SearchUserRepository>())

    private lateinit var searchKnownUsersUseCase: SearchKnownUsersUseCase

    @BeforeTest
    fun setUp() {
        searchKnownUsersUseCase = SearchKnownUsersUseCaseImpl(searchUserRepository)
    }

    @Test
    fun givenAnInputStartingWithAtSymbol_whenSearchingUsers_thenSearchOnlyByHandle() = runTest {
        //given
        val handleSearchQuery = "@someHandle"

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
            .whenInvokedWith(eq(handleSearchQuery))
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
                            userType =  UserType.EXTERNAL
                        )
                    )
                )
            )
        //when
        searchKnownUsersUseCase(handleSearchQuery)
        //then
        verify(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
            .with(eq(handleSearchQuery))
            .wasInvoked()

        verify(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenNormalInput_whenSearchingUsers_thenSearchByNameOrHandleOrEmail() = runTest {
        //given
        val searchQuery = "someSearchQuery"

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .whenInvokedWith(eq(searchQuery))
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
        //when
        searchKnownUsersUseCase(searchQuery)
        //then
        verify(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
            .with(anything())
            .wasNotInvoked()

        verify(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(eq(searchQuery))
            .wasInvoked()
    }

    @Test
    fun givenFederatedInput_whenSearchingUsers_thenSearchByNameOrHandleOrEmail() = runTest {
        //given
        val searchQuery = "someSearchQuery@wire.com"

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .whenInvokedWith(eq("someSearchQuery"))
            .thenReturn(
                UserSearchResult(
                    listOf(
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
                        )
                    )
                )
            )
        //when
        searchKnownUsersUseCase(searchQuery)
        //then
        verify(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
            .with(anything())
            .wasNotInvoked()

        verify(searchUserRepository)
            .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(eq("someSearchQuery"))
            .wasInvoked()
    }

}
