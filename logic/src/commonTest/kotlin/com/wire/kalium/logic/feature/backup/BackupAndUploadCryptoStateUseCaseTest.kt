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
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class BackupAndUploadCryptoStateUseCaseTest {

    @Test
    fun givenBackupSuccess_whenUploadingSucceeds_thenReturnsUnit() = runTest {
        val backupUseCase = mock<BackupCryptoDBUseCase>()
        val remoteRepository = mock<CryptoStateBackupRemoteRepository>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val fileSystem = FakeKaliumFileSystem()
        val backupPath = fileSystem.tempFilePath("crypto_state.zip")
        fileSystem.sink(backupPath, mustCreate = true).buffer().use { it.writeUtf8("data") }

        everySuspend { backupUseCase.invoke() } returns BackupCryptoDBResult.Success(backupPath, "crypto_state.zip", "event-123")
        everySuspend { currentClientIdProvider.invoke() } returns Either.Right(ClientId("client-id"))
        everySuspend { remoteRepository.uploadCryptoState(any(), any(), any()) } returns Either.Right(Unit)

        val useCase = BackupAndUploadCryptoStateUseCaseImpl(
            backupUseCase,
            remoteRepository,
            fileSystem,
            currentClientIdProvider
        )

        val result = useCase.invoke()

        assertIs<BackupAndUploadCryptoStateResult.Success>(result)
        verifySuspend { remoteRepository.uploadCryptoState("client-id", any(), any()) }
    }

    @Test
    fun givenBackupFailure_whenInvoked_thenReturnsFailure() = runTest {
        val backupUseCase = mock<BackupCryptoDBUseCase>()
        val remoteRepository = mock<CryptoStateBackupRemoteRepository>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val fileSystem = FakeKaliumFileSystem()
        val failure = CoreFailure.Unknown(RuntimeException("backup failed"))

        everySuspend { backupUseCase.invoke() } returns BackupCryptoDBResult.Failure(failure)

        val useCase = BackupAndUploadCryptoStateUseCaseImpl(
            backupUseCase,
            remoteRepository,
            fileSystem,
            currentClientIdProvider
        )

        val result = useCase.invoke()

        assertIs<BackupAndUploadCryptoStateResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(0)) { currentClientIdProvider.invoke() }
    }

    @Test
    fun givenUploadFailure_whenInvoked_thenReturnsFailure() = runTest {
        val backupUseCase = mock<BackupCryptoDBUseCase>()
        val remoteRepository = mock<CryptoStateBackupRemoteRepository>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val fileSystem = FakeKaliumFileSystem()
        val backupPath = fileSystem.tempFilePath("crypto_state.zip")
        fileSystem.sink(backupPath, mustCreate = true).buffer().use { it.writeUtf8("data") }
        val error = KaliumException.GenericError(RuntimeException("upload failed"))

        everySuspend { backupUseCase.invoke() } returns BackupCryptoDBResult.Success(backupPath, "crypto_state.zip", "event-123")
        everySuspend { currentClientIdProvider.invoke() } returns Either.Right(ClientId("client-id"))
        everySuspend { remoteRepository.uploadCryptoState(any(), any(), any()) } returns Either.Left(
            NetworkFailure.ServerMiscommunication(error)
        )

        val useCase = BackupAndUploadCryptoStateUseCaseImpl(
            backupUseCase,
            remoteRepository,
            fileSystem,
            currentClientIdProvider
        )

        val result = useCase.invoke()

        assertIs<BackupAndUploadCryptoStateResult.Failure>(result)
    }

    @Test
    fun givenSecondInvokeWhileUploadInFlight_whenInvoked_thenSecondIsCoalescedAndTrailingRunUploadsOnceMore() = runTest {
        val fileSystem = FakeKaliumFileSystem()
        var backupCount = 0
        val backupUseCase = object : BackupCryptoDBUseCase {
            override suspend fun invoke(): BackupCryptoDBResult {
                backupCount += 1
                val backupName = "crypto_state_$backupCount.zip"
                val backupPath = fileSystem.tempFilePath(backupName)
                fileSystem.sink(backupPath, mustCreate = true).buffer().use { it.writeUtf8("data-$backupCount") }
                return BackupCryptoDBResult.Success(
                    backupFilePath = backupPath,
                    backupName = backupName,
                    lastProcessedEventId = "event-$backupCount"
                )
            }
        }

        val currentClientIdProvider = object : CurrentClientIdProvider {
            override suspend fun invoke(): Either<CoreFailure, ClientId> = Either.Right(ClientId("client-id"))
        }

        val firstUploadStarted = CompletableDeferred<Unit>()
        val allowFirstUploadToFinish = CompletableDeferred<Unit>()
        var uploadCalls = 0
        var isUploading = false
        val remoteRepository = object : CryptoStateBackupRemoteRepository {
            override suspend fun uploadCryptoState(
                clientId: String,
                sourceProvider: () -> okio.Source,
                size: Long
            ): Either<NetworkFailure, Unit> {
                uploadCalls += 1
                check(!isUploading) { "upload overlapped" }
                isUploading = true

                if (uploadCalls == 1) {
                    firstUploadStarted.complete(Unit)
                    allowFirstUploadToFinish.await()
                }

                isUploading = false
                return Either.Right(Unit)
            }

            override suspend fun downloadCryptoState(tempBackupFileSink: okio.Sink): Either<CoreFailure, Unit> =
                error("not used")

            override suspend fun setLastDeviceId(deviceId: String): Either<NetworkFailure, Unit> =
                error("not used")
        }

        val useCase = BackupAndUploadCryptoStateUseCaseImpl(
            backupCryptoDBUseCase = backupUseCase,
            cryptoStateBackupRemoteRepository = remoteRepository,
            kaliumFileSystem = fileSystem,
            currentClientIdProvider = currentClientIdProvider,
        )

        val firstCall = async { useCase.invoke() }
        firstUploadStarted.await()

        val secondCallResult = useCase.invoke()
        assertIs<BackupAndUploadCryptoStateResult.Success>(secondCallResult)

        allowFirstUploadToFinish.complete(Unit)
        assertIs<BackupAndUploadCryptoStateResult.Success>(firstCall.await())

        // Trailing run should have happened after the first upload completed.
        assertEquals(2, uploadCalls)
        assertEquals(2, backupCount)
    }
}
