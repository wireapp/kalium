/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

// Composition ports preserve the existing DAO and network-owner boundaries.
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
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.KaliumUserApi
import com.wire.kalium.logic.di.UserLifetime
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
import com.wire.kalium.logic.feature.client.FinalizeMLSClientAfterE2EIEnrollmentUseCase
import com.wire.kalium.logic.feature.client.FinalizeMLSClientAfterE2EIEnrollmentUseCaseImpl
import com.wire.kalium.logic.feature.client.IsProfileQRCodeEnabledUseCase
import com.wire.kalium.logic.feature.client.IsProfileQRCodeEnabledUseCaseImpl
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCase
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCaseImpl
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledUseCase
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.ObserveIsWireCellsEnabledUseCase
import com.wire.kalium.logic.feature.client.ObserveIsWireCellsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase
import com.wire.kalium.logic.feature.conversation.GetAllContactsNotInConversationUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingMLSGroupJoinsUseCase
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingOneOnOneResolutionsUseCase
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
import com.wire.kalium.logic.feature.user.linkPreviews.ObserveLinkPreviewsEnabledUseCase
import com.wire.kalium.logic.feature.user.linkPreviews.ObserveLinkPreviewsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.linkPreviews.PersistLinkPreviewsStatusConfigUseCase
import com.wire.kalium.logic.feature.user.linkPreviews.PersistLinkPreviewsStatusConfigUseCaseImpl
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
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.dao.MetadataDAO
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@Qualifier
internal annotation class LegacyUserBinding(val value: String)

/** Deferred inputs from the user owner. Each capture is read once per user graph, only when needed. */
internal interface LegacyUserDependencies {
    val userRepository: UserRepository
    val userConfigRepository: UserConfigRepository
    val accountRepository: AccountRepository
    val syncManager: SyncManager
    val assetRepository: AssetRepository
    val teamRepository: TeamRepository
    val sessionRepository: SessionRepository
    val serverConfigRepository: ServerConfigRepository
    val selfUserId: UserId
    val metadataDAO: MetadataDAO
    val userPropertyRepository: UserPropertyRepository
    val messageSender: MessageSender
    val clientIdProvider: CurrentClientIdProvider
    val registerMLSClientUseCase: RegisterMLSClientUseCase
    val e2EIRepository: E2EIRepository
    val mlsConversationRepository: MLSConversationRepository
    val conversationRepository: ConversationRepository
    val isSelfATeamMember: IsSelfATeamMemberUseCase
    val updateSelfUserSupportedProtocolsUseCase: UpdateSelfUserSupportedProtocolsUseCase
    val clientRepository: ClientRepository
    val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
    val isE2EIEnabledUseCase: IsE2EIEnabledUseCase
    val incrementalSyncRepository: IncrementalSyncRepository
    val slowSyncRepository: SlowSyncRepository
    val sessionManager: SessionManager
    val selfTeamIdProvider: SelfTeamIdProvider
    val userScopedLogger: KaliumLogger
    val teamUrlUseCase: GetTeamUrlUseCase
    val isMLSEnabledUseCase: IsMLSEnabledUseCase
    val updateApiVersionsUseCase: UpdateApiVersionsUseCase
    val userConfigSyncWorker: UserConfigSyncWorker
    val mlsClientManager: MLSClientManager
    val mlsMigrationManager: MLSMigrationManager
    val keyingMaterialsManager: KeyingMaterialsManager
    val recoverPendingMLSGroupJoinsUseCase: RecoverPendingMLSGroupJoinsUseCase
    val recoverPendingOneOnOneResolutionsUseCase: RecoverPendingOneOnOneResolutionsUseCase
    val transactionProvider: CryptoTransactionProvider
    val userCoroutineScope: CoroutineScope
}

/** Individual accessors; qualified captures preserve the cached facade's original identities. */
internal interface LegacyUserApi : KaliumUserApi {
    @get:LegacyUserBinding("userRepository")
    val userUserRepository: UserRepository

    @get:LegacyUserBinding("userConfigRepository")
    val userUserConfigRepository: UserConfigRepository

    @get:LegacyUserBinding("accountRepository")
    val userAccountRepository: AccountRepository

    @get:LegacyUserBinding("syncManager")
    val userSyncManager: SyncManager

    @get:LegacyUserBinding("assetRepository")
    val userAssetRepository: AssetRepository

    @get:LegacyUserBinding("teamRepository")
    val userTeamRepository: TeamRepository

    @get:LegacyUserBinding("sessionRepository")
    val userSessionRepository: SessionRepository

    @get:LegacyUserBinding("serverConfigRepository")
    val userServerConfigRepository: ServerConfigRepository

    @get:LegacyUserBinding("selfUserId")
    val userSelfUserId: UserId

    @get:LegacyUserBinding("metadataDAO")
    val userMetadataDAO: MetadataDAO

    @get:LegacyUserBinding("userPropertyRepository")
    val userUserPropertyRepository: UserPropertyRepository

    @get:LegacyUserBinding("messageSender")
    val userMessageSender: MessageSender

    @get:LegacyUserBinding("clientIdProvider")
    val userClientIdProvider: CurrentClientIdProvider

    @get:LegacyUserBinding("registerMLSClientUseCase")
    val userRegisterMLSClientUseCase: RegisterMLSClientUseCase

    @get:LegacyUserBinding("e2EIRepository")
    val userE2EIRepository: E2EIRepository

    @get:LegacyUserBinding("mlsConversationRepository")
    val userMlsConversationRepository: MLSConversationRepository

