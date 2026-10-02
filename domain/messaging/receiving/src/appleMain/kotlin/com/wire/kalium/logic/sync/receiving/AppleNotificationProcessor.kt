/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandlerImpl
import com.wire.kalium.util.InternalKaliumApi
import okio.FileSystem
import okio.Path.Companion.toPath

/** Receiving composition for an owned, prepared Apple account. */
@InternalKaliumApi
public suspend fun AppleAccountRuntime.notificationProcessor(
    options: ReceivingOptions = ReceivingOptions(),
    diagnostics: ReceivingDiagnostics? = null,
): NotificationProcessor {
    check(prepared.nomadServiceUrl == null) { "Nomad receiving hooks are not implemented" }
    val rejoin = ReceivedMLSRejoin(network.mlsMessageApi, database.metadataDAO, diagnostics)
    val mls = mlsCoreCrypto?.mlsClient(
        CryptoQualifiedClientId(prepared.clientId, prepared.userId.toCrypto()),
        checkNotNull(mlsCipherSuite).toCrypto(),
        rejoin,
        object : MLSEpochObserver {
            override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) {
                // Required epoch work is checked synchronously in the receiving transaction.
                // Native observer callbacks arrive asynchronously, including after rollback.
            }
        },
        processingScope,
    )
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
