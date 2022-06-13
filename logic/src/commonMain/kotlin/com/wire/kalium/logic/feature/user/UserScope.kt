package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.other.OtherUserRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.self.SelfUserRepository
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
    private val otherUserRepository: OtherUserRepository,
    private val selfUserRepository: SelfUserRepository,
    private val syncManager: SyncManager,
    private val assetRepository: AssetRepository,
    private val teamRepository: TeamRepository
) {
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val getSelfUser: GetSelfUserUseCase get() = GetSelfUserUseCase(selfUserRepository, syncManager)
    val syncSelfUser: SyncSelfUserUseCase get() = SyncSelfUserUseCase(selfUserRepository)
    val syncContacts: SyncContactsUseCase get() = SyncContactsUseCaseImpl(otherUserRepository)
    val uploadUserAvatar: UploadUserAvatarUseCase get() = UploadUserAvatarUseCaseImpl(selfUserRepository, assetRepository)
    val searchKnownUsers: SearchKnownUsersUseCase get() = SearchKnownUsersUseCaseImpl(otherUserRepository)
    val getPublicAsset: GetAvatarAssetUseCase get() = GetAvatarAssetUseCaseImpl(assetRepository)
    val searchUserDirectory: SearchUserDirectoryUseCase get() = SearchUserDirectoryUseCaseImpl(otherUserRepository)
    val setUserHandle: SetUserHandleUseCase get() = SetUserHandleUseCase(selfUserRepository, validateUserHandleUseCase, syncManager)
    val getAllKnownUsers: GetAllContactsUseCase get() = GetAllContactsUseCaseImpl(otherUserRepository)
    val getKnownUser: GetKnownUserUseCase get() = GetKnownUserUseCaseImpl(otherUserRepository)
    val getUserInfo: GetUserInfoUseCase get() = GetUserInfoUseCaseImpl(otherUserRepository,teamRepository)
    val updateSelfAvailabilityStatus: UpdateSelfAvailabilityStatusUseCase
        get() =
            UpdateSelfAvailabilityStatusUseCase(selfUserRepository, syncManager)
}
