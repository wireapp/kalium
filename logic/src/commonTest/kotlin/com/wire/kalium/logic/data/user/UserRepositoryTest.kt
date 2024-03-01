/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.user

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.legalhold.ListUsersLegalHoldConsent
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserDataSource.Companion.BATCH_SIZE
import com.wire.kalium.logic.data.user.UserDataSource.Companion.SELF_USER_ID_KEY
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestUser.LIST_USERS_DTO
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.test_util.TestNetworkException.federationNotEnabled
import com.wire.kalium.logic.test_util.TestNetworkException.generic
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.QualifiedUserIdListRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserEntityMinimized
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import io.ktor.http.HttpStatusCode
import io.mockative.KFunction1
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserRepositoryTest {

    @Test
    fun givenAllUsersAreKnown_whenFetchingUsersIfUnknown_thenShouldNotFetchFromApiAndSucceed() = runTest {
        val requestedUserIds = setOf(
            UserId(value = "id1", domain = "domain1"),
            UserId(value = "id2", domain = "domain2")
        )
        val knownUserEntities = listOf(
            TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1")),
            TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id2", domain = "domain2"))
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .arrange()

        given(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::getUsersDetailsByQualifiedIDList)
            .whenInvokedWith(any())
            .thenReturn(knownUserEntities)

        userRepository.fetchUsersIfUnknownByIds(requestedUserIds).shouldSucceed()

        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenAUserIsNotKnown_whenFetchingUsersIfUnknown_thenShouldFetchFromAPIAndSucceed() = runTest {
        val missingUserId = UserId(value = "id2", domain = "domain2")
        val requestedUserIds = setOf(UserId(value = "id1", domain = "domain1"), missingUserId)
        val knownUserEntities = listOf(TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1")))
        val (arrangement, userRepository) = Arrangement()
            .withGetSelfUserId()
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .withSuccessfulGetMultipleUsersApiRequest(ListUsersDTO(usersFailed = emptyList(), listOf(TestUser.USER_PROFILE_DTO)))
            .arrange()

        userRepository.fetchUsersIfUnknownByIds(requestedUserIds).shouldSucceed()

        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(matching { request: ListUserRequest ->
                (request as QualifiedUserIdListRequest).qualifiedIds.first() == missingUserId.toApi()
            })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserEvent_whenPersistingTheUser_thenShouldSucceed() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateUserReturning()
            .arrange()

        val result = userRepository.updateUserFromEvent(TestEvent.updateUser(userId = SELF_USER.id))

        with(result) {
            shouldSucceed()
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::updateUser, KFunction1<PartialUserEntity>())
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAnEmptyUserIdList_whenFetchingUsers_thenShouldNotFetchFromApiAndSucceed() = runTest {
        // given
        val requestedUserIds = emptySet<UserId>()
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(
                ListUsersDTO(
                    usersFailed = emptyList(),
                    usersFound = listOf(TestUser.USER_PROFILE_DTO)
                )
            )
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenAnUserIdListWithDifferentDomain_whenApiReturnsFederationDisabledError_thenShouldTryToFetchOnlyUsersWithSelfDomain() = runTest {
        // given
        val requestedUserIds = setOf(TestUser.OTHER_USER_ID, TestUser.OTHER_FEDERATED_USER_ID)
        val (arrangement, userRepository) = Arrangement()
            .withGetMultipleUsersApiRequestFederationNotEnabledError()
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldFail()
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
            .wasInvoked(exactly = once)
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(eq(QualifiedUserIdListRequest(listOf(TestUser.OTHER_USER_ID.toApi()))))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnEmptyUserIdListFromSameDomainAsSelf_whenFetchingUsers_thenShouldNotFetchMultipleUsersAndSucceed() = runTest {
        // given
        val requestedUserIds = setOf(
            UserId(value = "id1", domain = "domain1"),
            UserId(value = "id2", domain = "domain2")
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(ListUsersDTO(usersFailed = emptyList(), listOf(TestUser.USER_PROFILE_DTO)))
            .arrange()
        assertTrue { requestedUserIds.none { it.domain == arrangement.selfUserId.domain } }
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserIdListSmallerThanBatchSize_whenFetchingUsers_thenShouldExecuteRequestsOnce() = runTest {
        // given
        val requestedUserIds = buildSet {
            repeat(BATCH_SIZE - 1) { add(UserId(value = "id$it", domain = "domain")) }
        }
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(
                ListUsersDTO(
                    usersFailed = emptyList(),
                    usersFound = listOf(TestUser.USER_PROFILE_DTO)
                )
            )
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserIdListLargerThanBatchSize_whenFetchingUsers_thenShouldExecuteRequestsTwice() = runTest {
        // given
        val requestedUserIds = buildSet {
            repeat(BATCH_SIZE + 1) { add(UserId(value = "id$it", domain = "domain")) }
        }
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(
                ListUsersDTO(
                    usersFailed = emptyList(),
                    usersFound = listOf(TestUser.USER_PROFILE_DTO)
                )
            )
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenARemoteUserIsDeleted_whenFetchingSelfUser_thenShouldFailWithProperError() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withRemoteGetSelfReturningDeletedUser()
            .arrange()
        // when
        val result = userRepository.fetchSelfUser()
        // then
        with(result) {
            shouldFail { it is SelfUserDeleted }
            verify(arrangement.selfApi)
                .suspendFunction(arrangement.selfApi::getSelfInfo)
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun whenFetchingKnownUsers_thenShouldFetchFromDatabaseAndApiAndSucceed() = runTest {
        // Given
        val knownUserEntities = listOf(
            UserIDEntity(value = "id1", domain = "domain1"),
            UserIDEntity(value = "id2", domain = "domain2")
        )
        val knownUserIds = knownUserEntities.map { UserId(it.value, it.domain) }.toSet()
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsers()
            .arrange {
                withAllOtherUsersIdSuccess(knownUserEntities)
            }

        // When
        userRepository.fetchAllOtherUsers().shouldSucceed()

        // Then
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::allOtherUsersId)
            .wasInvoked(exactly = once)

        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(eq(ListUserRequest.qualifiedIds(knownUserIds.map { userId -> userId.toApi() })))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfUserIsUnknown_whenObservingSelfUser_thenShouldAttemptToFetchIt() = runTest {
        val selfUserIdChannel = Channel<String?>(Channel.UNLIMITED)
        selfUserIdChannel.send(null)
        selfUserIdChannel.send(TestUser.JSON_QUALIFIED_ID)
        // given
        val (arrangement, userRepository) = Arrangement()
            .withSelfUserIdFlowMetadataReturning(selfUserIdChannel.consumeAsFlow())
            .withRemoteGetSelfReturningDeletedUser()
            .arrange()
        // when
        userRepository.observeSelfUser().first()
        // then
        verify(arrangement.selfApi)
            .suspendFunction(arrangement.selfApi::getSelfInfo)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAKnownFederatedUser_whenGettingFromDbAndCacheExpiredOrNotPresent_thenShouldRefreshItsDataFromAPI() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.DETAILS_ENTITY.copy(userType = UserTypeEntity.FEDERATED))
            .withSuccessfulGetUsersInfo()
            .arrange()

        val result = userRepository.getKnownUser(TestUser.USER_ID)

        result.collect {
            verify(arrangement.userDetailsApi)
                .suspendFunction(arrangement.userDetailsApi::getUserInfo)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertUsers)
                .with(any())
                .wasInvoked()
        }
    }

    @Test
    fun givenAKnownUser_whenGettingFromDb_thenShouldRefreshItsDataFromAPI() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.DETAILS_ENTITY)
            .withSuccessfulGetUsersInfo()
            .arrange()

        val result = userRepository.getKnownUser(TestUser.USER_ID)

        result.collect {
            verify(arrangement.userDetailsApi)
                .suspendFunction(arrangement.userDetailsApi::getUserInfo)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertUsers)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAKnownUser_whenGettingFromDbAndCacheValid_thenShouldNOTRefreshItsDataFromAPI() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.DETAILS_ENTITY)
            .withSuccessfulGetUsersInfo()
            .arrange()

        val result = userRepository.getKnownUser(TestUser.USER_ID)

        result.collect {
            verify(arrangement.userDetailsApi)
                .suspendFunction(arrangement.userDetailsApi::getUserInfo)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertUsers)
                .with(any())
                .wasInvoked(exactly = once)
        }

        val resultSecondTime = userRepository.getKnownUser(TestUser.USER_ID)
        resultSecondTime.collect {
            verify(arrangement.userDetailsApi)
                .suspendFunction(arrangement.userDetailsApi::getUserInfo)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertUsers)
                .with(any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenThereAreUsersWithoutMetadata_whenSyncingUsers_thenShouldUpdateThem() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withDaoReturningNoMetadataUsers(listOf(TestUser.DETAILS_ENTITY.copy(name = null)))
            .withSuccessfulGetMultipleUsersApiRequest(ListUsersDTO(emptyList(), listOf(TestUser.USER_PROFILE_DTO)))
            .arrange()

        // when
        userRepository.syncUsersWithoutMetadata()
            .shouldSucceed()

        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertUsers)
            .with(matching {
                it.firstOrNull()?.name != null
            })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenThereAreNOUsersWithoutMetadata_whenSyncingUsers_thenShouldNOTUpdateThem() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withDaoReturningNoMetadataUsers(listOf())
            .arrange()

        // when
        userRepository.syncUsersWithoutMetadata()
            .shouldSucceed()

        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertUsers)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun whenRemovingUserBrokenAsset_thenShouldCallDaoAndSucceed() = runTest {
        // Given
        val qualifiedIdToRemove = QualifiedID(value = "id", domain = "domain")
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulRemoveUserAsset()
            .arrange()

        // When
        userRepository.removeUserBrokenAsset(qualifiedIdToRemove).shouldSucceed()

        // Then
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::removeUserAsset)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun whenObservingKnowUsers_thenShouldReturnUsersThatHaveMetadata() = runTest {
        // Given
        val (arrangement, userRepository) = Arrangement()
            .withGetSelfUserId()
            .withDaoObservingByConnectionStatusReturning(
                listOf(
                    TestUser.DETAILS_ENTITY.copy(id = QualifiedIDEntity("id-valid", "domain2"), hasIncompleteMetadata = false),
                    TestUser.DETAILS_ENTITY.copy(id = QualifiedIDEntity("id2", "domain2"), hasIncompleteMetadata = true)
                )
            )
            .arrange()

        // When
        userRepository.observeAllKnownUsers().test {
            // Then
            awaitItem().also {
                val users = it.getOrNull()
                assertEquals(1, users!!.size)
                assertTrue { users.first().name == TestUser.ENTITY.name }
            }
            cancelAndIgnoreRemainingEvents()
        }

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::observeAllUsersDetailsByConnectionStatus)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun whenObservingKnowUsersNotInConversation_thenShouldReturnUsersThatHaveMetadata() = runTest {
        // Given
        val (arrangement, userRepository) = Arrangement()
            .withGetSelfUserId()
            .withDaoObservingNotInConversationReturning(
                listOf(
                    TestUser.DETAILS_ENTITY.copy(id = QualifiedIDEntity("id-valid", "domain2"), hasIncompleteMetadata = false),
                    TestUser.DETAILS_ENTITY.copy(id = QualifiedIDEntity("id2", "domain2"), hasIncompleteMetadata = true)
                )
            )
            .arrange()

        // When
        userRepository.observeAllKnownUsersNotInConversation(TestConversation.ID).test {
            // Then
            awaitItem().also {
                val users = it.getOrNull()
                assertEquals(1, users!!.size)
                assertTrue { users.first().name == TestUser.ENTITY.name }
            }
            cancelAndIgnoreRemainingEvents()
        }

        verify(arrangement.userDAO)
            .function(arrangement.userDAO::observeUsersDetailsNotInConversation)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenUserIdWhenDefederateUser_thenShouldMarkUserAsDefederated() = runTest {
        // Given
        val (arrangement, userRepository) = Arrangement()
            .withMarkUserAsDefederated()
            .arrange()

        // When
        userRepository.defederateUser(TestUser.OTHER_FEDERATED_USER_ID).shouldSucceed()

        // Then
        verify(arrangement.userDAO)
            .function(arrangement.userDAO::markUserAsDefederated)
            .with(eq(TestUser.OTHER_FEDERATED_USER_ID.toDao()))
            .wasInvoked(once)
    }

    @Test
    fun givenUserIds_WhenRequestingSummaries_thenShouldSucceed() = runTest {
        // Given
        val requestedUserIds = listOf(
            UserId(value = "id1", domain = "domain1"),
            UserId(value = "id2", domain = "domain2")
        )
        val knownUserEntities = listOf(
            TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1")),
            TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id2", domain = "domain2"))
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .arrange()

        // When
        userRepository.getUsersSummaryByIds(requestedUserIds).shouldSucceed()

        // Then
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::getUsersDetailsByQualifiedIDList)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenANewSupportedProtocols_whenUpdatingOk_thenShouldSucceedAndPersistTheSupportedProtocolsLocally() = runTest {
        val successResponse = NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value)
        val (arrangement, userRepository) = Arrangement()
            .withGetSelfUserId()
            .withUpdateSupportedProtocolsApiRequestResponse(successResponse)
            .arrange()

        val result = userRepository.updateSupportedProtocols(setOf(SupportedProtocol.MLS))

        with(result) {
            shouldSucceed()
            verify(arrangement.selfApi)
                .suspendFunction(arrangement.selfApi::updateSupportedProtocols)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::updateUserSupportedProtocols)
                .with(any(), any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenANewSupportedProtocols_whenUpdatingFails_thenShouldNotPersistSupportedProtocolsLocally() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withGetSelfUserId()
            .withUpdateSupportedProtocolsApiRequestResponse(TestNetworkResponseError.genericResponseError())
            .arrange()

        val result = userRepository.updateSupportedProtocols(setOf(SupportedProtocol.MLS))

        with(result) {
            shouldFail()
            verify(arrangement.selfApi)
                .suspendFunction(arrangement.selfApi::updateSupportedProtocols)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::updateUserSupportedProtocols)
                .with(any(), any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenUserIdAndConversationId_whenUpdatingOneOnOneConversation_thenShouldCallDAOWithCorrectArguments() = runTest {
        val userId = TestUser.USER_ID
        val conversationId = TestConversation.CONVERSATION.id

        val (arrangement, userRepository) = Arrangement()
            .withUpdateOneOnOneConversationSuccess()
            .arrange()

        userRepository.updateActiveOneOnOneConversation(
            userId,
            conversationId
        ).shouldSucceed()

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::updateActiveOneOnOneConversation)
            .with(eq(userId.toDao()), eq(conversationId.toDao()))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenDAOFails_whenUpdatingOneOnOneConversation_thenShouldPropagateException() = runTest {
        val exception = IllegalStateException("Oopsie Doopsie!")
        val (_, connectionRepository) = Arrangement()
            .withUpdateOneOnOneConversationFailing(exception)
            .arrange()
        val userId = TestUser.USER_ID
        val conversationId = TestConversation.CONVERSATION.id

        connectionRepository.updateActiveOneOnOneConversation(
            userId,
            conversationId
        ).shouldFail {
            assertIs<StorageFailure.Generic>(it)
            assertEquals(exception, it.rootCause)
        }
    }

    @Test
    fun givenUserDAOReturnsFailure_whenCallingGetKnownUserMinimized_thenReturnFailure() = runTest {
        val (_, userRepository) = Arrangement()
            .withUserDAOReturning(null)
            .arrange()

        val result = userRepository.getKnownUserMinimized(TestUser.USER_ID)

        result.shouldFail()
    }

    @Test
    fun givenUserDAOReturnsUserMinimized_whenCallingGetKnownUserMinimized_thenReturnUserMinimized() = runTest {
        val userMinimized = UserEntityMinimized(
            id = QualifiedIDEntity("id", "domain"),
            name = "Max",
            userType = UserTypeEntity.ADMIN,
            completeAssetId = null
        )
        val (arrangement, userRepository) = Arrangement()
            .withUserDAOReturning(userMinimized)
            .arrange()

        val result = userRepository.getKnownUserMinimized(TestUser.USER_ID)

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::getUserMinimizedByQualifiedID)
            .with(any())
            .wasInvoked(exactly = once)
        result.shouldSucceed {
            assertIs<OtherUserMinimized>(it)
        }
    }

    @Test
    fun givenATeamMemberUser_whenFetchingUserInfo_thenItShouldBeUpsertedAsATeamMember() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.DETAILS_ENTITY.copy(team = TestTeam.TEAM_ID.value))
            .withSuccessfulGetUsersInfo(TestUser.USER_PROFILE_DTO.copy(teamId = TestTeam.TEAM_ID.value))
            .withSuccessfulFetchTeamMembersByIds(listOf(TestTeam.memberDTO((TestUser.USER_PROFILE_DTO.id.value))))
            .arrange()

        val result = userRepository.fetchUserInfo(TestUser.USER_ID)

        assertIs<Either.Right<Unit>>(result)
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getUserInfo)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::getTeamMembersByIds)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertUsers)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertConnectionStatuses)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertUsers)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserId_whenFetchingUserInfoFailed_thenItShouldInsertIncompleteUserData() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withFailingGetUserInfo()
            .arrange()

        userRepository.fetchUserInfo(TestUser.USER_ID)

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::insertOrIgnoreIncompleteUsers)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun givenApiRequestSucceeds_whenFetchingUsersLegalHoldConsent_thenShouldReturnProperValues() = runTest {
        // given
        val userIdWithConsent = TestUser.OTHER_USER_ID.copy(value = "idWithConsent")
        val userIdWithoutConsent = TestUser.OTHER_USER_ID.copy(value = "idWithoutConsent")
        val userIdFailed = TestUser.OTHER_USER_ID.copy(value = "idFailed")
        val requestedUserIds = setOf(userIdWithConsent, userIdWithoutConsent, userIdFailed)
        val expectedResult = ListUsersLegalHoldConsent(
            usersWithConsent = listOf(userIdWithConsent),
            usersWithoutConsent = listOf(userIdWithoutConsent),
            usersFailed = listOf(userIdFailed),
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(
                ListUsersDTO(
                    usersFound = listOf(
                        TestUser.USER_PROFILE_DTO.copy(id = userIdWithConsent.toApi(), legalHoldStatus = LegalHoldStatusDTO.DISABLED),
                        TestUser.USER_PROFILE_DTO.copy(id = userIdWithoutConsent.toApi(), legalHoldStatus = LegalHoldStatusDTO.NO_CONSENT),
                    ),
                    usersFailed = listOf(userIdFailed.toApi()),
                )
            )
            .arrange()
        // when
        val result = userRepository.fetchUsersLegalHoldConsent(requestedUserIds)
        // then
        result.shouldSucceed {
            assertEquals(expectedResult, it)
        }
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestFails_whenFetchingUsersLegalHoldConsent_thenShouldPropagateError() = runTest {
        // given
        val requestedUserIds = setOf(TestUser.OTHER_USER_ID)
        val (arrangement, userRepository) = Arrangement()
            .withGetMultipleUsersApiRequestGenericError()
            .arrange()
        // when
        val result = userRepository.fetchUsersLegalHoldConsent(requestedUserIds)
        // then
        result.shouldFail()
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userDAO = configure(mock(classOf<UserDAO>())) { stubsUnitByDefault = true }

        @Mock
        val metadataDAO = configure(mock(classOf<MetadataDAO>())) { stubsUnitByDefault = true }

        @Mock
        val clientDAO = configure(mock(classOf<ClientDAO>())) { stubsUnitByDefault = true }

        @Mock
        val selfApi = mock(classOf<SelfApi>())

        @Mock
        val userDetailsApi = mock(classOf<UserDetailsApi>())

        @Mock
        val teamsApi = mock(classOf<TeamsApi>())

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        val selfUserId = TestUser.SELF.id

        val userRepository: UserRepository by lazy {
            UserDataSource(
                userDAO,
                metadataDAO,
                clientDAO,
                selfApi,
                userDetailsApi,
                teamsApi,
                sessionRepository,
                selfUserId,
                selfTeamIdProvider
            )
        }

        init {
            withSelfUserIdFlowMetadataReturning(flowOf(TestUser.JSON_QUALIFIED_ID))
            given(userDAO).suspendFunction(userDAO::observeUserDetailsByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(TestUser.DETAILS_ENTITY) }

            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .then { Either.Right(TestTeam.TEAM_ID) }
        }

        fun withUserDAOReturning(value: UserEntityMinimized?) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUserMinimizedByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(value)
        }

        fun withSelfUserIdFlowMetadataReturning(selfUserIdStringFlow: Flow<String?>) = apply {
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(eq(SELF_USER_ID_KEY))
                .thenReturn(selfUserIdStringFlow)
        }

        fun withDaoObservingByConnectionStatusReturning(userEntities: List<UserDetailsEntity>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::observeAllUsersDetailsByConnectionStatus)
                .whenInvokedWith(any())
                .thenReturn(flowOf(userEntities))
        }

        fun withDaoObservingNotInConversationReturning(userEntities: List<UserDetailsEntity>) = apply {
            given(userDAO)
                .function(userDAO::observeUsersDetailsNotInConversation)
                .whenInvokedWith(any())
                .thenReturn(flowOf(userEntities))
        }

        fun withUpdateUserReturning() = apply {
            given(userDAO)
                .suspendFunction(userDAO::updateUser, KFunction1<PartialUserEntity>())
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withSuccessfulGetUsersInfo(result: UserProfileDTO = TestUser.USER_PROFILE_DTO) = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getUserInfo)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(result, mapOf(), 200))
        }

        fun withFailingGetUserInfo() = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getUserInfo)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Error(TestNetworkException.generic))
        }

        fun withSuccessfulFetchTeamMembersByIds(result: List<TeamsApi.TeamMemberDTO>) = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::getTeamMembersByIds)
                .whenInvokedWith(any(), any())
