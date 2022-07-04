package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.publicuser.SearchUserRepositoryTest
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import io.ktor.http.HttpStatusCode
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ConfigurationApi::class)
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
            .thenReturn(flowOf(knownUserEntities))

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
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .withSuccessfulGetMultipleUsersApiRequest(listOf(TestUser.USER_PROFILE_DTO))
            .arrange()

        userRepository.fetchUsersIfUnknownByIds(requestedUserIds).shouldSucceed()

        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(eq(ListUserRequest.qualifiedIds(listOf(QualifiedID(value = missingUserId.value, domain = missingUserId.domain)))))
            .wasInvoked(exactly = once)
    }

    // TODO other UserRepository tests


    private class Arrangement() {
        @Mock
        val userDAO = configure(mock(classOf<UserDAO>())) { stubsUnitByDefault = true }
        @Mock
        val metadataDAO = configure(mock(classOf<MetadataDAO>())) { stubsUnitByDefault = true }
        @Mock
        val selfApi = mock(classOf<SelfApi>())
        @Mock
        val userDetailsApi = mock(classOf<UserDetailsApi>())
        @Mock
        val assetRepository = mock(classOf<AssetRepository>())

        val userRepository: UserRepository by lazy {
            UserDataSource(userDAO, metadataDAO, selfApi, userDetailsApi, assetRepository)
        }

        init {
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKey)
                .whenInvokedWith(any())
                .then { flowOf(TestUser.JSON_QUALIFIED_ID) }
            given(userDAO).suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(TestUser.ENTITY) }
        }

        fun withSuccessfulGetUsersByQualifiedIdList(knownUserEntities: List<UserEntity>): Arrangement {
            given(userDAO)
                .suspendFunction(userDAO::getUsersByQualifiedIDList)
                .whenInvokedWith(any())
                .thenReturn(flowOf(knownUserEntities))
            return this
        }

        fun withSuccessfulGetMultipleUsersApiRequest(result: List<UserProfileDTO>): Arrangement {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value))
            return this
        }

        fun arrange() = this to userRepository
    }
}
