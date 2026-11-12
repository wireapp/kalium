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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.toCrypto
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.util.DebugKaliumApi
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(DebugKaliumApi::class)
class ResetMLSKeyPackagesUseCaseTest {
    @Test
    fun givenOrphanedBackendPool_whenRecovering_thenReplaceEntirePoolOnlyAfterGenerationCommits() = runTest {
        val arrangement = Arrangement().prepare()

        assertIs<ResetMLSKeyPackagesResult.Success>(arrangement.useCase())

        assertEquals(listOf("generate", "commit", "flag", "replace", "refill"), arrangement.events)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.replaceKeyPackages(TestClient.CLIENT_ID, KEY_PACKAGES, CIPHER_SUITE)
        }
        // A high backend count must never bypass replacement of an orphaned pool.
        verifySuspend(VerifyMode.not) { arrangement.repository.getAvailableKeyPackageCount(any(), any()) }
        // Existing private keys may still have in-flight Welcomes and must be retained.
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.removeKeyPackages(any()) }
    }

    @Test
    fun givenCommitFailure_whenRecovering_thenNothingIsPublished() = runTest {
        val arrangement = Arrangement().prepare(failCommit = true)

        assertFailsWith<IllegalStateException> { arrangement.useCase() }

        assertEquals(listOf("generate"), arrangement.events)
        verifySuspend(VerifyMode.not) { arrangement.repository.replaceKeyPackages(any(), any(), any()) }
    }

    @Test
    fun givenGenerationFailure_whenRecovering_thenLeaveBackendUntouched() = runTest {
        val arrangement = Arrangement().prepare()
        everySuspend { arrangement.mlsContext.generateKeyPackages(any(), any()) } throws IllegalStateException("generation failed")

        assertIs<ResetMLSKeyPackagesResult.Failure>(arrangement.useCase())

        verifySuspend(VerifyMode.not) { arrangement.repository.replaceKeyPackages(any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.refill() }
    }

    @Test
    fun givenNoCurrentClient_whenRecovering_thenDoNotGenerateOrPublish() = runTest {
        val arrangement = Arrangement().prepare()
        everySuspend { arrangement.currentClientId() } returns Either.Left(CoreFailure.MissingClientRegistration)

        assertEquals(
            ResetMLSKeyPackagesResult.Failure(CoreFailure.MissingClientRegistration),
            arrangement.useCase(),
        )
        assertEquals(emptyList(), arrangement.events)
    }

    @Test
    fun givenRecoveryFlagCannotBePersisted_whenRecovering_thenDoNotReplaceOrRefill() = runTest {
        val arrangement = Arrangement().prepare()
        everySuspend { arrangement.config.setShouldJoinPendingMLSConversations(true) } returns Either.Left(StorageFailure.DataNotFound)

        assertEquals(ResetMLSKeyPackagesResult.Failure(StorageFailure.DataNotFound), arrangement.useCase())

        verifySuspend(VerifyMode.not) { arrangement.repository.replaceKeyPackages(any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.refill() }
    }

    @Test
    fun givenReplacementFails_whenRecovering_thenDoNotRefillAndAllowRetry() = runTest {
        val arrangement = Arrangement().prepare()
        val failure = NetworkFailure.NoNetworkConnection(null)
        everySuspend { arrangement.repository.replaceKeyPackages(any(), any(), any()) } returns Either.Left(failure)

        assertEquals(ResetMLSKeyPackagesResult.Failure(failure), arrangement.useCase())
        verifySuspend(VerifyMode.not) { arrangement.refill() }

        everySuspend { arrangement.repository.replaceKeyPackages(any(), any(), any()) } returns Either.Right(Unit)
        assertIs<ResetMLSKeyPackagesResult.Success>(arrangement.useCase())
    }

    @Test
    fun givenRefillFailsAfterReplacement_whenRecovering_thenReportIncompleteRecovery() = runTest {
        val arrangement = Arrangement().prepare()
        val failure = NetworkFailure.NoNetworkConnection(null)
        everySuspend { arrangement.refill() } returns RefillKeyPackagesResult.Failure(failure)

        assertEquals(ResetMLSKeyPackagesResult.Failure(failure), arrangement.useCase())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.repository.replaceKeyPackages(any(), any(), any()) }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val repository = mock<KeyPackageRepository>()
        val limits = mock<KeyPackageLimitsProvider>()
        val currentClientId = mock<CurrentClientIdProvider>()
        val config = mock<UserConfigRepository>()
        val refill = mock<RefillKeyPackagesUseCase>()
        val events = mutableListOf<String>()
        val useCase = ResetMLSKeyPackagesUseCaseImpl(repository, limits, currentClientId, config, cryptoTransactionProvider, refill)

        @Suppress("UNCHECKED_CAST")
        suspend fun prepare(failCommit: Boolean = false) = apply {
            everySuspend { currentClientId() } returns Either.Right(TestClient.CLIENT_ID)
            every { limits.refillAmount() } returns 100
            every { mlsContext.getDefaultCipherSuite() } returns CIPHER_SUITE.toCrypto()
            everySuspend { mlsContext.generateKeyPackages(100, any()) } calls {
                events += "generate"
                KEY_PACKAGES
            }
            everySuspend { cryptoTransactionProvider.mlsTransaction<Any?>(any(), any()) } calls {
                val block = it.args[1] as suspend (MlsCoreCryptoContext) -> Either<CoreFailure, Any?>
                val result = block(mlsContext)
                if (failCommit) error("commit failed")
                events += "commit"
                result
            }
            everySuspend { config.setShouldJoinPendingMLSConversations(true) } calls {
                events += "flag"
                Either.Right(Unit)
            }
            everySuspend { repository.replaceKeyPackages(any(), any(), any()) } calls {
                events += "replace"
                Either.Right(Unit)
            }
            everySuspend { refill() } calls {
                events += "refill"
                RefillKeyPackagesResult.Success
            }
        }
    }

    private companion object {
        val CIPHER_SUITE = CipherSuite.fromTag(1)
        val KEY_PACKAGES = listOf(byteArrayOf(1, 2, 3))
    }
}
