/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ApplyMLSConfigurationTest {
    private val failure = StorageFailure.Generic(IllegalStateException("read failed"))
    private val model = MLSModel(defaultProtocol = SupportedProtocol.MLS, supportedProtocols = SupportedProtocol.entries.toSet(),
        status = Status.ENABLED, supportedCipherSuite = SupportedCipherSuite(listOf(CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519),
            CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519))

    @Test
    fun unchangedProtocolsDoNotUploadOrResolve() = runTest {
        val repository = repository()
        assertEquals(Either.Right(Unit), repository.applyMLSConfiguration(model) { fail("Unchanged protocols resolved") })
        verifySuspend { repository.setSupportedCipherSuite(model.supportedCipherSuite!!) }
    }

    @Test
    fun appRetainsProteusFallbackAfterFailedRead() = runTest {
        val repository = repository()
        everySuspend { repository.getSupportedProtocols() } returns Either.Left(failure)
        var updated = false
        assertEquals(Either.Right(Unit), repository.applyMLSConfiguration(model) { updated = true; Either.Right(Unit) })
        assertEquals(true, updated)
    }

    @Test
    fun requiredReadFailureStopsBeforeSettingsChange() = runTest {
        val repository = repository()
        everySuspend { repository.getSupportedProtocols() } returns Either.Left(failure)
        assertEquals(Either.Left(failure), repository.applyMLSConfiguration(model, requirePreviousProtocols = true) {
            fail("Failed read reached update")
        })
        verifySuspend(VerifyMode.exactly(0)) { repository.setMLSEnabled(any()) }
    }

    @Test
    fun absentProtocolsRetainTheAppProteusDefault() = runTest {
        val repository = repository()
        everySuspend { repository.getSupportedProtocols() } returns Either.Left(StorageFailure.DataNotFound)
        assertEquals(Either.Right(Unit), repository.applyMLSConfiguration(
            model.copy(supportedProtocols = setOf(SupportedProtocol.PROTEUS)), requirePreviousProtocols = true
        ) { fail("Implicit Proteus default resolved") })
        verifySuspend { repository.setMLSEnabled(false) }
    }

    @Test
    fun updateFailurePreventsLaterCipherSettings() = runTest {
        val repository = repository()
        everySuspend { repository.getSupportedProtocols() } returns Either.Right(setOf(SupportedProtocol.PROTEUS))
        assertEquals(Either.Left(failure), repository.applyMLSConfiguration(model) { Either.Left(failure) })
        verifySuspend { repository.setSupportedProtocols(model.supportedProtocols) }
        verifySuspend(VerifyMode.exactly(0)) { repository.setSupportedCipherSuite(any()) }
    }

    private suspend fun repository(): UserConfigRepository = mock<UserConfigRepository>().also { repository ->
        everySuspend { repository.getSupportedProtocols() } returns Either.Right(model.supportedProtocols)
        everySuspend { repository.setMLSEnabled(any()) } returns Either.Right(Unit)
        everySuspend { repository.setDefaultProtocol(any()) } returns Either.Right(Unit)
        everySuspend { repository.setSupportedProtocols(any()) } returns Either.Right(Unit)
        everySuspend { repository.setSupportedCipherSuite(any()) } returns Either.Right(Unit)
    }
}