    @get:LegacyUserBinding("conversationRepository")
    val userConversationRepository: ConversationRepository

    @get:LegacyUserBinding("isSelfATeamMember")
    val userIsSelfATeamMember: IsSelfATeamMemberUseCase

    @get:LegacyUserBinding("updateSelfUserSupportedProtocolsUseCase")
    val userUpdateSelfUserSupportedProtocolsUseCase: UpdateSelfUserSupportedProtocolsUseCase

    @get:LegacyUserBinding("clientRepository")
    val userClientRepository: ClientRepository

    @get:LegacyUserBinding("refreshUsersWithoutMetadata")
    override val userRefreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase

    @get:LegacyUserBinding("isE2EIEnabledUseCase")
    val userIsE2EIEnabledUseCase: IsE2EIEnabledUseCase

    @get:LegacyUserBinding("incrementalSyncRepository")
    val userIncrementalSyncRepository: IncrementalSyncRepository

    @get:LegacyUserBinding("slowSyncRepository")
    val userSlowSyncRepository: SlowSyncRepository

    @get:LegacyUserBinding("sessionManager")
    val userSessionManager: SessionManager

    @get:LegacyUserBinding("selfTeamIdProvider")
    val userSelfTeamIdProvider: SelfTeamIdProvider

    @get:LegacyUserBinding("userScopedLogger")
    val userUserScopedLogger: KaliumLogger

    @get:LegacyUserBinding("teamUrlUseCase")
    val userTeamUrlUseCase: GetTeamUrlUseCase

    @get:LegacyUserBinding("isMLSEnabledUseCase")
    val userIsMLSEnabledUseCase: IsMLSEnabledUseCase

    @get:LegacyUserBinding("updateApiVersionsUseCase")
    val userUpdateApiVersionsUseCase: UpdateApiVersionsUseCase

    @get:LegacyUserBinding("userConfigSyncWorker")
    val userUserConfigSyncWorker: UserConfigSyncWorker

    @get:LegacyUserBinding("mlsClientManager")
    val userMlsClientManager: MLSClientManager

    @get:LegacyUserBinding("mlsMigrationManager")
    val userMlsMigrationManager: MLSMigrationManager

    @get:LegacyUserBinding("keyingMaterialsManager")
    val userKeyingMaterialsManager: KeyingMaterialsManager

    @get:LegacyUserBinding("recoverPendingMLSGroupJoinsUseCase")
    val userRecoverPendingMLSGroupJoinsUseCase: RecoverPendingMLSGroupJoinsUseCase

    @get:LegacyUserBinding("recoverPendingOneOnOneResolutionsUseCase")
    val userRecoverPendingOneOnOneResolutionsUseCase: RecoverPendingOneOnOneResolutionsUseCase

    @get:LegacyUserBinding("transactionProvider")
    val userTransactionProvider: CryptoTransactionProvider

    @get:LegacyUserBinding("userCoroutineScope")
    val userUserCoroutineScope: CoroutineScope

    @get:LegacyUserBinding("validateUserHandleUseCase")
    val userValidateUserHandleUseCase: ValidateUserHandleUseCase

    @get:LegacyUserBinding("getSelfUser")
    override val userGetSelfUser: GetSelfUserUseCase

    @get:LegacyUserBinding("getSelfTeamId")
    override val userGetSelfTeamId: GetSelfTeamIdUseCase

    @get:LegacyUserBinding("observeSelfUser")
    override val userObserveSelfUser: ObserveSelfUserUseCase

    @get:LegacyUserBinding("observeSelfUserWithTeam")
    override val userObserveSelfUserWithTeam: ObserveSelfUserWithTeamUseCase

    @get:LegacyUserBinding("observeUserInfo")
    override val userObserveUserInfo: ObserveUserInfoUseCase

    @get:LegacyUserBinding("uploadUserAvatar")
    override val userUploadUserAvatar: UploadUserAvatarUseCase

    @get:LegacyUserBinding("persistSelfUserEmail")
    val userPersistSelfUserEmail: PersistSelfUserEmailUseCase

    @get:LegacyUserBinding("getPublicAsset")
    override val userGetPublicAsset: GetAvatarAssetUseCase

    @get:LegacyUserBinding("enrollE2EI")
    val userEnrollE2EI: EnrollE2EIUseCase

    @get:LegacyUserBinding("getTeamUrl")
    val userGetTeamUrl: GetTeamUrlUseCase

    @get:LegacyUserBinding("finalizeMLSClientAfterE2EIEnrollment")
    override val userFinalizeMLSClientAfterE2EIEnrollment: FinalizeMLSClientAfterE2EIEnrollmentUseCase

    @get:LegacyUserBinding("getE2EICertificate")
    override val userGetE2EICertificate: GetMLSClientIdentityUseCase

    @get:LegacyUserBinding("getUserE2eiCertificateStatus")
    override val userGetUserE2eiCertificateStatus: IsOtherUserE2EIVerifiedUseCase

    @get:LegacyUserBinding("getUserMlsClientIdentities")
    override val userGetUserMlsClientIdentities: GetUserMlsClientIdentitiesUseCase

    @get:LegacyUserBinding("getMembersE2EICertificateStatuses")
    override val userGetMembersE2EICertificateStatuses: GetMembersE2EICertificateStatusesUseCase

    @get:LegacyUserBinding("deleteAsset")
    override val userDeleteAsset: DeleteAssetUseCase

    @get:LegacyUserBinding("setUserHandle")
    override val userSetUserHandle: SetUserHandleUseCase

