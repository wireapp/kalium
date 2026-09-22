/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.isActive
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.feature.mlsmigration.hasMigrationEnded
import kotlin.time.Instant

public val DISABLED_MLS_MIGRATION: MLSMigrationModel = MLSMigrationModel(
    startTime = Instant.DISTANT_FUTURE,
    endTime = Instant.DISTANT_FUTURE,
    status = Status.DISABLED
)

@Suppress("MagicNumber")
public fun isMLSApiSupported(apiVersion: Int): Boolean = apiVersion >= 6

/** Team policy, migration timing and active clients determine the advertised user protocols. */
@Suppress("LongParameterList")
public fun selfSupportedProtocols(
    teamProtocols: Set<SupportedProtocol>,
    migration: MLSMigrationModel,
    clients: List<Client>,
    currentClientId: ClientId,
    previous: Set<SupportedProtocol>?,
    logger: KaliumLogger,
): Set<SupportedProtocol> {
    val calculated = mutableSetOf<SupportedProtocol>()
    val proteusIsSupported = SupportedProtocol.PROTEUS in teamProtocols
    val migrationEndedForProteus = migration.hasMigrationEnded()
    logger.d("proteus is supported = $proteusIsSupported, migration has ended = $migrationEndedForProteus")
    if (proteusIsSupported || !migrationEndedForProteus) calculated.add(SupportedProtocol.PROTEUS)

    val mlsIsSupported = SupportedProtocol.MLS in teamProtocols
    val migrationEndedForMLS = migration.hasMigrationEnded()
    val activeClients = clients.filter { it.isActive || it.id == currentClientId }
    if (activeClients.isEmpty()) logger.w("user has 0 active clients")
    val allSelfClientsAreMLSCapable = activeClients.all { it.isMLSCapable }
    logger.d(
        "mls is supported = $mlsIsSupported, " +
            "all active self clients are mls capable = $allSelfClientsAreMLSCapable " +
            "migration has ended = $migrationEndedForMLS"
    )
    if (mlsIsSupported && (migrationEndedForMLS || allSelfClientsAreMLSCapable)) calculated.add(SupportedProtocol.MLS)
    val finalized = if (previous?.contains(SupportedProtocol.MLS) == true) calculated + SupportedProtocol.MLS else calculated
    logger.i("Updating supported protocols = $calculated previously = $previous, finalized = $finalized")
    return finalized
}
