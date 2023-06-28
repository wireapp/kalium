/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.TimestampKeyRepositoryImpl
import com.wire.kalium.logic.feature.asset.DeleteAssetUseCase
import com.wire.kalium.logic.feature.asset.DeleteAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCase
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCaseImpl
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCase
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.conversation.GetAllContactsNotInConversationUseCase
import com.wire.kalium.logic.feature.e2ei.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCase
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.search.SearchKnownUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchKnownUsersUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.search.SearchPublicUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchPublicUsersUseCaseImpl
import com.wire.kalium.logic.feature.user.readReceipts.ObserveReadReceiptsEnabledUseCase
import com.wire.kalium.logic.feature.user.readReceipts.ObserveReadReceiptsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.readReceipts.PersistReadReceiptsStatusConfigUseCase
import com.wire.kalium.logic.feature.user.readReceipts.PersistReadReceiptsStatusConfigUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.persistence.dao.MetadataDAO

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
    private val serverConfigRepository: ServerConfigRepository,
    private val selfUserId: UserId,
    private val metadataDAO: MetadataDAO,
    private val userPropertyRepository: UserPropertyRepository,
    private val messageSender: MessageSender,
    private val clientIdProvider: CurrentClientIdProvider,
    private val conversationRepository: ConversationRepository,
    private val isSelfATeamMember: IsSelfATeamMemberUseCase,
    private val e2EIRepository: E2EIRepository
) {
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val getSelfUser: GetSelfUserUseCase get() = GetSelfUserUseCaseImpl(userRepository)
    val getSelfUserWithTeam: ObserveSelfUserWithTeamUseCase get() = ObserveSelfUserWithTeamUseCaseImpl(userRepository)
    val observeUserInfo: ObserveUserInfoUseCase get() = ObserveUserInfoUseCaseImpl(userRepository, teamRepository)
    val uploadUserAvatar: UploadUserAvatarUseCase get() = UploadUserAvatarUseCaseImpl(userRepository, assetRepository)
    val searchUsers: SearchPublicUsersUseCase
        get() = SearchPublicUsersUseCaseImpl(
            searchUserRepository,
            connectionRepository,
            qualifiedIdMapper
        )
    val searchKnownUsers: SearchKnownUsersUseCase
        get() = SearchKnownUsersUseCaseImpl(
            searchUserRepository,
            userRepository,
            qualifiedIdMapper
        )
    val getPublicAsset: GetAvatarAssetUseCase get() = GetAvatarAssetUseCaseImpl(assetRepository)
    val enrollE2EI: EnrollE2EIUseCase get() = EnrollE2EIUseCaseImpl(e2EIRepository)
    val deleteAsset: DeleteAssetUseCase get() = DeleteAssetUseCaseImpl(assetRepository)
    val setUserHandle: SetUserHandleUseCase get() = SetUserHandleUseCase(userRepository, validateUserHandleUseCase, syncManager)
    val getAllKnownUsers: GetAllContactsUseCase get() = GetAllContactsUseCaseImpl(userRepository)
    val getKnownUser: GetKnownUserUseCase get() = GetKnownUserUseCaseImpl(userRepository)
    val getUserInfo: GetUserInfoUseCase get() = GetUserInfoUseCaseImpl(userRepository, teamRepository)
    val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase get() = RefreshUsersWithoutMetadataUseCaseImpl(userRepository)
    val updateSelfAvailabilityStatus: UpdateSelfAvailabilityStatusUseCase
        get() = UpdateSelfAvailabilityStatusUseCase(userRepository, messageSender, clientIdProvider, selfUserId)
    val getAllContactsNotInConversation: GetAllContactsNotInConversationUseCase
        get() = GetAllContactsNotInConversationUseCase(userRepository)

    val isPasswordRequired
        get() = IsPasswordRequiredUseCase(
            selfUserId = selfUserId,
            sessionRepository = sessionRepository
        )

    val isReadOnlyAccount: IsReadOnlyAccountUseCase
        get() = IsReadOnlyAccountUseCaseImpl(
            selfUserId = selfUserId,
            sessionRepository = sessionRepository
        )

    val observeReadReceiptsEnabled: ObserveReadReceiptsEnabledUseCase
        get() = ObserveReadReceiptsEnabledUseCaseImpl(
            userPropertyRepository = userPropertyRepository
        )
    val persistReadReceiptsStatusConfig: PersistReadReceiptsStatusConfigUseCase
        get() = PersistReadReceiptsStatusConfigUseCaseImpl(userPropertyRepository = userPropertyRepository)

    val serverLinks get() = SelfServerConfigUseCase(selfUserId, serverConfigRepository)

    val timestampKeyRepository get() = TimestampKeyRepositoryImpl(metadataDAO)

    val persistMigratedUsers: PersistMigratedUsersUseCase get() = PersistMigratedUsersUseCaseImpl(userRepository)

    val updateDisplayName: UpdateDisplayNameUseCase get() = UpdateDisplayNameUseCaseImpl(userRepository)

    val updateEmail: UpdateEmailUseCase get() = UpdateEmailUseCase(userRepository)

    val getAssetSizeLimit: GetAssetSizeLimitUseCase get() = GetAssetSizeLimitUseCaseImpl(isSelfATeamMember)
}