    @get:LegacyUserBinding("getAllKnownUsers")
    override val userGetAllKnownUsers: GetAllContactsUseCase

    @get:LegacyUserBinding("getKnownUser")
    override val userGetKnownUser: GetKnownUserUseCase

    @get:LegacyUserBinding("getUserInfo")
    override val userGetUserInfo: GetUserInfoUseCase

    @get:LegacyUserBinding("updateSelfAvailabilityStatus")
    override val userUpdateSelfAvailabilityStatus: UpdateSelfAvailabilityStatusUseCase

    @get:LegacyUserBinding("getAllContactsNotInConversation")
    override val userGetAllContactsNotInConversation: GetAllContactsNotInConversationUseCase

    @get:LegacyUserBinding("isPasswordRequired")
    override val userIsPasswordRequired: IsPasswordRequiredUseCase

    @get:LegacyUserBinding("isReadOnlyAccount")
    override val userIsReadOnlyAccount: IsReadOnlyAccountUseCase

    @get:LegacyUserBinding("getMessageContentPreference")
    val userGetMessageContentPreference: GetMessageContentPreferenceUseCase

    @get:LegacyUserBinding("setMessageContentPreference")
    val userSetMessageContentPreference: SetMessageContentPreferenceUseCase

    @get:LegacyUserBinding("observeReadReceiptsEnabled")
    override val userObserveReadReceiptsEnabled: ObserveReadReceiptsEnabledUseCase

    @get:LegacyUserBinding("observeTypingIndicatorEnabled")
    override val userObserveTypingIndicatorEnabled: ObserveTypingIndicatorEnabledUseCase

    @get:LegacyUserBinding("observeLinkPreviewsEnabled")
    override val userObserveLinkPreviewsEnabled: ObserveLinkPreviewsEnabledUseCase

    @get:LegacyUserBinding("persistReadReceiptsStatusConfig")
    override val userPersistReadReceiptsStatusConfig: PersistReadReceiptsStatusConfigUseCase

    @get:LegacyUserBinding("persistTypingIndicatorStatusConfig")
    override val userPersistTypingIndicatorStatusConfig: PersistTypingIndicatorStatusConfigUseCase

    @get:LegacyUserBinding("persistLinkPreviewsStatusConfig")
    override val userPersistLinkPreviewsStatusConfig: PersistLinkPreviewsStatusConfigUseCase

    @get:LegacyUserBinding("serverLinks")
    override val userServerLinks: SelfServerConfigUseCase

    @get:LegacyUserBinding("timestampKeyRepository")
    val userTimestampKeyRepository: TimestampKeyRepositoryImpl

    @get:LegacyUserBinding("persistMigratedUsers")
    val userPersistMigratedUsers: PersistMigratedUsersUseCase

    @get:LegacyUserBinding("updateDisplayName")
    override val userUpdateDisplayName: UpdateDisplayNameUseCase

    @get:LegacyUserBinding("updateAccentColor")
    override val userUpdateAccentColor: UpdateAccentColorUseCase

    @get:LegacyUserBinding("updateEmail")
    override val userUpdateEmail: UpdateEmailUseCase

    @get:LegacyUserBinding("getAssetSizeLimit")
    override val userGetAssetSizeLimit: GetAssetSizeLimitUseCase

    @get:LegacyUserBinding("deleteAccount")
    override val userDeleteAccount: DeleteAccountUseCase

    @get:LegacyUserBinding("updateSupportedProtocols")
    val userUpdateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase

    @get:LegacyUserBinding("observeCertificateRevocationForSelfClient")
    val userObserveCertificateRevocationForSelfClient: ObserveCertificateRevocationForSelfClientUseCase

    @get:LegacyUserBinding("syncCertificateRevocationListUseCase")
    val userSyncCertificateRevocationListUseCase: SyncCertificateRevocationListUseCase

    @get:LegacyUserBinding("isPersonalToTeamAccountSupportedByBackend")
    override val userIsPersonalToTeamAccountSupportedByBackend: CanMigrateFromPersonalToTeamUseCase

    @get:LegacyUserBinding("foregroundActions")
    override val userForegroundActions: ForegroundActionsUseCase

    @get:LegacyUserBinding("isWireCellsEnabled")
    override val userIsWireCellsEnabled: IsWireCellsEnabledUseCase

    @get:LegacyUserBinding("observeIsWireCellsEnabled")
    override val userObserveIsWireCellsEnabled: ObserveIsWireCellsEnabledUseCase

    @get:LegacyUserBinding("isWireCellsEnabledForConversation")
    override val userIsWireCellsEnabledForConversation: IsWireCellsEnabledForConversationUseCase

    @get:LegacyUserBinding("isProfileQRCodeEnabled")
    override val userIsProfileQRCodeEnabled: IsProfileQRCodeEnabledUseCase

    @get:LegacyUserBinding("assetAuditLog")
    val userAssetAuditLog: AssetAuditFeatureHandler
}

