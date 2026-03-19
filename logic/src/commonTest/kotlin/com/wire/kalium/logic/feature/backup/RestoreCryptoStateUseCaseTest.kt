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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertIs

internal class RestoreCryptoStateUseCaseTest {

    @Test
    fun givenDownloadFails_whenInvoked_thenReturnFailure() = runTest {
        val arrangement = Arrangement().withDownloadFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.downloadCryptoState() }
    }

    @Test
    fun givenNoBackupAvailable_whenInvoked_thenReturnNoBackupAvailable() = runTest {
        val arrangement = Arrangement().withNoBackupAvailable()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.NoBackupAvailable>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.downloadCryptoState() }
    }

    @Test
    fun givenExtractFails_whenDownloadSucceeds_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.extractCryptoState(any()) }
    }

    @Test
    fun givenApplyFails_whenExtractSucceeds_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplyFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.applyCryptoState(any()) }
    }

    @Test
    fun givenSetLastDeviceIdFails_whenApplySucceeds_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplySuccess()
            .withSetLastDeviceIdFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.setLastDeviceId(CLIENT_ID_VALUE)
        }
    }

    @Test
    fun givenSelfListOfClientsFails_whenSetLastDeviceIdSucceeds_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplySuccess()
            .withSetLastDeviceIdSuccess()
            .withSelfListOfClientsFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)
    }

    @Test
    fun givenRestoredClientNotInList_whenFetchingClients_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplySuccess()
            .withSetLastDeviceIdSuccess()
            .withSelfListOfClientsSuccess(emptyList())

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)
    }

    @Test
    fun givenSessionUpgradeFails_whenRestoredClientFound_thenReturnFailure() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplySuccess()
            .withSetLastDeviceIdSuccess()
            .withSelfListOfClientsSuccess(listOf(restoredClient))
            .withUpgradeCurrentSessionFailure()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Failure>(result)
    }

    @Test
    fun givenAllStepsSucceed_whenInvoked_thenReturnSuccess() = runTest {
        val arrangement = Arrangement()
            .withDownloadSuccess()
            .withExtractSuccess()
            .withApplySuccess()
            .withSetLastDeviceIdSuccess()
            .withSelfListOfClientsSuccess(listOf(restoredClient))
            .withUpgradeCurrentSessionSuccess()
            .withPersistClientIdSuccess()
            .withPersistClientHasConsumableNotificationsSuccess()

        val result = arrangement.useCase()

        assertIs<RestoreCryptoStateResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.downloadCryptoState()
            arrangement.extractCryptoState(any())
            arrangement.applyCryptoState(any())
            arrangement.setLastDeviceId(CLIENT_ID_VALUE)
            arrangement.clientRepository.selfListOfClients()
            arrangement.upgradeCurrentSession(restoredClient.id)
        }
    }

    private class Arrangement {
        val downloadCryptoState = mock<DownloadCryptoStateUseCase>()
        val extractCryptoState = mock<ExtractCryptoStateUseCase>()
        val applyCryptoState = mock<ApplyCryptoStateUseCase>()
        val setLastDeviceId = mock<SetLastDeviceIdUseCase>()
        val clientRepository = mock<ClientRepository>()
        val upgradeCurrentSession = mock<UpgradeCurrentSessionUseCase>()

        val useCase by lazy {
            RestoreCryptoStateUseCaseImpl(
                downloadCryptoState = downloadCryptoState,
                extractCryptoState = extractCryptoState,
                applyCryptoState = applyCryptoState,
                setLastDeviceId = setLastDeviceId,
                clientRepository = clientRepository,
                upgradeCurrentSession = upgradeCurrentSession,
            )
        }

        private val fakePath = FakeKaliumFileSystem().tempFilePath("path")

        private val extractSuccess = ExtractCryptoStateResult.Success(
            extractedDir = fakePath,
            metadata = metadata,
            mlsKeystorePath = fakePath,
            proteusKeystorePath = fakePath
        )

        fun withDownloadSuccess() = apply {
            everySuspend { downloadCryptoState() }.returns(
                DownloadCryptoStateResult.Success(backupFilePath = fakePath, backupFileName = "backup.zip")
            )
        }

        fun withDownloadFailure() = apply {
            everySuspend { downloadCryptoState() }.returns(
                DownloadCryptoStateResult.Failure(CoreFailure.Unknown(Exception()))
            )
        }

        fun withNoBackupAvailable() = apply {
            everySuspend { downloadCryptoState() }.returns(DownloadCryptoStateResult.NoBackupAvailable)
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
            everySuspend { applyCryptoState(any()) }.returns(ApplyCryptoStateResult.Success)
        }

        fun withApplyFailure() = apply {
            everySuspend { applyCryptoState(any()) }.returns(
                ApplyCryptoStateResult.Failure(CoreFailure.Unknown(Exception()))
            )
        }

        fun withSetLastDeviceIdSuccess() = apply {
            everySuspend { setLastDeviceId(CLIENT_ID_VALUE) }.returns(SetLastDeviceIdResult.Success)
        }

        fun withSetLastDeviceIdFailure() = apply {
            everySuspend { setLastDeviceId(CLIENT_ID_VALUE) }.returns(
                SetLastDeviceIdResult.Failure(CoreFailure.Unknown(Exception()))
            )
        }

        fun withSelfListOfClientsSuccess(clients: List<Client>) = apply {
            everySuspend { clientRepository.selfListOfClients() }.returns(Either.Right(clients))
        }

        fun withSelfListOfClientsFailure() = apply {
            everySuspend { clientRepository.selfListOfClients() }.returns(
                Either.Left(NetworkFailure.NoNetworkConnection(null))
            )
        }

        fun withUpgradeCurrentSessionSuccess() = apply {
            everySuspend { upgradeCurrentSession(any()) }.returns(Either.Right(Unit))
        }

        fun withUpgradeCurrentSessionFailure() = apply {
            everySuspend { upgradeCurrentSession(any()) }.returns(
                Either.Left(CoreFailure.Unknown(Exception()))
            )
        }

        fun withPersistClientIdSuccess() = apply {
            everySuspend { clientRepository.persistClientId(any()) }.returns(Either.Right(Unit))
        }

        fun withPersistClientHasConsumableNotificationsSuccess() = apply {
            everySuspend { clientRepository.persistClientHasConsumableNotifications(any()) }.returns(Either.Right(Unit))
        }
    }

    companion object {
        private const val CLIENT_ID_VALUE = "test-client-id"

        private val metadata = CryptoStateBackupMetadata(
            version = CryptoStateBackupMetadata.CURRENT_VERSION,
            clientId = CLIENT_ID_VALUE,
            mlsDbPassphrase = "mls-passphrase",
            proteusDbPassphrase = "proteus-passphrase"
        )

        private val restoredClient = Client(
            id = ClientId(CLIENT_ID_VALUE),
            type = ClientType.Permanent,
            registrationTime = Instant.DISTANT_PAST,
            lastActive = Instant.DISTANT_PAST,
            deviceType = null,
            model = null,
            label = null,
            isVerified = false,
            isValid = true,
            mlsPublicKeys = null,
            isMLSCapable = false,
            isAsyncNotificationsCapable = false
        )
    }
}