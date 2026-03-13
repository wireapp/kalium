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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplyCryptoStateUseCaseTest {

    @Test
    fun givenValidCompressedBackupWithAllFiles_whenInvoked_thenReturnSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFileExistingCheckReturning(true)
            .withSuccessfulFileDeletion()
            .withSuccessfulFolderDeletion()
            .withSuccessfulFileCopy()
            .withSuccessfulPassphraseStorage()
            .arrange()

        val result = useCase.invoke(extractResult)

        assertEquals(ApplyCryptoStateResult.Success, result)
        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.passphraseStorage.setPassphrase(
                any(),
                any()
            )
        }
    }

    @Test
    fun givenMissingMLSKeystore_whenInvoked_thenReturnFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withExistingMLSKeystore(false)
            .withExistingProteusKeystore(true)
            .arrange()

        val result = useCase.invoke(extractResult)

        assertTrue(result is ApplyCryptoStateResult.Failure)
        assertEquals(StorageFailure.DataNotFound, result.error)
    }

    @Test
    fun givenMissingProteusKeystore_whenInvoked_thenReturnFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withFileExistingCheckReturning(true)
            .withExistingMLSKeystore(true)
            .withExistingProteusKeystore(false)
            .withSuccessfulFileDeletion()
            .withSuccessfulFileCopy()
            .arrange()

        val result = useCase.invoke(extractResult)

        assertTrue(result is ApplyCryptoStateResult.Failure)
        assertEquals(StorageFailure.DataNotFound, result.error)
    }

    @Test
    fun givenExceptionDuringApplyingCryptoState_whenInvoked_thenReturnUnknownFailure() = runTest {
        val exception = RuntimeException("boom")
        val (_, useCase) = Arrangement()
            .withApplyingException(exception)
            .arrange()

        val result = useCase.invoke(extractResult)

        assertTrue(result is ApplyCryptoStateResult.Failure)

        assertTrue(result.error is CoreFailure.Unknown)
        assertEquals(exception, result.error.rootCause)
    }

    private inner class Arrangement {

        private val kaliumFileSystem = mock<KaliumFileSystem>()
        val passphraseStorage = mock<PassphraseStorage>()

        fun withExistingMLSKeystore(result: Boolean) = apply {
            everySuspend { kaliumFileSystem.exists(mlsKeystorePath) } returns result
        }

        fun withExistingProteusKeystore(result: Boolean) = apply {
            everySuspend { kaliumFileSystem.exists(proteusKeystorePath) } returns result
        }

        fun withSuccessfulFileDeletion() = apply {
            everySuspend { kaliumFileSystem.delete(any(), any()) } returns Unit
        }

        fun withSuccessfulFolderDeletion() = apply {
            everySuspend { kaliumFileSystem.deleteContents(any(), any()) } returns Unit
        }

        fun withSuccessfulFileCopy() = apply {
            everySuspend { kaliumFileSystem.copy(any(), any()) } returns Unit
        }

        fun withSuccessfulPassphraseStorage() = apply {
            everySuspend { passphraseStorage.setPassphrase(any(), any()) } returns Unit
        }

        fun withFileExistingCheckReturning(result: Boolean) = apply {
            everySuspend { kaliumFileSystem.exists(any()) } returns result
        }

        fun withApplyingException(exception: Exception) = apply {
            everySuspend { kaliumFileSystem.exists(any()) } throws exception
        }

        fun arrange(): Pair<Arrangement, ApplyCryptoStateUseCase> {
            return this to ApplyCryptoStateUseCaseImpl(
                userId = selfUserId,
                rootPathsProvider = FakeRootPathsProvider(),
                kaliumFileSystem = kaliumFileSystem,
                passphraseStorage = passphraseStorage,
            )
        }
    }

    companion object {
        private val selfUserId = QualifiedID("participant1", "domain")
        private val mlsKeystorePath = "/tmp/mls-keystore".toPath()
        private val proteusKeystorePath = "/tmp/proteus-keystore".toPath()
        private val extractedDir = "/tmp/extracted".toPath()
        private const val CLIENT_ID = "client-123"
        const val MLS_DB_PASSPHRASE = "mls-pass"
        const val PROTEUS_DB_PASSPHRASE = "proteus-pass"

        private val metadata = CryptoStateBackupMetadata(
            version = CryptoStateBackupMetadata.CURRENT_VERSION,
            clientId = CLIENT_ID,
            mlsDbPassphrase = MLS_DB_PASSPHRASE,
            proteusDbPassphrase = PROTEUS_DB_PASSPHRASE
        )

        private val extractResult = ExtractCryptoStateResult.Success(
            mlsKeystorePath = mlsKeystorePath,
            proteusKeystorePath = proteusKeystorePath,
            extractedDir = extractedDir,
            metadata = metadata
        )
    }
}

private class FakeRootPathsProvider : RootPathsProvider("/root") {
    override fun rootAccountPath(userId: UserId) = "/root/${userId.value}/account"
    override fun rootProteusPath(userId: UserId) = "/root/${userId.value}/proteus"
    override fun rootMLSPath(userId: UserId) = "/root/${userId.value}/mls"
}
