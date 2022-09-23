package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.authenticated.search.ContactDTO
import com.wire.kalium.network.api.base.authenticated.search.SearchPolicyDTO
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserSearchApiWrapperTest {

    @Test
    fun givenUserSearchIncludesContactMember_whenSearchingForUsersExcludingSelfUser_ThenResultDoesNotContainTheContactMembers() = runTest {
        val conversationMembers = listOf(
            Member(
                user = QualifiedIDEntity(
                    "value1",
                    "someDomain"
                ),
                role = Member.Role.Member
            )
        )

        val selfUser = Arrangement.generateSelfUser(QualifiedID("selfUserId", "someDomain"))

        val searchResultUsers = listOf(
            Arrangement.generateContactDTO(UserId("value1", "someDomain")),
            Arrangement.generateContactDTO(UserId("value2", "someDomain")),
            Arrangement.generateContactDTO(UserId("value3", "someDomain")),
            Arrangement.generateContactDTO(UserId(selfUser.id.value, selfUser.id.domain))
        )

        val expectedResult = listOf(
            Arrangement.generateContactDTO(UserId("value2", "someDomain")),
            Arrangement.generateContactDTO(UserId("value3", "someDomain"))
        )

        val (_, userSearchApiWrapper) = Arrangement().withSuccessConversationExcludedFullSearch(
            conversationMembers,
            searchResultUsers,
            selfUser
        ).arrange()

        val result = userSearchApiWrapper.search(
            "someQuery",
            "someDomain",
            null,
            searchUsersOptions = SearchUsersOptions(
                ConversationMemberExcludedOptions.ConversationExcluded(
                    ConversationId(
                        "someValue",
                        "someDomain"
                    )
                ),
                selfUserIncluded = false
            )
        )

        assertIs<Either.Right<UserSearchResponse>>(result)
        assertTrue { result.value.documents == expectedResult }
        assertTrue { result.value.found == expectedResult.size }
    }

    @Test
    fun givenUserSearchIncludesOnlyContactMembers_WhenSearchingForUsersExcludingSelfUser_ThenResultIsEmpty() = runTest {
        val conversationMembers = listOf(
            Member(
                user = QualifiedIDEntity(
                    "value1",
                    "someDomain"
                ),
                role = Member.Role.Member
            ),
            Member(
                user = QualifiedIDEntity(
                    "value2",
                    "someDomain"
                ),
                role = Member.Role.Member
            ),
            Member(
                user = QualifiedIDEntity(
                    "value3",
                    "someDomain"
                ), role = Member.Role.Member
            )
        )

        val selfUser = Arrangement.generateSelfUser(QualifiedID("selfUserId", "someDomain"))

        val searchResultUsers = listOf(
            Arrangement.generateContactDTO(UserId("value1", "someDomain")),
            Arrangement.generateContactDTO(UserId("value2", "someDomain")),
            Arrangement.generateContactDTO(UserId("value3", "someDomain")),
            Arrangement.generateContactDTO(UserId(selfUser.id.value, selfUser.id.domain))
        )

        val (_, userSearchApiWrapper) = Arrangement().withSuccessConversationExcludedFullSearch(
            conversationMembers,
            searchResultUsers,
            selfUser
        ).arrange()

        val result = userSearchApiWrapper.search(
            "someQuery",
            "someDomain",
            null,
            searchUsersOptions = SearchUsersOptions(
                ConversationMemberExcludedOptions.ConversationExcluded(
                    ConversationId(
                        "someValue",
                        "someDomain"
                    )
                ), selfUserIncluded = false
            )
        )

        assertIs<Either.Right<UserSearchResponse>>(result)
        assertTrue { result.value.documents.isEmpty() }
        assertTrue { result.value.found == 0 }
    }

    @Test
    fun givenUserSearchIncludesSelfUser_WhenSearchingForUsersExcludingSelfUser_ThenPropagateUsersWithoutSelfUser() = runTest {
        val selfUser = Arrangement.generateSelfUser(QualifiedID("selfUserId", "someDomain"))

        val searchResultUsers = listOf(
            Arrangement.generateContactDTO(UserId("value1", "someDomain")),
            Arrangement.generateContactDTO(UserId("value2", "someDomain")),
            Arrangement.generateContactDTO(UserId("value3", "someDomain")),
            Arrangement.generateContactDTO(UserId(selfUser.id.value, selfUser.id.domain))
        )

        val expectedResult = listOf(
            Arrangement.generateContactDTO(UserId("value1", "someDomain")),
            Arrangement.generateContactDTO(UserId("value2", "someDomain")),
            Arrangement.generateContactDTO(UserId("value3", "someDomain"))
        )

        val (_, userSearchApiWrapper) = Arrangement().withSuccessFullSearch(
            searchResultUsers,
            selfUser
        ).arrange()

        val result = userSearchApiWrapper.search(
            "someQuery",
            "someDomain",
            null,
            searchUsersOptions = SearchUsersOptions.Default
        )

        assertIs<Either.Right<UserSearchResponse>>(result)
        assertTrue { result.value.documents == expectedResult }
        assertTrue { result.value.found == expectedResult.size }
    }

    @Test
    fun givenUserSearchHasOnlySelfUser_WhenSearchingForUsersExcludingSelfUser_ThenSearchResultIsEmpty() = runTest {
        val selfUser = Arrangement.generateSelfUser(QualifiedID("selfUserId", "someDomain"))

        val searchResultUsers = listOf(
            Arrangement.generateContactDTO(UserId(selfUser.id.value, selfUser.id.domain))
        )

        val (_, userSearchApiWrapper) = Arrangement().withSuccessFullSearch(
            searchResultUsers,
            selfUser
        ).arrange()

        val result = userSearchApiWrapper.search(
            "someQuery",
            "someDomain",
            null,
            searchUsersOptions = SearchUsersOptions.Default,
        )

        assertIs<Either.Right<UserSearchResponse>>(result)
        assertTrue { result.value.documents.isEmpty() }
        assertTrue { result.value.found == 0 }
    }

    @Test
    fun givenUserSearchHasOnlySelfUser_WhenSearchingForUsersIncludingSelfUserThatIsNotInConversation_ThenSearchResultContainsSelfUser() =
        runTest {
            val selfUser = Arrangement.generateSelfUser(QualifiedID("selfUserId", "someDomain"))

            val expectedResult = listOf(
                Arrangement.generateContactDTO(UserId(selfUser.id.value, selfUser.id.domain))
            )

            val searchResultUsers = listOf(
                Arrangement.generateContactDTO(UserId(selfUser.id.value, selfUser.id.domain))
            )

            val (_, userSearchApiWrapper) = Arrangement().withSuccessFullSearch(
                searchResultUsers,
                selfUser
            ).arrange()

            val result = userSearchApiWrapper.search(
                "someQuery",
                "someDomain",
                null,
                searchUsersOptions = SearchUsersOptions.Default.copy(selfUserIncluded = true),
            )

            assertIs<Either.Right<UserSearchResponse>>(result)
            assertTrue { result.value.documents == expectedResult }
            assertTrue { result.value.found == 1 }
        }

    @Test
    fun givenUserSearchHasOnlySelfUser_WhenSearchingForUsersIncludingSelfUserThatIsPartOfConversation_ThenSearchResultIsEmpty() = runTest {
        val selfUser = Arrangement.generateSelfUser(QualifiedID("selfUserId", "someDomain"))

        val conversationMembers = listOf(
            Member(
                user = QualifiedIDEntity(
                    "value1",
                    "someDomain"
                ),
                role = Member.Role.Member
            ),
            Member(
                user = QualifiedIDEntity(
                    "value2",
                    "someDomain"
                ),
                role = Member.Role.Member
            ),
            Member(
                user = QualifiedIDEntity(
                    "value3",
                    "someDomain"
                ), role = Member.Role.Member
            ),
            Member(
                user = QualifiedIDEntity(
                    selfUser.id.value,
                    selfUser.id.domain
                ), role = Member.Role.Member
            )
        )

        val searchResultUsers = listOf(
            Arrangement.generateContactDTO(UserId(selfUser.id.value, selfUser.id.domain))
        )

        val (_, userSearchApiWrapper) = Arrangement().withSuccessConversationExcludedFullSearch(
            conversationMembers,
            searchResultUsers,
            selfUser
        ).arrange()

        val result = userSearchApiWrapper.search(
            "someQuery",
            "someDomain",
            null,
            searchUsersOptions = SearchUsersOptions(
                ConversationMemberExcludedOptions.ConversationExcluded(
                    ConversationId(
                        "someValue",
                        "someDomain"
                    )
                ), selfUserIncluded = true
            )
        )

        assertIs<Either.Right<UserSearchResponse>>(result)
        assertTrue { result.value.documents.isEmpty() }
        assertTrue { result.value.found == 0 }
    }

    private class Arrangement {

        @Mock
        private val userSearchApi: UserSearchApi = mock(classOf<UserSearchApi>())

        @Mock
        private val metadataDAO: MetadataDAO = mock(classOf<MetadataDAO>())

        @Mock
        private val userDAO: UserDAO = mock(classOf<UserDAO>())

        @Mock
        private val userMapper: UserMapper = mock(classOf<UserMapper>())

        @Mock
        private val conversationDAO: ConversationDAO = mock(classOf<ConversationDAO>())

        @Mock
        private val idMapper: IdMapper = mock(classOf<IdMapper>())

        // Propagate the mapping of the id so their content are equal, when passing it to the
        // mock functions
        init {
            given(idMapper)
                .function(idMapper::toDaoModel)
                .whenInvokedWith(anything())
                .then { PersistenceQualifiedId(it.value, it.domain) }

            given(idMapper)
                .function(idMapper::fromDaoModel)
                .whenInvokedWith(anything())
                .then { QualifiedID(it.value, it.domain) }

            given(idMapper)
                .function(idMapper::fromApiModel)
                .whenInvokedWith(anything())
                .then { QualifiedID(it.value, it.domain) }
        }

        fun withSuccessConversationExcludedFullSearch(
            conversationMembers: List<Member>,
            searchApiUsers: List<ContactDTO>,
            selfUser: SelfUser = SELF_USER
        ): Arrangement {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getAllMembers)
                .whenInvokedWith(any())
                .thenReturn(flowOf(conversationMembers))

            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(any())
                .then { flowOf(JSON_QUALIFIED_ID) }

            given(userDAO)
                .suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(USER_ENTITY) }

            given(userMapper)
                .function(userMapper::fromDaoModelToSelfUser)
                .whenInvokedWith(any())
                .then { selfUser }

            given(userSearchApi)
                .suspendFunction(userSearchApi::search)
                .whenInvokedWith(any())
                .thenReturn(
                    NetworkResponse.Success(
                        generateUserSearchResponse(searchApiUsers),
                        mapOf(),
                        200
                    )
                )

            return this
        }

        fun withSuccessFullSearch(
            searchApiUsers: List<ContactDTO>,
            selfUser: SelfUser = SELF_USER
        ): Arrangement {
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(any())
                .then { flowOf(JSON_QUALIFIED_ID) }

            given(userDAO)
                .suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(USER_ENTITY) }

            given(userMapper)
                .function(userMapper::fromDaoModelToSelfUser)
                .whenInvokedWith(any())
                .then { selfUser }

            given(userSearchApi)
                .suspendFunction(userSearchApi::search)
                .whenInvokedWith(any())
                .thenReturn(
                    NetworkResponse.Success(
                        generateUserSearchResponse(searchApiUsers),
                        mapOf(),
                        200
                    )
                )

            return this
        }

        fun arrange() = this to UserSearchApiWrapperImpl(userSearchApi, conversationDAO, userDAO, metadataDAO, userMapper)

        companion object {
            fun generateContactDTO(id: UserId): ContactDTO {
                return ContactDTO(
                    accentId = null,
                    handle = null,
                    id = null,
                    name = "",
                    qualifiedID = id,
                    team = null
                )
            }

            fun generateUserSearchResponse(contactDTOs: List<ContactDTO> = listOf()): UserSearchResponse {
                return UserSearchResponse(
                    documents = contactDTOs,
                    found = contactDTOs.size,
                    returned = contactDTOs.size,
                    searchPolicy = SearchPolicyDTO.FULL_SEARCH,
                    took = 100
                )
            }

            fun generateSelfUser(id: QualifiedID): SelfUser {
                return SelfUser(
                    id = id,
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    teamId = null,
                    connectionStatus = ConnectionState.NOT_CONNECTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                )
            }

            val SELF_USER = SelfUser(
                id = QualifiedID("someValue", "someId"),
                name = null,
                handle = null,
                email = null,
                phone = null,
                accentId = 0,
                teamId = null,
                connectionStatus = ConnectionState.NOT_CONNECTED,
                previewPicture = null,
                completePicture = null,
                availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            )

            const val JSON_QUALIFIED_ID = """{"value":"test" , "domain":"test" }"""

            val USER_ENTITY = UserEntity(
                id = QualifiedIDEntity("value", "domain"),
                name = null,
                handle = null,
                email = null,
                phone = null,
                accentId = 0,
                team = null,
                connectionStatus = ConnectionEntity.State.NOT_CONNECTED,
                previewAssetId = null,
                completeAssetId = null,
                availabilityStatus = UserAvailabilityStatusEntity.AVAILABLE,
                userType = UserTypeEntity.EXTERNAL,
                botService = null,
                deleted = false
            )
        }
    }

}
