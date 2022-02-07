package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager

class UserScope(
    userRepository: UserRepository,
    syncManager: SyncManager
) {
    val getSelfUser: GetSelfUserUseCase get() = GetSelfUserUseCase(userRepository, syncManager)
    val syncSelfUser: SyncSelfUserUseCase = SyncSelfUserUseCase(userRepository)
}
