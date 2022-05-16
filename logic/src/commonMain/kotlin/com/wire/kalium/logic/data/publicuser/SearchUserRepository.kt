package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SearchUserRepository {
    suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String): UserSearchResult
    suspend fun searchKnownUsersByHandle(handle: String): UserSearchResult
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

    override suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String) =
        UserSearchResult(
            result = userDAO.getUserByNameOrHandleOrEmailAndConnectionState(
                searchQuery = searchQuery,
                connectionState = UserEntity.ConnectionState.ACCEPTED
            ).map(publicUserMapper::fromDaoModelToPublicUser)
        )

    override suspend fun searchKnownUsersByHandle(handle: String) =
        UserSearchResult(
            result = userDAO.getUserByHandleAndConnectionState(
                handle = handle,
                connectionState = UserEntity.ConnectionState.ACCEPTED
            ).map(publicUserMapper::fromDaoModelToPublicUser)
        )

    override suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<CoreFailure, UserSearchResult> = wrapApiRequest {
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
