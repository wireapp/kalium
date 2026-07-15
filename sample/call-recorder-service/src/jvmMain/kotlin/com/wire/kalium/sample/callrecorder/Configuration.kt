@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)
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

package com.wire.kalium.sample.callrecorder

import com.wire.kalium.calling.runtime.CallEventSink
import com.wire.kalium.calling.runtime.CallingFailure
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.calling.runtime.SelfConversationTarget
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.service.EncryptedJvmServiceStateStore
import com.wire.kalium.logic.service.EncryptedServiceStateResult
import com.wire.kalium.logic.service.ServiceCrlDistributionPointHandler
import com.wire.kalium.logic.service.ServiceCryptoStorage
import com.wire.kalium.logic.service.ServiceCryptoStorageMode
import com.wire.kalium.logic.service.WireKaliumServiceConfig
import com.wire.kalium.logic.service.WireServiceClientProvisioner
import com.wire.kalium.logic.service.WireServiceClientProvisioningConfig
import com.wire.kalium.logic.service.WireServiceClientProvisioningResult
import com.wire.kalium.logic.service.api.ServiceConfig
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unauthenticated.login.LoginParam
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.Url
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.UUID

internal data class ApplicationOptions(
    val configFile: Path,
    val recordingsDirectory: Path,
    val shutdownTimeoutMillis: Long,
    val notificationOpenTimeoutMillis: Long,
)

internal class LoadedConfiguration(
    val wire: WireKaliumServiceConfig,
    val stateStore: EncryptedJvmServiceStateStore,
    private val sensitiveBuffers: List<ByteArray>,
) : AutoCloseable {
    fun clearInputSecrets() {
        sensitiveBuffers.forEach { it.fill(0) }
    }

    override fun close() {
        clearInputSecrets()
        stateStore.close()
    }
}

internal object ConfigurationLoader {
    @Suppress("LongMethod", "ThrowsCount")
    suspend fun load(options: ApplicationOptions): LoadedConfiguration {
        val configFile = LocalTestingConfigFile(options.configFile)
        var local = configFile.load().withGeneratedSecrets().withNormalizedSelfConversations()
        configFile.save(local)
        val serverConfig = discoverServerConfig(local)
        local = local.withDiscoveredBackend(
            apiVersion = serverConfig.metaData.commonApiVersion.version,
            federation = serverConfig.metaData.federation,
        )
        configFile.save(local)
        var loginSession = authenticate(local, serverConfig)
        val userId = qualifiedId(loginSession.userId.value, loginSession.userId.domain)
        local.userId?.let { storedUserId ->
            require(storedUserId == userId.value && local.userDomain == userId.domain) {
                "Test config JSON belongs to a different Wire account"
            }
        }

        val stateKey = requiredBase64(local.stateKeyBase64, "stateKeyBase64")
        val proteusPassphrase = requiredBase64(local.proteusPassphraseBase64, "proteusPassphraseBase64")
        val mlsPassphrase = requiredBase64(local.mlsPassphraseBase64, "mlsPassphraseBase64")
        val sensitiveBuffers = listOf(stateKey, proteusPassphrase, mlsPassphrase)
        val storageRoot = Path.of(local.cryptoDir).toAbsolutePath().normalize()
        if (local.clientId == null) archiveIncompleteCryptoStorage(storageRoot)
        var defaultCipherSuite = local.mlsCiphersuite?.let(MLSCiphersuite::valueOf) ?: MLSCiphersuite.DEFAULT
        val registrationStorage = ServiceCryptoStorage(
            proteusRoot = storageRoot.resolve("proteus"),
            proteusPassphrase = proteusPassphrase,
            mlsRoot = storageRoot.resolve("mls"),
            mlsPassphrase = mlsPassphrase,
            mode = ServiceCryptoStorageMode.CREATE_NEW,
            allowedCipherSuites = MLSCiphersuite.entries,
            defaultCipherSuite = defaultCipherSuite,
        )

        if (local.clientId == null) {
            when (
                val provisioned = WireServiceClientProvisioner.provision(
                    WireServiceClientProvisioningConfig(
                        userId = userId,
                        backendDomain = local.backendDomain,
                        session = loginSession,
                        password = local.password,
                        serverConfig = serverConfig,
                        cryptoStorage = registrationStorage,
                        userAgent = local.userAgent,
                        clientLabel = local.clientLabel,
                        clientModel = local.clientModel,
                        secondFactorVerificationCode = local.verificationCode,
                        provisionMls = requiresMlsProvisioning(local.selfConversations),
                    ),
                )
            ) {
                is WireServiceClientProvisioningResult.Failure -> {
                    sensitiveBuffers.forEach { it.fill(0) }
                    throw IllegalStateException(provisioned.description, provisioned.cause)
                }
                is WireServiceClientProvisioningResult.Success -> {
                    loginSession = provisioned.session
                    defaultCipherSuite = provisioned.defaultCipherSuite
                    local = local.withIdentity(
                        userId.value,
                        userId.domain,
                        provisioned.identity.clientId,
                        defaultCipherSuite.name,
                    )
                    configFile.save(local)
                }
            }
        }

        val identity = ServiceIdentity(
            userId = userId,
            clientId = checkNotNull(local.clientId) { "Client registration did not return an ID" },
            backendDomain = local.backendDomain,
        )
        val stateStore = try {
            EncryptedJvmServiceStateStore(Path.of(local.stateDir), identity, stateKey)
        } catch (failure: Throwable) {
            sensitiveBuffers.forEach { it.fill(0) }
            throw failure
        }
        try {
            saveSession(stateStore, identity, loginSession)
            val wire = WireKaliumServiceConfig(
                service = ServiceConfig(identity, maxConcurrentCalls = 1, options.shutdownTimeoutMillis),
                stateStore = stateStore,
                cryptoStorage = registrationStorage.copy(
                    mode = ServiceCryptoStorageMode.RESTORE_EXISTING,
                    defaultCipherSuite = defaultCipherSuite,
                ),
                serverConfig = serverConfig,
                userAgent = local.userAgent,
                certificatePinning = emptyMap(),
                selfConversationTargets = selfConversationTargets(local.selfConversations),
                selfUserTeamId = local.teamId,
                crlHandler = RejectUnverifiedCrlPoints,
                callEventSink = CallEventSink { CallingResult.Success },
                notificationOpenTimeoutMillis = options.notificationOpenTimeoutMillis,
                avsReadyTimeoutMillis = local.avsReadyTimeoutSeconds * MILLIS_PER_SECOND,
                audioCbr = local.audioCbr,
            )
            return LoadedConfiguration(wire, stateStore, sensitiveBuffers)
        } catch (failure: Throwable) {
            sensitiveBuffers.forEach { it.fill(0) }
            stateStore.close()
            throw failure
        }
    }

