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

package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.network.api.base.authenticated.search.ContactDTO
import com.wire.kalium.network.api.base.authenticated.search.SearchPolicyDTO
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import io.mockative.Mock
import io.mockative.any
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
            MemberEntity(
                user = QualifiedIDEntity(
                    "value1",
                    "someDomain"
                ),
                role = MemberEntity.Role.Member
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
            MemberEntity(
                user = QualifiedIDEntity(
                    "value1",
                    "someDomain"
                ),
                role = MemberEntity.Role.Member
            ),
            MemberEntity(
                user = QualifiedIDEntity(
                    "value2",
                    "someDomain"
                ),
                role = MemberEntity.Role.Member
            ),
            MemberEntity(
                user = QualifiedIDEntity(
                    "value3",
                    "someDomain"
                ), role = MemberEntity.Role.Member
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
            MemberEntity(
                user = QualifiedIDEntity(
                    "value1",
                    "someDomain"
                ),
                role = MemberEntity.Role.Member
            ),
            MemberEntity(
                user = QualifiedIDEntity(
                    "value2",
                    "someDomain"
                ),
                role = MemberEntity.Role.Member
            ),
            MemberEntity(
                user = QualifiedIDEntity(
                    "value3",
                    "someDomain"
                ), role = MemberEntity.Role.Member
            ),
            MemberEntity(
                user = QualifiedIDEntity(
                    selfUser.id.value,
                    selfUser.id.domain
                ), role = MemberEntity.Role.Member
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

    private class Arrangement :
        MemberDAOArrangement by MemberDAOArrangementImpl() {

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

        fun withSuccessConversationExcludedFullSearch(
            conversationMembers: List<MemberEntity>,
            searchApiUsers: List<ContactDTO>,
            selfUser: SelfUser = SELF_USER
        ): Arrangement {

            withObserveConversationMembers(flowOf(conversationMembers))
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(any())
                .then { flowOf(JSON_QUALIFIED_ID) }

            given(userDAO)
                .suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(USER_ENTITY) }

            given(userMapper)
                .function(userMapper::fromUserEntityToSelfUser)
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
                .function(userMapper::fromUserEntityToSelfUser)
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

        fun arrange() = this to UserSearchApiWrapperImpl(
            userSearchApi,
            conversationDAO,
            memberDAO,
            userDAO,
            metadataDAO,
            userMapper
        ) as UserSearchApiWrapper

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
                    expiresAt = null,
                    supportedProtocols = null,
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
                expiresAt = null,
                supportedProtocols = null
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
                deleted = false,
                expiresAt = null,
                defederated = false,
                supportedProtocols = null
            )
        }
    }

}
