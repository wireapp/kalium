/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.MessageSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.remoteBackup.BackupCryptoStateUseCaseImpl
import com.wire.kalium.logic.sync.remoteBackup.BackupStateVisibilityCoordinator
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.FileUtil
import io.ktor.util.encodeBase64
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.buffer
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for backup and restore flow.
 * Tests the complete cycle: initialize crypto state -> backup -> delete -> restore -> verify
 *
 * NOTE: This test requires CoreCrypto native libraries to be available in ./native/libs
 * Run `make` in the project root to build them, or download pre-built binaries.
 */
class BackupRestoreIntegrationTest {

    private var tempDir: String? = null
    private lateinit var arrangement: Arrangement

    @Before
    fun setup() {
        // Create temp directory for the test
        tempDir = "/tmp/backup_restore_test_${System.currentTimeMillis()}"
        java.io.File(tempDir!!).mkdirs()

        // Check if native libraries are available (after tempDir is set)
        checkNativeLibrariesAvailable()
    }

    @After
    fun cleanup() {
        // Clean up temp directory if it was created
        tempDir?.let { FileUtil.deleteDirectory(it) }
    }

    private fun checkNativeLibrariesAvailable() {
        // Check if we can load a CoreCrypto class (which requires native libs)
        try {
            // Try to instantiate a simple crypto object that requires native libs
            val testPassphrase = ByteArray(32) { 0 }
            // This will fail if native libraries aren't available
            val testDir = "/tmp/crypto_lib_test_${System.currentTimeMillis()}"
            java.io.File(testDir).mkdirs()
            runBlocking {
                coreCryptoCentral(testDir, testPassphrase)
            }
            FileUtil.deleteDirectory(testDir)
        } catch (e: UnsatisfiedLinkError) {
            // Skip test if native libraries are not available
            Assume.assumeTrue(
                "Skipping test: CoreCrypto native libraries not found. " +
                        "Run 'make' in project root to build them, or set -Djava.library.path=./native/libs when running tests. " +
                        "Error: ${e.message}",
                false
            )
        } catch (e: Exception) {
            // If it's another exception, the libs are probably there but something else went wrong
            // Let the test proceed and fail properly
        }
    }

    @Test
    fun givenInitializedCryptoState_whenBackupAndRestore_thenCanOpenRestoredState() = runTest {
        // Given: Initialized crypto state
        arrangement = Arrangement(tempDir!!)
            .withInitializedCryptoState()
            .arrange()

        // When: Create backup
        val backupResult = arrangement.backupCryptoStateUseCase.invoke()
        assertTrue(backupResult is Either.Right, "Backup should succeed")
        val backupHash = (backupResult as Either.Right).value

        // Verify backup was uploaded
        coVerify {
            arrangement.messageSyncApi.uploadStateBackup(any(), any(), any())
        }.wasInvoked(once)

        // When: Delete crypto state
        arrangement.deleteCryptoState()

        // When: Restore backup
        val restoreResult = arrangement.downloadAndRestoreCryptoStateUseCase.invoke()
        assertTrue(restoreResult is DownloadAndRestoreCryptoStateResult.Success, "Restore should succeed")
        val clientId = (restoreResult as DownloadAndRestoreCryptoStateResult.Success).clientId
        assertEquals(arrangement.clientId.value, clientId.value, "Client ID should match")

        // Then: Verify restored crypto state can be opened
        arrangement.verifyRestoredCryptoStateCanBeOpened()
    }

    private class Arrangement(private val tempDir: String) {
        val userId = TestUser.USER_ID
        val clientId = ClientId("test-client-id")
        val fakeFileSystem = FakeKaliumFileSystem()

        val messageSyncApi = mock(classOf<MessageSyncApi>())
        val messageSyncRepository = mock(classOf<MessageSyncRepository>())
        val clientIdProvider = mock(classOf<CurrentClientIdProvider>())
        val rootPathsProvider = mock(classOf<RootPathsProvider>())
        val securityHelper = mock(classOf<SecurityHelper>())
        val eventRepository = mock(classOf<EventRepository>())
        val mlsClientProvider = mock(classOf<MLSClientProvider>())
        val clientRepository = mock(classOf<com.wire.kalium.logic.data.client.ClientRepository>())
        val metadataDAO = mock(classOf<com.wire.kalium.persistence.dao.MetadataDAO>())
        val backupStateVisibilityCoordinator = mock(classOf<BackupStateVisibilityCoordinator>())
        val passphraseStorage = mock(classOf<PassphraseStorage>())

        val proteusPath: Path = tempDir.toPath() / "proteus"
        val mlsPath: Path = tempDir.toPath() / "mls"
        val backupZipPath: Path = tempDir.toPath() / "backup.zip"

        private val mlsPassphrase = ByteArray(32) { it.toByte() }
        private val proteusPassphrase = ByteArray(32) { (it + 1).toByte() }

        lateinit var backupCryptoStateUseCase: BackupCryptoStateUseCaseImpl
        lateinit var downloadAndRestoreCryptoStateUseCase: DownloadAndRestoreCryptoStateUseCaseImpl

