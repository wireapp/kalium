/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.asset.AssetAuditFeatureHandler
import com.wire.kalium.logic.feature.TimestampKeyRepositoryImpl
import com.wire.kalium.logic.feature.asset.DeleteAssetUseCase
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCase
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCase
import com.wire.kalium.logic.feature.auth.PersistSelfUserEmailUseCase
import com.wire.kalium.logic.feature.client.FinalizeMLSClientAfterE2EIEnrollmentUseCase
import com.wire.kalium.logic.feature.client.IsProfileQRCodeEnabledUseCase
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCase
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledUseCase
import com.wire.kalium.logic.feature.client.ObserveIsWireCellsEnabledUseCase
import com.wire.kalium.logic.feature.conversation.GetAllContactsNotInConversationUseCase
import com.wire.kalium.logic.feature.e2ei.SyncCertificateRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetMembersE2EICertificateStatusesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetUserMlsClientIdentitiesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.IsOtherUserE2EIVerifiedUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveCertificateRevocationForSelfClientUseCase
import com.wire.kalium.logic.feature.personaltoteamaccount.CanMigrateFromPersonalToTeamUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCase
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.server.GetTeamUrlUseCase
import com.wire.kalium.logic.feature.user.linkPreviews.ObserveLinkPreviewsEnabledUseCase
import com.wire.kalium.logic.feature.user.linkPreviews.PersistLinkPreviewsStatusConfigUseCase
import com.wire.kalium.logic.feature.user.readReceipts.ObserveReadReceiptsEnabledUseCase
import com.wire.kalium.logic.feature.user.readReceipts.PersistReadReceiptsStatusConfigUseCase
import com.wire.kalium.logic.feature.user.typingIndicator.ObserveTypingIndicatorEnabledUseCase
import com.wire.kalium.logic.feature.user.typingIndicator.PersistTypingIndicatorStatusConfigUseCase
import com.wire.kalium.logic.sync.ForegroundActionsUseCase

/** Compatibility facade over individual bindings retained by the user owner. */
public class UserScope internal constructor(private val api: LegacyUserApi) {
    public val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
        get() = api.userRefreshUsersWithoutMetadata

    public val getSelfUser: GetSelfUserUseCase
        get() = api.userGetSelfUser

    public val getSelfTeamId: GetSelfTeamIdUseCase
        get() = api.userGetSelfTeamId

    public val observeSelfUser: ObserveSelfUserUseCase
        get() = api.userObserveSelfUser

    public val observeSelfUserWithTeam: ObserveSelfUserWithTeamUseCase
        get() = api.userObserveSelfUserWithTeam

    public val observeUserInfo: ObserveUserInfoUseCase
        get() = api.userObserveUserInfo

    public val uploadUserAvatar: UploadUserAvatarUseCase
        get() = api.userUploadUserAvatar

    public val persistSelfUserEmail: PersistSelfUserEmailUseCase
        get() = api.userPersistSelfUserEmail

    public val getPublicAsset: GetAvatarAssetUseCase
        get() = api.userGetPublicAsset

    public val enrollE2EI: EnrollE2EIUseCase
        get() = api.userEnrollE2EI

    internal val getTeamUrl: GetTeamUrlUseCase
        get() = api.userGetTeamUrl

    public val finalizeMLSClientAfterE2EIEnrollment: FinalizeMLSClientAfterE2EIEnrollmentUseCase
        get() = api.userFinalizeMLSClientAfterE2EIEnrollment

    public val getE2EICertificate: GetMLSClientIdentityUseCase
        get() = api.userGetE2EICertificate

    public val getUserE2eiCertificateStatus: IsOtherUserE2EIVerifiedUseCase
        get() = api.userGetUserE2eiCertificateStatus

    public val getUserMlsClientIdentities: GetUserMlsClientIdentitiesUseCase
        get() = api.userGetUserMlsClientIdentities

    public val getMembersE2EICertificateStatuses: GetMembersE2EICertificateStatusesUseCase
        get() = api.userGetMembersE2EICertificateStatuses

    public val deleteAsset: DeleteAssetUseCase
        get() = api.userDeleteAsset

    public val setUserHandle: SetUserHandleUseCase
        get() = api.userSetUserHandle

