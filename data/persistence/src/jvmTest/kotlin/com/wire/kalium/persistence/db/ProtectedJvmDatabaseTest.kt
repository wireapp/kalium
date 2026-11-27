/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.QueryResult
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.protection.JvmStorageKeyStore
import com.wire.kalium.persistence.protection.MacStorageKeyStore
import com.wire.kalium.persistence.util.FileNameUtil
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class ProtectedJvmDatabaseTest {
    @Test
    fun globalAndAccountDatabasesPersistEncryptedRows() = runBlocking {
        val root = createTempDirectory("kalium database ?").toFile()
        try {
            val store = TestKeyStore()
            exerciseProtectedDatabases(root, store, write = true)
            exerciseProtectedDatabases(root, store, write = false)
            assertEquals(2, store.keys.size)
            for (file in listOf(root.resolve("global/${FileNameUtil.globalDBName()}"), root.resolve("account/main.db"))) {
                val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
                assertFalse(bytes.startsWith("SQLite format 3"))
                assertFalse(MARKER in bytes)
            }
        } finally {
            root.deleteRecursively()
        }
        Unit
    }

    @Test
    fun wrongOrMissingKeyDoesNotChangeExistingDatabases() = runBlocking {
        val root = createTempDirectory("kalium-key-failure").toFile()
        try {
            val store = TestKeyStore()
            exerciseProtectedDatabases(root, store, write = true)
            val globalDirectory = root.resolve("global")
            val accountDirectory = root.resolve("account")
            val databases: Map<File, (JvmStorageKeyStore) -> Unit> = mapOf(
                globalDirectory.resolve(FileNameUtil.globalDBName()) to {
                    protectedGlobalDatabaseProvider(globalDirectory, it, Dispatchers.IO).close()
                },
                accountDirectory.resolve("main.db") to {
                    protectedUserDatabaseBuilder(accountDirectory, account, it, Dispatchers.IO).close()
                },
            )
            val wrongKey = JvmStorageKeyStore { _, allowCreation ->
                assertFalse(allowCreation)
                ByteArray(32)
            }
            for ((file, open) in databases) {
                val original = file.readBytes()
                assertFailsWith<IllegalStateException> { open(wrongKey) }
                assertFailsWith<IllegalStateException> { open(TestKeyStore()) }
                assertContentEquals(original, file.readBytes())
            }
            exerciseProtectedDatabases(root, store, write = false)
        } finally {
            root.deleteRecursively()
        }
        Unit
    }

    @Test
    fun plaintextClientKeepsItsDataAndProtectedOpenRefusesIt() = runBlocking {
        val root = createTempDirectory("kalium-legacy-db").toFile()
        try {
            val data = PlatformDatabaseData(StorageData.FileBacked(root))
            val legacy = globalDatabaseProvider(data, Dispatchers.IO, null)
            try {
                legacy.accountsDAO.insertOrReplace(account, null, null, "fixture", false)
            } finally {
                legacy.close()
            }
            val file = root.resolve(FileNameUtil.globalDBName())
            val original = file.readBytes()
            val store = TestKeyStore()
            assertFailsWith<IllegalStateException> {
                protectedGlobalDatabaseProvider(root, store, Dispatchers.IO)
            }
            assertTrue(store.keys.isEmpty())
            assertContentEquals(original, file.readBytes())

            val reopened = globalDatabaseProvider(data, Dispatchers.IO, null)
            try {
                assertTrue(reopened.accountsDAO.doesAccountExist(account))
            } finally {
                reopened.close()
            }
        } finally {
            root.deleteRecursively()
        }
        Unit
    }

    @Test
    fun reopensBothDatabasesInASeparateProcessWithTheSameIsolatedKeychain() {
        val keychain = System.getenv("KALIUM_TEST_KEYCHAIN")
        assumeTrue(System.getProperty("os.name") == "Mac OS X" && !keychain.isNullOrBlank())
        val root = createTempDirectory("kalium-db-restart").toFile()
        try {
            val classpath = generateSequence(javaClass.classLoader) { it.parent }
                .filterIsInstance<URLClassLoader>()
                .flatMap { it.urLs.asSequence() }
                .map { File(it.toURI()).path }
                .toList() + System.getProperty("java.class.path").split(File.pathSeparator)
            val java = File(System.getProperty("java.home"), "bin/java").path
            for (action in listOf("write", "read")) {
                val log = root.resolve("$action.log")
                val process = ProcessBuilder(
                    java, "-Dorg.sqlite.lib.path=${System.getProperty("org.sqlite.lib.path")}",
                    "-cp", classpath.distinct().joinToString(File.pathSeparator),
                    ProtectedDatabaseRestartProbe::class.java.name, root.path, checkNotNull(keychain), action
                ).redirectErrorStream(true).redirectOutput(log).start()
                try {
                    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Database process timed out")
                    assertEquals(0, process.exitValue(), log.readText().takeLast(3000))
                } finally {
                    if (process.isAlive) process.destroyForcibly().waitFor()
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private class TestKeyStore : JvmStorageKeyStore {
        val keys = mutableMapOf<String, ByteArray>()

        override fun load(keyId: String, allowCreation: Boolean): ByteArray {
            val key = keys[keyId] ?: run {
                check(allowCreation) { "Missing test key" }
                ByteArray(32).also {
                    SecureRandom().nextBytes(it)
                    keys[keyId] = it
                }
            }
            return key.copyOf()
        }
    }
}

object ProtectedDatabaseRestartProbe {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        exerciseProtectedDatabases(File(args[0]), MacStorageKeyStore(Path.of(args[1])), write = args[2] == "write")
    }
}

private val account = UserIDEntity("fixture-account", "example.invalid")
private const val MARKER = "kalium-protected-db-fixture-row"

private suspend fun exerciseProtectedDatabases(root: File, store: JvmStorageKeyStore, write: Boolean) {
    val global = protectedGlobalDatabaseProvider(root.resolve("global"), store, Dispatchers.IO)
    try {
        if (write) global.accountsDAO.insertOrReplace(account, null, null, MARKER, false)
        assertTrue(global.accountsDAO.doesAccountExist(account))
    } finally {
        global.close()
    }

    val user = protectedUserDatabaseBuilder(root.resolve("account"), account, store, Dispatchers.IO)
    try {
        if (write) {
            user.sqlDriver.execute(null, "CREATE TABLE StorageFixture(value TEXT NOT NULL)", 0)
            user.sqlDriver.execute(null, "INSERT INTO StorageFixture VALUES (?)", 1) { bindString(0, MARKER) }
        }
        val value = user.sqlDriver.executeQuery(null, "SELECT value FROM StorageFixture", { cursor ->
            check(cursor.next().value)
            QueryResult.Value(cursor.getString(0))
        }, 0).value
        assertEquals(MARKER, value)
    } finally {
        user.close()
    }
}
