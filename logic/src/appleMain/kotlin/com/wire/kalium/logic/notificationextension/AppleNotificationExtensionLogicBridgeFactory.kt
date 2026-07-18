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

@file:Suppress("LongParameterList")

package com.wire.kalium.logic.notificationextension

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.MlsTransportResponse
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.CryptoTransactionProviderImpl
import com.wire.kalium.logic.data.client.MLSClientProviderImpl
import com.wire.kalium.logic.data.client.MLSTransportProvider
import com.wire.kalium.logic.data.client.ProteusClientProviderImpl
import com.wire.kalium.logic.data.client.ProteusMigrationRecoveryHandler
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.EpochChangesObserverImpl
import com.wire.kalium.logic.data.featureConfig.FeatureConfigDataSource
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactoryImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.network.api.model.UserId as NetworkUserId
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.utils.MockUnboundNetworkClient
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkApis
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Apple-only spike assembly for the exact account resources needed by the NSE.
 *
 * This intentionally does not construct [com.wire.kalium.logic.feature.UserSessionScope]: that
 * scope owns the foreground application's migrations, continuous sync, recovery, schedulers,
 * receipts and calling lifecycle. Outbound MLS transport and Proteus migration recovery are also
 * fail-closed here because an NSE receive attempt must not initiate application repair traffic.
 */
internal class AppleNotificationExtensionLogicBridgeFactory(
    private val rootPath: String,
    private val keychainConfig: ApplePersistenceConfig,
    private val kaliumConfigs: KaliumConfigs,
    private val userAgent: String,
    private val globalPreferences: GlobalPrefProvider,
    private val sessionRepository: SessionRepository,
    private val rootPathsProvider: RootPathsProvider,
    private val userStorageProvider: UserStorageProvider,
    private val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider
) {
    fun create(userId: UserId): NotificationExtensionLogicBridge {
        val userStorage = createUserStorage(userId)
        val currentClientId = createCurrentClientIdProvider(userStorage)
        val authenticatedNetwork = createAuthenticatedNetwork(userId, currentClientId)
        val userConfigRepository = UserConfigDataSource(
            userStorage.database.userPrefsDAO,
            userStorage.database.userConfigDAO,
            kaliumConfigs
        )
        val crypto = createCryptoResources(
            userId,
            currentClientId,
            authenticatedNetwork,
            userConfigRepository
        )
        val avsIdentifier = AppleNotificationExtensionAvsIdentifier(userId, sessionRepository)
        return NotificationExtensionLogicBridge(
            selfUserId = userId,
            currentClientId = currentClientId::invoke,
            notificationApi = authenticatedNetwork.notificationApi,
            cryptoTransactionProvider = crypto.transactionProvider,
            conversationMlsGroupId = { conversationId ->
                val protocol = userStorage.database.conversationDAO.getConversationProtocolInfo(conversationId.toDao())
                (protocol as? ConversationEntity.ProtocolInfo.MLSCapable)?.groupId
            },
            conversationCallType = { conversationId ->
                resolveCallConversationType(userStorage, userConfigRepository, conversationId)
            },
            avsIdentifier = avsIdentifier::format,
            closeResources = crypto.processingScope::cancel
        )
    }

    private fun createUserStorage(userId: UserId): UserStorage =
        userStorageProvider.getOrCreate(
            userId = userId,
            platformUserStorageProperties = PlatformUserStorageProperties(
                rootPath = rootPath,
                keychainConfig = keychainConfig,
                rootStoragePath = "${rootPathsProvider.rootAccountPath(userId)}/storage"
            ),
            shouldEncryptData = kaliumConfigs.shouldEncryptData(),
            dbInvalidationControlEnabled = kaliumConfigs.dbInvalidationControlEnabled
        )

    private fun createCurrentClientIdProvider(userStorage: UserStorage): CurrentClientIdProvider {
        val clientRegistrationStorage = ClientRegistrationStorageImpl(userStorage.database.metadataDAO)
        return CurrentClientIdProvider {
            when (val result = wrapStorageNullableRequest { clientRegistrationStorage.getRegisteredClientId() }) {
                is Either.Left -> result
                is Either.Right -> result.value?.let { Either.Right(ClientId(it)) }
                    ?: Either.Left(CoreFailure.MissingClientRegistration)
            }
        }
    }

    private fun createAuthenticatedNetwork(
        userId: UserId,
        currentClientId: CurrentClientIdProvider
    ): AuthenticatedNetworkContainer {
        val sessionManager = SessionManagerImpl(
            sessionRepository = sessionRepository,
            accessTokenRefresherFactory = AccessTokenRefresherFactoryImpl(
                userId = userId,
                tokenStorage = globalPreferences.authTokenStorage
            ),
            userId = userId,
            currentClientIdProvider = currentClientId,
            tokenStorage = globalPreferences.authTokenStorage,
            // An NSE reports auth failure to the host. It never logs out or wipes an account.
            logout = {}
        )
        return userAuthenticatedNetworkProvider.getOrCreate(userId.toApi()) {
            UserAuthenticatedNetworkApis(
                AuthenticatedNetworkContainer.create(
                    sessionManager = sessionManager,
                    nomadServiceUrl = sessionManager.nomadServiceUrl()?.takeIf(String::isNotBlank),
                    selfUserId = NetworkUserId(userId.value, userId.domain),
                    userAgent = userAgent,
                    certificatePinning = kaliumConfigs.certPinningConfig,
                    mockEngine = kaliumConfigs.mockedRequests?.let(MockUnboundNetworkClient::createMockEngine),
                    mockWebSocketSession = kaliumConfigs.mockedWebSocket?.session,
                    kaliumLogger = kaliumLogger
                )
            )
        }.container
    }

    private fun createCryptoResources(
        userId: UserId,
        currentClientId: CurrentClientIdProvider,
        authenticatedNetwork: AuthenticatedNetworkContainer,
        userConfigRepository: UserConfigDataSource
    ): NotificationExtensionCryptoResources {
        val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val mlsClientProvider = MLSClientProviderImpl(
            rootKeyStorePath = rootPathsProvider.rootMLSPath(userId),
            userId = userId,
            currentClientIdProvider = currentClientId,
            passphraseStorage = globalPreferences.passphraseStorage,
            userConfigRepository = userConfigRepository,
            featureConfigRepository = FeatureConfigDataSource(authenticatedNetwork.featureConfigApi),
            mlsTransportProvider = PassiveNotificationExtensionMlsTransport,
            epochObserver = EpochChangesObserverImpl(),
            processingScope = processingScope
        )
        val proteusClientProvider = ProteusClientProviderImpl(
            rootProteusPath = rootPathsProvider.rootProteusPath(userId),
            userId = userId,
            passphraseStorage = globalPreferences.passphraseStorage,
            currentClientIdProvider = currentClientId,
            proteusMigrationRecoveryHandler = PassiveNotificationExtensionProteusRecovery
        )
        return NotificationExtensionCryptoResources(
            transactionProvider = CryptoTransactionProviderImpl(
                mlsClientProvider = mlsClientProvider,
                proteusClientProvider = proteusClientProvider
            ),
            processingScope = processingScope
        )
    }

    private suspend fun resolveCallConversationType(
        userStorage: UserStorage,
        userConfigRepository: UserConfigDataSource,
        conversationId: QualifiedID
    ): Int {
        val conversation = userStorage.database.conversationDAO.getConversationById(conversationId.toDao())
        if (conversation == null) return AVS_CONVERSATION_TYPE_UNKNOWN
        return when {
            conversation.type == ConversationEntity.Type.ONE_ON_ONE -> {
                when (val result = userConfigRepository.shouldUseSFTForOneOnOneCalls()) {
                    is Either.Left -> AVS_CONVERSATION_TYPE_UNKNOWN
                    is Either.Right -> if (result.value) {
                        conversation.protocolInfo.toConferenceCallType()
                    } else {
                        AVS_CONVERSATION_TYPE_ONE_ON_ONE
                    }
                }
            }

            conversation.type.isGroup -> conversation.protocolInfo.toConferenceCallType()
            else -> AVS_CONVERSATION_TYPE_UNKNOWN
        }
    }
}