/** Individual bindings in the user graph; this package does not introduce a feature graph. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Declarative bindings preserve legacy captures and factories.
internal interface LegacyUserBindings : LegacyUserApi {
    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("userRepository")
    fun provideUserUserRepository(dependencies: LegacyUserDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("userConfigRepository")
    fun provideUserUserConfigRepository(dependencies: LegacyUserDependencies): UserConfigRepository =
        dependencies.userConfigRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("accountRepository")
    fun provideUserAccountRepository(dependencies: LegacyUserDependencies): AccountRepository =
        dependencies.accountRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("syncManager")
    fun provideUserSyncManager(dependencies: LegacyUserDependencies): SyncManager =
        dependencies.syncManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("assetRepository")
    fun provideUserAssetRepository(dependencies: LegacyUserDependencies): AssetRepository =
        dependencies.assetRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("teamRepository")
    fun provideUserTeamRepository(dependencies: LegacyUserDependencies): TeamRepository =
        dependencies.teamRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("sessionRepository")
    fun provideUserSessionRepository(dependencies: LegacyUserDependencies): SessionRepository =
        dependencies.sessionRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("serverConfigRepository")
    fun provideUserServerConfigRepository(dependencies: LegacyUserDependencies): ServerConfigRepository =
        dependencies.serverConfigRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("selfUserId")
    fun provideUserSelfUserId(dependencies: LegacyUserDependencies): UserId =
        dependencies.selfUserId

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("metadataDAO")
    fun provideUserMetadataDAO(dependencies: LegacyUserDependencies): MetadataDAO =
        dependencies.metadataDAO

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("userPropertyRepository")
    fun provideUserUserPropertyRepository(dependencies: LegacyUserDependencies): UserPropertyRepository =
        dependencies.userPropertyRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("messageSender")
    fun provideUserMessageSender(dependencies: LegacyUserDependencies): MessageSender =
        dependencies.messageSender

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("clientIdProvider")
    fun provideUserClientIdProvider(dependencies: LegacyUserDependencies): CurrentClientIdProvider =
        dependencies.clientIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("registerMLSClientUseCase")
    fun provideUserRegisterMLSClientUseCase(dependencies: LegacyUserDependencies): RegisterMLSClientUseCase =
        dependencies.registerMLSClientUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("e2EIRepository")
    fun provideUserE2EIRepository(dependencies: LegacyUserDependencies): E2EIRepository =
        dependencies.e2EIRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("mlsConversationRepository")
    fun provideUserMlsConversationRepository(dependencies: LegacyUserDependencies): MLSConversationRepository =
        dependencies.mlsConversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("conversationRepository")
    fun provideUserConversationRepository(dependencies: LegacyUserDependencies): ConversationRepository =
        dependencies.conversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("isSelfATeamMember")
    fun provideUserIsSelfATeamMember(dependencies: LegacyUserDependencies): IsSelfATeamMemberUseCase =
        dependencies.isSelfATeamMember

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("updateSelfUserSupportedProtocolsUseCase")
    fun provideUserUpdateSelfUserSupportedProtocolsUseCase(dependencies: LegacyUserDependencies): UpdateSelfUserSupportedProtocolsUseCase =
        dependencies.updateSelfUserSupportedProtocolsUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("clientRepository")
    fun provideUserClientRepository(dependencies: LegacyUserDependencies): ClientRepository =
        dependencies.clientRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("refreshUsersWithoutMetadata")
    fun provideUserRefreshUsersWithoutMetadata(dependencies: LegacyUserDependencies): RefreshUsersWithoutMetadataUseCase =
        dependencies.refreshUsersWithoutMetadata

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("isE2EIEnabledUseCase")
    fun provideUserIsE2EIEnabledUseCase(dependencies: LegacyUserDependencies): IsE2EIEnabledUseCase =
        dependencies.isE2EIEnabledUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("incrementalSyncRepository")
    fun provideUserIncrementalSyncRepository(dependencies: LegacyUserDependencies): IncrementalSyncRepository =
        dependencies.incrementalSyncRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("slowSyncRepository")
    fun provideUserSlowSyncRepository(dependencies: LegacyUserDependencies): SlowSyncRepository =
        dependencies.slowSyncRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("sessionManager")
    fun provideUserSessionManager(dependencies: LegacyUserDependencies): SessionManager =
        dependencies.sessionManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("selfTeamIdProvider")
    fun provideUserSelfTeamIdProvider(dependencies: LegacyUserDependencies): SelfTeamIdProvider =
        dependencies.selfTeamIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("userScopedLogger")
    fun provideUserUserScopedLogger(dependencies: LegacyUserDependencies): KaliumLogger =
        dependencies.userScopedLogger

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("teamUrlUseCase")
    fun provideUserTeamUrlUseCase(dependencies: LegacyUserDependencies): GetTeamUrlUseCase =
        dependencies.teamUrlUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("isMLSEnabledUseCase")
    fun provideUserIsMLSEnabledUseCase(dependencies: LegacyUserDependencies): IsMLSEnabledUseCase =
        dependencies.isMLSEnabledUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("updateApiVersionsUseCase")
    fun provideUserUpdateApiVersionsUseCase(dependencies: LegacyUserDependencies): UpdateApiVersionsUseCase =
        dependencies.updateApiVersionsUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("userConfigSyncWorker")
    fun provideUserUserConfigSyncWorker(dependencies: LegacyUserDependencies): UserConfigSyncWorker =
        dependencies.userConfigSyncWorker

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("mlsClientManager")
    fun provideUserMlsClientManager(dependencies: LegacyUserDependencies): MLSClientManager =
        dependencies.mlsClientManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("mlsMigrationManager")
    fun provideUserMlsMigrationManager(dependencies: LegacyUserDependencies): MLSMigrationManager =
        dependencies.mlsMigrationManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("keyingMaterialsManager")
    fun provideUserKeyingMaterialsManager(dependencies: LegacyUserDependencies): KeyingMaterialsManager =
        dependencies.keyingMaterialsManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("recoverPendingMLSGroupJoinsUseCase")
    fun provideUserRecoverPendingMLSGroupJoinsUseCase(dependencies: LegacyUserDependencies): RecoverPendingMLSGroupJoinsUseCase =
        dependencies.recoverPendingMLSGroupJoinsUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("recoverPendingOneOnOneResolutionsUseCase")
    fun provideUserRecoverPendingOneOnOneResolutionsUseCase(
        dependencies: LegacyUserDependencies,
    ): RecoverPendingOneOnOneResolutionsUseCase =
        dependencies.recoverPendingOneOnOneResolutionsUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("transactionProvider")
    fun provideUserTransactionProvider(dependencies: LegacyUserDependencies): CryptoTransactionProvider =
        dependencies.transactionProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("userCoroutineScope")
    fun provideUserUserCoroutineScope(dependencies: LegacyUserDependencies): CoroutineScope =
        dependencies.userCoroutineScope

    @Provides
    @LegacyUserBinding("validateUserHandleUseCase")
    fun provideUserValidateUserHandleUseCase(): ValidateUserHandleUseCase = ValidateUserHandleUseCaseImpl()

    @Provides
    @LegacyUserBinding("getSelfUser")
    fun provideUserGetSelfUser(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
    ): GetSelfUserUseCase = GetSelfUserUseCaseImpl(userRepository)

    @Provides
    @LegacyUserBinding("getSelfTeamId")
    fun provideUserGetSelfTeamId(
        @LegacyUserBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
    ): GetSelfTeamIdUseCase = GetSelfTeamIdUseCaseImpl(selfTeamIdProvider)

    @Provides
    @LegacyUserBinding("observeSelfUser")
    fun provideUserObserveSelfUser(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
    ): ObserveSelfUserUseCase = ObserveSelfUserUseCaseImpl(userRepository)

    @Provides
    @LegacyUserBinding("observeSelfUserWithTeam")
    fun provideUserObserveSelfUserWithTeam(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
    ): ObserveSelfUserWithTeamUseCase = ObserveSelfUserWithTeamUseCaseImpl(userRepository)

    @Provides
    @LegacyUserBinding("observeUserInfo")
    fun provideUserObserveUserInfo(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
        @LegacyUserBinding("teamRepository") teamRepository: TeamRepository,
    ): ObserveUserInfoUseCase = ObserveUserInfoUseCaseImpl(userRepository, teamRepository)

    @Provides
    @LegacyUserBinding("uploadUserAvatar")
    fun provideUserUploadUserAvatar(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
        @LegacyUserBinding("assetRepository") assetRepository: AssetRepository,
    ): UploadUserAvatarUseCase = UploadUserAvatarUseCaseImpl(userRepository, assetRepository)

    @Provides
    @LegacyUserBinding("persistSelfUserEmail")
    fun provideUserPersistSelfUserEmail(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
    ): PersistSelfUserEmailUseCase = PersistSelfUserEmailUseCaseImpl(userRepository)

    @Provides
    @LegacyUserBinding("getPublicAsset")
    fun provideUserGetPublicAsset(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
        @LegacyUserBinding("assetRepository") assetRepository: AssetRepository,
    ): GetAvatarAssetUseCase = GetAvatarAssetUseCaseImpl(assetRepository, userRepository)

    @Provides
    @LegacyUserBinding("enrollE2EI")
    fun provideUserEnrollE2EI(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
        @LegacyUserBinding("e2EIRepository") e2EIRepository: E2EIRepository,
        @LegacyUserBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyUserBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
        @LegacyUserBinding("userCoroutineScope") userCoroutineScope: CoroutineScope,
    ): EnrollE2EIUseCase =
        EnrollE2EIUseCaseImpl(
            e2EIRepository = e2EIRepository,
            userRepository = userRepository,
            coroutineScope = userCoroutineScope,
            conversationRepository = conversationRepository,
            transactionProvider = transactionProvider
        )

    @Provides
    @LegacyUserBinding("getTeamUrl")
    fun provideUserGetTeamUrl(
        @LegacyUserBinding("teamUrlUseCase") teamUrlUseCase: GetTeamUrlUseCase,
    ): GetTeamUrlUseCase = teamUrlUseCase

    @Provides
    @LegacyUserBinding("finalizeMLSClientAfterE2EIEnrollment")
    fun provideUserFinalizeMLSClientAfterE2EIEnrollment(
        @LegacyUserBinding("clientIdProvider") clientIdProvider: CurrentClientIdProvider,
        @LegacyUserBinding("registerMLSClientUseCase") registerMLSClientUseCase: RegisterMLSClientUseCase,
        @LegacyUserBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyUserBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
    ): FinalizeMLSClientAfterE2EIEnrollmentUseCase =
        FinalizeMLSClientAfterE2EIEnrollmentUseCaseImpl(
            clientRepository,
            clientIdProvider,
            registerMLSClientUseCase,
            slowSyncRepository
        )

    @Provides
    @LegacyUserBinding("getE2EICertificate")
    fun provideUserGetE2EICertificate(
        @LegacyUserBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyUserBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): GetMLSClientIdentityUseCase =
        GetMLSClientIdentityUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            transactionProvider = transactionProvider
        )

    @Provides
    @LegacyUserBinding("getUserE2eiCertificateStatus")
    fun provideUserGetUserE2eiCertificateStatus(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
        @LegacyUserBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyUserBinding("isE2EIEnabledUseCase") isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
        @LegacyUserBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): IsOtherUserE2EIVerifiedUseCase =
        IsOtherUserE2EIVerifiedUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            isE2EIEnabledUseCase = isE2EIEnabledUseCase,
            userRepository = userRepository,
            transactionProvider = transactionProvider
        )

    @Provides
    @LegacyUserBinding("getUserMlsClientIdentities")
    fun provideUserGetUserMlsClientIdentities(
        @LegacyUserBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyUserBinding("isMLSEnabledUseCase") isMLSEnabledUseCase: IsMLSEnabledUseCase,
        @LegacyUserBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): GetUserMlsClientIdentitiesUseCase =
        GetUserMlsClientIdentitiesUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            isMlsEnabledUseCase = isMLSEnabledUseCase,
            transactionProvider = transactionProvider
        )

    @Provides
    @LegacyUserBinding("getMembersE2EICertificateStatuses")
    fun provideUserGetMembersE2EICertificateStatuses(
        @LegacyUserBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyUserBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyUserBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): GetMembersE2EICertificateStatusesUseCase =
        GetMembersE2EICertificateStatusesUseCaseImpl(
            mlsClientProvider = transactionProvider.mlsClientProvider,
            mlsConversationRepository = mlsConversationRepository,
            conversationRepository = conversationRepository
        )

    @Provides
    @LegacyUserBinding("deleteAsset")
    fun provideUserDeleteAsset(
        @LegacyUserBinding("assetRepository") assetRepository: AssetRepository,
    ): DeleteAssetUseCase = DeleteAssetUseCaseImpl(assetRepository)

    @Provides
    @LegacyUserBinding("setUserHandle")
    fun provideUserSetUserHandle(
        @LegacyUserBinding("accountRepository") accountRepository: AccountRepository,
        @LegacyUserBinding("syncManager") syncManager: SyncManager,
        @LegacyUserBinding("validateUserHandleUseCase") validateUserHandleUseCase: ValidateUserHandleUseCase,
    ): SetUserHandleUseCase = SetUserHandleUseCase(accountRepository, validateUserHandleUseCase, syncManager)

    @Provides
    @LegacyUserBinding("getAllKnownUsers")
    fun provideUserGetAllKnownUsers(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
    ): GetAllContactsUseCase = GetAllContactsUseCaseImpl(userRepository)

    @Provides
    @LegacyUserBinding("getKnownUser")
    fun provideUserGetKnownUser(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
    ): GetKnownUserUseCase = GetKnownUserUseCaseImpl(userRepository)

    @Provides
    @LegacyUserBinding("getUserInfo")
    fun provideUserGetUserInfo(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
        @LegacyUserBinding("teamRepository") teamRepository: TeamRepository,
    ): GetUserInfoUseCase = GetUserInfoUseCaseImpl(userRepository, teamRepository)

    @Provides
    @LegacyUserBinding("updateSelfAvailabilityStatus")
    fun provideUserUpdateSelfAvailabilityStatus(
        @LegacyUserBinding("accountRepository") accountRepository: AccountRepository,
        @LegacyUserBinding("selfUserId") selfUserId: UserId,
        @LegacyUserBinding("messageSender") messageSender: MessageSender,
        @LegacyUserBinding("clientIdProvider") clientIdProvider: CurrentClientIdProvider,
    ): UpdateSelfAvailabilityStatusUseCase =
        UpdateSelfAvailabilityStatusUseCase(accountRepository, messageSender, clientIdProvider, selfUserId)

    @Provides
    @LegacyUserBinding("getAllContactsNotInConversation")
    fun provideUserGetAllContactsNotInConversation(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
    ): GetAllContactsNotInConversationUseCase = GetAllContactsNotInConversationUseCase(userRepository)

    @Provides
    @LegacyUserBinding("isPasswordRequired")
    fun provideUserIsPasswordRequired(
        @LegacyUserBinding("sessionRepository") sessionRepository: SessionRepository,
        @LegacyUserBinding("selfUserId") selfUserId: UserId,
    ): IsPasswordRequiredUseCase =
        IsPasswordRequiredUseCase(
            selfUserId = selfUserId,
            sessionRepository = sessionRepository
        )

    @Provides
    @LegacyUserBinding("isReadOnlyAccount")
    fun provideUserIsReadOnlyAccount(
        @LegacyUserBinding("sessionRepository") sessionRepository: SessionRepository,
        @LegacyUserBinding("selfUserId") selfUserId: UserId,
    ): IsReadOnlyAccountUseCase =
        IsReadOnlyAccountUseCaseImpl(
            selfUserId = selfUserId,
            sessionRepository = sessionRepository
        )

    @Provides
    @LegacyUserBinding("getMessageContentPreference")
    fun provideUserGetMessageContentPreference(
        @LegacyUserBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
    ): GetMessageContentPreferenceUseCase = GetMessageContentPreferenceUseCase(userConfigRepository)

    @Provides
    @LegacyUserBinding("setMessageContentPreference")
    fun provideUserSetMessageContentPreference(
        @LegacyUserBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
    ): SetMessageContentPreferenceUseCase = SetMessageContentPreferenceUseCase(userConfigRepository)

    @Provides
    @LegacyUserBinding("observeReadReceiptsEnabled")
    fun provideUserObserveReadReceiptsEnabled(
        @LegacyUserBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
    ): ObserveReadReceiptsEnabledUseCase =
        ObserveReadReceiptsEnabledUseCaseImpl(
            userPropertyRepository = userPropertyRepository
        )

    @Provides
    @LegacyUserBinding("observeTypingIndicatorEnabled")
    fun provideUserObserveTypingIndicatorEnabled(
        @LegacyUserBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
    ): ObserveTypingIndicatorEnabledUseCase =
        ObserveTypingIndicatorEnabledUseCaseImpl(
            userPropertyRepository = userPropertyRepository
        )

    @Provides
    @LegacyUserBinding("observeLinkPreviewsEnabled")
    fun provideUserObserveLinkPreviewsEnabled(
        @LegacyUserBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
    ): ObserveLinkPreviewsEnabledUseCase =
        ObserveLinkPreviewsEnabledUseCaseImpl(
            userPropertyRepository = userPropertyRepository
        )

    @Provides
    @LegacyUserBinding("persistReadReceiptsStatusConfig")
    fun provideUserPersistReadReceiptsStatusConfig(
        @LegacyUserBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
    ): PersistReadReceiptsStatusConfigUseCase = PersistReadReceiptsStatusConfigUseCaseImpl(userPropertyRepository = userPropertyRepository)

    @Provides
    @LegacyUserBinding("persistTypingIndicatorStatusConfig")
    fun provideUserPersistTypingIndicatorStatusConfig(
        @LegacyUserBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
    ): PersistTypingIndicatorStatusConfigUseCase =
        PersistTypingIndicatorStatusConfigUseCaseImpl(userPropertyRepository = userPropertyRepository)

    @Provides
    @LegacyUserBinding("persistLinkPreviewsStatusConfig")
    fun provideUserPersistLinkPreviewsStatusConfig(
        @LegacyUserBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
    ): PersistLinkPreviewsStatusConfigUseCase = PersistLinkPreviewsStatusConfigUseCaseImpl(userPropertyRepository = userPropertyRepository)

    @Provides
    @LegacyUserBinding("serverLinks")
    fun provideUserServerLinks(
        @LegacyUserBinding("serverConfigRepository") serverConfigRepository: ServerConfigRepository,
        @LegacyUserBinding("selfUserId") selfUserId: UserId,
    ): SelfServerConfigUseCase = SelfServerConfigUseCase(selfUserId, serverConfigRepository)

    @Provides
    @LegacyUserBinding("timestampKeyRepository")
    fun provideUserTimestampKeyRepository(
        @LegacyUserBinding("metadataDAO") metadataDAO: MetadataDAO,
    ): TimestampKeyRepositoryImpl = TimestampKeyRepositoryImpl(metadataDAO)

    @Provides
    @LegacyUserBinding("persistMigratedUsers")
    fun provideUserPersistMigratedUsers(
        @LegacyUserBinding("userRepository") userRepository: UserRepository,
    ): PersistMigratedUsersUseCase = PersistMigratedUsersUseCaseImpl(userRepository)

    @Provides
    @LegacyUserBinding("updateDisplayName")
    fun provideUserUpdateDisplayName(
        @LegacyUserBinding("accountRepository") accountRepository: AccountRepository,
    ): UpdateDisplayNameUseCase = UpdateDisplayNameUseCaseImpl(accountRepository)

    @Provides
    @LegacyUserBinding("updateAccentColor")
    fun provideUserUpdateAccentColor(
        @LegacyUserBinding("accountRepository") accountRepository: AccountRepository,
    ): UpdateAccentColorUseCase = UpdateAccentColorUseCaseImpl(accountRepository)

    @Provides
    @LegacyUserBinding("updateEmail")
    fun provideUserUpdateEmail(
        @LegacyUserBinding("accountRepository") accountRepository: AccountRepository,
    ): UpdateEmailUseCase = UpdateEmailUseCase(accountRepository)

    @Provides
    @LegacyUserBinding("getAssetSizeLimit")
    fun provideUserGetAssetSizeLimit(
        @LegacyUserBinding("isSelfATeamMember") isSelfATeamMember: IsSelfATeamMemberUseCase,
    ): GetAssetSizeLimitUseCase = GetAssetSizeLimitUseCaseImpl(isSelfATeamMember)

    @Provides
    @LegacyUserBinding("deleteAccount")
    fun provideUserDeleteAccount(
        @LegacyUserBinding("accountRepository") accountRepository: AccountRepository,
    ): DeleteAccountUseCase = DeleteAccountUseCase(accountRepository)

    @Provides
    @LegacyUserBinding("updateSupportedProtocols")
    fun provideUserUpdateSupportedProtocols(
        @LegacyUserBinding("updateSelfUserSupportedProtocolsUseCase")
        updateSelfUserSupportedProtocolsUseCase: UpdateSelfUserSupportedProtocolsUseCase,
    ): UpdateSelfUserSupportedProtocolsUseCase = updateSelfUserSupportedProtocolsUseCase

    @Provides
    @LegacyUserBinding("observeCertificateRevocationForSelfClient")
    fun provideUserObserveCertificateRevocationForSelfClient(
        @LegacyUserBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
        @LegacyUserBinding("clientIdProvider") clientIdProvider: CurrentClientIdProvider,
        @LegacyUserBinding("userScopedLogger") userScopedLogger: KaliumLogger,
        @LegacyUserBinding("getE2EICertificate") getE2EICertificate: GetMLSClientIdentityUseCase,
    ): ObserveCertificateRevocationForSelfClientUseCase =
        ObserveCertificateRevocationForSelfClientUseCaseImpl(
            userConfigRepository = userConfigRepository,
            currentClientIdProvider = clientIdProvider,
            getE2eiCertificate = getE2EICertificate,
            kaliumLogger = userScopedLogger,
        )

    @Provides
    @LegacyUserBinding("syncCertificateRevocationListUseCase")
    fun provideUserSyncCertificateRevocationListUseCase(
        @LegacyUserBinding("e2EIRepository") e2EIRepository: E2EIRepository,
        @LegacyUserBinding("isE2EIEnabledUseCase") isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
        @LegacyUserBinding("incrementalSyncRepository") incrementalSyncRepository: IncrementalSyncRepository,
        @LegacyUserBinding("userScopedLogger") userScopedLogger: KaliumLogger,
    ): SyncCertificateRevocationListUseCase =
        SyncCertificateRevocationListUseCaseImpl(
            incrementalSyncRepository = incrementalSyncRepository,
            e2eiRepository = e2EIRepository,
            isE2EIEnabledUseCase = isE2EIEnabledUseCase,
            kaliumLogger = userScopedLogger,
        )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyUserBinding("isPersonalToTeamAccountSupportedByBackend")
    fun provideUserIsPersonalToTeamAccountSupportedByBackend(
        @LegacyUserBinding("serverConfigRepository") serverConfigRepository: ServerConfigRepository,
        @LegacyUserBinding("sessionManager") sessionManager: SessionManager,
        @LegacyUserBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
    ): CanMigrateFromPersonalToTeamUseCase =
        CanMigrateFromPersonalToTeamUseCaseImpl(
            sessionManager = sessionManager,
            serverConfigRepository = serverConfigRepository,
            selfTeamIdProvider = selfTeamIdProvider
        )

    @Provides
    @LegacyUserBinding("foregroundActions")
    fun provideUserForegroundActions(
        @LegacyUserBinding("updateApiVersionsUseCase") updateApiVersionsUseCase: UpdateApiVersionsUseCase,
        @LegacyUserBinding("userConfigSyncWorker") userConfigSyncWorker: UserConfigSyncWorker,
        @LegacyUserBinding("mlsClientManager") mlsClientManager: MLSClientManager,
        @LegacyUserBinding("mlsMigrationManager") mlsMigrationManager: MLSMigrationManager,
        @LegacyUserBinding("keyingMaterialsManager") keyingMaterialsManager: KeyingMaterialsManager,
        @LegacyUserBinding("recoverPendingMLSGroupJoinsUseCase") recoverPendingMLSGroupJoinsUseCase: RecoverPendingMLSGroupJoinsUseCase,
        @LegacyUserBinding("recoverPendingOneOnOneResolutionsUseCase")
        recoverPendingOneOnOneResolutionsUseCase: RecoverPendingOneOnOneResolutionsUseCase,
        @LegacyUserBinding("observeCertificateRevocationForSelfClient")
        observeCertificateRevocationForSelfClient: ObserveCertificateRevocationForSelfClientUseCase,
        @LegacyUserBinding("syncCertificateRevocationListUseCase")
        syncCertificateRevocationListUseCase: SyncCertificateRevocationListUseCase,
    ): ForegroundActionsUseCase =
        ForegroundActionsUseCaseImpl(
            updateApiVersionsUseCase = updateApiVersionsUseCase,
            userConfigSyncWorker = userConfigSyncWorker,
            syncCertificateRevocationListUseCase = syncCertificateRevocationListUseCase,
            observeCertificateRevocationForSelfClientUseCase = observeCertificateRevocationForSelfClient,
            mlsClientManager = mlsClientManager,
            mlsMigrationManager = mlsMigrationManager,
            keyingMaterialsManager = keyingMaterialsManager,
            recoverPendingMLSGroupJoinsUseCase = recoverPendingMLSGroupJoinsUseCase,
            recoverPendingOneOnOneResolutionsUseCase = recoverPendingOneOnOneResolutionsUseCase,
        )

    @Provides
    @LegacyUserBinding("isWireCellsEnabled")
    fun provideUserIsWireCellsEnabled(
        @LegacyUserBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
    ): IsWireCellsEnabledUseCase =
        IsWireCellsEnabledUseCaseImpl(
            userConfigRepository = userConfigRepository,
        )

    @Provides
    @LegacyUserBinding("observeIsWireCellsEnabled")
    fun provideUserObserveIsWireCellsEnabled(
        @LegacyUserBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
    ): ObserveIsWireCellsEnabledUseCase =
        ObserveIsWireCellsEnabledUseCaseImpl(
            userConfigRepository = userConfigRepository,
        )

    @Provides
    @LegacyUserBinding("isWireCellsEnabledForConversation")
    fun provideUserIsWireCellsEnabledForConversation(
        @LegacyUserBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): IsWireCellsEnabledForConversationUseCase =
        IsWireCellsEnabledForConversationUseCaseImpl(
            conversationRepository = conversationRepository
        )

    @Provides
    @LegacyUserBinding("isProfileQRCodeEnabled")
    fun provideUserIsProfileQRCodeEnabled(
        @LegacyUserBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
    ): IsProfileQRCodeEnabledUseCase =
        IsProfileQRCodeEnabledUseCaseImpl(
            userConfigRepository = userConfigRepository,
        )

    @Provides
    @LegacyUserBinding("assetAuditLog")
    fun provideUserAssetAuditLog(
        @LegacyUserBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
        @LegacyUserBinding("serverConfigRepository") serverConfigRepository: ServerConfigRepository,
        @LegacyUserBinding("selfUserId") selfUserId: UserId,
    ): AssetAuditFeatureHandler =
        AssetAuditFeatureHandlerImpl(
            userId = selfUserId,
            userConfigRepository = userConfigRepository,
            serverConfigRepository = serverConfigRepository,
        )
}
