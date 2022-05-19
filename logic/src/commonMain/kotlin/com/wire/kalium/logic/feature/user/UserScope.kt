package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCase
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCaseImpl
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
    val getPublicAsset: GetAvatarAssetUseCase get() = GetAvatarAssetUseCaseImpl(assetRepository)
    val searchUserDirectory: SearchUserDirectoryUseCase get() = SearchUserDirectoryUseCaseImpl(searchUserRepository)
    val setUserHandle: SetUserHandleUseCase get() = SetUserHandleUseCase(userRepository, validateUserHandleUseCase, syncManager)
    val getAllKnownUsers: GetAllContactsUseCase get() = GetAllContactsUseCaseImpl(userRepository)
    val getKnownUser: GetKnownUserUseCase get() = GetKnownUserUseCaseImpl(userRepository)
    val getUserInfo: GetUserInfoUseCase get() = GetUserInfoUseCaseImpl(userRepository)
}
