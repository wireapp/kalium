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
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertIs

class BackupAndUploadCryptoStateUseCaseTest {

    @Test
    fun givenBackupSuccess_whenUploadingSucceeds_thenReturnsUnit() = runTest {
        val backupUseCase = mock<BackupCryptoDBUseCase>()
        val remoteRepository = mock<CryptoStateBackupRemoteRepository>()
        val fileSystem = FakeKaliumFileSystem()
        val backupPath = fileSystem.tempFilePath("crypto_state.zip")
        fileSystem.sink(backupPath, mustCreate = true).buffer().use { it.writeUtf8("data") }

        everySuspend { backupUseCase.invoke() } returns BackupCryptoDBResult.Success(backupPath, "crypto_state.zip")
        everySuspend { remoteRepository.uploadCryptoState(any(), any()) } returns Either.Right(Unit)

        val useCase = BackupAndUploadCryptoStateUseCaseImpl(backupUseCase, remoteRepository, fileSystem)

        val result = useCase.invoke()

        assertIs<BackupAndUploadCryptoStateResult.Success>(result)
        verifySuspend { remoteRepository.uploadCryptoState(any(), any()) }
    }

    @Test
    fun givenBackupFailure_whenInvoked_thenReturnsFailure() = runTest {
        val backupUseCase = mock<BackupCryptoDBUseCase>()
        val remoteRepository = mock<CryptoStateBackupRemoteRepository>()
        val fileSystem = FakeKaliumFileSystem()
        val failure = CoreFailure.Unknown(RuntimeException("backup failed"))

        everySuspend { backupUseCase.invoke() } returns BackupCryptoDBResult.Failure(failure)

        val useCase = BackupAndUploadCryptoStateUseCaseImpl(backupUseCase, remoteRepository, fileSystem)

        val result = useCase.invoke()

        assertIs<BackupAndUploadCryptoStateResult.Failure>(result)
    }

    @Test
    fun givenUploadFailure_whenInvoked_thenReturnsFailure() = runTest {
        val backupUseCase = mock<BackupCryptoDBUseCase>()
        val remoteRepository = mock<CryptoStateBackupRemoteRepository>()
        val fileSystem = FakeKaliumFileSystem()
        val backupPath = fileSystem.tempFilePath("crypto_state.zip")
        fileSystem.sink(backupPath, mustCreate = true).buffer().use { it.writeUtf8("data") }
        val error = KaliumException.GenericError(RuntimeException("upload failed"))

        everySuspend { backupUseCase.invoke() } returns BackupCryptoDBResult.Success(backupPath, "crypto_state.zip")
        everySuspend { remoteRepository.uploadCryptoState(any(), any()) } returns Either.Left(
            NetworkFailure.ServerMiscommunication(error)
        )

        val useCase = BackupAndUploadCryptoStateUseCaseImpl(backupUseCase, remoteRepository, fileSystem)

        val result = useCase.invoke()

        assertIs<BackupAndUploadCryptoStateResult.Failure>(result)
    }
}
