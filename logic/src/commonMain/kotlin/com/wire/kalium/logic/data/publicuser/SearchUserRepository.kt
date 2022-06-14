package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
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
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserDAO

interface SearchUserRepository {
    suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String): UserSearchResult
    suspend fun searchKnownUsersByHandle(handle: String): UserSearchResult
    suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<NetworkFailure, UserSearchResult>
}

class SearchUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val userSearchApi: UserSearchApi,
    private val userDetailsApi: UserDetailsApi,
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(userDAO, metadataDAO),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(userDAO,metadataDAO)
) : SearchUserRepository {

    override suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String) =
        UserSearchResult(
            result = userDAO.getUserByNameOrHandleOrEmailAndConnectionState(
                searchQuery = searchQuery,
                connectionState = ConnectionEntity.State.ACCEPTED
            ).map(publicUserMapper::fromDaoModelToPublicUser)
        )

    override suspend fun searchKnownUsersByHandle(handle: String) =
        UserSearchResult(
            result = userDAO.getUserByHandleAndConnectionState(
                handle = handle,
                connectionState = ConnectionEntity.State.ACCEPTED
            ).map(publicUserMapper::fromDaoModelToPublicUser)
        )

    override suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<NetworkFailure, UserSearchResult> = wrapApiRequest {
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
            UserSearchResult(userDetailsResponses.map { userProfileDTO ->
                publicUserMapper.fromUserDetailResponseWithUsertype(
                    userDetailResponse = userProfileDTO,
                    userType = userTypeMapper.fromOtherUserTeamAndDomain(
                        otherUserDomain = userProfileDTO.id.domain,
                        otherUserTeamID = userProfileDTO.teamId
                    )
                )
            })
        }
    }

}