    public val getAllKnownUsers: GetAllContactsUseCase
        get() = api.userGetAllKnownUsers

    public val getKnownUser: GetKnownUserUseCase
        get() = api.userGetKnownUser

    public val getUserInfo: GetUserInfoUseCase
        get() = api.userGetUserInfo

    public val updateSelfAvailabilityStatus: UpdateSelfAvailabilityStatusUseCase
        get() = api.userUpdateSelfAvailabilityStatus

    public val getAllContactsNotInConversation: GetAllContactsNotInConversationUseCase
        get() = api.userGetAllContactsNotInConversation

    public val isPasswordRequired: IsPasswordRequiredUseCase
        get() = api.userIsPasswordRequired

    public val isReadOnlyAccount: IsReadOnlyAccountUseCase
        get() = api.userIsReadOnlyAccount

    public val getMessageContentPreference: GetMessageContentPreferenceUseCase
        get() = api.userGetMessageContentPreference

    public val setMessageContentPreference: SetMessageContentPreferenceUseCase
        get() = api.userSetMessageContentPreference

    public val observeReadReceiptsEnabled: ObserveReadReceiptsEnabledUseCase
        get() = api.userObserveReadReceiptsEnabled

    public val observeTypingIndicatorEnabled: ObserveTypingIndicatorEnabledUseCase
        get() = api.userObserveTypingIndicatorEnabled

    public val observeLinkPreviewsEnabled: ObserveLinkPreviewsEnabledUseCase
        get() = api.userObserveLinkPreviewsEnabled

    public val persistReadReceiptsStatusConfig: PersistReadReceiptsStatusConfigUseCase
        get() = api.userPersistReadReceiptsStatusConfig

    public val persistTypingIndicatorStatusConfig: PersistTypingIndicatorStatusConfigUseCase
        get() = api.userPersistTypingIndicatorStatusConfig

    public val persistLinkPreviewsStatusConfig: PersistLinkPreviewsStatusConfigUseCase
        get() = api.userPersistLinkPreviewsStatusConfig

    public val serverLinks: SelfServerConfigUseCase
        get() = api.userServerLinks

    internal val timestampKeyRepository: TimestampKeyRepositoryImpl
        get() = api.userTimestampKeyRepository

    internal val persistMigratedUsers: PersistMigratedUsersUseCase
        get() = api.userPersistMigratedUsers

    public val updateDisplayName: UpdateDisplayNameUseCase
        get() = api.userUpdateDisplayName

    public val updateAccentColor: UpdateAccentColorUseCase
        get() = api.userUpdateAccentColor

    public val updateEmail: UpdateEmailUseCase
        get() = api.userUpdateEmail

    public val getAssetSizeLimit: GetAssetSizeLimitUseCase
        get() = api.userGetAssetSizeLimit

    public val deleteAccount: DeleteAccountUseCase
        get() = api.userDeleteAccount

    public val updateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase
        get() = api.userUpdateSupportedProtocols

    internal val observeCertificateRevocationForSelfClient: ObserveCertificateRevocationForSelfClientUseCase
        get() = api.userObserveCertificateRevocationForSelfClient

    internal val syncCertificateRevocationListUseCase: SyncCertificateRevocationListUseCase
        get() = api.userSyncCertificateRevocationListUseCase

    public val isPersonalToTeamAccountSupportedByBackend: CanMigrateFromPersonalToTeamUseCase by lazy {
        api.userIsPersonalToTeamAccountSupportedByBackend
    }

    public val foregroundActions: ForegroundActionsUseCase
        get() = api.userForegroundActions

    public val isWireCellsEnabled: IsWireCellsEnabledUseCase
        get() = api.userIsWireCellsEnabled

    public val observeIsWireCellsEnabled: ObserveIsWireCellsEnabledUseCase
        get() = api.userObserveIsWireCellsEnabled

    public val isWireCellsEnabledForConversation: IsWireCellsEnabledForConversationUseCase
        get() = api.userIsWireCellsEnabledForConversation

    public val isProfileQRCodeEnabled: IsProfileQRCodeEnabledUseCase
        get() = api.userIsProfileQRCodeEnabled

    internal val assetAuditLog: AssetAuditFeatureHandler
        get() = api.userAssetAuditLog
}
