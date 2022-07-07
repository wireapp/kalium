package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchRequest
import com.wire.kalium.network.api.contact.search.UserSearchResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import kotlinx.coroutines.flow.firstOrNull

internal interface UserSearchApiWrapper {
    /*
     * Searches for users that match given the [searchQuery] using the API.
     * Depending on the [searchUsersOptions], the members of a conversation can be excluded.
     */
    suspend fun search(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<NetworkFailure, UserSearchResponse>
}

internal class UserSearchApiWrapperImpl(
    private val userSearchApi: UserSearchApi,
    private val conversationDAO: ConversationDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : UserSearchApiWrapper {

    override suspend fun search(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<NetworkFailure, UserSearchResponse> {

        val searchResponse = wrapApiRequest {
            userSearchApi.search(
                UserSearchRequest(
                    searchQuery = searchQuery,
                    domain = domain,
                    maxResultSize = maxResultSize
                )
            )
        }

        return if (searchUsersOptions.conversationExcluded is ConversationMemberExcludedOptions.ConversationExcluded) {
            val conversationMembersId = conversationDAO.getAllMembers(
                qualifiedID = idMapper.toDaoModel(qualifiedID = searchUsersOptions.conversationExcluded.conversationId)
            ).firstOrNull()?.map { idMapper.fromDaoModel(it.user) }

            searchResponse.map {
                val filteredContactResponse = it.documents.filter { contactDTO ->
                    !(conversationMembersId?.contains(idMapper.fromApiModel(contactDTO.qualifiedID)) ?: false)
                }

                it.copy(
                    documents = filteredContactResponse,
                    found = filteredContactResponse.size,
                    returned = filteredContactResponse.size
                )
            }
        } else {
            searchResponse
        }
    }
}
