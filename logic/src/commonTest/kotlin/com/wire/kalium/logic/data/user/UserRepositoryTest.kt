/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserDataSource.Companion.BATCH_SIZE
import com.wire.kalium.logic.data.user.UserDataSource.Companion.SELF_USER_ID_KEY
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestUser.LIST_USERS_DTO
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.sync.receiver.UserEventReceiverTest
import com.wire.kalium.logic.test_util.TestNetworkException.federationNotEnabled
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.QualifiedUserIdListRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import io.ktor.http.HttpStatusCode
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
            TestUser.ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1")),
            TestUser.ENTITY.copy(id = UserIDEntity(value = "id2", domain = "domain2"))
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .arrange()

        given(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::getUsersByQualifiedIDList)
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
        val knownUserEntities = listOf(TestUser.ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1")))
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
            .withMapperQualifiedUserId()
            .arrange()

        val result = userRepository.updateUserFromEvent(TestEvent.updateUser(userId = UserEventReceiverTest.SELF_USER_ID))

        with(result) {
            shouldSucceed()

            verify(arrangement.qualifiedIdMapper)
                .function(arrangement.qualifiedIdMapper::fromStringToQualifiedID)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::getUserByQualifiedID)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::updateUser)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAUserEvent_whenPersistingTheUserAndNotExists_thenShouldFail() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withMapperQualifiedUserId()
            .withUserDaoReturning(null)
            .arrange()

        val result = userRepository.updateUserFromEvent(TestEvent.updateUser(userId = UserEventReceiverTest.SELF_USER_ID))

        with(result) {
            shouldFail()

            verify(arrangement.qualifiedIdMapper)
                .function(arrangement.qualifiedIdMapper::fromStringToQualifiedID)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::getUserByQualifiedID)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::updateUser)
                .with(any())
                .wasNotInvoked()
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
            .withUserDaoReturning(TestUser.ENTITY.copy(userType = UserTypeEntity.FEDERATED))
            .withSuccessfulGetUsersInfo()
            .arrange()

        val result = userRepository.getKnownUser(TestUser.USER_ID)

        result.collect {
            verify(arrangement.userDetailsApi)
                .suspendFunction(arrangement.userDetailsApi::getUserInfo)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertTeamMembers)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertUsers)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAKnownUser_whenGettingFromDb_thenShouldRefreshItsDataFromAPI() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.ENTITY)
            .withSuccessfulGetUsersInfo()
            .arrange()

        val result = userRepository.getKnownUser(TestUser.USER_ID)

        result.collect {
            verify(arrangement.userDetailsApi)
                .suspendFunction(arrangement.userDetailsApi::getUserInfo)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertTeamMembers)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertUsers)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAKnownUser_whenGettingFromDbAndCacheValid_thenShouldNOTRefreshItsDataFromAPI() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.ENTITY)
            .withSuccessfulGetUsersInfo()
            .arrange()

        val result = userRepository.getKnownUser(TestUser.USER_ID)

        result.collect {
            verify(arrangement.userDetailsApi)
                .suspendFunction(arrangement.userDetailsApi::getUserInfo)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertTeamMembers)
                .with(any())
                .wasNotInvoked()
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
                .suspendFunction(arrangement.userDAO::upsertTeamMembers)
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
            .withDaoReturningNoMetadataUsers(listOf(TestUser.ENTITY.copy(name = null)))
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
                it.first().name != null
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
                    TestUser.ENTITY.copy(id = QualifiedIDEntity("id-valid", "domain2"), hasIncompleteMetadata = false),
                    TestUser.ENTITY.copy(id = QualifiedIDEntity("id2", "domain2"), hasIncompleteMetadata = true)
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
            .suspendFunction(arrangement.userDAO::observeAllUsersByConnectionStatus)
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
                    TestUser.ENTITY.copy(id = QualifiedIDEntity("id-valid", "domain2"), hasIncompleteMetadata = false),
                    TestUser.ENTITY.copy(id = QualifiedIDEntity("id2", "domain2"), hasIncompleteMetadata = true)
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
            .function(arrangement.userDAO::observeUsersNotInConversation)
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
    fun givenATeamMemberUser_whenFetchingUserInfo_thenItShouldBeUpsertedAsATeamMember() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.ENTITY.copy(team = TestTeam.TEAM_ID.value))
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
            .suspendFunction(arrangement.userDAO::upsertTeamMembers)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertTeamMembersTypes)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertUsers)
            .with(any())
            .wasNotInvoked()
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
        val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

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
                qualifiedIdMapper,
                selfTeamIdProvider
            )
        }

        init {
            withSelfUserIdFlowMetadataReturning(flowOf(TestUser.JSON_QUALIFIED_ID))
            given(userDAO).suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(TestUser.ENTITY) }

            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .then { Either.Right(TestTeam.TEAM_ID) }
        }

        fun withSelfUserIdFlowMetadataReturning(selfUserIdStringFlow: Flow<String?>) = apply {
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(eq(SELF_USER_ID_KEY))
                .thenReturn(selfUserIdStringFlow)
        }

        fun withDaoObservingByConnectionStatusReturning(userEntities: List<UserEntity>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::observeAllUsersByConnectionStatus)
                .whenInvokedWith(any())
                .thenReturn(flowOf(userEntities))
        }

        fun withDaoObservingNotInConversationReturning(userEntities: List<UserEntity>) = apply {
            given(userDAO)
                .function(userDAO::observeUsersNotInConversation)
                .whenInvokedWith(any())
                .thenReturn(flowOf(userEntities))
        }

        fun withSuccessfulGetUsersInfo(result: UserProfileDTO = TestUser.USER_PROFILE_DTO) = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getUserInfo)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(result, mapOf(), 200))
        }

        fun withSuccessfulFetchTeamMembersByIds(result: List<TeamsApi.TeamMemberDTO>) = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::getTeamMembersByIds)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Success(TeamsApi.TeamMemberList(false, result), mapOf(), 200))
        }

        fun withSuccessfulGetUsersByQualifiedIdList(knownUserEntities: List<UserEntity>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUsersByQualifiedIDList)
                .whenInvokedWith(any())
                .thenReturn(knownUserEntities)
        }

        fun withMapperQualifiedUserId(nonQualifiedId: String = "alice@wonderland") = apply {
            given(qualifiedIdMapper)
                .function(qualifiedIdMapper::fromStringToQualifiedID)
                .whenInvokedWith(eq(nonQualifiedId))
                .thenReturn(com.wire.kalium.logic.data.id.QualifiedID("alice", "wonderland"))
        }

        fun withUserDaoReturning(userEntity: UserEntity? = TestUser.ENTITY) = apply {
            given(userDAO).suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(userEntity) }
        }

        fun withDaoReturningNoMetadataUsers(userEntity: List<UserEntity> = emptyList()) = apply {
            given(userDAO).suspendFunction(userDAO::getUsersWithoutMetadata)
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

        fun withUpdateDisplayNameApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            given(selfApi)
                .suspendFunction(selfApi::updateSelf)
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

        fun withSuccessfulGetAllUsers(userEntities: List<UserEntity>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getAllUsers)
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

        fun arrange(block: (Arrangement.() -> Unit) = { }): Pair<Arrangement, UserRepository> {
            apply(block)
            return this to userRepository
        }
    }

    private companion object {
        val SELF_USER = TestUser.SELF_USER_DTO
    }
}
