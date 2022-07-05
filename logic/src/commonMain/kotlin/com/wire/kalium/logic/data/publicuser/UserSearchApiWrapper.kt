package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.contact.search.ContactDTO
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchRequest
import com.wire.kalium.network.api.contact.search.UserSearchResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import kotlinx.coroutines.flow.firstOrNull

class UserSearchApiWrapper(
    private val userSearchApi: UserSearchApi,
    private val conversationDAO: ConversationDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) {



    suspend fun search(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<NetworkFailure, UserSearchResponse> {
        val excludedConversationOption = searchUsersOptions.conversationExcluded

        return if (excludedConversationOption is ConversationMemberExcludedOptions.ConversationExcluded) {
            val conversationMembers = conversationDAO.getAllMembers(
                idMapper.toDaoModel(excludedConversationOption.conversationId)
            ).firstOrNull()

            wrapApiRequest {
                userSearchApi.search(
                    UserSearchRequest(
                        searchQuery = searchQuery,
                        domain = domain,
                        maxResultSize = maxResultSize
                    )
                )
            }.map { contactResponse ->
                contactResponse.copy(documents = contactResponse.documents.filter { contactDTO ->
                    conversationMembers
                        ?.map { idMapper.fromDaoModel(it.user) }
                        ?.contains(idMapper.fromApiModel(contactDTO.qualifiedID))
                        ?: false
                })
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
