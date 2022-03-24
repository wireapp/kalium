package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.wireuser.WireUserRepository
import com.wire.kalium.logic.feature.asset.GetPublicAssetUseCase
import com.wire.kalium.logic.feature.asset.GetPublicAssetUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.wireuser.search.SearchKnownUsersUseCase
import com.wire.kalium.logic.feature.wireuser.search.SearchKnownUsersUseCaseImpl
import com.wire.kalium.logic.feature.wireuser.search.SearchPublicWireUserUseCase
import com.wire.kalium.logic.feature.wireuser.search.SearchPublicWireWireUserUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager

class UserScope(
    private val userRepository: UserRepository,
    private val wireUserRepository: WireUserRepository,
    private val syncManager: SyncManager,
    private val assetRepository: AssetRepository
) {
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val getSelfUser: GetSelfUserUseCase get() = GetSelfUserUseCase(userRepository, syncManager)
    val syncSelfUser: SyncSelfUserUseCase get() = SyncSelfUserUseCase(userRepository)
    val syncContacts: SyncContactsUseCase get() = SyncContactsUseCaseImpl(userRepository)
    val uploadUserAvatar: UploadUserAvatarUseCase get() = UploadUserAvatarUseCaseImpl(userRepository, assetRepository)
    val searchKnownUsers: SearchKnownUsersUseCase get() = SearchKnownUsersUseCaseImpl(wireUserRepository)
    val getPublicAsset: GetPublicAssetUseCase get() = GetPublicAssetUseCaseImpl(assetRepository)
    val searchPublicWireUser: SearchPublicWireUserUseCase get() = SearchPublicWireWireUserUseCaseImpl(wireUserRepository)
    val setUserHandle: SetUserHandleUseCase get() = SetUserHandleUseCase(userRepository, validateUserHandleUseCase)
}
