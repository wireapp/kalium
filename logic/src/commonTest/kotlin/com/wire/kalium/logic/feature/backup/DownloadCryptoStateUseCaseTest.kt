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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import okio.Sink
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DownloadCryptoStateUseCaseTest {

    @Test
    fun givenDownloadSucceedsWithContent_whenInvoking_thenReturnsSuccess() = runTest {
        // given
        val fileContent = "test crypto state content"
        val (arrangement, useCase) = Arrangement()
            .withDownloadSuccess(fileContent)
            .arrange()

        // when
        val result = useCase()

        // then
        assertIs<DownloadCryptoStateResult.Success>(result)
        assertTrue(result.backupFilePath.name.startsWith("crypto_backup_download"))
        assertTrue(result.backupFilePath.name.endsWith(".zip"))

        verifySuspend((VerifyMode.atMost(1))) {
            arrangement.cryptoStateBackupRemoteRepository.downloadCryptoState(any())
        }
    }

    @Test
    fun givenDownloadSucceedsWithEmptyContent_whenInvoking_thenReturnsNoBackupAvailable() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withDownloadSuccess("")
            .arrange()

        // when
        val result = useCase()

        // then
        assertIs<DownloadCryptoStateResult.NoBackupAvailable>(result)

        verifySuspend((VerifyMode.atMost(1))) {
            arrangement.cryptoStateBackupRemoteRepository.downloadCryptoState(any())
        }
    }

    @Test
    fun givenDownloadFails_whenInvoking_thenReturnsFailure() = runTest {
        // given
        val error = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, useCase) = Arrangement()
            .withDownloadFailure(error)
            .arrange()

        // when
        val result = useCase()

        // then
        assertIs<DownloadCryptoStateResult.Failure>(result)
        assertEquals(error, result.error)

        verifySuspend((VerifyMode.atMost(1))) {
            arrangement.cryptoStateBackupRemoteRepository.downloadCryptoState(any())
        }
    }

    @Test
    fun givenDownloadSucceeds_whenInvoking_thenBackupFileNameContainsUserId() = runTest {
        // given
        val userId = UserId("test-user", "test-domain")
        val (_, useCase) = Arrangement()
            .withUserId(userId)
            .withDownloadSuccess("content")
            .arrange()

        // when
        val result = useCase()

        // then
        assertIs<DownloadCryptoStateResult.Success>(result)
        assertTrue(result.backupFileName.contains(userId.toString()))
    }

    private class Arrangement {
        val cryptoStateBackupRemoteRepository = mock<CryptoStateBackupRemoteRepository>()

        private val fakeFileSystem = FakeKaliumFileSystem()
        private var userId = UserId("user-id", "domain")

        fun withUserId(userId: UserId) = apply {
            this.userId = userId
        }

        fun withDownloadSuccess(fileContent: String) = apply {
            everySuspend {
                cryptoStateBackupRemoteRepository.downloadCryptoState(any())
            }.calls { invocation ->
                val sink = invocation.args[0] as Sink
                val bufferedSink = sink.buffer()
                bufferedSink.writeUtf8(fileContent)
                bufferedSink.flush()
                Either.Right(Unit)
            }
        }

        fun withDownloadFailure(error: NetworkFailure) = apply {
            everySuspend { cryptoStateBackupRemoteRepository.downloadCryptoState(any()) }
                .returns(Either.Left(error))
        }

        fun arrange(): Pair<Arrangement, DownloadCryptoStateUseCase> {
            val useCase = DownloadCryptoStateUseCaseImpl(
                userId = userId,
                cryptoStateBackupRemoteRepository = cryptoStateBackupRemoteRepository,
                kaliumFileSystem = fakeFileSystem,
            )
            return this to useCase
        }
    }
}

