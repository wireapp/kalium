@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.events.ExperimentalEventApi::class,
    com.wire.kalium.event.processing.ExperimentalEventProcessingApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)

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
 */

package com.wire.kalium.logic.service

import com.wire.kalium.calling.runtime.CallEventSink
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.calling.runtime.SelfConversationTarget
import com.wire.kalium.conversation.remote.RemoteConversationContextProvider
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.KaliumServiceRuntime
import com.wire.kalium.logic.service.api.ServiceConfig
import com.wire.kalium.logic.service.api.ServiceFailure
import com.wire.kalium.logic.service.api.ServiceObserver
import com.wire.kalium.logic.service.api.ServiceResult
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.utils.NetworkResponse
import java.time.Instant

/** Complete JVM service composition input. All durable state belongs to exactly one identity. */
@ExperimentalKaliumServiceApi
@Suppress("LongParameterList")
public data class WireKaliumServiceConfig(
    public val service: ServiceConfig,
    public val stateStore: EncryptedJvmServiceStateStore,
    public val cryptoStorage: ServiceCryptoStorage,
    public val serverConfig: ServerConfigDTO,
    public val userAgent: String,
    public val certificatePinning: CertificatePinning,
    public val selfConversationTargets: List<SelfConversationTarget>,
    public val selfUserTeamId: String?,
    public val crlHandler: ServiceCrlDistributionPointHandler,
    public val callEventSink: CallEventSink,
    public val logger: KaliumLogger = KaliumLogger.disabled(),
    public val nomadServiceUrl: String? = null,
    public val proxyCredentials: ProxyCredentialsDTO? = null,
    public val notificationOpenTimeoutMillis: Long = 30_000,
    public val avsReadyTimeoutMillis: Long = 30_000,
    public val audioCbr: Boolean = false,
) {
    init {
        require(stateStore.identity == service.identity) { "Encrypted state and service config identities must match" }
        require(userAgent.isNotBlank()) { "userAgent must not be blank" }
        require(selfConversationTargets.isNotEmpty()) { "Durable self-conversation targets must not be empty" }
        require(notificationOpenTimeoutMillis > 0) { "notificationOpenTimeoutMillis must be positive" }
        require(avsReadyTimeoutMillis > 0) { "avsReadyTimeoutMillis must be positive" }
    }
}

