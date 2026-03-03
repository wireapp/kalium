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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.CryptoTransactionProviderImpl
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.ExtractFilesParam
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.util.InternalCryptoAccess
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okio.buffer
import okio.use
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@IgnoreIOS
class BackupCryptoDBUseCaseTest {

    private val fakeFileSystem = FakeKaliumFileSystem()
    private val dispatcher = TestKaliumDispatcher

    @BeforeTest
    fun before() {
        Dispatchers.setMain(dispatcher.default)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun givenValidCryptoBackupData_whenCreatingNonEncryptedBackup_thenCreatesBackupZip() = runTest(dispatcher.default) {
        val dbData: ByteArray = Base64.decode("c29tZS1jYy1kYi1ieXRlcw==")
        val proteusDbData: ByteArray = Base64.decode("cHJvdGV1cy1kYi1ieXRlcw==")
        val passphrase = ByteArray(32) { 0xAB.toByte() }
        val proteusPassphrase = ByteArray(32) { 0xBC.toByte() }
        val (arrangement, useCase) = Arrangement()
            .withClientId(TestClient.CLIENT_ID)
            .withMlsExportedCryptoDB("keystore_export", dbData, passphrase, TestClient.CLIENT_ID)
            .withProteusExportedCryptoDB("keystore_export_proteus", proteusDbData, proteusPassphrase, TestClient.CLIENT_ID)
            .withMlsTransactionSuccess()
            .withProteusTransactionSuccess()
            .arrange()

        val result = useCase()
        advanceUntilIdle()

        assertIs<BackupCryptoDBResult.Success>(result)
        assertTrue(result.backupFilePath.name.contains(".zip"))
        verifySuspend(VerifyMode.atMost(1)) {
            arrangement.mlsClientProvider.exportCryptoDB(any())
        }
        verifySuspend(VerifyMode.atMost(1)) {
            arrangement.proteusClientProvider.exportCryptoDB(any())
        }

        with(fakeFileSystem) {
            val extractedFilesPath = tempFilePath()
            createDirectory(extractedFilesPath)
            extractCompressedFile(source(result.backupFilePath), extractedFilesPath, ExtractFilesParam.All, fakeFileSystem)

            val metadataPath = listDirectories(extractedFilesPath).firstOrNull {
                it.name == BackupConstants.BACKUP_METADATA_FILE_NAME
            }
            assertTrue(metadataPath != null)
            val metadataJson = source(metadataPath!!).buffer().use { it.readUtf8() }
            val metadata = Json.decodeFromString<CryptoStateBackupMetadata>(metadataJson)
            assertEquals(metadata.version, CryptoStateBackupMetadata.CURRENT_VERSION)
            assertEquals(metadata.clientId, TestClient.CLIENT_ID.value)
            assertEquals(metadata.mlsDbPassphrase, Base64.encode(passphrase))
            assertEquals(metadata.proteusDbPassphrase, Base64.encode(proteusPassphrase))
            val extractedMlsDB = listDirectories(extractedFilesPath).firstOrNull {
                it.name == "keystore-mls"
            }?.let {
                source(it).buffer().use { bufferedSource ->
                    bufferedSource.readByteArray()
                }
            }
            val extractedProteusDB = listDirectories(extractedFilesPath).firstOrNull {
                it.name == "keystore-proteus"
            }?.let {
                source(it).buffer().use { bufferedSource ->
                    bufferedSource.readByteArray()
                }
            }
            assertTrue(extractedMlsDB.contentEquals(dbData))
            assertTrue(extractedProteusDB.contentEquals(proteusDbData))
        }
    }

    @Test
    fun givenExportFailure_whenBackingUpCryptoDb_thenReturnsFailure() = runTest(dispatcher.default) {
        val (arrangement, useCase) = Arrangement()
            .withExportFailure(StorageFailure.DataNotFound)
            .withMlsTransactionSuccess()
            .withProteusTransactionSuccess()
            .arrange()

        val result = useCase()
        advanceUntilIdle()

        assertIs<BackupCryptoDBResult.Failure>(result)
        assertTrue(result.error is StorageFailure.DataNotFound)
        verifySuspend(VerifyMode.atMost(1)) {
            arrangement.mlsClientProvider.exportCryptoDB(any())
        }
    }

    @Test
    fun givenMissingExportedDb_whenBackingUpCryptoDb_thenReturnsFailure() = runTest(dispatcher.default) {
        val (arrangement, useCase) = Arrangement()
            .withMissingExportedCryptoDB("keystore_export", TestClient.CLIENT_ID)
            .withMlsTransactionSuccess()
            .withProteusTransactionSuccess()
            .arrange()

        val result = useCase()
        advanceUntilIdle()

        assertIs<BackupCryptoDBResult.Failure>(result)
        assertIs<StorageFailure.Generic>(result.error)
        verifySuspend(VerifyMode.atMost(1)) {
            arrangement.mlsClientProvider.exportCryptoDB(any())
        }
    }

    @Test
    fun givenExistingBackup_whenNewBackupSucceeds_thenDeletesOlderBackup() = runTest(dispatcher.default) {
        val existingBackup = fakeFileSystem.tempFilePath("${CRYPTO_BACKUP_PREFIX}_${TestUser.USER_ID}_old.zip")
        fakeFileSystem.sink(existingBackup).buffer().use { it.write(byteArrayOf(0x1)) }

        val (_, useCase) = Arrangement()
            .withClientId(TestClient.CLIENT_ID)
            .withMlsExportedCryptoDB("keystore_export", byteArrayOf(0x1), ByteArray(32) { 0xAB.toByte() }, TestClient.CLIENT_ID)
            .withProteusExportedCryptoDB("keystore_export_proteus", byteArrayOf(0x2), ByteArray(32) { 0xBC.toByte() }, TestClient.CLIENT_ID)
            .withMlsTransactionSuccess()
            .withProteusTransactionSuccess()
            .arrange()

        val result = useCase()
        advanceUntilIdle()

        assertIs<BackupCryptoDBResult.Success>(result)
        assertTrue(!fakeFileSystem.exists(existingBackup))
        assertTrue(fakeFileSystem.exists(result.backupFilePath))
    }

    private inner class Arrangement {
        val clientIdProvider = mock<CurrentClientIdProvider>()
        val mlsClientProvider = mock<MLSClientProvider>()
        val proteusClientProvider = mock<ProteusClientProvider>()
        private val mlsClient = mock<MLSClient>()
        private val mlsContext = mock<MlsCoreCryptoContext>()
        private val proteusClient = mock<ProteusClient>()
        private val proteusContext = mock<ProteusCoreCryptoContext>()

        private val cryptoTransactionProvider: CryptoTransactionProvider = CryptoTransactionProviderImpl(
            mlsClientProvider = mlsClientProvider,
            proteusClientProvider = proteusClientProvider
        )

        fun withClientId(clientId: ClientId) = apply {
            everySuspend { clientIdProvider.invoke() }.returns(Either.Right(clientId))
        }

        fun withMlsExportedCryptoDB(path: String, dbData: ByteArray, passphrase: ByteArray, clientId: ClientId) = apply {
            with(fakeFileSystem) {
                val exportPath = fakeFileSystem.tempFilePath(path)
                sink(exportPath).buffer().use { it.write(dbData) }
                everySuspend {
                    mlsClientProvider.exportCryptoDB(any())
                }.returns(
                    com.wire.kalium.logic.data.client.CryptoBackupMetadata(
                        dbPath = exportPath.toString(),
                        passphrase = passphrase,
                        clientId = clientId
                    ).right()
                )
            }
        }

        fun withProteusExportedCryptoDB(path: String, dbData: ByteArray, passphrase: ByteArray, clientId: ClientId) = apply {
            with(fakeFileSystem) {
                val exportPath = fakeFileSystem.tempFilePath(path)
                sink(exportPath).buffer().use { it.write(dbData) }
                everySuspend {
                    proteusClientProvider.exportCryptoDB(any())
                }.returns(
                    com.wire.kalium.logic.data.client.CryptoBackupMetadata(
                        dbPath = exportPath.toString(),
                        passphrase = passphrase,
                        clientId = clientId
                    ).right()
                )
            }
        }

        fun withMissingExportedCryptoDB(path: String, clientId: ClientId) = apply {
            val exportPath = fakeFileSystem.tempFilePath(path)
            everySuspend {
                mlsClientProvider.exportCryptoDB(any())
            }.returns(
                com.wire.kalium.logic.data.client.CryptoBackupMetadata(
                    dbPath = exportPath.toString(),
                    passphrase = ByteArray(32) { 0xAB.toByte() },
                    clientId = clientId
                ).right()
            )
            everySuspend {
                proteusClientProvider.exportCryptoDB(any())
            }.returns(
                com.wire.kalium.logic.data.client.CryptoBackupMetadata(
                    dbPath = exportPath.toString(),
                    passphrase = ByteArray(32) { 0xAB.toByte() },
                    clientId = clientId
                ).right()
            )
        }

        fun withMlsTransactionSuccess() = apply {
            everySuspend { mlsClientProvider.getMLSClient(any()) }.returns(mlsClient.right())
            everySuspend {
                mlsClient.transaction(
                    any(),
                    any<suspend (MlsCoreCryptoContext) -> Either<CoreFailure, ByteArray>>()
                )
            }.calls { invocation ->
                val block = invocation.args[1] as suspend (MlsCoreCryptoContext) -> Either<CoreFailure, ByteArray>
                block(mlsContext)
            }
        }

        @OptIn(InternalCryptoAccess::class)
        fun withProteusTransactionSuccess() = apply {
            everySuspend { proteusClientProvider.getOrError() }.returns(proteusClient.right())
            everySuspend {
                proteusClient.transaction(
                    any(),
                    any<suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, ByteArray>>()
                )
            }.calls { invocation ->
                val block = invocation.args[1] as suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, ByteArray>
                block(proteusContext)
            }
        }

        fun withExportFailure(failure: CoreFailure) = apply {
            everySuspend {
                mlsClientProvider.exportCryptoDB(any())
            }.returns(Either.Left(failure))
            everySuspend {
                proteusClientProvider.exportCryptoDB(any())
            }.returns(Either.Left(failure))
        }

        fun arrange(): Pair<Arrangement, BackupCryptoDBUseCase> = this to BackupCryptoDBUseCaseImpl(
            userId = TestUser.USER_ID,
            cryptoTransactionProvider = cryptoTransactionProvider,
            kaliumFileSystem = fakeFileSystem,
            dispatchers = dispatcher
        )
    }

    companion object {
        internal const val CRYPTO_BACKUP_PREFIX = "crypto_backup"
    }
}
