package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.other.OtherUserRepository
import com.wire.kalium.logic.data.user.other.model.OtherUser
import com.wire.kalium.logic.data.user.other.model.OtherUserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
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
    private val otherUserRepository = mock(classOf<OtherUserRepository>())

    private lateinit var searchKnownUsersUseCase: SearchKnownUsersUseCase

    @BeforeTest
    fun setUp() {
        searchKnownUsersUseCase = SearchKnownUsersUseCaseImpl(otherUserRepository)
    }

    @Test
    fun givenAnInputStartingWithAtSymbol_whenSearchingUsers_thenSearchOnlyByHandle() = runTest {
        //given
        val handleSearchQuery = "@someHandle"

        given(otherUserRepository)
            .suspendFunction(otherUserRepository::searchKnownUsersByHandle)
            .whenInvokedWith(eq(handleSearchQuery))
            .thenReturn(
                OtherUserSearchResult(
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
        //when
        searchKnownUsersUseCase(handleSearchQuery)
        //then
        verify(otherUserRepository)
            .suspendFunction(otherUserRepository::searchKnownUsersByHandle)
            .with(eq(handleSearchQuery))
            .wasInvoked()

        verify(otherUserRepository)
            .suspendFunction(otherUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenNormalInput_whenSearchingUsers_thenSearchByNameOrHandleOrEmail() = runTest {
        //given
        val searchQuery = "someSearchQuery"

        given(otherUserRepository)
            .suspendFunction(otherUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .whenInvokedWith(eq(searchQuery))
            .thenReturn(
                OtherUserSearchResult(
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
        //when
        searchKnownUsersUseCase(searchQuery)
        //then
        verify(otherUserRepository)
            .suspendFunction(otherUserRepository::searchKnownUsersByHandle)
            .with(anything())
            .wasNotInvoked()

        verify(otherUserRepository)
            .suspendFunction(otherUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(eq(searchQuery))
            .wasInvoked()
    }

}
