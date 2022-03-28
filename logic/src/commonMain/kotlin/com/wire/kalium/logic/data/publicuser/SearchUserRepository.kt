package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SearchUserRepository {
    suspend fun searchKnownUsers(searchQuery: String): Flow<UserSearchResult>
    suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<CoreFailure, UserSearchResult>
}

class SearchUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val userSearchApi: UserSearchApi,
    private val userDetailsApi: UserDetailsApi,
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper()
) : SearchUserRepository {

    override suspend fun searchKnownUsers(searchQuery: String) =
        userDAO.getUserByNameOrHandleOrEmail(searchQuery)
            .map {
                UserSearchResult(it.map { userEntity -> publicUserMapper.fromDaoModelToPublicUser(userEntity) })
            }

    override suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, UserSearchResult> {
        return suspending {
            wrapApiRequest {
                userSearchApi.search(
                    UserSearchRequest(
                        searchQuery = searchQuery,
                        domain = domain,
                        maxResultSize = maxResultSize
                    )
                )
            }.flatMap { contactResultValue ->
                wrapApiRequest {
                    userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(contactResultValue.documents.map { it.qualifiedID }))
                }.map { userDetailsResponses ->
                    UserSearchResult(publicUserMapper.fromUserDetailResponses(userDetailsResponses))
                }
            }
        }
    }
}
