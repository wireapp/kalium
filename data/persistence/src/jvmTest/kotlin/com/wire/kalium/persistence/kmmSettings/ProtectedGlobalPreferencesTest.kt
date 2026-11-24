/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.ProxyCredentialsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.db.protectedGlobalDatabaseProvider
import com.wire.kalium.persistence.protection.JvmStorageKeyStore
import com.wire.kalium.persistence.protection.MacStorageKeyStore
import com.wire.kalium.persistence.protection.runStorageProcessProbe
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProtectedGlobalPreferencesTest {
    @Test
    fun storesAndRemovesSessionMaterialWithoutWritingPlaintext() {
        withProfile { root ->
            exercise(root, keyStore, "write")
            exercise(root, keyStore, "read")
            assertNoPlaintext(root)
            exercise(root, keyStore, "delete")
            exercise(root, keyStore, "deleted")
            assertNoPlaintext(root)
        }
    }

    @Test
    fun legacySettingsKeepTheirFormatAndAPlainDatabaseCannotHostProtectedTokens() {
        withProfile { root ->
            GlobalPrefProvider(root.path).authTokenStorage.addOrReplace(FIRST, PROXY)
            val legacy = root.resolve(FileNameUtil.appPrefFile())
            val original = legacy.readBytes()
            val data = PlatformDatabaseData(StorageData.FileBacked(root))
            val database = globalDatabaseProvider(data, Dispatchers.IO, passphrase = null)
            try {
                assertFailsWith<IllegalStateException> { GlobalPrefProvider.protectedStorage(database) }
                assertContentEquals(original, legacy.readBytes())
                assertEquals(FIRST, GlobalPrefProvider(root.path).authTokenStorage.getToken(FIRST.userId))
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun replacingByADirectSessionRemovesTheOldProxyCredentials() {
        withProfile { root ->
            val database = protectedGlobalDatabaseProvider(root, keyStore, Dispatchers.IO)
            try {
                val tokens = GlobalPrefProvider.protectedStorage(database).authTokenStorage
                tokens.addOrReplace(FIRST, PROXY)
                tokens.addOrReplace(FIRST, null)
                assertEquals(FIRST, tokens.getToken(FIRST.userId))
                assertNull(tokens.proxyCredentials(FIRST.userId))
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun concurrentWritersOnSeparateConnectionsKeepAllCredentials() {
        withProfile { root ->
            val firstDatabase = protectedGlobalDatabaseProvider(root, keyStore, Dispatchers.IO)
            val secondDatabase = protectedGlobalDatabaseProvider(root, keyStore, Dispatchers.IO)
            try {
                val stores = listOf(firstDatabase, secondDatabase).map { GlobalPrefProvider.protectedStorage(it).authTokenStorage }
                val accounts = List(8) { FIRST.copy(userId = UserIDEntity("fixture-$it", "concurrent.invalid")) }
                runBlocking {
                    accounts.mapIndexed { index, account ->
                        async(Dispatchers.IO) { stores[index % stores.size].addOrReplace(account, null) }
                    }.awaitAll()
                }
                accounts.forEach { assertEquals(it, stores.first().getToken(it.userId)) }
            } finally {
                secondDatabase.close()
                firstDatabase.close()
            }
        }
    }

    @Test
    fun reopensAndDeletesSessionMaterialAcrossProcessesUsingTheIsolatedKeychain() {
        val keychain = System.getenv("KALIUM_TEST_KEYCHAIN")
        assumeTrue(System.getProperty("os.name") == "Mac OS X" && !keychain.isNullOrBlank())
        withProfile { root ->
            for (action in listOf("write", "read", "delete", "deleted")) {
                runStorageProcessProbe(
                    ProtectedPreferenceRestartProbe::class.java, root, root.path, checkNotNull(keychain), action
                )
                assertNoPlaintext(root)
            }
        }
    }

    private fun withProfile(action: (File) -> Unit) {
        val root = createTempDirectory("kalium-token-profile").toFile()
        try {
            action(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private val keyStore = JvmStorageKeyStore { _, _ -> ByteArray(32) { 1 } }

    private fun assertNoPlaintext(root: File) {
        assertFalse(root.resolve(FileNameUtil.appPrefFile()).exists())
        val markers = listOf(FIRST.accessToken, FIRST.refreshToken, PROXY.password, UPDATED_ACCESS, SECOND.accessToken)
        for (file in root.listFiles().orEmpty().filter { it.isFile }) {
            val content = file.readBytes().toString(Charsets.ISO_8859_1)
            markers.forEach { assertFalse(it in content) }
        }
    }
}

object ProtectedPreferenceRestartProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        exercise(File(args[0]), MacStorageKeyStore(Path.of(args[1])), args[2])
    }
}

private val FIRST = AuthTokenEntity(UserIDEntity("fixture", "one.invalid"), "fixture-access-A", "fixture-refresh-A", "Bearer", null)
private val SECOND = AuthTokenEntity(UserIDEntity("fixture", "two.invalid"), "fixture-access-B", "fixture-refresh-B", "Bearer", null)
private val PROXY = ProxyCredentialsEntity("fixture-proxy-user", "fixture-proxy-password")
private const val UPDATED_ACCESS = "fixture-updated-access"

private fun exercise(root: File, store: JvmStorageKeyStore, action: String) {
    val database = protectedGlobalDatabaseProvider(root, store, Dispatchers.IO)
    try {
        val storage = GlobalPrefProvider.protectedStorage(database).authTokenStorage
        when (action) {
            "write" -> {
                storage.addOrReplace(FIRST, PROXY)
                storage.addOrReplace(SECOND, null)
                assertEquals(FIRST.copy(accessToken = UPDATED_ACCESS), storage.updateToken(FIRST.userId, UPDATED_ACCESS, "Bearer", null))
            }
            "read" -> {
                assertEquals(FIRST.copy(accessToken = UPDATED_ACCESS), storage.getToken(FIRST.userId))
                assertEquals(PROXY, storage.proxyCredentials(FIRST.userId))
                assertEquals(SECOND, storage.getToken(SECOND.userId))
            }
            "delete" -> storage.deleteToken(FIRST.userId)
            "deleted" -> {
                assertNull(storage.getToken(FIRST.userId))
                assertNull(storage.proxyCredentials(FIRST.userId))
                assertEquals(SECOND, storage.getToken(SECOND.userId))
            }
            else -> error("Unexpected storage probe action")
        }
    } finally {
        database.close()
    }
}
