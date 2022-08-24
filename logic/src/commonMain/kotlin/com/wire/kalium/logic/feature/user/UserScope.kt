package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCase
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.conversation.GetAllContactsNotInConversationUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCase
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.search.SearchKnownUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchKnownUsersUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.search.SearchUserDirectoryUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchUserDirectoryUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager

@Suppress("LongParameterList")
class UserScope internal constructor(
    private val userRepository: UserRepository,
    private val searchUserRepository: SearchUserRepository,
    private val syncManager: SyncManager,
    private val assetRepository: AssetRepository,
    private val teamRepository: TeamRepository,
    private val connectionRepository: ConnectionRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId
) {
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val getSelfUser: GetSelfUserUseCase get() = GetSelfUserUseCaseImpl(userRepository)
    val observeUserInfo: ObserveUserInfoUseCase get() = ObserveUserInfoUseCaseImpl(userRepository, teamRepository)
    val uploadUserAvatar: UploadUserAvatarUseCase get() = UploadUserAvatarUseCaseImpl(userRepository, assetRepository)
    val searchUsers: SearchUsersUseCase get() = SearchUsersUseCaseImpl(searchUserRepository, connectionRepository, qualifiedIdMapper)
    val searchKnownUsers: SearchKnownUsersUseCase
        get() = SearchKnownUsersUseCaseImpl(
            searchUserRepository,
            userRepository,
            qualifiedIdMapper
        )
    val getPublicAsset: GetAvatarAssetUseCase get() = GetAvatarAssetUseCaseImpl(assetRepository)
    val searchUserDirectory: SearchUserDirectoryUseCase get() = SearchUserDirectoryUseCaseImpl(searchUserRepository)
    val setUserHandle: SetUserHandleUseCase get() = SetUserHandleUseCase(userRepository, validateUserHandleUseCase, syncManager)
    val getAllKnownUsers: GetAllContactsUseCase get() = GetAllContactsUseCaseImpl(userRepository)
    val getKnownUser: GetKnownUserUseCase get() = GetKnownUserUseCaseImpl(userRepository)
    val getUserInfo: GetUserInfoUseCase get() = GetUserInfoUseCaseImpl(userRepository, teamRepository)
    val updateSelfAvailabilityStatus: UpdateSelfAvailabilityStatusUseCase
        get() =
            UpdateSelfAvailabilityStatusUseCase(userRepository)
    val getAllContactsNotInConversation: GetAllContactsNotInConversationUseCase
        get() = GetAllContactsNotInConversationUseCase(userRepository)

    val isPasswordRequired get() = IsPasswordRequiredUseCase(
        selfUserId = selfUserId,
        sessionRepository = sessionRepository
    )
}
