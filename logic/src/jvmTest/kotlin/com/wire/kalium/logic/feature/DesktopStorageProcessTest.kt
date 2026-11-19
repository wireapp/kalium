/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.dbPassphrase.USER_DB_PASSPHRASE_PREFIX_V2
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val accounts = listOf(UserId("same", "first.invalid"), UserId("same", "second.invalid"))
private const val marker = "fixture-secret-for-restart"

class DesktopStorageProcessTest {
    @Test
    fun migratesBothAccountsAndReopensWithSystemKeyInAnotherProcess() = runBlocking {
        val os = System.getProperty("os.name")
        if (!os.startsWith("Mac") && !os.startsWith("Windows")) return@runBlocking
        val root = Files.createTempDirectory("kalium-desktop-process-").toFile()
        try {
            GlobalPrefProvider(root.path, false).passphraseStorage.setPassphrase("test-marker", marker)
            val db = globalDatabaseProvider(PlatformDatabaseData(StorageData.FileBacked(File(root, "global-storage"))),
                Dispatchers.IO, null)
            try {
                val url = "https://example.invalid"
                db.serverConfigurationDAO.insert(ServerConfigurationDAO.InsertData(
                    id = "fixture-host", apiBaseUrl = url, accountBaseUrl = url, webSocketBaseUrl = "wss://example.invalid",
                    blackListUrl = url, teamsUrl = url, websiteUrl = url, title = "Fixture", isOnPremises = false,
                    federation = false, domain = null, commonApiVersion = 8,
                    apiProxyHost = null, apiProxyNeedsAuthentication = null, apiProxyPort = null))
                for (id in accounts) {
                    val entity = UserIDEntity(id.value, id.domain)
                    File(root, FileNameUtil.userPrefFile(entity)).writeText("legacy=value-for-${id.domain}\n")
                    db.accountsDAO.insertOrReplace(entity, null, null, "fixture-host", false)
                    userDatabaseBuilder(PlatformDatabaseData(StorageData.FileBacked(File(root, "${id.domain}/${id.value}/storage"))),
                        entity, null, Dispatchers.IO).close()
                }
            } finally {
                db.close()
            }
            val cp = generateSequence(javaClass.classLoader) { it.parent }.filterIsInstance<URLClassLoader>()
                .flatMap { it.urLs.asSequence() }.map { File(it.toURI()).path }.toList() +
                System.getProperty("java.class.path").split(File.pathSeparator)
            val args = File(root, "process.args")
            val log = File(root, "process.log")
            for (mode in listOf("migrate", "reopen", "missing", "missing-global")) {
                fun quote(path: String) = "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                args.writeText("-cp\n" + quote(cp.distinct().joinToString(File.pathSeparator)) + "\n" +
                    DesktopStorageProcessProbe::class.java.name + "\n" + quote(root.path) + "\n" + mode)
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "@${args.path}")
                    .redirectErrorStream(true).redirectOutput(log).start()
                try {
                    assertTrue(process.waitFor(60, TimeUnit.SECONDS))
                    assertEquals(0, process.exitValue(), log.readText().takeLast(3000))
                } finally {
                    if (process.isAlive) process.destroyForcibly().waitFor()
                }
            }
        } finally {
            if (os.startsWith("Mac")) {
                val names = accounts.map { "${it.domain}/${it.value}/storage/user-preference" } + FileNameUtil.appPrefFile()
                for (name in names) {
                    val path = File(root, name).canonicalPath
                    val account = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(path.encodeToByteArray()))
                    ProcessBuilder("/usr/bin/security", "delete-generic-password", "-s", "com.wire.prism.desktop.settings.v1",
                        "-a", account).start().waitFor()
                }
            }
            root.deleteRecursively()
        }
    }
}

object DesktopStorageProcessProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(args[0])
        if (args[1] == "missing-global") {
            val keys = GlobalPrefProvider(root.path, true, true).passphraseStorage
            keys.clearPassphrase("global_db_passphrase_alias_v2")
            val error = runCatching {
                CoreLogic(root.path, KaliumConfigs(enableCalling = false, mockedRequests = emptyList()), "fixture", protectDesktopStorage = true)
            }.exceptionOrNull()
            check(error?.message == "Missing global database key")
            check(keys.getPassphrase("global_db_passphrase_alias_v2") == null)
            exitProcess(0)
        }
        val logic = CoreLogic(root.path, KaliumConfigs(enableCalling = false, mockedRequests = emptyList()), "storage-fixture",
            protectDesktopStorage = true)
        // Construction migrates inactive accounts before any session starts.
        for (account in accounts) {
            val db = File(root, "${account.domain}/${account.value}/storage/main.db")
            check(com.wire.kalium.persistence.db.desktopDatabaseNeedsExistingKey(db.toPath()))
            val old = File(root, FileNameUtil.userPrefFile(UserIDEntity(account.value, account.domain)))
            check(!old.exists())
            val settings = File(db.parentFile, "user-preference.encrypted")
            check("value-for-" !in settings.readBytes().toString(Charsets.ISO_8859_1))
        }
        val keys = GlobalPrefProvider(root.path, true, true).passphraseStorage
        check(keys.getPassphrase("test-marker") == marker)
        val aliases = accounts.map { "${USER_DB_PASSPHRASE_PREFIX_V2}_$it" }
        if (args[1] == "reopen") {
            val orphan = File(root, "fixture-orphan-keystore").apply { mkdirs() }
            File(orphan, "keystore").writeText("fixture for existing native store")
            val helper = com.wire.kalium.logic.util.SecurityHelperImpl(keys)
            runBlocking {
                check(runCatching { helper.mlsDBSecret(accounts.first(), orphan.path) }.exceptionOrNull()?.message ==
                    "Missing key for existing Core Crypto store")
                check(runCatching { helper.proteusDBSecret(accounts.first(), orphan.path) }.exceptionOrNull()?.message ==
                    "Missing key for existing Core Crypto store")
            }
        }
        if (args[1] == "missing") {
            keys.clearPassphrase(aliases.first())
            check(runCatching { logic.getSessionScope(accounts.first()) }.exceptionOrNull()?.message == "Missing user database key")
            check(keys.getPassphrase(aliases.first()) == null)
        } else {
            for (user in accounts) logic.getSessionScope(user)
            val first = checkNotNull(keys.getPassphrase(aliases.first()))
            val second = checkNotNull(keys.getPassphrase(aliases.last()))
            check(first != second)
            val protected = File(root, "app-preference.encrypted").readBytes().toString(Charsets.ISO_8859_1)
            check(marker !in protected && first !in protected && second !in protected)
        }
        exitProcess(0)
    }
}