        suspend fun withInitializedCryptoState() = apply {
            // Setup paths
            every { rootPathsProvider.rootProteusPath(userId) }.returns(proteusPath.toString())
            every { rootPathsProvider.rootMLSPath(userId) }.returns(mlsPath.toString())

            // Create directories
            java.io.File(proteusPath.toString()).mkdirs()
            java.io.File(mlsPath.toString()).mkdirs()

            // Initialize Proteus CoreCrypto
            val proteusCoreCrypto = coreCryptoCentral(
                rootDir = proteusPath.toString(),
                passphrase = proteusPassphrase
            )
            proteusCoreCrypto.proteusClient() // This initializes the Proteus client

            // Initialize MLS CoreCrypto
            val mlsCoreCrypto = coreCryptoCentral(
                rootDir = mlsPath.toString(),
                passphrase = mlsPassphrase
            )

            // Setup mocks
            coEvery { clientIdProvider.invoke() }.returns(Either.Right(clientId))
            coEvery { securityHelper.mlsDBSecret(any(), any()) }
                .returns(com.wire.kalium.cryptography.MlsDBSecret(mlsPassphrase))
            coEvery { securityHelper.proteusDBSecret(any(), any()) }
                .returns(com.wire.kalium.cryptography.ProteusDBSecret(proteusPassphrase))
            coEvery { eventRepository.lastSavedEventId() }.returns(Either.Right("test-event-id"))
            coEvery { mlsClientProvider.getCoreCrypto(any()) }.returns(Either.Right(mlsCoreCrypto))

            // Mock upload to save to file instead
            coEvery { messageSyncApi.uploadStateBackup(any(), any(), any()) }.invokes { args ->
                val source = args[2] as okio.Source
                fakeFileSystem.sink(backupZipPath).buffer().use { sink ->
                    source.buffer().use { bufferedSource ->
                        sink.writeAll(bufferedSource)
                    }
                }
                NetworkResponse.Success(Unit, mapOf(), 200)
            }

            // Mock download to read from saved file
            coEvery { messageSyncRepository.downloadStateBackup(any(), any()) }.invokes { args ->
                val sink = args[1] as Sink
                fakeFileSystem.source(backupZipPath).buffer().use { source ->
                    sink.buffer().use { bufferedSink ->
                        bufferedSink.writeAll(source)
                    }
                }
                Either.Right(Unit)
            }

            coEvery { clientRepository.markMLSClientAsRegisteredLocally() }.returns(Either.Right(Unit))
            coEvery { metadataDAO.insertValue(any(), any()) }.returns(Unit)
            coEvery { backupStateVisibilityCoordinator.setLastUploadedHash(any()) }.returns(Unit)

            // Store passphrases
            val mlsPassphraseKey = "mls_db_secret_alias_v2_$userId"
            val proteusPassphraseKey = "proteus_db_secret_alias_v2_$userId"
            every { passphraseStorage.getPassphrase(mlsPassphraseKey) }
                .returns(mlsPassphrase.encodeBase64())
            every { passphraseStorage.getPassphrase(proteusPassphraseKey) }
                .returns(proteusPassphrase.encodeBase64())
            coEvery { passphraseStorage.setPassphrase(any(), any()) }.returns(Unit)
        }

        fun deleteCryptoState() {
            // Delete the crypto state directories
            FileUtil.deleteDirectory(proteusPath.toString())
            FileUtil.deleteDirectory(mlsPath.toString())
        }

        suspend fun verifyRestoredCryptoStateCanBeOpened() {
            // Verify directories were restored
            assertTrue(java.io.File(proteusPath.toString()).exists(), "Proteus directory should exist")
            assertTrue(java.io.File(mlsPath.toString()).exists(), "MLS directory should exist")

            // Verify we can open the restored Proteus database
            val proteusCoreCrypto = coreCryptoCentral(
                rootDir = proteusPath.toString(),
                passphrase = proteusPassphrase
            )
            val proteusClient = proteusCoreCrypto.proteusClient()
            // If we can get the client, the database is valid

            // Verify we can open the restored MLS database
            val mlsCoreCrypto = coreCryptoCentral(
                rootDir = mlsPath.toString(),
                passphrase = mlsPassphrase
            )
            // If coreCryptoCentral succeeds, the database is valid
        }

        fun arrange(): Arrangement {
            val kaliumConfigs = KaliumConfigs(
                messageSynchronizationEnabledFlag = true,
                remoteBackupURL = "https://example.com"
            )

            backupCryptoStateUseCase = BackupCryptoStateUseCaseImpl(
                selfUserId = userId,
                currentClientIdProvider = clientIdProvider,
                messageSyncApi = messageSyncApi,
                rootPathsProvider = rootPathsProvider,
                kaliumFileSystem = fakeFileSystem,
                kaliumConfigs = kaliumConfigs,
                securityHelper = securityHelper,
                eventRepository = eventRepository,
                mlsClientProvider = mlsClientProvider
            )

            downloadAndRestoreCryptoStateUseCase = DownloadAndRestoreCryptoStateUseCaseImpl(
                selfUserId = userId,
                messageSyncRepository = messageSyncRepository,
                rootPathsProvider = rootPathsProvider,
                kaliumFileSystem = fakeFileSystem,
                passphraseStorage = passphraseStorage,
                clientRepository = clientRepository,
                metadataDAO = metadataDAO,
                backupStateVisibilityCoordinator = backupStateVisibilityCoordinator
            )

            return this
        }
    }
}