/** Concrete, calling-only Wire service factory. */
@ExperimentalKaliumServiceApi
public object WireKaliumService {
    @Suppress("LongMethod")
    public fun create(config: WireKaliumServiceConfig, observer: ServiceObserver): KaliumServiceRuntime {
        val identity = config.service.identity
        val networkOwner = JvmServiceNetworkOwner(
            identity = identity,
            sessionStore = config.stateStore,
            configuredServerConfig = config.serverConfig,
            userAgent = config.userAgent,
            certificatePinning = config.certificatePinning,
            kaliumLogger = config.logger,
            configuredNomadServiceUrl = config.nomadServiceUrl,
            configuredProxyCredentials = config.proxyCredentials,
        )
        val network = networkOwner.networkForComposition()
        val contextProvider = RemoteConversationContextProvider(
            conversationApi = network.conversationApi,
            clientApi = network.clientApi,
            selfUserTeamId = config.selfUserTeamId,
        )
        val epochBus = WireMlsEpochBus()
        val crypto = WireServiceCryptoRuntime(
            expectedIdentity = identity,
            storage = config.cryptoStorage,
            transporter = WireMlsTransporter(networkOwner),
            epochBus = epochBus,
        )
        val eventSource = WireNotificationEventSource(networkOwner, config.notificationOpenTimeoutMillis)
        val pendingProposalScheduler = WirePendingMlsCommitScheduler(
            crypto,
            config.stateStore,
            onFatalFailure = { failure -> eventSource.failClosed(failure.description, failure.cause) },
        )
        val signalSender = WireEncryptedCallingSignalSender(
            identity = identity,
            networkOwner = networkOwner,
            crypto = crypto,
            contextProvider = contextProvider,
            outbox = config.stateStore,
        )
        val transport = WireCallTransport(network.callApi, signalSender)
        val conferenceMembership = WireConferenceMembership(
            conversationApi = network.conversationApi,
            cryptoRuntime = crypto,
            epochBus = epochBus,
            stateStore = config.stateStore,
            crlHandler = config.crlHandler,
            pendingProposalScheduler = pendingProposalScheduler,
        )
        crypto.afterStart = {
            when (val flushed = signalSender.flushPending()) {
                is CallingResult.Failure -> ServiceResult.Failure(
                    ServiceFailure.Crypto("Unable to flush encrypted calling signalling: ${flushed.failure}"),
                )
                CallingResult.Success -> when (val reconciled = conferenceMembership.reconcileStartup()) {
                    is CallingResult.Failure -> ServiceResult.Failure(
                        ServiceFailure.Crypto("Unable to reconcile conference state: ${reconciled.failure}"),
                    )
                    CallingResult.Success -> when (val scheduled = pendingProposalScheduler.start()) {
                        is EncryptedServiceStateResult.Failure -> ServiceResult.Failure(
                            ServiceFailure.Crypto(scheduled.description, scheduled.cause),
                        )
                        is EncryptedServiceStateResult.Success -> ServiceResult.Success
                    }
                }
            }
        }
        crypto.beforeClose = {
            when (val result = pendingProposalScheduler.close()) {
                is EncryptedServiceStateResult.Failure -> ServiceResult.Failure(
                    ServiceFailure.Crypto(result.description, result.cause),
                )
                is EncryptedServiceStateResult.Success -> ServiceResult.Success
            }
        }
        val engine = JvmAvsCallingEngine(
            identity = identity,
            transport = transport,
            contextProvider = contextProvider,
            conferenceMembership = conferenceMembership,
            deliveryJournal = config.stateStore,
            serverTimeSeconds = {
                when (val response = networkOwner.requireNetwork().serverTimeApi.getServerTime()) {
                    is NetworkResponse.Error -> throw response.kException
                    is NetworkResponse.Success -> Instant.parse(response.value.time).epochSecond
                }
            },
            federationEnabled = config.serverConfig.metaData.federation,
            readyTimeoutMillis = config.avsReadyTimeoutMillis,
            audioCbr = config.audioCbr,
        )
        val decryptor = WireServiceEventDecryptor(
            identity = identity,
            crypto = crypto,
            conversationApi = network.conversationApi,
            contextProvider = contextProvider,
            journal = config.stateStore,
        )
        val components = KaliumServiceComponents(
            identity = identity,
            sessionManager = networkOwner,
            cryptoRuntime = crypto,
            eventSource = eventSource,
            eventDeliveryStateStore = JournaledEventDeliveryStateStore(config.stateStore, config.stateStore),
            eventDecoder = WireServiceEventDecoder(),
            eventDecryptor = decryptor,
            protocolEventHandlers = RequiredProtocolEventHandlers.forProteusAndMls(
                WireProteusProtocolHandler(identity),
                WireMlsProtocolHandler(
                    crypto,
                    contextProvider,
                    config.stateStore,
                    conferenceMembership,
                    config.crlHandler,
                    pendingProposalScheduler,
                ),
            ),
            callingPayloadExtractor = WireCallingPayloadExtractor,
            callingEventIdempotencyStore = config.stateStore,
            conversationContextProvider = contextProvider,
            conversationProtocolStateStore = config.stateStore,
            callTransport = transport,
            conferenceMembership = conferenceMembership,
            avsCallingEngine = engine,
            selfConversationProvider = ConfiguredServiceSelfConversationProvider(config.selfConversationTargets),
            callingControlHandler = ForwardAllCallingControlHandler,
            callEventSink = config.callEventSink,
        )
        return KaliumService.create(config.service, components, observer)
    }
}
