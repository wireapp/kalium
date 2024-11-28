package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.exceptions.ProteusStorageMigrationException
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import io.mockative.given
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.exists

class ProteusClientProviderTest {

    @Test
    fun givenGettingOrCreatingAProteusClient_whenMigrationPerformedAndFails_thenCatchErrorAndStartRecovery() = runTest {
        // given
        val (arrangement, proteusClientProvider) = Arrangement()
            .withCorruptedProteusStorage()
            .arrange()

        // when - then
        val clearLocalFiles = suspend { }
        try {
            proteusClientProvider.getOrCreate()
        } catch (e: ProteusStorageMigrationException) {
            verify(arrangement.proteusMigrationRecoveryHandler)
                .coroutine { clearClientData(clearLocalFiles) }
                .wasInvoked(exactly = once)
        }
    }

    private class Arrangement {

        @Mock
        val passphraseStorage = mock(PassphraseStorage::class)

        @Mock
        val proteusMigrationRecoveryHandler = mock(ProteusMigrationRecoveryHandler::class)

        init {
            given(passphraseStorage)
                .suspendFunction(passphraseStorage::getPassphrase)
                .whenInvokedWith(any())
                .thenReturn("passphrase")
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

        fun arrange() = this to ProteusClientProviderImpl(
            rootProteusPath = "/tmp/rootProteusPath",
            userId = TestUser.USER_ID,
            passphraseStorage = passphraseStorage,
            kaliumConfigs = KaliumConfigs(encryptProteusStorage = true),
            dispatcher = KaliumDispatcherImpl,
            proteusMigrationRecoveryHandler = proteusMigrationRecoveryHandler
        )
    }
}
