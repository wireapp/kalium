package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.data.id.FEDERATION_REGEX
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface SearchKnownUsersUseCase {
    suspend operator fun invoke(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions = SearchUsersOptions.Default
    ): Flow<SearchUsersResult>
}

internal class SearchKnownUsersUseCaseImpl(
    private val searchUserRepository: SearchUserRepository,
    private val userRepository: UserRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SearchKnownUsersUseCase {

    // TODO:handle failure
    override suspend fun invoke(
        searchQuery: String,
        searchUsersOptions: SearchUsersOptions
    ): Flow<SearchUsersResult> {
        return if (isUserLookingForHandle(searchQuery)) {
            searchUserRepository.searchKnownUsersByHandle(
                handle = searchQuery,
                searchUsersOptions = searchUsersOptions
            )
        } else {
            searchUserRepository.searchKnownUsersByNameOrHandleOrEmail(
                searchQuery = if (searchQuery.matches(FEDERATION_REGEX))
                    searchQuery.run {
                        qualifiedIdMapper.fromStringToQualifiedID(this)
                    }.value
                else searchQuery,
                searchUsersOptions = searchUsersOptions
            )
        }
            .map { SearchUsersResult.Success(excludeSelfUserAndDeletedUsers(it)) }
            .flowOn(dispatcher.io)
    }

    private fun isUserLookingForHandle(searchQuery: String) = searchQuery.startsWith('@')

    // TODO: we should think about the way to exclude the self user on TABLE level
    private suspend fun excludeSelfUserAndDeletedUsers(searchResult: UserSearchResult): UserSearchResult {
        val selfUser = userRepository.getSelfUser()

        return searchResult.copy(result = searchResult.result.filter { it.id != selfUser?.id && !it.deleted })
    }

}
