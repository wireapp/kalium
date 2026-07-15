@file:OptIn(com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class)
@file:Suppress("TooGenericExceptionCaught")

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.logic.service

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.MlsTransportResponse
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.network.api.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.authenticated.client.MLSPublicKeyTypeDTO
import com.wire.kalium.network.api.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.authenticated.client.UpdateClientMlsPublicKeysRequest
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Input for registering a fresh, dedicated headless Wire client. */
@ExperimentalKaliumServiceApi
@Suppress("LongParameterList")
public class WireServiceClientProvisioningConfig(
    public val userId: com.wire.kalium.logic.data.id.QualifiedID,
    public val backendDomain: String,
    public val session: SessionDTO,
    public val password: String,
    public val serverConfig: ServerConfigDTO,
    public val cryptoStorage: ServiceCryptoStorage,
    public val userAgent: String,
    public val certificatePinning: CertificatePinning = emptyMap(),
    public val clientLabel: String = "kalium-headless-service",
    public val clientModel: String = "Kalium headless service",
    public val secondFactorVerificationCode: String? = null,
    public val provisionMls: Boolean = true,
    public val proteusPreKeyCount: Int = 100,
    public val mlsKeyPackageCount: Int = 100,
) {
    init {
        require(backendDomain.isNotBlank()) { "backendDomain must not be blank" }
        require(password.isNotBlank()) { "password must not be blank" }
        require(userAgent.isNotBlank()) { "userAgent must not be blank" }
        require(clientLabel.isNotBlank()) { "clientLabel must not be blank" }
        require(clientModel.isNotBlank()) { "clientModel must not be blank" }
        require(proteusPreKeyCount > 0) { "proteusPreKeyCount must be positive" }
        require(mlsKeyPackageCount > 0) { "mlsKeyPackageCount must be positive" }
        require(cryptoStorage.mode == ServiceCryptoStorageMode.CREATE_NEW) {
            "Fresh client provisioning requires CREATE_NEW crypto storage"
        }
    }
}

/** Result of fresh client registration and initial Proteus/MLS publication. */
@ExperimentalKaliumServiceApi
public sealed interface WireServiceClientProvisioningResult {
    public class Success(
        public val identity: ServiceIdentity,
        public val session: SessionDTO,
        public val defaultCipherSuite: MLSCiphersuite,
    ) :
        WireServiceClientProvisioningResult

    public class Failure(public val description: String, public val cause: Throwable? = null) :
        WireServiceClientProvisioningResult
}

