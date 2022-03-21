package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.publicuser.model.PublicUserSearchResult
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.contact.search.ContactSearchApi
import com.wire.kalium.network.api.contact.search.ContactSearchRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds


interface PublicUserRepository {
    suspend fun searchPublicContact(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<CoreFailure, PublicUserSearchResult>
}

class PublicUserRepositoryImpl(
    private val contactSearchApi: ContactSearchApi,
    private val userApi: UserDetailsApi,
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper()
) : PublicUserRepository {

    override suspend fun searchPublicContact(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<NetworkFailure, PublicUserSearchResult> {
        return suspending {
            wrapApiRequest {
                contactSearchApi.search(
                    ContactSearchRequest(
                        searchQuery = searchQuery,
                        domain = domain,
                        maxResultSize = maxResultSize
                    )
                )
            }.flatMap { contactResultValue ->
                wrapApiRequest {
                    userApi.getMultipleUsers(ListUserRequest.qualifiedIds(contactResultValue.documents.map { it.qualifiedID }))
                }.map { userDetailsResponses ->
                    PublicUserSearchResult(
                        totalFound = contactResultValue.found,
                        publicUsers = publicUserMapper.fromUserDetailResponses(userDetailsResponses)
                    )
                }
            }
        }
    }
}
