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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.asset.AssetAuditFeatureHandler
import com.wire.kalium.logic.data.asset.AssetAuditFeatureHandlerImpl
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.TimestampKeyRepositoryImpl
import com.wire.kalium.logic.feature.asset.DeleteAssetUseCase
import com.wire.kalium.logic.feature.asset.DeleteAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCase
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCaseImpl
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCase
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCaseImpl
import com.wire.kalium.logic.feature.auth.PersistSelfUserEmailUseCase
import com.wire.kalium.logic.feature.auth.PersistSelfUserEmailUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.client.FinalizeMLSClientAfterE2EIEnrollment
import com.wire.kalium.logic.feature.client.FinalizeMLSClientAfterE2EIEnrollmentImpl
import com.wire.kalium.logic.feature.client.IsProfileQRCodeEnabledUseCase
import com.wire.kalium.logic.feature.client.IsProfileQRCodeEnabledUseCaseImpl
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCase
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCaseImpl
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledUseCase
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.conversation.GetAllContactsNotInConversationUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.e2ei.SyncCertificateRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.SyncCertificateRevocationListUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.GetMembersE2EICertificateStatusesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetMembersE2EICertificateStatusesUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.GetUserMlsClientIdentitiesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetUserMlsClientIdentitiesUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.IsOtherUserE2EIVerifiedUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.IsOtherUserE2EIVerifiedUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveCertificateRevocationForSelfClientUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveCertificateRevocationForSelfClientUseCaseImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.personaltoteamaccount.CanMigrateFromPersonalToTeamUseCase
import com.wire.kalium.logic.feature.personaltoteamaccount.CanMigrateFromPersonalToTeamUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCase
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.server.GetTeamUrlUseCase
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.feature.user.readReceipts.ObserveReadReceiptsEnabledUseCase
import com.wire.kalium.logic.feature.user.readReceipts.ObserveReadReceiptsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.readReceipts.PersistReadReceiptsStatusConfigUseCase
import com.wire.kalium.logic.feature.user.readReceipts.PersistReadReceiptsStatusConfigUseCaseImpl
import com.wire.kalium.logic.feature.user.typingIndicator.ObserveTypingIndicatorEnabledUseCase
import com.wire.kalium.logic.feature.user.typingIndicator.ObserveTypingIndicatorEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.typingIndicator.PersistTypingIndicatorStatusConfigUseCase
import com.wire.kalium.logic.feature.user.typingIndicator.PersistTypingIndicatorStatusConfigUseCaseImpl
import com.wire.kalium.logic.sync.ForegroundActionsUseCase
import com.wire.kalium.logic.sync.ForegroundActionsUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.CoroutineScope

