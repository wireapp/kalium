package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.exceptions.ProteusStorageMigrationException
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.encoding.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ProteusClientProviderTest {

    @Test
    fun givenGettingOrCreatingAProteusClient_whenMigrationPerformedAndFails_thenCatchErrorAndStartRecovery() = runTest {
        // given
        val (arrangement, proteusClientProvider) = Arrangement()
            .withCorruptedProteusStorage()
            .arrange()

        // when - then
        try {
            proteusClientProvider.getOrCreate()
        } catch (e: ProteusStorageMigrationException) {
            verifySuspend(mode = VerifyMode.exactly(1)) { arrangement.proteusMigrationRecoveryHandler.clearClientData(any()) }
        }
    }

    @Test
    fun givenNativeOpenFailed_whenRetrying_thenOpensAndCleansUp() = runTest {
        val (arrangement, proteusClientProvider) = Arrangement().arrange()
        // A directory in place of the keystore makes CoreCrypto fail to open it.
        val keystore = Paths.get(arrangement.rootProteusPath, Arrangement.KEYSTORE_NAME).createDirectories()

        assertFails { proteusClientProvider.getOrCreate() }

        keystore.deleteExisting()
        proteusClientProvider.getOrCreate()
        proteusClientProvider.clearLocalFiles()
        assertFalse(Paths.get(arrangement.rootProteusPath).exists())
    }

    @Test
    fun givenNoClient_whenExportingDB_thenReturnsFailure() = runTest {
        val (_, proteusClientProvider) = Arrangement()
            .withCurrentClientIdFailure(StorageFailure.DataNotFound)
            .arrange()

        proteusClientProvider.exportCryptoDB("/tmp/keystore").shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }
    }

    @Test
    fun givenProteusClientNotInitialized_whenExportingDB_thenReturnsDataNotFound() = runTest {
        val (_, proteusClientProvider) = Arrangement()
            .withProteusDatabase()
            .arrange()

        // Proteus client is not initialized, so should return DataNotFound
        proteusClientProvider.exportCryptoDB("/tmp/keystore").shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }
    }

    @Test
    fun givenDBDoesNotExist_whenExportingDB_thenReturnsFailure() = runTest {
        val (_, proteusClientProvider) = Arrangement()
            .withProteusClient()
            .arrange()

        // Proteus client was never initialized, so it should fail
        proteusClientProvider.exportCryptoDB("/tmp/keystore").shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }
    }

    private class Arrangement {

        val currentClientIdProvider: CurrentClientIdProvider = mock<CurrentClientIdProvider>()
        val passphraseStorage = mock<PassphraseStorage>()
        val proteusMigrationRecoveryHandler = mock<ProteusMigrationRecoveryHandler>()
        val rootProteusPath = "/tmp/rootProteusPath_${System.nanoTime()}"
        val passphraseBase64 = Base64.encode(ByteArray(32) { 0xAB.toByte() })

        init {
            runBlocking {
                // getPassphrase is a regular (non-suspend) function
                every { passphraseStorage.getPassphrase(any()) }.calls { passphraseBase64 }
                // currentClientIdProvider is a suspend function
                everySuspend { currentClientIdProvider() }.returns(TestClient.CLIENT_ID.right())
            }
        }

        /**
         * Corrupted because it's just an empty file called "prekeys".
         * But nothing to migrate, this is just to test that we are calling recovery.
         */
        fun withCorruptedProteusStorage() = apply {
            val rootProteusPath = Paths.get("/tmp/rootProteusPath")
            if (rootProteusPath.exists()) {
                FileUtil.deleteDirectory(rootProteusPath.toString())
            }
            rootProteusPath.createDirectory()
            rootProteusPath.resolve("prekeys").createFile()
        }

        fun withProteusDatabase() = apply {
            val dbPath = "$rootProteusPath/$KEYSTORE_NAME"
            FileUtil.mkDirs(dbPath)
        }

        fun withProteusClient() = apply {
            // Create the directory structure for Proteus client
            val root = Paths.get(rootProteusPath)
            if (!root.exists()) {
                root.createDirectory()
            }
        }

        fun withCurrentClientIdFailure(failure: StorageFailure) = apply {
            runBlocking {
                everySuspend { currentClientIdProvider() }.returns(failure.left())
            }
        }

        fun arrange() = this to ProteusClientProviderImpl(
            rootProteusPath = rootProteusPath,
            userId = TestUser.USER_ID,
            passphraseStorage = passphraseStorage,
            dispatcher = KaliumDispatcherImpl,
            proteusMigrationRecoveryHandler = proteusMigrationRecoveryHandler,
            currentClientIdProvider = currentClientIdProvider,
        )

        companion object {
            const val KEYSTORE_NAME = "keystore"
        }
    }
}
