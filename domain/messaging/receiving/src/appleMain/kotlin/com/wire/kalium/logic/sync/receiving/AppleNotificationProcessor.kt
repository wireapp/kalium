/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandlerImpl
import com.wire.kalium.util.InternalKaliumApi
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlinx.coroutines.flow.first

/**
 * Bounded catch-up for this owned account, before [NotificationProcessor.processToLive], on the
 * consumable stream when the client registered that capability. The WebSocket is closed and joined
 * when this returns.
 */
@InternalKaliumApi
public suspend fun AppleAccountRuntime.catchUp(budget: kotlin.time.Duration): CatchUpOutcome = NotificationCatchUp(
    eventIngestion(),
    closeWebSockets = { network.closeWebSockets() },
).run(budget, consumable = hasConsumableNotifications())

/**
 * Keeps this owned account live while [block] runs, on the consumable stream when the client registered
 * that capability; see [LiveHold]. The WebSocket is closed and joined when this returns.
 */
@InternalKaliumApi
public suspend fun <T> AppleAccountRuntime.holdLive(
    connectBudget: kotlin.time.Duration,
    block: suspend (started: CatchUpOutcome) -> T,
): HeldResult<T> = LiveHold(
    eventIngestion(),
    closeWebSockets = { network.closeWebSockets() },
).run(connectBudget, consumable = hasConsumableNotifications(), block)

private fun AppleAccountRuntime.eventIngestion() =
    EventIngestion(network.notificationApi, database.eventDAO, database.metadataDAO, prepared.clientId)

private suspend fun AppleAccountRuntime.hasConsumableNotifications() =
    com.wire.kalium.persistence.client.ClientRegistrationStorageImpl(database.metadataDAO)
        .observeHasConsumableNotifications().first()

/** Receiving composition for an owned, prepared Apple account. */
@InternalKaliumApi
public suspend fun AppleAccountRuntime.notificationProcessor(
    options: ReceivingOptions = ReceivingOptions(),
    diagnostics: ReceivingDiagnostics? = null,
): NotificationProcessor {
    check(prepared.nomadServiceUrl == null) { "Nomad receiving hooks are not implemented" }
    val rejoin = ReceivedMLSRejoin(network.mlsMessageApi, database.metadataDAO, diagnostics)
    val mls = mlsClient(rejoin)
    return NotificationProcessor(
        isolateConversations = true,
        database = database,
        notificationApi = network.notificationApi,
        clientId = prepared.clientId,
        selfUserId = prepared.userId,
        proteus = proteusCoreCrypto.proteusClient(),
        mls = mls,
        options = options,
        serverConfigLinks = prepared.serverConfig.links,
        sessionRefresh = SessionRefreshSuggestedEventHandlerImpl(network, sessionManager),
        userDetailsApi = network.userDetailsApi,
        teamsApi = network.teamsApi,
        meetingApi = network.meetingApi,
        conversationApi = network.conversationApi,
        deleteFile = { FileSystem.SYSTEM.delete(it.toPath(), mustExist = false) },
        assetApi = network.assetApi,
        assetDirectory = assetDirectory,
        clientApi = network.clientApi,
        selfApi = network.selfApi,
        apiVersion = sessionManager.serverConfig().metaData.commonApiVersion.version,
        hasMLSCredential = { cipher ->
            checkNotNull(mlsCoreCrypto).hasCredential(CryptoQualifiedClientId(prepared.clientId, prepared.userId.toCrypto()), cipher)
        },
        evaluateCalls = { events -> evaluateCallingEvents(events, prepared.userId, prepared.clientId, database) },
        diagnostics = diagnostics,
        invalidateReceiving = { invalidateReceiving() },
        recovery = if (mls == null) null else MLSReceiveRecovery(
            MLSReceiveRecoveryStore(database.metadataDAO, database.notificationEventStore, prepared.clientId),
            network.conversationApi,
            prepared.userId,
            rejoin::join,
        ),
    )
}
