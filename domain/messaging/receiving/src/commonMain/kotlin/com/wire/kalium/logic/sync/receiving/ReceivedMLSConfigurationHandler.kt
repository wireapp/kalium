/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.client.clientFromDTO
import com.wire.kalium.logic.data.client.selfClientParameters
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.toApi
import com.wire.kalium.logic.feature.mlsmigration.hasMigrationEnded
import com.wire.kalium.logic.feature.user.DISABLED_MLS_MIGRATION
import com.wire.kalium.logic.feature.user.selfSupportedProtocols
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.persistence.db.UserDatabaseBuilder

/** Fetch required inputs in payload order, then stage the app's canonical client/configuration writes. */
@Suppress("LongParameterList")
internal class ReceivedMLSConfigurationHandler(
    database: UserDatabaseBuilder,
    private val users: MembershipUserLoader,
    private val self: UserId,
    private val clientId: ClientId,
    private val clientApi: ClientApi?,
    private val selfApi: SelfApi?,
    private val oneOnOnes: ReceivedOneOnOneHandler,
    private val mlsSupported: Boolean,
    private val hasCredential: suspend (MLSCiphersuite) -> Boolean,
) {
    private val config = UserConfigDataSource(
        database.userPrefsDAO,
        database.userConfigDAO,
        lazy { BuildFileRestrictionState.NoRestriction }
    )
    private var teamProtocols: Set<SupportedProtocol>? = null

    private var migration: MLSMigrationModel? = null

    suspend fun handle(
        event: Event.FeatureConfig.MLSUpdated,
        payloadId: String,
        context: MlsCoreCryptoContext?,
    ): ReceivedPayload.Configuration {
        val cipher = event.model.supportedCipherSuite?.default
        if (context != null && cipher != null) {
            check(cipher !is CipherSuite.UNKNOWN) { "Configured MLS cipher suite is unsupported" }
            if (cipher.toCrypto() != context.getDefaultCipherSuite()) {
                check(hasCredential(cipher.toCrypto())) { "Changing the MLS default requires an installed credential" }
            }
        }
        val previous = currentTeamProtocols()
        val result = if (mlsSupported && previous != event.model.supportedProtocols) {
            refreshProtocols(event, event.model.supportedProtocols, payloadId, context)
        } else ReceivedPayload.Configuration(event)
        teamProtocols = event.model.supportedProtocols
        return result
    }

    suspend fun handle(
        event: Event.FeatureConfig.MLSMigrationUpdated,
        payloadId: String,
        context: MlsCoreCryptoContext?,
    ): ReceivedPayload.Configuration {
        // The app refreshes against the previous migration policy before storing the incoming one.
        val result = if (mlsSupported && event.model.hasMigrationEnded()) {
            refreshProtocols(event, currentTeamProtocols(), payloadId, context)
        } else ReceivedPayload.Configuration(event)
        migration = event.model
        return result
    }

    private suspend fun currentTeamProtocols(): Set<SupportedProtocol> = teamProtocols ?: config.getSupportedProtocols().flatMapLeft {
        if (it is StorageFailure.DataNotFound) Either.Right(setOf(SupportedProtocol.PROTEUS)) else Either.Left(it)
    }.orThrow()

    private suspend fun refreshProtocols(
        event: Event.FeatureConfig,
        supportedProtocols: Set<SupportedProtocol>,
        payloadId: String,
        context: MlsCoreCryptoContext?,
    ): ReceivedPayload.Configuration {
        val selfProtocols = checkNotNull(users.user(self)).supportedProtocols
            ?.map { SupportedProtocol.valueOf(it.name) }?.toSet()
        val fetched = wrapApiRequest {
            checkNotNull(clientApi) { "Updating self protocols requires authenticated self clients" }.fetchSelfUserClient()
        }.orThrow()
        val previousMigration = migration ?: config.getMigrationConfiguration().flatMapLeft {
            if (it is StorageFailure.DataNotFound) Either.Right(DISABLED_MLS_MIGRATION) else Either.Left(it)
        }.orThrow()
        val calculated = selfSupportedProtocols(
            teamProtocols = supportedProtocols,
            migration = previousMigration,
            clients = fetched.map(::clientFromDTO),
            currentClientId = clientId,
            previous = selfProtocols,
            logger = kaliumLogger,
        )
        if (calculated == selfProtocols) {
            return ReceivedPayload.Configuration(event, selfClients = fetched.map { it.selfClientParameters(self) })
        }
        wrapApiRequest {
            checkNotNull(selfApi) { "Changed self protocols require authenticated upload" }
                .updateSupportedProtocols(calculated.map { it.toApi() })
        }.orThrow()
        users.updateSelfProtocols(calculated)
        val profiles = users.refreshAll()
        val resolutions = oneOnOnes.resolveAll(payloadId, context)
        val clients = fetched.map { it.selfClientParameters(self) }
        return ReceivedPayload.Configuration(event, clients, calculated, profiles, resolutions)
    }
}
