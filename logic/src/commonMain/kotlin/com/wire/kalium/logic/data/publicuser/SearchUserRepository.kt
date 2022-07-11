package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
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
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal interface SearchUserRepository {

    suspend fun searchKnownUsersByNameOrHandleOrEmail(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): UserSearchResult

    suspend fun searchKnownUsersByHandle(
        handle: String,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): UserSearchResult

    suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int? = null,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Either<NetworkFailure, UserSearchResult>

}

data class SearchUsersOptions(
    val conversationExcluded: ConversationMemberExcludedOptions,
) {
    companion object {
        val Default = SearchUsersOptions(
            conversationExcluded = ConversationMemberExcludedOptions.None,
        )
    }
}

sealed class ConversationMemberExcludedOptions {
    object None : ConversationMemberExcludedOptions()
    data class ConversationExcluded(val conversationId: QualifiedID) : ConversationMemberExcludedOptions()
}

@Suppress("LongParameterList")
internal class SearchUserRepositoryImpl(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val userDetailsApi: UserDetailsApi,
    private val userSearchAPiWrapper: UserSearchApiWrapper,
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SearchUserRepository {

    override suspend fun searchKnownUsersByNameOrHandleOrEmail(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions
    ): UserSearchResult =
        handeSearchUsersOptions(
            searchUsersOptions,
            excluded = { conversationId ->
                userDAO.getUsersNotInConversationByNameOrHandleOrEmail(
                    conversationId = idMapper.toDaoModel(conversationId),
                    searchQuery = searchQuery
                )
            },
            default = {
                userDAO.getUserByNameOrHandleOrEmailAndConnectionState(
                    searchQuery = searchQuery,
                    connectionState = ConnectionEntity.State.ACCEPTED
                )
            }
        )

    override suspend fun searchKnownUsersByHandle(
        handle: String,
        searchUsersOptions: SearchUsersOptions
    ): UserSearchResult =
        handeSearchUsersOptions(
            searchUsersOptions,
            excluded = { conversationId ->
                userDAO.getUsersNotInConversationByHandle(
                    conversationId = idMapper.toDaoModel(conversationId),
                    handle = handle
                )
            },
            default = {
                userDAO.getUserByHandleAndConnectionState(
                    handle = handle,
                    connectionState = ConnectionEntity.State.ACCEPTED
                )
            }
        )

    override suspend fun searchUserDirectory(
        searchQuery: String,
        domain: String,
        maxResultSize: Int?,
        searchUsersOptions: SearchUsersOptions
    ): Either<NetworkFailure, UserSearchResult> =
        userSearchAPiWrapper.search(
            searchQuery,
            domain,
            maxResultSize,
            searchUsersOptions
        ).flatMap {
            wrapApiRequest {
                userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(it.documents.map { it.qualifiedID }))
            }.map { userDetailsResponses ->
                val selfUser = getSelfUser()
                UserSearchResult(userDetailsResponses.map { userProfileDTO ->
                    publicUserMapper.fromUserDetailResponseWithUsertype(
                        userDetailResponse = userProfileDTO,
                        userType = userTypeMapper.fromOtherUserTeamAndDomain(
                            otherUserDomain = userProfileDTO.id.domain,
                            selfUserTeamId = getSelfUser().teamId?.value,
                            otherUserTeamId = userProfileDTO.teamId,
                            selfUserDomain = selfUser.id.domain
                        )
                    )
                })
            }
        }

    // TODO: code duplication here for getting self user, the same is done inside
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
            }.firstOrNull() ?: throw IllegalStateException()
    }

    private suspend fun handeSearchUsersOptions(
        localSearchUserOptions: SearchUsersOptions,
        excluded: suspend (conversationId: ConversationId) -> List<UserEntity>,
        default: suspend () -> List<UserEntity>
    ): UserSearchResult {
        val result = when (val searchOptions = localSearchUserOptions.conversationExcluded) {
            ConversationMemberExcludedOptions.None -> default()
            is ConversationMemberExcludedOptions.ConversationExcluded -> excluded(searchOptions.conversationId)
        }

        return UserSearchResult(result.map(publicUserMapper::fromDaoModelToPublicUser))
    }

}
