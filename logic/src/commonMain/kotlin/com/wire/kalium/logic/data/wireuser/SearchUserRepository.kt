package com.wire.kalium.logic.data.wireuser

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.wireuser.search.WireUserSearchResult
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.contact.search.WireUserSearchApi
import com.wire.kalium.network.api.contact.search.WireUserSearchRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SearchUserRepository {
    suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String): Flow<WireUserSearchResult>
    suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<CoreFailure, WireUserSearchResult>
}

class SearchUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val wireUserSearchApi: WireUserSearchApi,
    private val userDetailsApi: UserDetailsApi,
    private val wireUserMapper: WireUserMapper = MapperProvider.wireUserMapper()
) : SearchUserRepository {

    override suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String) =
        userDAO.getUserByNameOrHandleOrEmail(searchQuery)
            .map {
                WireUserSearchResult(it.map { userEntity -> wireUserMapper.fromDaoModelToWireUser(userEntity) })
            }

    override suspend fun searchWireContact(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, WireUserSearchResult> {
        return suspending {
            wrapApiRequest {
                wireUserSearchApi.search(
                    WireUserSearchRequest(
                        searchQuery = searchQuery,
                        domain = domain,
                        maxResultSize = maxResultSize
                    )
                )
            }.flatMap { contactResultValue ->
                wrapApiRequest {
                    userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(contactResultValue.documents.map { it.qualifiedID }))
                }.map { userDetailsResponses ->
                    WireUserSearchResult(wireUserMapper.fromUserDetailResponses(userDetailsResponses))
                }
            }
        }
    }
}
