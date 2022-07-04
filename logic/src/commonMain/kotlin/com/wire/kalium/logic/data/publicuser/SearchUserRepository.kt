package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserMapper
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
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

interface SearchUserRepository {
    suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String): UserSearchResult
    suspend fun searchKnownUsersByHandle(handle: String): UserSearchResult
    suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<NetworkFailure, UserSearchResult>
}

@Suppress("LongParameterList")
class SearchUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val userSearchApi: UserSearchApi,
    private val userDetailsApi: UserDetailsApi,
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper()
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


    //TODO: We can pass the selfUser.teamId as a parameter to this function,
    // and the UseCase can figure it out by using the UserRepository(?).
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
                        selfUserTeamId = getSelfUser().teamId,
                        otherUserTeamId = userProfileDTO.teamId
                    )
                )
            })
        }
    }

    //TODO: code duplication here for getting self user, the same is done inside
    // UserRepository, what would be best ?
    // creating SelfUserDao managing the UserEntity corresponding to SelfUser ?
    private suspend fun getSelfUser(): SelfUser {
        return metadataDAO.valueByKey(UserDataSource.SELF_USER_ID_KEY)
            .filterNotNull()
            .flatMapMerge { encodedValue ->
                val selfUserID: QualifiedIDEntity = Json.decodeFromString(encodedValue)

                userDAO.getUserByQualifiedID(selfUserID)
                    .filterNotNull()
                    .map(userMapper::fromDaoModelToSelfUser)
            }.firstOrNull() ?: throw  IllegalStateException()
    }

}
