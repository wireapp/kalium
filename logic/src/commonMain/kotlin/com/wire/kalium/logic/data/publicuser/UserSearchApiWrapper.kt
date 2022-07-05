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

interface UserSearchApiWrapper {
    /*
    Back-end has not support to return user that are not part of the conversation,
    therefore we need to filter those users ourself.
    */
    suspend fun search(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<NetworkFailure, UserSearchResponse>
}

class UserSearchApiWrapperImpl(
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
        val excludedConversationOption = searchUsersOptions.conversationExcluded

        return if (excludedConversationOption is ConversationMemberExcludedOptions.ConversationExcluded) {
            val conversationMembersId = conversationDAO.getAllMembers(
                idMapper.toDaoModel(excludedConversationOption.conversationId)
            ).firstOrNull()?.map { idMapper.fromDaoModel(it.user) }

            wrapApiRequest {
                userSearchApi.search(
                    UserSearchRequest(
                        searchQuery = searchQuery,
                        domain = domain,
                        maxResultSize = maxResultSize
                    )
                )
            }.map { contactResponse ->
                val filteredContactResponse = contactResponse.documents.filter { contactDTO ->
                    !(conversationMembersId?.contains(idMapper.fromApiModel(contactDTO.qualifiedID)) ?: false)
                }

                contactResponse.copy(
                    documents = filteredContactResponse,
                    found = filteredContactResponse.size,
                    returned = filteredContactResponse.size
                )
            }
        } else {
            wrapApiRequest {
                userSearchApi.search(
                    UserSearchRequest(
                        searchQuery = searchQuery,
                        domain = domain,
                        maxResultSize = maxResultSize
                    )
                )
            }
        }
    }
}
