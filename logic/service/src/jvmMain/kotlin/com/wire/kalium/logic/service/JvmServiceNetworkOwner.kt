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
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.service

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.ServiceFailure
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.logic.service.api.ServiceResult
import com.wire.kalium.logic.service.api.ServiceSessionManager
import com.wire.kalium.network.api.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedId
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Identity-scoped owner of durable credentials and authenticated Wire HTTP/WebSocket clients.
 *
 * The supplied [sessionStore] is mandatory and must be durable. A store that also implements
 * [AutoCloseable] is closed after every owned transport during [close].
 */
@ExperimentalKaliumServiceApi
@Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList", "TooManyFunctions")
public class JvmServiceNetworkOwner(
    public val identity: ServiceIdentity,
    private val sessionStore: ServiceSessionStore,
    private val configuredServerConfig: ServerConfigDTO,
    private val userAgent: String,
    private val certificatePinning: CertificatePinning,
    private val kaliumLogger: KaliumLogger,
    private val configuredNomadServiceUrl: String? = null,
    private val configuredProxyCredentials: ProxyCredentialsDTO? = null,
) : ServiceSessionManager, SessionManager {
    private val lifecycleMutex = Mutex()
    private val sessionMutex = Mutex()

    @Volatile
    private var ownedNetwork: AuthenticatedNetworkContainer? = createNetwork()

    @Volatile
    private var started: Boolean = false

    @Volatile
    private var closed: Boolean = false

    private var currentSession: SessionDTO? = null
    private var sessionStoreClosed: Boolean = sessionStore !is AutoCloseable

    override suspend fun start(identity: ServiceIdentity): ServiceResult = lifecycleMutex.withLock {
        if (identity != this.identity) {
            return@withLock sessionFailure("The requested service identity does not match this network owner")
        }
        if (closed) return@withLock ServiceResult.Failure(ServiceFailure.Closed)
        if (started) return@withLock ServiceResult.Success

        val serverDomain = configuredServerConfig.metaData.domain
        if (serverDomain != null && serverDomain != identity.backendDomain) {
            return@withLock sessionFailure("The server configuration domain does not match the service identity")
        }

        val restoredResult = try {
            sessionStore.loadSession()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            return@withLock sessionFailure("Failed to restore the durable service session", failure)
        }
        val restored = when (restoredResult) {
            is EncryptedServiceStateResult.Failure -> return@withLock sessionFailure(
                restoredResult.description,
                restoredResult.cause,
            )
            is EncryptedServiceStateResult.Success -> restoredResult.value
        } ?: return@withLock sessionFailure("No durable service session is available")

        if (!restored.matches(identity)) {
            return@withLock sessionFailure("The durable session belongs to a different Wire identity")
        }

        sessionMutex.withLock { currentSession = restored }
        val candidate = ownedNetwork ?: return@withLock sessionFailure("The authenticated network container is unavailable")
        try {
            when (val clientResponse = candidate.clientApi.fetchClientInfo(identity.clientId)) {
                is NetworkResponse.Error -> {
                    releaseFailedCandidate(candidate)
                    sessionMutex.withLock { currentSession = null }
                    return@withLock sessionFailure("Failed to verify the supplied Wire client", clientResponse.kException)
                }

                is NetworkResponse.Success -> {
                    val client = clientResponse.value
                    if (client.clientId != identity.clientId) {
                        releaseFailedCandidate(candidate)
                        sessionMutex.withLock { currentSession = null }
                        return@withLock sessionFailure("The backend returned a different Wire client identity")
                    }
                    if (ClientCapabilityDTO.ConsumableNotifications !in client.capabilities) {
                        releaseFailedCandidate(candidate)
                        sessionMutex.withLock { currentSession = null }
                        return@withLock sessionFailure("The Wire client does not support consumable notifications")
                    }
                }
            }
            started = true
            ServiceResult.Success
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { releaseFailedCandidate(candidate) }
            sessionMutex.withLock { currentSession = null }
            throw cancellation
        } catch (failure: Throwable) {
            releaseFailedCandidate(candidate)
            sessionMutex.withLock { currentSession = null }
            sessionFailure("Failed to start authenticated service networking", failure)
        }
    }

    /** Returns the authenticated container after successful [start]. */
    public fun requireNetwork(): AuthenticatedNetworkContainer {
        check(started) {
            if (closed) "The service network owner is closed" else "The service network owner has not been started"
        }
        return checkNotNull(ownedNetwork) { "The authenticated network container is unavailable" }
    }

    internal fun networkForComposition(): AuthenticatedNetworkContainer = checkNotNull(ownedNetwork) {
        "The authenticated network container is unavailable"
    }

    internal fun networkOrNull(): AuthenticatedNetworkContainer? = ownedNetwork

    override suspend fun session(): SessionDTO? = sessionMutex.withLock { currentSession }

    override fun serverConfig(): ServerConfigDTO = configuredServerConfig

    override fun nomadServiceUrl(): String? = configuredNomadServiceUrl

    override fun proxyCredentials(): ProxyCredentialsDTO? = configuredProxyCredentials

    override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String?): SessionDTO = sessionMutex.withLock {
        if (closed) throw FailureToRefreshTokenException("The service network owner is closed")
        val current = currentSession ?: throw FailureToRefreshTokenException("No durable service session is loaded")
        val refreshToken = oldRefreshToken ?: throw FailureToRefreshTokenException("The refresh token is missing")
        if (refreshToken != current.refreshToken) {
            throw FailureToRefreshTokenException("The requested refresh token is stale")
        }

        val refreshed = when (val response = accessTokenApi.getToken(refreshToken, identity.clientId)) {
            is NetworkResponse.Error -> throw FailureToRefreshTokenException("Failed to refresh the service session", response.kException)
            is NetworkResponse.Success -> response.value
        }
        if (refreshed.first.userId != identity.userId.value) {
            throw FailureToRefreshTokenException("The refreshed access token belongs to a different Wire user")
        }
        val updated = SessionDTO(
            userId = current.userId,
            tokenType = refreshed.first.tokenType,
            accessToken = refreshed.first.value,
            refreshToken = refreshed.second?.value ?: current.refreshToken,
            cookieLabel = current.cookieLabel,
        )
        try {
            when (val saved = sessionStore.saveSession(updated)) {
                is EncryptedServiceStateResult.Failure -> throw FailureToRefreshTokenException(
                    saved.description,
                    saved.cause,
                )
                is EncryptedServiceStateResult.Success -> Unit
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            throw FailureToRefreshTokenException("Failed to persist the refreshed service session", failure)
        }
        currentSession = updated
        updated
    }

    override suspend fun close(): ServiceResult = lifecycleMutex.withLock {
        if (closed && ownedNetwork == null && sessionStoreClosed) return@withLock ServiceResult.Success
        closed = true
        started = false
        sessionMutex.withLock { currentSession = null }

        var firstFailure: Throwable? = null
        val network = ownedNetwork
        if (network != null) {
            try {
                when (network.notificationApi.closeLiveEvents()) {
                    is NetworkResponse.Error -> Unit // Closing the owning container below remains authoritative.
                    is NetworkResponse.Success -> Unit
                }
            } catch (_: Throwable) {
                // Closing the owning container below remains authoritative.
            }
            try {
                network.close()
                if (ownedNetwork === network) ownedNetwork = null
            } catch (failure: Throwable) {
                firstFailure = failure
            }
        }
        if (!sessionStoreClosed) {
            try {
                (sessionStore as AutoCloseable).close()
                sessionStoreClosed = true
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }

        if (ownedNetwork == null && sessionStoreClosed) {
            ServiceResult.Success
        } else {
            ServiceResult.Failure(
                ServiceFailure.Cleanup(
                    "Failed to close service networking",
                    firstFailure ?: IllegalStateException("Service networking cleanup remains incomplete"),
                ),
            )
        }
    }

    private fun SessionDTO.matches(identity: ServiceIdentity): Boolean =
        userId.value == identity.userId.value && userId.domain == identity.userId.domain

    private fun sessionFailure(description: String, cause: Throwable? = null): ServiceResult.Failure =
        ServiceResult.Failure(ServiceFailure.Session(description, cause))

    private fun createNetwork(): AuthenticatedNetworkContainer = AuthenticatedNetworkContainer.create(
        sessionManager = this,
        nomadServiceUrl = configuredNomadServiceUrl,
        selfUserId = NetworkQualifiedId(identity.userId.value, identity.userId.domain),
        userAgent = userAgent,
        certificatePinning = certificatePinning,
        mockEngine = null,
        mockWebSocketSession = null,
        kaliumLogger = kaliumLogger,
    )

    private suspend fun releaseFailedCandidate(candidate: AuthenticatedNetworkContainer) {
        try {
            candidate.close()
            if (ownedNetwork === candidate) ownedNetwork = null
        } catch (_: Throwable) {
            // Retain ownership so an explicit close can retry the failed cleanup.
        }
    }
}
