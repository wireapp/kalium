/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.toModel
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.util.DebugKaliumApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Replaces this device's entire backend pool for its active MLS cipher suite. Manual recovery only. */
@DebugKaliumApi("Debug-only manual recovery of orphaned MLS key packages.")
public fun interface ResetMLSKeyPackagesUseCase {
    public suspend operator fun invoke(): ResetMLSKeyPackagesResult
}

@DebugKaliumApi("Debug-only result of manual MLS key-package recovery.")
public sealed interface ResetMLSKeyPackagesResult {
    public data object Success : ResetMLSKeyPackagesResult
    public data class Failure(public val cause: CoreFailure) : ResetMLSKeyPackagesResult
}

@OptIn(DebugKaliumApi::class)
internal class ResetMLSKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val userConfigRepository: UserConfigRepository,
    private val transactionProvider: CryptoTransactionProvider,
    private val refillKeyPackages: RefillKeyPackagesUseCase,
) : ResetMLSKeyPackagesUseCase {
    private val mutex = Mutex()

    override suspend fun invoke(): ResetMLSKeyPackagesResult = mutex.withLock {
        val clientId = currentClientIdProvider().getOrElse {
            return@withLock ResetMLSKeyPackagesResult.Failure(it)
        }

        // Finish and commit generation BEFORE touching the server. Never nest this in another crypto transaction.
        // Keep existing local private keys: an already-claimed package may still receive its Welcome.
        transactionProvider.mlsTransaction("PrepareMLSKeyPackageRecovery") { context ->
            wrapMLSRequest {
                context.getDefaultCipherSuite().toModel() to context.generateKeyPackages(keyPackageLimitsProvider.refillAmount())
            }
        }.flatMap { (cipherSuite, packages) ->
            // Persist this before replacing: old Welcomes may reference packages lost in the original rollback.
            userConfigRepository.setShouldJoinPendingMLSConversations(true).flatMap {
                // PUT replaces the whole pool, even when the backend count is already above the refill threshold.
                keyPackageRepository.replaceKeyPackages(clientId, packages, cipherSuite)
            }
        }.flatMap {
            when (val refill = refillKeyPackages()) {
                RefillKeyPackagesResult.Success -> Either.Right(Unit)
                is RefillKeyPackagesResult.Failure -> Either.Left(refill.failure)
            }
        }.fold(
            { ResetMLSKeyPackagesResult.Failure(it) },
            { ResetMLSKeyPackagesResult.Success },
        )
    }
}
