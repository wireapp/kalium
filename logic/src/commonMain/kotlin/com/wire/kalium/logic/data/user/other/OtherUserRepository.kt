package com.wire.kalium.logic.data.user.other

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.mapper.UserEntityMapper
import com.wire.kalium.logic.data.user.other.mapper.OtherUserMapper
import com.wire.kalium.logic.data.user.other.model.OtherUser
import com.wire.kalium.logic.data.user.other.model.OtherUserSearchResult
import com.wire.kalium.logic.data.user.self.SelfUserRepositoryImpl
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchRequest
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


interface OtherUserRepository {
    suspend fun getAllKnownUsers(): List<OtherUser>
    suspend fun getKnownUserById(userId: UserId): Flow<OtherUser?>
    suspend fun fetchKnownUsers(): Either<CoreFailure, Unit>
    suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit>
    suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String): OtherUserSearchResult
    suspend fun searchKnownUsersByHandle(handle: String): OtherUserSearchResult
    suspend fun getRemoteUser(userId: UserId): Either<CoreFailure, OtherUser>
    suspend fun searchRemoteUsers(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null
    ): Either<NetworkFailure, OtherUserSearchResult>
}

class OtherUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val userSearchApi: UserSearchApi,
    private val userDetailsApi: UserDetailsApi,
    private val userEntityMapper: UserEntityMapper = MapperProvider.userMapper(),
    private val otherUserMapper: OtherUserMapper = MapperProvider.publicUserMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : OtherUserRepository {

    override suspend fun getAllKnownUsers(): List<OtherUser> {
        val selfUserId = getSelfUserIdEntity()

        return userDAO.getAllUsersByConnectionStatus(connectionState = ConnectionEntity.State.ACCEPTED)
            .filter { it.id != selfUserId }
            .map { userEntity -> otherUserMapper.fromDaoModel(userEntity) }
    }

    private suspend fun getSelfUserIdEntity(): QualifiedIDEntity {
        val encodedValue = metadataDAO.valueByKey(SelfUserRepositoryImpl.SELF_USER_ID_KEY).firstOrNull()

        return encodedValue?.let { Json.decodeFromString<QualifiedIDEntity>(it) }
            ?: run { throw IllegalStateException() }
    }

    override suspend fun getKnownUserById(userId: UserId): Flow<OtherUser?> =
        userDAO.getUserByQualifiedID(qualifiedID = idMapper.toDaoModel(userId))
            .map { userEntity -> userEntity?.let { otherUserMapper.fromDaoModel(userEntity) } }

    override suspend fun fetchKnownUsers(): Either<CoreFailure, Unit> {
        val ids = userDAO.getAllUsers().first().map { userEntry ->
            idMapper.fromDaoModel(userEntry.id)
        }

        return fetchUsersByIds(ids.toSet())
    }

    override suspend fun fetchUsersByIds(ids: Set<UserId>): Either<CoreFailure, Unit> =
        wrapApiRequest {
            userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(ids.map(idMapper::toApiModel)))
        }.flatMap {
            wrapStorageRequest {
                userDAO.upsertUsers(it.map(userEntityMapper::fromUserProfileDTO))
            }
        }

    override suspend fun getRemoteUser(userId: UserId): Either<CoreFailure, OtherUser> =
        wrapApiRequest { userDetailsApi.getUserInfo(idMapper.toApiModel(userId)) }.map { userProfile ->
            otherUserMapper.fromUserDetailResponse(userProfile)
        }

    override suspend fun searchKnownUsersByNameOrHandleOrEmail(searchQuery: String) =
        OtherUserSearchResult(
            result = userDAO.getUserByNameOrHandleOrEmailAndConnectionState(
                searchQuery = searchQuery,
                connectionState = ConnectionEntity.State.ACCEPTED
            ).map(otherUserMapper::fromDaoModel)
        )

    override suspend fun searchKnownUsersByHandle(handle: String) =
        OtherUserSearchResult(
            result = userDAO.getUserByHandleAndConnectionState(
                handle = handle,
                connectionState = ConnectionEntity.State.ACCEPTED
            ).map(otherUserMapper::fromDaoModel)
        )

    override suspend fun searchRemoteUsers(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?
    ): Either<NetworkFailure, OtherUserSearchResult> = wrapApiRequest {
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
            OtherUserSearchResult(otherUserMapper.fromUserDetailResponses(userDetailsResponses))
        }
    }

}
