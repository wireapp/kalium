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

package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.network.api.base.authenticated.search.ContactDTO
import com.wire.kalium.network.api.base.authenticated.search.SearchPolicyDTO
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

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
            Arrangement.generateContactDTO(UserIdDTO("value1", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value2", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value3", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO(selfUser.id.value, selfUser.id.domain))
        )

        val expectedResult = listOf(
            Arrangement.generateContactDTO(UserIdDTO("value2", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value3", "someDomain"))
        )

        val (_, userSearchApiWrapper) = Arrangement()
            .withSelfUserId(selfUser.id)
            .withSuccessConversationExcludedFullSearch(
                conversationMembers,
                searchResultUsers
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
            Arrangement.generateContactDTO(UserIdDTO("value1", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value2", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value3", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO(selfUser.id.value, selfUser.id.domain))
        )

        val (_, userSearchApiWrapper) = Arrangement()
            .withSelfUserId(selfUser.id)
            .withSuccessConversationExcludedFullSearch(
                conversationMembers,
                searchResultUsers
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
        assertEquals(0, result.value.found)
    }

    @Test
    fun givenUserSearchIncludesSelfUser_WhenSearchingForUsersExcludingSelfUser_ThenPropagateUsersWithoutSelfUser() = runTest {
        val selfUser = Arrangement.generateSelfUser(QualifiedID("selfUserId", "someDomain"))

        val searchResultUsers = listOf(
            Arrangement.generateContactDTO(UserIdDTO("value1", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value2", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value3", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO(selfUser.id.value, selfUser.id.domain))
        )

        val expectedResult = listOf(
            Arrangement.generateContactDTO(UserIdDTO("value1", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value2", "someDomain")),
            Arrangement.generateContactDTO(UserIdDTO("value3", "someDomain"))
        )

        val (_, userSearchApiWrapper) = Arrangement()
            .withSelfUserId(selfUser.id)
            .withSuccessFullSearch(searchResultUsers)
            .arrange()

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
            Arrangement.generateContactDTO(UserIdDTO(selfUser.id.value, selfUser.id.domain))
        )

        val (_, userSearchApiWrapper) = Arrangement()
            .withSelfUserId(selfUser.id)
            .withSuccessFullSearch(searchResultUsers)
            .arrange()

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
                Arrangement.generateContactDTO(UserIdDTO(selfUser.id.value, selfUser.id.domain))
            )

            val searchResultUsers = listOf(
                Arrangement.generateContactDTO(UserIdDTO(selfUser.id.value, selfUser.id.domain))
            )

            val (_, userSearchApiWrapper) = Arrangement()
                .withSelfUserId(selfUser.id)
                .withSuccessFullSearch(searchResultUsers)
                .arrange()

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
            Arrangement.generateContactDTO(UserIdDTO(selfUser.id.value, selfUser.id.domain))
        )

        val (_, userSearchApiWrapper) = Arrangement()
            .withSelfUserId(selfUser.id)
            .withSuccessConversationExcludedFullSearch(
                conversationMembers,
                searchResultUsers
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

    private class Arrangement : MemberDAOArrangement by MemberDAOArrangementImpl() {

        lateinit var selfUserId: UserId

        @Mock
        private val userSearchApi: UserSearchApi = mock(UserSearchApi::class)

        fun withSelfUserId(selfUserId: UserId) = apply {
            this.selfUserId = selfUserId
        }

        suspend fun withSuccessConversationExcludedFullSearch(
            conversationMembers: List<MemberEntity>,
            searchApiUsers: List<ContactDTO>,
        ): Arrangement {

            withObserveConversationMembers(flowOf(conversationMembers))

            coEvery {
                userSearchApi.search(any())
            }.returns(
                    NetworkResponse.Success(
                        generateUserSearchResponse(searchApiUsers),
                        mapOf(),
                        200
                    )
                )

            return this
        }

        suspend fun withSuccessFullSearch(
            searchApiUsers: List<ContactDTO>,
        ): Arrangement {

            coEvery {
                userSearchApi.search(any())
            }.returns(
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
            memberDAO,
            selfUserId
        ) as UserSearchApiWrapper

        companion object {
            fun generateContactDTO(id: UserIdDTO): ContactDTO {
                return ContactDTO(
                    accentId = null,
                    handle = null,
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
                    userType = UserType.INTERNAL,
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
                supportedProtocols = null,
                userType = UserType.INTERNAL,
            )
        }
    }
}