private class AppleNotificationExtensionAvsIdentifier(
    private val selfUserId: UserId,
    private val sessionRepository: SessionRepository
) {
    private var federationResolved: Boolean = false
    private var federationEnabled: Boolean = false

    suspend fun format(id: QualifiedID): String {
        if (!federationResolved) {
            federationEnabled = when (val result = sessionRepository.isFederated(selfUserId)) {
                is Either.Left -> false
                is Either.Right -> result.value
            }
            federationResolved = true
        }
        return if (federationEnabled && id.domain.isNotEmpty()) id.toString() else id.value
    }
}

private fun ConversationEntity.ProtocolInfo.toConferenceCallType(): Int = when (this) {
    is ConversationEntity.ProtocolInfo.MLS -> AVS_CONVERSATION_TYPE_CONFERENCE_MLS
    is ConversationEntity.ProtocolInfo.Mixed,
    ConversationEntity.ProtocolInfo.Proteus -> AVS_CONVERSATION_TYPE_CONFERENCE
}

private data class NotificationExtensionCryptoResources(
    val transactionProvider: CryptoTransactionProvider,
    val processingScope: CoroutineScope
)

private object PassiveNotificationExtensionMlsTransport : MLSTransportProvider {
    override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse =
        MlsTransportResponse.Abort(PASSIVE_TRANSPORT_REASON)

    override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse =
        MlsTransportResponse.Abort(PASSIVE_TRANSPORT_REASON)
}

private object PassiveNotificationExtensionProteusRecovery : ProteusMigrationRecoveryHandler {
    override suspend fun clearClientData(clearLocalFiles: suspend () -> Unit) = Unit
}

private const val PASSIVE_TRANSPORT_REASON = "notification-extension-receive-only"
private const val AVS_CONVERSATION_TYPE_ONE_ON_ONE = 0
private const val AVS_CONVERSATION_TYPE_CONFERENCE = 2
private const val AVS_CONVERSATION_TYPE_CONFERENCE_MLS = 3
private const val AVS_CONVERSATION_TYPE_UNKNOWN = -1
