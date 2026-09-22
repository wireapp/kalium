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

@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.dbPassphrase.PROTEUS_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleAccountRuntimeTest {
    private val userId = UserId("fcbe2b38-c66a-4fbe-9e54-6fab39c017a4", "example.com")
    private val entity = UserIDEntity(userId.value, userId.domain)
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun `missing readiness and busy ownership create no account storage`() = runTest {
        withFixture { fixture ->
            assertEquals(Either.Left(AccountRuntimeFailure.NOT_PREPARED), fixture.refused())
            val lease = fixture.acquire()
            try {
                assertEquals(Either.Left(AccountRuntimeFailure.BUSY), fixture.refused())
            } finally {
                lease.release()
            }
            assertFalse(FileSystem.SYSTEM.exists(fixture.account.toPath()))
        }
    }

    @Test
    fun `missing token or key does not create crypto or SQLite`() = runTest {
        withFixture { fixture ->
            fixture.prepareReadiness()
            assertEquals(Either.Left(AccountRuntimeFailure.STORAGE), fixture.refused())
            fixture.preferences.authTokenStorage.addOrReplace(AuthTokenEntity(entity, "access", "refresh", "Bearer", "cookie"), null)
            assertEquals(Either.Left(AccountRuntimeFailure.STORAGE), fixture.refused())
            assertFalse(FileSystem.SYSTEM.exists("${fixture.account}/proteus".toPath()))
            assertFalse(FileSystem.SYSTEM.exists("${fixture.account}/storage".toPath()))
        }
    }

    @Test
    fun `missing SQLite refuses before opening a nonempty corrupt crypto store`() = runTest {
        withFixture { fixture ->
            fixture.prepareReadiness()
            fixture.prepareCredentials()
            fixture.corruptCryptoFile()
            assertEquals(Either.Left(AccountRuntimeFailure.STORAGE), fixture.refused())
            assertFalse(FileSystem.SYSTEM.exists("${fixture.account}/storage".toPath()))
            assertEquals("invalid crypto", FileSystem.SYSTEM.read("${fixture.account}/proteus/keystore".toPath()) { readUtf8() })
        }
    }

    @Test
    fun `incompatible SQLite refuses before crypto and stays unchanged`() = runTest {
        withFixture { fixture ->
            fixture.prepareReadiness()
            fixture.prepareCredentials()
            fixture.corruptCryptoFile()
            fixture.prepareDatabase().close()
            val file = "${fixture.account}/storage/${FileNameUtil.userDBName(entity)}".toPath()
            val bytes = FileSystem.SYSTEM.read(file) { readByteArray() }
            // After SQLite closes/checkpoints, change its big-endian user_version header directly.
            val version = UserDatabase.Schema.version + 1
            repeat(4) { bytes[60 + it] = (version shr (8 * (3 - it))).toByte() }
            FileSystem.SYSTEM.write(file) { write(bytes) }
            val before = bytes.toList()
            assertEquals(Either.Left(AccountRuntimeFailure.INCOMPATIBLE), fixture.refused())
            assertEquals(before, FileSystem.SYSTEM.read(file) { readByteArray().toList() })
        }
    }

    @Test
    fun `changed registration closes SQLite and refuses before crypto`() = runTest {
        withFixture { fixture ->
            fixture.prepareReadiness()
            fixture.prepareCredentials()
            fixture.corruptCryptoFile()
            fixture.prepareDatabase().close()
            assertEquals(Either.Left(AccountRuntimeFailure.STORAGE), fixture.refused())
        }
    }

    @Test
    fun `native crypto opening failure retains lease and other accounts remain available`() = runTest {
        withFixture { fixture ->
            fixture.prepareReadiness()
            fixture.prepareCredentials()
            fixture.corruptCryptoFile()
            val database = fixture.prepareDatabase()
            ClientRegistrationStorageImpl(database.metadataDAO).setRegisteredClientId("abc123")
            database.close()
            fixture.quarantined = true
            assertEquals(Either.Left(AccountRuntimeFailure.STORAGE), fixture.refused())
            assertEquals(Either.Left(AccountRuntimeFailure.BUSY), fixture.owner.tryAcquire(userId))
            val other = UserId("other", userId.domain)
            val lease = assertIs<Either.Right<AccountRuntimeLease>>(fixture.owner.tryAcquire(other)).value
            lease.release()
        }
    }

    @Test
    fun `caller cancellation drains owned work and allows a real crypto reopen`() = runTest {
        withContext(Dispatchers.Default) {
            withFixture { fixture ->
                fixture.prepareReadiness()
                fixture.prepareCredentials()
                val database = fixture.prepareDatabase()
                ClientRegistrationStorageImpl(database.metadataDAO).setRegisteredClientId("abc123")
                database.close()
                val crypto = coreCryptoCentral("${fixture.account}/proteus", key)
                val fingerprint = crypto.proteusClient().transaction("prepare-runtime-test") { it.getLocalFingerprint() }
                crypto.close()
                val entered = CompletableDeferred<Unit>()
                val drained = CompletableDeferred<Unit>()
                val opening = async {
                    AppleAccountRuntime.use(fixture.root, userId, fixture.config, "runtime-test", emptyMap()) {
                        entered.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            drained.complete(Unit)
                        }
                    }
                }
                entered.await()
                opening.cancelAndJoin()
                assertTrue(drained.isCompleted)
                assertEquals(Either.Right(fingerprint), AppleAccountRuntime.use(
                    fixture.root, userId, fixture.config, "runtime-test", emptyMap()
                ) { runtime ->
                    runtime.proteusCoreCrypto.proteusClient().transaction("reopen-runtime-test") { it.getLocalFingerprint() }
                })
            }
        }
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            block(fixture)
            if (!fixture.quarantined) fixture.acquire().release()
            assertFalse(FileSystem.SYSTEM.exists("${fixture.root}/global-storage".toPath()))
        } finally {
            if (!fixture.quarantined) FileSystem.SYSTEM.deleteRecursively(fixture.root.toPath())
        }
    }

    private inner class Fixture {
        private val identity = NSUUID.UUID().UUIDString
        val root = "${NSTemporaryDirectory()}kalium-runtime-$identity"
        val account = appleAccountPath(root, userId)
        val config = ApplePersistenceConfig("com.wire.kalium.test.$identity")
        val preferences by lazy { GlobalPrefProvider(config) }
        val owner = AppleAccountRuntimeOwner(root)
        var quarantined = false

        init {
            FileSystem.SYSTEM.createDirectories(root.toPath())
        }

        suspend fun acquire() = assertIs<Either.Right<AccountRuntimeLease>>(owner.tryAcquire(userId)).value

        suspend fun refused() = AppleAccountRuntime.use(root, userId, config, "runtime-test", emptyMap()) {
            error("Unprepared runtime reached caller work")
        }

        suspend fun prepareReadiness() {
            val lease = acquire()
            try {
                FileSystem.SYSTEM.createDirectories(account.toPath())
                ApplePreparedAccountStore(root, userId).publish(lease, PreparedAccount(
                    userId, "abc123",
                    ServerConfig("test", ServerConfig.DUMMY, ServerConfig.MetaData(false, CommonApiVersionType.Valid(8), userId.domain)),
                    null, null,
                ))
            } finally {
                lease.release()
            }
        }

        fun prepareCredentials() {
            preferences.authTokenStorage.addOrReplace(AuthTokenEntity(entity, "access", "refresh", "Bearer", "cookie"), null)
            preferences.passphraseStorage.setPassphrase("${PROTEUS_DB_PASSPHRASE_PREFIX_V2}_$userId", Base64.encode(key))
        }

        fun corruptCryptoFile() {
            FileSystem.SYSTEM.createDirectories("$account/proteus".toPath())
            FileSystem.SYSTEM.write("$account/proteus/keystore".toPath()) { writeUtf8("invalid crypto") }
        }

        fun prepareDatabase() = userDatabaseBuilder(
            PlatformDatabaseData(StorageData.FileBacked("$account/storage"), useGradleSafeSqliterLogging = true),
            entity, null, Dispatchers.Default,
        )
    }
}
