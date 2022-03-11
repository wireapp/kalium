package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.publicuser.model.PublicUserSearchResult
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.contact.search.ContactSearchApi
import com.wire.kalium.network.api.contact.search.ContactSearchRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess


interface PublicUserRepository {
    suspend fun searchPublicContact(
        searchQuery: String,
        domain: String? = null,
        resultSize: Int? = null
    ): Either<CoreFailure, PublicUserSearchResult>
}

class PublicUserRepositoryImpl(
    private val contactSearchApi: ContactSearchApi,
    private val userApi: UserDetailsApi,
    private val publicUserMapper: PublicUserMapper,
) : PublicUserRepository {

    override suspend fun searchPublicContact(
        searchQuery: String,
        domain: String?,
        resultSize: Int?
    ): Either<CoreFailure, PublicUserSearchResult> {
        return wrapApiRequest {
            contactSearchApi.search(
                ContactSearchRequest(
                    searchQuery = searchQuery,
                    domain = domain,
                    resultSize = resultSize
                )
            ).flatMap { contactResponse ->
                val contactResultValue = contactResponse.value

                userApi.getMultipleUsers(ListUserRequest.qualifiedIds(contactResultValue.documents.map { it.qualifiedID }))
                    .mapSuccess { userDetailsResponses ->
                        PublicUserSearchResult(
                            totalFound = contactResultValue.found,
                            publicUsers = userDetailsResponses.map { userDetailResponse ->
                                publicUserMapper.fromUserDetailResponse(
                                    userDetailResponse
                                )
                            }
                        )
                    }
            }
        }.mapLeft { CoreFailure.Unknown(IllegalStateException()) }
    }

}
