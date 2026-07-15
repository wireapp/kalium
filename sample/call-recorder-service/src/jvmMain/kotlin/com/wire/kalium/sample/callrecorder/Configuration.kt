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
import com.wire.kalium.logic.service.ServiceCryptoStorage
import com.wire.kalium.logic.service.ServiceCryptoStorageMode
import com.wire.kalium.logic.service.ServiceCrlDistributionPointHandler
import com.wire.kalium.logic.service.WireKaliumServiceConfig
import com.wire.kalium.logic.service.api.ServiceConfig
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedId
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import java.nio.file.Path
import java.util.Base64

internal data class ApplicationOptions(
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
    suspend fun load(options: ApplicationOptions, environment: Map<String, String> = System.getenv()): LoadedConfiguration {
        val userId = qualifiedId(environment.required("WIRE_USER_ID"), environment.required("WIRE_USER_DOMAIN"))
        val backendDomain = environment["WIRE_BACKEND_DOMAIN"]?.takeIf(String::isNotBlank) ?: userId.domain
        val identity = ServiceIdentity(
            userId = userId,
            clientId = environment.required("WIRE_CLIENT_ID"),
            backendDomain = backendDomain,
        )
        val stateKey = environment.requiredBase64("WIRE_STATE_KEY_BASE64")
        val proteusPassphrase = environment.requiredBase64("WIRE_PROTEUS_PASSPHRASE_BASE64")
        val mlsPassphrase = environment.requiredBase64("WIRE_MLS_PASSPHRASE_BASE64")
        val stateStore = try {
            EncryptedJvmServiceStateStore(
                root = environment.path("WIRE_STATE_DIR", "./call-recorder-state"),
                identity = identity,
                key = stateKey,
            )
        } catch (failure: Throwable) {
            listOf(stateKey, proteusPassphrase, mlsPassphrase).forEach { it.fill(0) }
            throw failure
        }

        try {
            ensureSession(stateStore, identity, environment)
            val storageRoot = environment.path("WIRE_CRYPTO_DIR", "./call-recorder-crypto").toAbsolutePath().normalize()
            val defaultCipherSuite = environment["WIRE_MLS_CIPHERSUITE"]?.let(MLSCiphersuite::valueOf)
                ?: MLSCiphersuite.DEFAULT
            val config = WireKaliumServiceConfig(
                service = ServiceConfig(identity, maxConcurrentCalls = 1, options.shutdownTimeoutMillis),
                stateStore = stateStore,
                cryptoStorage = ServiceCryptoStorage(
                    proteusRoot = storageRoot.resolve("proteus"),
                    proteusPassphrase = proteusPassphrase,
                    mlsRoot = storageRoot.resolve("mls"),
                    mlsPassphrase = mlsPassphrase,
                    mode = environment.cryptoMode(),
                    allowedCipherSuites = MLSCiphersuite.entries,
                    defaultCipherSuite = defaultCipherSuite,
                ),
                serverConfig = environment.serverConfig(backendDomain),
                userAgent = environment["WIRE_USER_AGENT"]?.takeIf(String::isNotBlank) ?: DEFAULT_USER_AGENT,
                certificatePinning = emptyMap(),
                selfConversationTargets = environment.selfConversationTargets(),
                selfUserTeamId = environment["WIRE_TEAM_ID"]?.takeIf(String::isNotBlank),
                crlHandler = RejectUnverifiedCrlPoints,
                callEventSink = CallEventSink { CallingResult.Success },
                notificationOpenTimeoutMillis = options.notificationOpenTimeoutMillis,
                avsReadyTimeoutMillis = environment.positiveSeconds(
                    "WIRE_AVS_READY_TIMEOUT_SECONDS",
                    DEFAULT_AVS_READY_TIMEOUT_SECONDS,
                ) * MILLIS_PER_SECOND,
                audioCbr = environment.boolean("WIRE_AUDIO_CBR", false),
            )
            return LoadedConfiguration(config, stateStore, listOf(stateKey, proteusPassphrase, mlsPassphrase))
        } catch (failure: Throwable) {
            listOf(stateKey, proteusPassphrase, mlsPassphrase).forEach { it.fill(0) }
            stateStore.close()
            throw failure
        }
    }

    private suspend fun ensureSession(
        store: EncryptedJvmServiceStateStore,
        identity: ServiceIdentity,
        environment: Map<String, String>,
    ) {
        when (val existing = store.loadSession()) {
            is EncryptedServiceStateResult.Failure -> error(existing.description)
            is EncryptedServiceStateResult.Success -> if (existing.value == null) {
                val session = SessionDTO(
                    userId = NetworkQualifiedId(identity.userId.value, identity.userId.domain),
                    tokenType = environment["WIRE_TOKEN_TYPE"]?.takeIf(String::isNotBlank) ?: "Bearer",
                    accessToken = environment.required("WIRE_ACCESS_TOKEN"),
                    refreshToken = environment.required("WIRE_REFRESH_TOKEN"),
                    cookieLabel = environment["WIRE_COOKIE_LABEL"]?.takeIf(String::isNotBlank),
                )
                when (val saved = store.saveSession(session)) {
                    is EncryptedServiceStateResult.Failure -> error(saved.description)
                    is EncryptedServiceStateResult.Success -> Unit
                }
            }
        }
    }

    private fun Map<String, String>.serverConfig(backendDomain: String): ServerConfigDTO {
        val api = required("WIRE_API_URL")
        return ServerConfigDTO(
            id = this["WIRE_SERVER_CONFIG_ID"]?.takeIf(String::isNotBlank) ?: backendDomain,
            links = ServerConfigDTO.Links(
                api = api,
                accounts = this["WIRE_ACCOUNTS_URL"]?.takeIf(String::isNotBlank) ?: api,
                webSocket = required("WIRE_WEBSOCKET_URL"),
                blackList = this["WIRE_BLACKLIST_URL"]?.takeIf(String::isNotBlank) ?: api,
                teams = this["WIRE_TEAMS_URL"]?.takeIf(String::isNotBlank) ?: api,
                website = this["WIRE_WEBSITE_URL"]?.takeIf(String::isNotBlank) ?: api,
                title = this["WIRE_BACKEND_TITLE"]?.takeIf(String::isNotBlank) ?: "Wire",
                isOnPremises = boolean("WIRE_ON_PREMISES", true),
                apiProxy = null,
            ),
            metaData = ServerConfigDTO.MetaData(
                federation = boolean("WIRE_FEDERATION", true),
                commonApiVersion = ApiVersionDTO.Valid(this["WIRE_API_VERSION"]?.toIntOrNull() ?: DEFAULT_API_VERSION),
                domain = backendDomain,
            ),
        )
    }

    @Suppress("MagicNumber")
    private fun Map<String, String>.selfConversationTargets(): List<SelfConversationTarget> =
        required("WIRE_SELF_CONVERSATIONS").split(',').map { entry ->
            val fields = entry.trim().split('|')
            require(fields.size >= 2) { "Invalid WIRE_SELF_CONVERSATIONS entry" }
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

    private fun Map<String, String>.cryptoMode(): ServiceCryptoStorageMode = when (
        this["WIRE_CRYPTO_MODE"]?.uppercase() ?: "RESTORE_EXISTING"
    ) {
        "CREATE_NEW" -> ServiceCryptoStorageMode.CREATE_NEW
        "RESTORE_EXISTING" -> ServiceCryptoStorageMode.RESTORE_EXISTING
        else -> error("WIRE_CRYPTO_MODE must be CREATE_NEW or RESTORE_EXISTING")
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

    private fun Map<String, String>.required(name: String): String =
        this[name]?.takeIf(String::isNotBlank) ?: error("Required environment variable $name is missing")

    private fun Map<String, String>.requiredBase64(name: String): ByteArray = try {
        Base64.getDecoder().decode(required(name)).also { require(it.isNotEmpty()) { "$name must not decode to empty" } }
    } catch (failure: IllegalArgumentException) {
        throw IllegalArgumentException("$name must contain valid Base64", failure)
    }

    private fun Map<String, String>.path(name: String, default: String): Path =
        Path.of(this[name]?.takeIf(String::isNotBlank) ?: default)

    private fun Map<String, String>.boolean(name: String, default: Boolean): Boolean =
        this[name]?.let { value ->
            when (value.lowercase()) {
                "true" -> true
                "false" -> false
                else -> error("$name must be true or false")
            }
        } ?: default

    private fun Map<String, String>.positiveSeconds(name: String, default: Long): Long =
        (this[name]?.toLongOrNull() ?: default).also { require(it > 0) { "$name must be positive" } }

    private object RejectUnverifiedCrlPoints : ServiceCrlDistributionPointHandler {
        override suspend fun handle(distributionPoints: List<String>): CallingResult =
            if (distributionPoints.isEmpty()) {
                CallingResult.Success
            } else {
                CallingResult.Failure(
                    CallingFailure.Crypto("E2EI CRL verification is not configured for the recorder service"),
                )
            }
    }

    private const val DEFAULT_API_VERSION = 12
    private const val DEFAULT_AVS_READY_TIMEOUT_SECONDS = 30L
    private const val DEFAULT_USER_AGENT = "Kalium-Call-Recorder/experimental"
    private const val MILLIS_PER_SECOND = 1_000L
}