    private suspend fun saveSession(store: EncryptedJvmServiceStateStore, identity: ServiceIdentity, session: SessionDTO) {
        require(session.userId.value == identity.userId.value && session.userId.domain == identity.userId.domain) {
            "Authenticated session identity does not match the service identity"
        }
        when (val saved = store.saveSession(session)) {
            is EncryptedServiceStateResult.Failure -> error(saved.description)
            is EncryptedServiceStateResult.Success -> Unit
        }
    }

    private suspend fun authenticate(config: LocalTestingConfig, serverConfig: ServerConfigDTO): SessionDTO {
        val network = UnauthenticatedNetworkContainer.create(
            serverConfigDTO = serverConfig,
            proxyCredentials = null,
            userAgent = config.userAgent,
            developmentApiEnabled = false,
            certificatePinning = emptyMap(),
            mockEngine = null,
        )
        return try {
            when (
                val response = network.loginApi.login(
                    LoginParam.LoginWithEmail(
                        email = config.email,
                        password = config.password,
                        label = config.loginLabel,
                        verificationCode = config.verificationCode,
                    ),
                    persist = true,
                )
            ) {
                is NetworkResponse.Error -> error(
                    "Wire email/password login failed (${response.kException::class.simpleName ?: "network error"})",
                )
                is NetworkResponse.Success -> response.value.first
            }
        } finally {
            network.close()
        }
    }

    private suspend fun discoverServerConfig(config: LocalTestingConfig): ServerConfigDTO {
        val bootstrap = config.serverConfig()
        val network = createUnauthenticatedNetwork(config, bootstrap)
        return try {
            val discovered = when (val response = network.remoteVersion.fetchApiVersion(Url(config.apiUrl))) {
                is NetworkResponse.Error -> error(
                    "Wire API-version discovery failed (${response.kException.safeSummary()})",
                )
                is NetworkResponse.Success -> response.value
            }
            require(discovered.commonApiVersion is ApiVersionDTO.Valid) {
                "This Kalium build and the Wire backend have no supported API version in common"
            }
            bootstrap.copy(
                metaData = discovered.copy(domain = discovered.domain ?: config.backendDomain),
            )
        } finally {
            network.close()
        }
    }

    private fun createUnauthenticatedNetwork(
        config: LocalTestingConfig,
        serverConfig: ServerConfigDTO,
    ): UnauthenticatedNetworkContainer = UnauthenticatedNetworkContainer.create(
        serverConfigDTO = serverConfig,
        proxyCredentials = null,
        userAgent = config.userAgent,
        developmentApiEnabled = false,
        certificatePinning = emptyMap(),
        mockEngine = null,
    )

