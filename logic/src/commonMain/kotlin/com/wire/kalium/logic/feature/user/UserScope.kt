package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.asset.GetPublicAssetUseCase
import com.wire.kalium.logic.feature.asset.GetPublicAssetUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.GetAllKnownUsersUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllKnownUsersUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCase
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.SearchKnownUsersUseCase
import com.wire.kalium.logic.feature.publicuser.SearchKnownUsersUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.SearchUserDirectoryUseCase
import com.wire.kalium.logic.feature.publicuser.SearchUserDirectoryUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager

class UserScope(
    private val userRepository: UserRepository,
    private val searchUserRepository: SearchUserRepository,
    private val syncManager: SyncManager,
    private val assetRepository: AssetRepository
) {
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val getSelfUser: GetSelfUserUseCase get() = GetSelfUserUseCase(userRepository, syncManager)
    val syncSelfUser: SyncSelfUserUseCase get() = SyncSelfUserUseCase(userRepository)
    val syncContacts: SyncContactsUseCase get() = SyncContactsUseCaseImpl(userRepository)
    val uploadUserAvatar: UploadUserAvatarUseCase get() = UploadUserAvatarUseCaseImpl(userRepository, assetRepository)
    val searchKnownUsers: SearchKnownUsersUseCase get() = SearchKnownUsersUseCaseImpl(searchUserRepository)
    val getPublicAsset: GetPublicAssetUseCase get() = GetPublicAssetUseCaseImpl(assetRepository)
    val searchUserDirectory: SearchUserDirectoryUseCase get() = SearchUserDirectoryUseCaseImpl(searchUserRepository)
    val setUserHandle: SetUserHandleUseCase get() = SetUserHandleUseCase(userRepository, validateUserHandleUseCase)
    val getAllKnownUsers: GetAllKnownUsersUseCase get() = GetAllKnownUsersUseCaseImpl(userRepository)
    val getKnownUser: GetKnownUserUseCase get() = GetKnownUserUseCaseImpl(userRepository)
}