/** Registers a new backend client and prepares its durable CoreCrypto stores. */
@ExperimentalKaliumServiceApi
public object WireServiceClientProvisioner {
    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    public suspend fun provision(config: WireServiceClientProvisioningConfig): WireServiceClientProvisioningResult {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sessions = ProvisioningSessionManager(config.session, config.serverConfig)
        var network: AuthenticatedNetworkContainer? = null
        var proteusCentral: CoreCryptoCentral? = null
        var proteus: ProteusClient? = null
        var mlsCentral: CoreCryptoCentral? = null
        var mls: MLSClient? = null
        var registeredClientId: String? = null
        var stage = "validating fresh crypto storage"
        return try {
            requireFreshStorage(config.cryptoStorage)
            stage = "creating authenticated registration transport"
            network = AuthenticatedNetworkContainer.create(
                sessionManager = sessions,
                selfUserId = com.wire.kalium.network.api.model.QualifiedID(config.userId.value, config.userId.domain),
                userAgent = config.userAgent,
                certificatePinning = config.certificatePinning,
                mockEngine = null,
                mockWebSocketSession = null,
                kaliumLogger = KaliumLogger.disabled(),
            )

            stage = "synchronizing backend feature configuration"
            val features = network.featureConfigApi.featureConfigs()
                .valueOrThrow("Backend feature-configuration sync failed")
            val consumableNotificationsAllowed =
                config.serverConfig.metaData.commonApiVersion.version >= MIN_CONSUMABLE_NOTIFICATIONS_API_VERSION &&
                    features.consumableNotifications?.status == FeatureFlagStatusDTO.ENABLED
            stage = "validating MLS registration support"
            val mlsFeature = features.mls
            val mlsFeatureEnabled = mlsFeature?.status == FeatureFlagStatusDTO.ENABLED &&
                SupportedProtocolDTO.MLS in mlsFeature.config.supportedProtocols
            val backendMlsKeysAvailable = mlsFeatureEnabled &&
                network.mlsPublicKeyApi.getMLSPublicKeys() is NetworkResponse.Success
            val registerMls = mlsFeatureEnabled && backendMlsKeysAvailable
            provisionRequire(
                !config.provisionMls || registerMls,
                "an MLS target is configured but the backend does not allow MLS client registration",
            )
            provisionRequire(
                !(registerMls && features.mlsE2EI?.status == FeatureFlagStatusDTO.ENABLED),
                "the backend requires E2EI enrollment, which this test provisioner does not support",
            )
            val allowedCipherSuites = if (registerMls) {
                mlsFeature.config.allowedCipherSuites.map { it.toMlsCipherSuite() }
            } else {
                config.cryptoStorage.allowedCipherSuites
            }
            val defaultCipherSuite = if (registerMls) {
                mlsFeature.config.defaultCipherSuite.toMlsCipherSuite()
            } else {
                config.cryptoStorage.defaultCipherSuite
            }
            provisionRequire(
                defaultCipherSuite in allowedCipherSuites,
                "the backend MLS default ciphersuite is not in its allowed ciphersuite list",
            )

            stage = "generating Proteus prekeys"
            proteusCentral = coreCryptoCentral(
                config.cryptoStorage.proteusRoot.toString(),
                config.cryptoStorage.proteusPassphrase,
            )
            proteus = proteusCentral.proteusClient()
            val preKeys = proteus.newPreKeys(FIRST_PREKEY_ID, config.proteusPreKeyCount)
            val lastKey = proteus.newLastResortPreKey()
            stage = "registering the Wire client"
            val registered = network.clientApi.registerClient(
                RegisterClientRequest(
                    password = config.password,
                    preKeys = preKeys.map { PreKeyDTO(it.id, it.pkb) },
                    lastKey = PreKeyDTO(lastKey.id, lastKey.pkb),
                    deviceType = DeviceTypeDTO.Desktop,
                    type = ClientTypeDTO.Permanent,
                    label = config.clientLabel,
                    capabilities = buildList {
                        add(ClientCapabilityDTO.LegalHoldImplicitConsent)
                        if (consumableNotificationsAllowed) add(ClientCapabilityDTO.ConsumableNotifications)
                    },
                    model = config.clientModel,
                    cookieLabel = sessions.currentSession().cookieLabel,
                    secondFactorVerificationCode = config.secondFactorVerificationCode,
                ),
            ).valueOrThrow("Wire client registration failed")
            registeredClientId = registered.clientId
            val identity = ServiceIdentity(config.userId, registered.clientId, config.backendDomain)

            stage = "initializing the MLS client"
            mlsCentral = coreCryptoCentral(
                config.cryptoStorage.mlsRoot.toString(),
                config.cryptoStorage.mlsPassphrase,
            )
            mls = mlsCentral.mlsClient(
                clientId = CryptoQualifiedClientId(
                    registered.clientId,
                    CryptoQualifiedID(config.userId.value, config.userId.domain),
                ),
                allowedCipherSuites = allowedCipherSuites,
                defaultCipherSuite = defaultCipherSuite,
                mlsTransporter = ProvisioningMlsTransporter,
                epochObserver = ProvisioningEpochObserver,
                coroutineScope = scope,
            )
            if (registerMls) {
                stage = "publishing the MLS public key"
                val (publicKey, cipherSuite) = mls.getPublicKey()
                network.clientApi.updateClientMlsPublicKeys(
                    UpdateClientMlsPublicKeysRequest(mapOf(cipherSuite.signatureAlgorithm() to Base64.encode(publicKey))),
                    registered.clientId,
                ).valueOrThrow("MLS public-key registration failed")
                stage = "uploading MLS key packages"
                val keyPackages = mls.transaction("provisionServiceKeyPackages") { context ->
                    context.generateKeyPackages(config.mlsKeyPackageCount).map(Base64::encode)
                }
                network.keyPackageApi.uploadKeyPackages(registered.clientId, keyPackages)
                    .valueOrThrow("MLS key-package upload failed")
            }

            stage = "upgrading the login session with the registered client ID"
            sessions.upgrade(network.accessTokenApi, registered.clientId)
            network.clearCachedToken()
            stage = "binding local crypto storage to the registered identity"
            bindProvisionedServiceStorage(config.cryptoStorage.proteusRoot, identity)
            bindProvisionedServiceStorage(config.cryptoStorage.mlsRoot, identity)
            WireServiceClientProvisioningResult.Success(identity, sessions.currentSession(), defaultCipherSuite)
        } catch (failure: Throwable) {
            registeredClientId?.let { clientId ->
                try {
                    network?.clientApi?.deleteClient(config.password, clientId)
                } catch (_: Throwable) {
                    // The original provisioning failure remains authoritative.
                }
            }
            WireServiceClientProvisioningResult.Failure(
                "Unable to provision the Wire service client while $stage (${failure.safeProvisioningSummary()})",
                failure,
            )
        } finally {
            try {
                mls?.close() ?: mlsCentral?.close()
            } catch (_: Throwable) {
                // Provisioning already has an authoritative result.
            }
            try {
                proteus?.close() ?: proteusCentral?.close()
            } catch (_: Throwable) {
                // Provisioning already has an authoritative result.
            }
            try {
                network?.close()
            } catch (_: Throwable) {
                // Provisioning already has an authoritative result.
            }
            scope.cancel()
        }
    }

