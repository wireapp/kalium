package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.exceptions.ProteusStorageMigrationException
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mock
import io.mockative.any
<<<<<<< HEAD
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
=======
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import io.mockative.given
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
>>>>>>> c5c2468502 (chore: bulletproofing crypto box to cc migration (WPB-14250) (🍒4.6) (#3136))
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.exists

class ProteusClientProviderTest {

<<<<<<< HEAD
=======
    @Ignore("Old version of the testing library, wont fix")
>>>>>>> c5c2468502 (chore: bulletproofing crypto box to cc migration (WPB-14250) (🍒4.6) (#3136))
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
<<<<<<< HEAD
            coVerify { arrangement.proteusMigrationRecoveryHandler.clearClientData(any()) }.wasInvoked(once)
=======
            verify(arrangement.proteusMigrationRecoveryHandler)
                .coroutine { clearClientData({}) }
                .wasInvoked(exactly = once)
>>>>>>> c5c2468502 (chore: bulletproofing crypto box to cc migration (WPB-14250) (🍒4.6) (#3136))
        }
    }

    private class Arrangement {

        @Mock
        val passphraseStorage = mock(PassphraseStorage::class)

        @Mock
        val proteusMigrationRecoveryHandler = mock(ProteusMigrationRecoveryHandler::class)

        init {
<<<<<<< HEAD
            every { passphraseStorage.getPassphrase(any()) }.returns("passphrase")
=======
            given(passphraseStorage)
                .suspendFunction(passphraseStorage::getPassphrase)
                .whenInvokedWith(any<String>())
                .thenReturn("passphrase")
>>>>>>> c5c2468502 (chore: bulletproofing crypto box to cc migration (WPB-14250) (🍒4.6) (#3136))
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