    private fun KaliumException.safeSummary(): String = when (this) {
        is KaliumException.InvalidRequestError -> "HTTP ${errorResponse.code} ${errorResponse.label}"
        is KaliumException.RedirectError -> "HTTP ${errorResponse.code} ${errorResponse.label}"
        is KaliumException.ServerError -> "HTTP ${errorResponse.code} ${errorResponse.label}"
        is KaliumException.Unauthorized -> "HTTP $errorCode unauthorized"
        else -> this::class.simpleName ?: "network error"
    }

    private fun LocalTestingConfig.serverConfig(): ServerConfigDTO = ServerConfigDTO(
        id = backendDomain,
        links = ServerConfigDTO.Links(
            api = apiUrl,
            accounts = accountsUrl ?: apiUrl,
            webSocket = webSocketUrl,
            blackList = blackListUrl ?: apiUrl,
            teams = teamsUrl ?: apiUrl,
            website = websiteUrl ?: apiUrl,
            title = "Wire",
            isOnPremises = onPremises,
            apiProxy = null,
        ),
        metaData = ServerConfigDTO.MetaData(
            federation = federation,
            commonApiVersion = ApiVersionDTO.Valid(apiVersion),
            domain = backendDomain,
        ),
    )

    @Suppress("MagicNumber")
    private fun selfConversationTargets(value: String): List<SelfConversationTarget> {
        if (value.isBlank() || value.equals("auto", ignoreCase = true) || '|' !in value) return emptyList()
        return value.split(',').map { entry ->
            val fields = entry.trim().split('|')
            require(fields.size >= 2) { "Invalid selfConversations entry; use auto for backend discovery" }
            val conversationId = parseQualifiedId(fields[0])
            val protocol = when (fields[1].lowercase()) {
                "proteus" -> CallConversationProtocol.Proteus
                "mls" -> {
                    require(fields.size in 3..4) { "MLS self conversation requires group ID and optional epoch" }
                    CallConversationProtocol.Mls(GroupID(fields[2]), fields.getOrNull(3)?.toULong())
                }
                else -> error("Unsupported self-conversation protocol")
            }
            SelfConversationTarget(conversationId, protocol)
        }
    }

    private fun requiresMlsProvisioning(value: String): Boolean = value.split(',').any { entry ->
        entry.trim().split('|').getOrNull(1)?.equals("mls", ignoreCase = true) == true
    }

    private fun requiredBase64(value: String?, name: String): ByteArray = try {
        Base64.getDecoder().decode(checkNotNull(value) { "$name is missing" }).also {
            require(it.size == KEY_SIZE_BYTES) { "$name must decode to $KEY_SIZE_BYTES bytes" }
        }
    } catch (failure: IllegalArgumentException) {
        throw IllegalArgumentException("$name must contain valid Base64", failure)
    }

    private fun archiveIncompleteCryptoStorage(root: Path) {
        if (!Files.exists(root)) return
        val markerExists = listOf("proteus", "mls").any { store ->
            Files.exists(root.resolve(store).resolve(".kalium-service-identity"))
        }
        require(!markerExists) {
            "Crypto storage contains a bound identity but the test JSON has no clientId"
        }
        val hasKeystore = listOf("proteus", "mls").any { store -> Files.exists(root.resolve(store).resolve("keystore")) }
        if (!hasKeystore) return
        val archive = root.resolveSibling("${root.fileName}.incomplete-${UUID.randomUUID()}")
        Files.move(root, archive)
        SafeLog.warn("Archived incomplete first-run crypto state; retrying client provisioning")
    }

    private fun qualifiedId(value: String, domain: String): QualifiedID {
        require(value.isNotBlank() && domain.isNotBlank()) { "Wire identity fields must not be blank" }
        return QualifiedID(value, domain)
    }

    private fun parseQualifiedId(value: String): QualifiedID {
        val separator = value.lastIndexOf('@')
        require(separator > 0 && separator < value.lastIndex) { "Expected a qualified ID in value@domain form" }
        return qualifiedId(value.substring(0, separator), value.substring(separator + 1))
    }

    private object RejectUnverifiedCrlPoints : ServiceCrlDistributionPointHandler {
        override suspend fun handle(distributionPoints: List<String>): CallingResult =
            if (distributionPoints.isEmpty()) CallingResult.Success else CallingResult.Failure(
                CallingFailure.Crypto("E2EI CRL verification is not configured for the recorder service"),
            )
    }

    private const val KEY_SIZE_BYTES = 32
    private const val MILLIS_PER_SECOND = 1_000L
}