    private fun requireFreshStorage(storage: ServiceCryptoStorage) {
        listOf(storage.proteusRoot, storage.mlsRoot).forEach { root ->
            require(!Files.exists(root.resolve(SERVICE_KEYSTORE_FILE))) {
                "Fresh client provisioning requires an empty crypto directory"
            }
            require(!Files.exists(root.resolve(SERVICE_IDENTITY_MARKER_FILE))) {
                "Fresh client provisioning found an existing service identity"
            }
        }
    }

    private fun MLSCiphersuite.signatureAlgorithm(): MLSPublicKeyTypeDTO = when (this) {
        MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 ->
            MLSPublicKeyTypeDTO.ECDSA_SECP256R1_SHA256
        MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
        MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 ->
            MLSPublicKeyTypeDTO.ED25519
        MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 ->
            MLSPublicKeyTypeDTO.ECDSA_SECP384R1_SHA384
        MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 ->
            MLSPublicKeyTypeDTO.ECDSA_SECP521R1_SHA512
        MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448,
        MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 ->
            MLSPublicKeyTypeDTO.ED448
    }

    @Suppress("MagicNumber")
    private fun Int.toMlsCipherSuite(): MLSCiphersuite = when (this) {
        1 -> MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        2 -> MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
        3 -> MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
        4 -> MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
        5 -> MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
        6 -> MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
        7 -> MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
        else -> error("Unsupported backend MLS ciphersuite tag: $this")
    }

    private fun <Value : Any> NetworkResponse<Value>.valueOrThrow(description: String): Value = when (this) {
        is NetworkResponse.Error -> throw IllegalStateException("$description (${kException.safeSummary()})", kException)
        is NetworkResponse.Success -> value
    }

    private fun KaliumException.safeSummary(): String = when (this) {
        is KaliumException.InvalidRequestError -> "HTTP ${errorResponse.code} ${errorResponse.label}"
        is KaliumException.RedirectError -> "HTTP ${errorResponse.code} ${errorResponse.label}"
        is KaliumException.ServerError -> "HTTP ${errorResponse.code} ${errorResponse.label}"
        is KaliumException.Unauthorized -> "HTTP $errorCode unauthorized"
        else -> this::class.simpleName ?: "network error"
    }

    private fun Throwable.safeProvisioningSummary(): String {
        var current: Throwable? = this
        var summary: String? = null
        while (current != null && summary == null) {
            summary = when (current) {
                is KaliumException -> current.safeSummary()
                is ProvisioningConstraintException -> current.safeDescription
                else -> null
            }
            current = current.cause
        }
        return summary ?: this::class.simpleName ?: "provisioning error"
    }

    private fun provisionRequire(condition: Boolean, safeDescription: String) {
        if (!condition) throw ProvisioningConstraintException(safeDescription)
    }

    private const val FIRST_PREKEY_ID = 0
    private const val MIN_CONSUMABLE_NOTIFICATIONS_API_VERSION = 11
}

private class ProvisioningConstraintException(val safeDescription: String) : IllegalStateException(safeDescription)

private class ProvisioningSessionManager(
    initialSession: SessionDTO,
    private val config: ServerConfigDTO,
) : SessionManager {
    private var session = initialSession

    override suspend fun session(): SessionDTO = session

    fun currentSession(): SessionDTO = session

    suspend fun upgrade(accessTokenApi: AccessTokenApi, clientId: String) {
        refresh(accessTokenApi, session.refreshToken, clientId)
    }

    override fun serverConfig(): ServerConfigDTO = config

    override fun nomadServiceUrl(): String? = null

    override fun proxyCredentials(): ProxyCredentialsDTO? = null

    override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String?): SessionDTO {
        val refreshToken = oldRefreshToken ?: throw FailureToRefreshTokenException("The refresh token is missing")
        return refresh(accessTokenApi, refreshToken, clientId = null)
    }

    private suspend fun refresh(accessTokenApi: AccessTokenApi, refreshToken: String, clientId: String?): SessionDTO {
        val refreshed = when (val response = accessTokenApi.getToken(refreshToken, clientId)) {
            is NetworkResponse.Error -> throw FailureToRefreshTokenException("Failed to refresh provisioning session", response.kException)
            is NetworkResponse.Success -> response.value
        }
        session = SessionDTO(
            userId = session.userId,
            tokenType = refreshed.first.tokenType,
            accessToken = refreshed.first.value,
            refreshToken = refreshed.second?.value ?: session.refreshToken,
            cookieLabel = session.cookieLabel,
        )
        return session
    }
}

private object ProvisioningMlsTransporter : MLSTransporter {
    override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse =
        MlsTransportResponse.Abort("MLS transport is unavailable during client provisioning")

    override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse =
        MlsTransportResponse.Abort("MLS transport is unavailable during client provisioning")
}

private object ProvisioningEpochObserver : MLSEpochObserver {
    override suspend fun onEpochChange(groupId: com.wire.kalium.cryptography.MLSGroupId, epoch: ULong) = Unit
}
