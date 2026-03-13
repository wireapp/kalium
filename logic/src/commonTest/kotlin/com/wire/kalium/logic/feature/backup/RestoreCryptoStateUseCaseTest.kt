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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.feature.message.RetryFailedMessageUseCaseTest.Companion.fakeKaliumFileSystem
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

internal class RestoreCryptoStateUseCaseTest {

    @Test
    fun givenDownloadFails_whenInvoked_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.downloadCryptoState()
        }
    }

    @Test
    fun givenNoBackupAvailable_whenInvoked_thenReturnNoBackupAvailable() = runTest {
        val arrangement = Arrangement()
            .withNoBackupAvailable()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.NoBackupAvailable>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.downloadCryptoState()
        }
    }

    @Test
    fun givenExtractFails_whenDownloadSucceeds_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.extractCryptoState(any())
        }
    }

    @Test
    fun givenApplyCryptoStateFails_whenExtractSucceeds_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplyFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.applyCryptoState(any())
        }
    }

    @Test
    fun givenSetLastDeviceFails_whenApplySucceeds_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplySuccess()
            .withSetLastDeviceFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.setLastDeviceId()
        }
    }

    @Test
    fun givenAllStepsSucceed_whenInvoked_thenReturnSuccess() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplySuccess()
            .withSetLastDeviceSuccess()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.downloadCryptoState()
            arrangement.extractCryptoState(any())
            arrangement.applyCryptoState(any())
            arrangement.setLastDeviceId()
        }
    }

    private class Arrangement {

        val downloadCryptoState = mock<DownloadCryptoStateUseCase>()
        val extractCryptoState = mock<ExtractCryptoStateUseCase>()
        val applyCryptoState = mock<ApplyCryptoStateUseCase>()
        val setLastDeviceId = mock<SetLastDeviceIdUseCase>()

        val useCase by lazy {
            RestoreCryptoStateUseCaseImpl(
                downloadCryptoState = downloadCryptoState,
                extractCryptoState = extractCryptoState,
                applyCryptoState = applyCryptoState,
                setLastDeviceId = setLastDeviceId
            )
        }

        val fakePath = fakeKaliumFileSystem.tempFilePath("path")

        val metadata = CryptoStateBackupMetadata(
            version = CryptoStateBackupMetadata.CURRENT_VERSION,
            clientId = "test-client-id",
            mlsDbPassphrase = "mls-passphrase",
            proteusDbPassphrase = "proteus-passphrase"
        )

        private val extractSuccess = ExtractCryptoStateResult.Success(
            extractedDir = fakePath,
            metadata = metadata,
            mlsKeystorePath = fakePath,
            proteusKeystorePath = fakePath
        )

        fun withDownloadSuccess() = apply {
            everySuspend { downloadCryptoState() }.returns(
                DownloadCryptoStateResult.Success(
                    backupFilePath = fakePath,
                    backupFileName = "backup.zip"
                )
            )
        }

        fun withDownloadFailure() = apply {
            everySuspend { downloadCryptoState() }.returns(
                DownloadCryptoStateResult.Failure(CoreFailure.Unknown(Exception()))
            )
        }

        fun withNoBackupAvailable() = apply {
            everySuspend { downloadCryptoState() }.returns(
                DownloadCryptoStateResult.NoBackupAvailable
            )
        }

        fun withExtractSuccess() = apply {
            everySuspend { extractCryptoState(any()) }.returns(extractSuccess)
        }

        fun withExtractFailure() = apply {
            everySuspend { extractCryptoState(any()) }.returns(
                ExtractCryptoStateResult.Failure(CoreFailure.Unknown(Exception()))
            )
        }

        fun withApplySuccess() = apply {
            everySuspend { applyCryptoState(any()) }.returns(
                ApplyCryptoStateResult.Success
            )
        }

        fun withApplyFailure() = apply {
            everySuspend { applyCryptoState(any()) }.returns(
                ApplyCryptoStateResult.Failure(CoreFailure.Unknown(Exception()))
            )
        }

        fun withSetLastDeviceSuccess() = apply {
            everySuspend { setLastDeviceId() }.returns(
                SetLastDeviceIdResult.Success
            )
        }

        fun withSetLastDeviceFailure() = apply {
            everySuspend { setLastDeviceId() }.returns(
                SetLastDeviceIdResult.Failure(CoreFailure.Unknown(Exception()))
            )
        }
    }
}
