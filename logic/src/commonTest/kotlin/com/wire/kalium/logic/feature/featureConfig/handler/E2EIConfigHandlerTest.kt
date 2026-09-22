/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.E2EIConfigModel
import com.wire.kalium.logic.data.featureConfig.E2EIModel
import com.wire.kalium.logic.data.featureConfig.Status
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class E2EIConfigHandlerTest {
    private val model = E2EIModel(E2EIConfigModel("discovery", 3600, true, "crl"), Status.ENABLED)
    private val failure = StorageFailure.Generic(IllegalStateException("settings failed"))

    @Test
    fun appStillAttemptsNotificationAfterSettingsFailure() = runTest {
        val repository = repository()
        everySuspend { repository.setE2EISettings(any()) } returns Either.Left(failure)
        assertEquals(Either.Right(Unit), E2EIConfigHandler(repository).handle(model))
        verifySuspend { repository.setE2EINotificationTime(any()) }
    }

    @Test
    fun appStillReplacesSettingsAfterReadFailure() = runTest {
        val repository = repository()
        everySuspend { repository.getE2EISettings() } returns Either.Left(failure)
        assertEquals(Either.Right(Unit), E2EIConfigHandler(repository).handle(model))
        verifySuspend { repository.setE2EISettings(any()) }
        verifySuspend { repository.setE2EINotificationTime(any()) }
    }

    @Test
    fun unchangedRequiredStatusAndUrlRetainGraceAndProxySettings() = runTest {
        val repository = repository()
        everySuspend { repository.getE2EISettings() } returns Either.Right(
            E2EISettings(true, "discovery", Instant.DISTANT_PAST, false, "previous-crl"))
        assertEquals(Either.Right(Unit), E2EIConfigHandler(repository).handle(model))
        verifySuspend(VerifyMode.exactly(0)) { repository.setE2EISettings(any()) }
        verifySuspend { repository.setE2EINotificationTime(any()) }
    }

    @Test
    fun notificationFailureStillPropagatesToTheApp() = runTest {
        val repository = repository()
        everySuspend { repository.setE2EINotificationTime(any()) } returns Either.Left(failure)
        assertEquals(Either.Left(failure), E2EIConfigHandler(repository).handle(model))
    }

    @Test
    fun requiredReadFailureCannotOverwriteUnknownSettings() = runTest {
        val repository = repository()
        everySuspend { repository.getE2EISettings() } returns Either.Left(failure)
        assertEquals(Either.Left(failure), E2EIConfigHandler(repository).handle(model, requireSettings = true))
        verifySuspend(VerifyMode.exactly(0)) { repository.setE2EISettings(any()) }
        verifySuspend(VerifyMode.exactly(0)) { repository.setE2EINotificationTime(any()) }
    }

    @Test
    fun requiredSettingsFailureCannotReportSuccess() = runTest {
        val repository = repository()
        everySuspend { repository.setE2EISettings(any()) } returns Either.Left(failure)
        assertEquals(Either.Left(failure), E2EIConfigHandler(repository).handle(model, requireSettings = true))
        verifySuspend(VerifyMode.exactly(0)) { repository.setE2EINotificationTime(any()) }
    }

    private suspend fun repository(): UserConfigRepository = mock<UserConfigRepository>().also { repository ->
        everySuspend { repository.getE2EISettings() } returns Either.Left(StorageFailure.DataNotFound)
        everySuspend { repository.setE2EISettings(any()) } returns Either.Right(Unit)
        everySuspend { repository.setE2EINotificationTime(any()) } returns Either.Right(Unit)
    }
}
