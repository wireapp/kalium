package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.persistence.dao.UserEntity
import kotlinx.coroutines.flow.Flow


interface SearchKnownUsersUseCase {
    suspend operator fun invoke(searchQuery: String): Flow<List<UserEntity>>
}

internal class SearchKnownUsersUseCaseImpl(
    private val userRepository: UserRepository
) : SearchKnownUsersUseCase {

    // TODO:this use case is going to be refactor once we return Either from DAO's
    override suspend operator fun invoke(searchQuery: String): Flow<List<UserEntity>> =
        userRepository.searchKnownUsersByNameOrHandleOrEmail(searchQuery)

}
