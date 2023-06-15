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

import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserDataSource.Companion.SELF_USER_ID_KEY
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.UserEventReceiverTest
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
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
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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
        val requestedUserIds = setOf(
            UserId(value = "id1", domain = "domain1"),
            missingUserId
        )
        val knownUserEntities = listOf(
            TestUser.ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1"))
        )
        val (arrangement, userRepository) = Arrangement()
            .withGetSelfUserId()
            .withSuccessfulGetUsersInfo()
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .withSuccessfulGetMultipleUsersApiRequest(ListUsersDTO(usersFailed = emptyList(), listOf(TestUser.USER_PROFILE_DTO)))
            .arrange()

        userRepository.fetchUsersIfUnknownByIds(requestedUserIds).shouldSucceed()

        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getUserInfo)
            .with(eq(QualifiedID("id2", "domain2")))
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
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getUserInfo)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenAnEmptyUserIdListFromSameDomainAsSelf_whenFetchingUsers_thenShouldNotFetchMultipleUsersAndSucceed() = runTest {
        // given
        val requestedUserIds = setOf(
            UserId(value = "id1", domain = "domain1"),
            UserId(value = "id2", domain = "domain2")
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetUsersInfo()
            .arrange()
        assertTrue { requestedUserIds.none { it.domain == arrangement.selfUserId.domain } }
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()
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
    fun givenANewDisplayName_whenUpdatingOk_thenShouldSucceedAndPersistTheNameLocally() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withGetSelfUserId()
            .withUpdateDisplayNameApiRequestResponse(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))
            .arrange()

        val result = userRepository.updateSelfDisplayName("newDisplayName")

        with(result) {
            shouldSucceed()
            verify(arrangement.selfApi)
                .suspendFunction(arrangement.selfApi::updateSelf)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::updateUserDisplayName)
                .with(any(), any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenANewDisplayName_whenUpdatingFails_thenShouldNotPersistLocallyTheName() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withGetSelfUserId()
            .withUpdateDisplayNameApiRequestResponse(TestNetworkResponseError.genericResponseError())
            .arrange()

        val result = userRepository.updateSelfDisplayName("newDisplayName")

        with(result) {
            shouldFail()
            verify(arrangement.selfApi)
                .suspendFunction(arrangement.selfApi::updateSelf)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::updateUserDisplayName)
                .with(any(), any())
                .wasNotInvoked()
        }
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
                .wasInvoked(exactly = once)
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::upsertUsers)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAKnownNOTFederatedUser_whenGettingFromDb_thenShouldNotRefreshItsDataFromAPI() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.ENTITY.copy(userType = UserTypeEntity.STANDARD))
            .withSuccessfulGetUsersInfo()
            .arrange()

        val result = userRepository.getKnownUser(TestUser.USER_ID)

        result.collect {
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
    fun givenAKnownFederatedUser_whenGettingFromDbAndCacheValid_thenShouldNOTRefreshItsDataFromAPI() = runTest {
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
    fun givenUpdateEmailSuccess_whenChangingEmail_thenSuccessIsReturned() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withRemoteUpdateEmail(NetworkResponse.Success(true, mapOf(), 200))
            .arrange()

        val result = userRepository.updateSelfEmail("newEmail")

        with(result) {
            shouldSucceed()
            verify(arrangement.selfApi)
                .suspendFunction(arrangement.selfApi::updateEmailAddress)
                .with(eq("newEmail"))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenUpdateEmailFailure_whenChangingEmail_thenFailureIsReturned() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withRemoteUpdateEmail(NetworkResponse.Error(KaliumException.GenericError(IOException())))
            .arrange()

        val result = userRepository.updateSelfEmail("newEmail")

        with(result) {
            shouldFail()
            verify(arrangement.selfApi)
                .suspendFunction(arrangement.selfApi::updateEmailAddress)
                .with(eq("newEmail"))
                .wasInvoked(exactly = once)
        }
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

// TODO other UserRepository tests

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

        fun withSuccessfulGetUsersInfo() = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getUserInfo)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(TestUser.USER_PROFILE_DTO, mapOf(), 200))
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

        fun arrange() = this to userRepository
    }

    private companion object {
        val SELF_USER = TestUser.SELF_USER_DTO
    }
}
