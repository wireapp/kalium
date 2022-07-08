package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.contact.search.ContactDTO
import com.wire.kalium.network.api.contact.search.SearchPolicyDTO
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.QualifiedIDEntity
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
    fun givenUserSearchIncludesContactMember_whenSearchingForUsers_ThenResultDoesNotContainTheContactMembers() = runTest {
        val conversationMembers = listOf(
            Member(
                user = QualifiedIDEntity(
                    "value1",
                    "someDomain"
                ),
                role = Member.Role.Member
            )
        )

        val searchResultUsers = listOf(
            Arrangement.generateContactDTO(UserId("value1", "someDomain")),
            Arrangement.generateContactDTO(UserId("value2", "someDomain")),
            Arrangement.generateContactDTO(UserId("value3", "someDomain"))
        )

        val expectedResult = listOf(
            Arrangement.generateContactDTO(UserId("value2", "someDomain")),
            Arrangement.generateContactDTO(UserId("value3", "someDomain"))
        )

        val (_, userSearchApiWrapper) = Arrangement().withSuccessFullSearch(
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
                )
            )
        )

        assertIs<Either.Right<UserSearchResponse>>(result)
        assertTrue { result.value.documents == expectedResult }
        assertTrue { result.value.found == expectedResult.size }
    }

    @Test
    fun givenUserSearchIncludesOnlyContactMembers_WhenSearchingForUsers_ThenResultIsEmpty() = runTest {
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

        val searchResultUsers = listOf(
            Arrangement.generateContactDTO(UserId("value1", "someDomain")),
            Arrangement.generateContactDTO(UserId("value2", "someDomain")),
            Arrangement.generateContactDTO(UserId("value3", "someDomain"))
        )

        val (_, userSearchApiWrapper) = Arrangement().withSuccessFullSearch(
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
                )
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

        fun withSuccessFullSearch(conversationMembers: List<Member>, searchApiUsers: List<ContactDTO>): Arrangement {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getAllMembers)
                .whenInvokedWith(any())
                .thenReturn(flowOf(conversationMembers))

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

        fun arrange() = this to UserSearchApiWrapperImpl(userSearchApi, conversationDAO)

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
        }
    }

}
