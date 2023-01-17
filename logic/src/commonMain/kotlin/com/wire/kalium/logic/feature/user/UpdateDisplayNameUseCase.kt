package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Updates the display name of the current user.
 */
fun interface UpdateDisplayNameUseCase {
    /**
     * @param displayName The new display name.
     * @return The result of the operation [DisplayNameUpdateResult.Success] or a mapped [CoreFailure].
     */
    suspend operator fun invoke(displayName: String): DisplayNameUpdateResult
}

internal class UpdateDisplayNameUseCaseImpl(
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : UpdateDisplayNameUseCase {
    override suspend fun invoke(displayName: String): DisplayNameUpdateResult = withContext(dispatchers.default) {
        userRepository.updateSelfDisplayName(displayName)
            .fold(
                { DisplayNameUpdateResult.Failure(it) },
                { DisplayNameUpdateResult.Success }
            )
    }
}

sealed class DisplayNameUpdateResult {
    object Success : DisplayNameUpdateResult()
    data class Failure(val coreFailure: CoreFailure) : DisplayNameUpdateResult()
}