<<<<<<< HEAD
                .thenReturn(NetworkResponse.Success(TeamsApi.TeamMemberList(false, result), mapOf(), 200))
=======
                .thenReturn(NetworkResponse.Success(TeamsApi.TeamMemberListNonPaginated(false, result), mapOf(), 200))
>>>>>>> 11bc5e6c20 (fix: missing json value pagingState when getting team member by ID (#2576))
        }

        fun withSuccessfulGetUsersByQualifiedIdList(knownUserEntities: List<UserDetailsEntity>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUsersDetailsByQualifiedIDList)
                .whenInvokedWith(any())
                .thenReturn(knownUserEntities)
        }

        fun withUserDaoReturning(userEntity: UserDetailsEntity? = TestUser.DETAILS_ENTITY) = apply {
            given(userDAO).suspendFunction(userDAO::observeUserDetailsByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(userEntity) }
        }

        fun withDaoReturningNoMetadataUsers(userEntity: List<UserDetailsEntity> = emptyList()) = apply {
            given(userDAO).suspendFunction(userDAO::getUsersDetailsWithoutMetadata)
                .whenInvoked()
                .then { userEntity }
        }

        fun withGetSelfUserId() = apply {
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKey)
                .whenInvokedWith(any())
                .thenReturn(
                    """
                    {
                        "value" : "someValue",
                        "domain" : "someDomain"
                    }
                """.trimIndent()
                )
        }

        fun withRemoteGetSelfReturningDeletedUser(): Arrangement = apply {
            given(selfApi)
                .suspendFunction(selfApi::getSelfInfo)
                .whenInvoked()
                .thenReturn(NetworkResponse.Success(TestUser.SELF_USER_DTO.copy(deleted = true), mapOf(), 200))
        }

        fun withSuccessfulGetMultipleUsersApiRequest(result: ListUsersDTO) = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value))
        }

        fun withGetMultipleUsersApiRequestFederationNotEnabledError() = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Error(federationNotEnabled))
        }

        fun withGetMultipleUsersApiRequestGenericError() = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Error(generic))
        }

        fun withUpdateDisplayNameApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            given(selfApi)
                .suspendFunction(selfApi::updateSelf)
                .whenInvokedWith(any())
                .thenReturn(response)
        }

        fun withUpdateSupportedProtocolsApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            given(selfApi)
                .suspendFunction(selfApi::updateSupportedProtocols)
                .whenInvokedWith(any())
                .thenReturn(response)
        }

        fun withRemoteUpdateEmail(result: NetworkResponse<Boolean>) = apply {
            given(selfApi)
                .suspendFunction(selfApi::updateEmailAddress)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withSuccessfulRemoveUserAsset() = apply {
            given(userDAO)
                .suspendFunction(userDAO::removeUserAsset)
                .whenInvokedWith(any())
                .then { Either.Right(Unit) }
        }

        fun withSuccessfulGetAllUsers(userEntities: List<UserDetailsEntity>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getAllUsersDetails)
                .whenInvoked()
                .then { flowOf(userEntities) }
        }

        fun withSuccessfulGetMultipleUsers() = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(value = LIST_USERS_DTO, headers = mapOf(), httpCode = 200) }
        }

        fun withAllOtherUsersIdSuccess(
            result: List<UserIDEntity>,
        ) {
            given(userDAO)
                .suspendFunction(userDAO::allOtherUsersId)
                .whenInvoked()
                .then { result }
        }

        fun withMarkUserAsDefederated() = apply {
            given(userDAO)
                .suspendFunction(userDAO::markUserAsDefederated)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withUpdateOneOnOneConversationSuccess() = apply {
            given(userDAO)
                .suspendFunction(userDAO::updateActiveOneOnOneConversation)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withUpdateOneOnOneConversationFailing(exception: Throwable) = apply {
            given(userDAO)
                .suspendFunction(userDAO::updateActiveOneOnOneConversation)
                .whenInvokedWith(any(), any())
                .thenThrow(exception)
        }

        fun withInsertOrIgnoreUsers() {
            given(userDAO)
                .suspendFunction(userDAO::insertOrIgnoreIncompleteUsers)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun arrange(block: (Arrangement.() -> Unit) = { }): Pair<Arrangement, UserRepository> {
            apply(block)
            return this to userRepository
        }
    }

    private companion object {
        val SELF_USER = TestUser.SELF
    }
}
