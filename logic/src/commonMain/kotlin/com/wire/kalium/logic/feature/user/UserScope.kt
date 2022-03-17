package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.asset.GetPublicAssetUseCase
import com.wire.kalium.logic.feature.asset.GetPublicAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.UploadPrivateAssetUseCase
import com.wire.kalium.logic.feature.asset.UploadPrivateAssetUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager

class UserScope(
    private val userRepository: UserRepository,
    private val syncManager: SyncManager,
    private val assetRepository: AssetRepository
) {
    val getSelfUser: GetSelfUserUseCase get() = GetSelfUserUseCase(userRepository, syncManager)
    val syncSelfUser: SyncSelfUserUseCase get() = SyncSelfUserUseCase(userRepository)
    val syncContacts: SyncContactsUseCase get() = SyncContactsUseCaseImpl(userRepository)
    val uploadUserAvatar: UploadUserAvatarUseCase get() = UploadUserAvatarUseCaseImpl(userRepository, assetRepository)
    val searchKnownUsers: SearchKnownUsersUseCase get() = SearchKnownUsersUseCaseImpl(userRepository)
    val getPublicAsset: GetPublicAssetUseCase get() = GetPublicAssetUseCaseImpl(assetRepository)
    val uploadPrivateAsset: UploadPrivateAssetUseCase get() = UploadPrivateAssetUseCaseImpl(userRepository, assetRepository)
}