@Suppress("LongParameterList")
public class UserScope internal constructor(
    private val userRepository: UserRepository,
    private val userConfigRepository: UserConfigRepository,
    private val accountRepository: AccountRepository,
    private val syncManager: SyncManager,
    private val assetRepository: AssetRepository,
    private val teamRepository: TeamRepository,
    private val sessionRepository: SessionRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val selfUserId: UserId,
    private val metadataDAO: MetadataDAO,
    private val userPropertyRepository: UserPropertyRepository,
    private val messageSender: MessageSender,
    private val clientIdProvider: CurrentClientIdProvider,
    private val e2EIRepository: E2EIRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationRepository: ConversationRepository,
    private val isSelfATeamMember: IsSelfATeamMemberUseCase,
    private val updateSelfUserSupportedProtocolsUseCase: UpdateSelfUserSupportedProtocolsUseCase,
    private val clientRepository: ClientRepository,
    private val joinExistingMLSConversationsUseCase: JoinExistingMLSConversationsUseCase,
    public val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val sessionManager: SessionManager,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val checkRevocationList: RevocationListChecker,
    private val userScopedLogger: KaliumLogger,
    private val teamUrlUseCase: GetTeamUrlUseCase,
    private val isMLSEnabledUseCase: IsMLSEnabledUseCase,
    private val updateApiVersionsUseCase: UpdateApiVersionsUseCase,
    private val userConfigSyncWorker: UserConfigSyncWorker,
    private val mlsClientManager: MLSClientManager,
    private val mlsMigrationManager: MLSMigrationManager,
    private val keyingMaterialsManager: KeyingMaterialsManager,
    private val transactionProvider: CryptoTransactionProvider,
    private val userCoroutineScope: CoroutineScope,
) {
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    public val getSelfUser: GetSelfUserUseCase get() = GetSelfUserUseCaseImpl(userRepository)
    public val observeSelfUser: ObserveSelfUserUseCase get() = ObserveSelfUserUseCaseImpl(userRepository)
    internal val getSelfUserWithTeam: ObserveSelfUserWithTeamUseCase get() = ObserveSelfUserWithTeamUseCaseImpl(userRepository)
    public val observeUserInfo: ObserveUserInfoUseCase get() = ObserveUserInfoUseCaseImpl(userRepository, teamRepository)
    public val uploadUserAvatar: UploadUserAvatarUseCase get() = UploadUserAvatarUseCaseImpl(userRepository, assetRepository)
    public val persistSelfUserEmail: PersistSelfUserEmailUseCase get() = PersistSelfUserEmailUseCaseImpl(userRepository)

    public val getPublicAsset: GetAvatarAssetUseCase get() = GetAvatarAssetUseCaseImpl(assetRepository, userRepository)
    internal val enrollE2EI: EnrollE2EIUseCase
        get() = EnrollE2EIUseCaseImpl(
            e2EIRepository = e2EIRepository,
            userRepository = userRepository,
            coroutineScope = userCoroutineScope,
            conversationRepository = conversationRepository,
            transactionProvider = transactionProvider
        )
    internal val getTeamUrl: GetTeamUrlUseCase get() = teamUrlUseCase

    public val finalizeMLSClientAfterE2EIEnrollment: FinalizeMLSClientAfterE2EIEnrollment
        get() = FinalizeMLSClientAfterE2EIEnrollmentImpl(
            clientRepository,
            joinExistingMLSConversationsUseCase
        )
    public val getE2EICertificate: GetMLSClientIdentityUseCase
        get() = GetMLSClientIdentityUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            transactionProvider = transactionProvider
        )
    public val getUserE2eiCertificateStatus: IsOtherUserE2EIVerifiedUseCase
        get() = IsOtherUserE2EIVerifiedUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            isE2EIEnabledUseCase = isE2EIEnabledUseCase,
            userRepository = userRepository,
            transactionProvider = transactionProvider
        )
    public val getUserMlsClientIdentities: GetUserMlsClientIdentitiesUseCase
        get() = GetUserMlsClientIdentitiesUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            isMlsEnabledUseCase = isMLSEnabledUseCase,
            transactionProvider = transactionProvider
        )
    public val getMembersE2EICertificateStatuses: GetMembersE2EICertificateStatusesUseCase
        get() = GetMembersE2EICertificateStatusesUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            conversationRepository = conversationRepository,
            transactionProvider = transactionProvider
        )
    public val deleteAsset: DeleteAssetUseCase get() = DeleteAssetUseCaseImpl(assetRepository)
    public val setUserHandle: SetUserHandleUseCase get() = SetUserHandleUseCase(accountRepository, validateUserHandleUseCase, syncManager)
    public val getAllKnownUsers: GetAllContactsUseCase get() = GetAllContactsUseCaseImpl(userRepository)
    public val getKnownUser: GetKnownUserUseCase get() = GetKnownUserUseCaseImpl(userRepository)
    public val getUserInfo: GetUserInfoUseCase get() = GetUserInfoUseCaseImpl(userRepository, teamRepository)
    public val updateSelfAvailabilityStatus: UpdateSelfAvailabilityStatusUseCase
        get() = UpdateSelfAvailabilityStatusUseCase(accountRepository, messageSender, clientIdProvider, selfUserId)
    public val getAllContactsNotInConversation: GetAllContactsNotInConversationUseCase
        get() = GetAllContactsNotInConversationUseCase(userRepository)

    public val isPasswordRequired: IsPasswordRequiredUseCase
        get() = IsPasswordRequiredUseCase(
            selfUserId = selfUserId,
            sessionRepository = sessionRepository
        )

    public val isReadOnlyAccount: IsReadOnlyAccountUseCase
        get() = IsReadOnlyAccountUseCaseImpl(
            selfUserId = selfUserId,
            sessionRepository = sessionRepository
        )

    public val observeReadReceiptsEnabled: ObserveReadReceiptsEnabledUseCase
        get() = ObserveReadReceiptsEnabledUseCaseImpl(
            userPropertyRepository = userPropertyRepository
        )

    public val observeTypingIndicatorEnabled: ObserveTypingIndicatorEnabledUseCase
        get() = ObserveTypingIndicatorEnabledUseCaseImpl(
            userPropertyRepository = userPropertyRepository
        )
    public val persistReadReceiptsStatusConfig: PersistReadReceiptsStatusConfigUseCase
        get() = PersistReadReceiptsStatusConfigUseCaseImpl(userPropertyRepository = userPropertyRepository)

    public val persistTypingIndicatorStatusConfig: PersistTypingIndicatorStatusConfigUseCase
        get() = PersistTypingIndicatorStatusConfigUseCaseImpl(userPropertyRepository = userPropertyRepository)

    public val serverLinks: SelfServerConfigUseCase get() = SelfServerConfigUseCase(selfUserId, serverConfigRepository)

    internal val timestampKeyRepository get() = TimestampKeyRepositoryImpl(metadataDAO)

    internal val persistMigratedUsers: PersistMigratedUsersUseCase get() = PersistMigratedUsersUseCaseImpl(userRepository)

    public val updateDisplayName: UpdateDisplayNameUseCase get() = UpdateDisplayNameUseCaseImpl(accountRepository)

    public val updateAccentColor: UpdateAccentColorUseCase get() = UpdateAccentColorUseCaseImpl(accountRepository)

    public val updateEmail: UpdateEmailUseCase get() = UpdateEmailUseCase(accountRepository)

    public val getAssetSizeLimit: GetAssetSizeLimitUseCase get() = GetAssetSizeLimitUseCaseImpl(isSelfATeamMember)

    public val deleteAccount: DeleteAccountUseCase get() = DeleteAccountUseCase(accountRepository)

    internal val updateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase get() = updateSelfUserSupportedProtocolsUseCase

    internal val observeCertificateRevocationForSelfClient: ObserveCertificateRevocationForSelfClientUseCase
        get() = ObserveCertificateRevocationForSelfClientUseCaseImpl(
            userConfigRepository = userConfigRepository,
            currentClientIdProvider = clientIdProvider,
            getE2eiCertificate = getE2EICertificate,
            kaliumLogger = userScopedLogger,
        )

    internal val syncCertificateRevocationListUseCase: SyncCertificateRevocationListUseCase
        get() =
            SyncCertificateRevocationListUseCaseImpl(
                certificateRevocationListRepository = certificateRevocationListRepository,
                incrementalSyncRepository = incrementalSyncRepository,
                revocationListChecker = checkRevocationList,
                transactionProvider = transactionProvider,
                kaliumLogger = userScopedLogger,
            )

    public val isPersonalToTeamAccountSupportedByBackend: CanMigrateFromPersonalToTeamUseCase by lazy {
        CanMigrateFromPersonalToTeamUseCaseImpl(
            sessionManager = sessionManager,
            serverConfigRepository = serverConfigRepository,
            selfTeamIdProvider = selfTeamIdProvider
        )
    }

    public val foregroundActions: ForegroundActionsUseCase
        get() = ForegroundActionsUseCaseImpl(
            updateApiVersionsUseCase = updateApiVersionsUseCase,
            userConfigSyncWorker = userConfigSyncWorker,
            syncCertificateRevocationListUseCase = syncCertificateRevocationListUseCase,
            observeCertificateRevocationForSelfClientUseCase = observeCertificateRevocationForSelfClient,
            mlsClientManager = mlsClientManager,
            mlsMigrationManager = mlsMigrationManager,
            keyingMaterialsManager = keyingMaterialsManager,
        )

    public val isWireCellsEnabled: IsWireCellsEnabledUseCase
        get() = IsWireCellsEnabledUseCaseImpl(
            userConfigRepository = userConfigRepository,
        )
    public val isWireCellsEnabledForConversation: IsWireCellsEnabledForConversationUseCase
        get() = IsWireCellsEnabledForConversationUseCaseImpl(
            conversationRepository = conversationRepository
        )

    public val isProfileQRCodeEnabled: IsProfileQRCodeEnabledUseCase
        get() = IsProfileQRCodeEnabledUseCaseImpl(
            userConfigRepository = userConfigRepository,
        )

    internal val assetAuditLog: AssetAuditFeatureHandler
        get() = AssetAuditFeatureHandlerImpl(
            userId = selfUserId,
            userConfigRepository = userConfigRepository,
            serverConfigRepository = serverConfigRepository,
        )
}
