/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.db

import com.sun.jna.Platform
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.kmmSettings.DesktopProtectedSettings
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.sql.SQLException
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopStorageEncryptionTest {
    private val secret = ("x'" + "1f".repeat(32) + "'").encodeToByteArray()
    private val marker = "test only message 72531061"

    @Test
    fun migratesWalDataAndRejectsWrongKeysAndCorruption() = fixture { dir ->
        val db = dir.resolve("main.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { conn ->
            conn.createStatement().use {
                it.execute("PRAGMA journal_mode=WAL")
                it.execute("CREATE TABLE messages(id INTEGER PRIMARY KEY, text TEXT)")
                it.execute("PRAGMA user_version=7")
                it.execute("INSERT INTO messages VALUES(74, '$marker')")
            }
        }
        Files.writeString(dir.resolve("main.db.encrypting"), "interrupted output")
        DesktopDatabaseMigration.prepare(db, secret)
        assertTrue(desktopDatabaseNeedsExistingKey(db))
        val props = desktopCipherConfig(secret).apply { setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL) }.toProperties()
        DriverManager.getConnection("jdbc:sqlite:$db", props).use { conn ->
            conn.createStatement().use {
                it.executeQuery("SELECT text FROM messages WHERE id=74").use { rows -> assertEquals(marker, rows.getString(1)) }
                it.execute("INSERT INTO messages VALUES(75, '$marker')")
                for (file in dir.toFile().listFiles().orEmpty()) {
                    assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(marker), file.name)
                }
            }
        }
        assertFailsWith<SQLException> {
            DriverManager.getConnection("jdbc:sqlite:$db").use { it.createStatement().execute("SELECT * FROM messages") }
        }
        val other = ("x'" + "a5".repeat(32) + "'").encodeToByteArray()
        assertFailsWith<SQLException> { DriverManager.getConnection("jdbc:sqlite:$db", desktopCipherConfig(other).toProperties()).close() }
        val bytes = Files.readAllBytes(db)
        bytes[50] = (bytes[50].toInt() xor 0x80).toByte()
        Files.write(db, bytes)
        assertFailsWith<SQLException> { DriverManager.getConnection("jdbc:sqlite:$db", props).close() }
    }

    @Test
    fun migratesTheKaliumUserSchemaAndIsolatesBackupCleanup() = fixture { dir ->
        val user = UserIDEntity("account", "example.test")
        val data = PlatformDatabaseData(StorageData.FileBacked(dir.toFile()))
        userDatabaseBuilder(data, user, null, Dispatchers.IO, true, false).close()
        val reopened = userDatabaseBuilder(data, user, UserDBSecret(secret), Dispatchers.IO, true, false)
        try {
            assertTrue(reopened.databaseExporter.deleteBackupDBFile())
            assertTrue(Files.exists(dir.resolve("main.db")))
        } finally {
            reopened.close()
        }
        assertTrue(desktopDatabaseNeedsExistingKey(dir.resolve("main.db")))
    }

    @Test
    fun missingDatabaseWithRecoveryFilesStopsProvisioning() = fixture { dir ->
        val db = dir.resolve("main.db")
        for (suffix in listOf("-wal", "-shm", "-journal", ".encrypting")) {
            val recovery = dir.resolve("main.db$suffix")
            Files.writeString(recovery, marker)
            assertFailsWith<IllegalStateException> { desktopDatabaseNeedsExistingKey(db) }
            assertFailsWith<IllegalStateException> { DesktopDatabaseMigration.prepare(db, secret) }
            assertEquals(marker, Files.readString(recovery))
            assertTrue(Files.notExists(db))
            Files.delete(recovery)
        }
        assertFalse(desktopDatabaseNeedsExistingKey(db))
    }

    @Test
    fun failedMigrationPreservesSource() = fixture { dir ->
        val db = dir.resolve("main.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { conn ->
            conn.createStatement().execute("CREATE TABLE sample(id INTEGER PRIMARY KEY)")
        }
        val before = Files.readAllBytes(db)
        assertFailsWith<java.io.IOException> {
            DesktopDatabaseMigration.prepare(db, secret) { throw java.io.IOException("Injected before cutover") }
        }
        assertTrue(before.contentEquals(Files.readAllBytes(db)))
        DesktopDatabaseMigration.prepare(db, secret)
        assertTrue(desktopDatabaseNeedsExistingKey(db))
    }

    @Test
    fun abruptExitWithVerifiedStagingLeavesRecoverablePlaintextSource() = fixture { dir ->
        val db = dir.resolve("main.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use {
            it.createStatement().execute("CREATE TABLE original(id INTEGER PRIMARY KEY)")
        }
        val cp = generateSequence(javaClass.classLoader) { it.parent }.filterIsInstance<java.net.URLClassLoader>()
            .flatMap { it.urLs.asSequence() }.map { java.io.File(it.toURI()).path }.toList() +
            System.getProperty("java.class.path").split(java.io.File.pathSeparator)
        fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val args = dir.resolve("process.args")
        Files.writeString(args, "-cp\n" + quote(cp.distinct().joinToString(java.io.File.pathSeparator)) +
            "\n" + MigrationInterruptionProbe::class.java.name + "\n" + quote(db.toString()))
        val child = ProcessBuilder(java.io.File(System.getProperty("java.home"), "bin/java").path, "@$args")
            .redirectErrorStream(true).redirectOutput(dir.resolve("child.log").toFile()).start()
        try {
            assertTrue(child.waitFor(30, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(23, child.exitValue(), Files.readString(dir.resolve("child.log")))
        } finally {
            if (child.isAlive) child.destroyForcibly().waitFor()
        }
        assertFalse(desktopDatabaseNeedsExistingKey(db))
        assertTrue(desktopDatabaseNeedsExistingKey(dir.resolve("main.db.encrypting")))
        DesktopDatabaseMigration.prepare(db, secret)
        assertTrue(desktopDatabaseNeedsExistingKey(db))
        assertFalse(Files.exists(dir.resolve("main.db.encrypting")))
    }

    @Test
    fun systemKeyProtectsSettingsAndRejectsTampering() = fixture { dir ->
        if (!Platform.isMac() && !Platform.isWindows()) return@fixture
        val legacy = dir.resolve("app-preference")
        Files.writeString(legacy, "token=$marker\n")
        try {
            val settings = DesktopProtectedSettings(legacy)
            assertEquals(marker, settings.getStringOrNull("token"))
            assertFalse(Files.exists(legacy))
            settings.putString("token", marker.reversed())
            assertEquals(marker.reversed(), DesktopProtectedSettings(legacy).getStringOrNull("token"))
            dir.toFile().listFiles().orEmpty().forEach {
                val content = it.readBytes().toString(Charsets.ISO_8859_1)
                assertFalse(marker in content || marker.reversed() in content)
            }
            val ciphertext = dir.resolve("app-preference.encrypted")
            val bytes = Files.readAllBytes(ciphertext)
            bytes[bytes.lastIndex] = bytes.last().inc()
            val original = Files.readAllBytes(ciphertext)
            Files.write(ciphertext, bytes)
            assertFailsWith<javax.crypto.AEADBadTagException> { DesktopProtectedSettings(legacy) }
            Files.write(ciphertext, original)
            if (Platform.isMac()) deleteFixtureKey(legacy) else Files.delete(dir.resolve("app-preference.dpapi"))
            assertEquals("Missing " + (if (Platform.isMac()) "macOS" else "Windows") + " settings key",
                assertFails { DesktopProtectedSettings(legacy) }.message)
            assertTrue(original.contentEquals(Files.readAllBytes(ciphertext)))
        } finally {
            if (Platform.isMac()) deleteFixtureKey(legacy)
        }
    }

    private fun deleteFixtureKey(legacy: Path) {
        val digest = MessageDigest.getInstance("SHA-256").digest(legacy.toFile().canonicalPath.encodeToByteArray())
        val status = ProcessBuilder("/usr/bin/security", "delete-generic-password", "-s",
            "com.wire.prism.desktop.settings.v1", "-a", HexFormat.of().formatHex(digest)).start().waitFor()
        check(status == 0 || status == 44)
    }

    private fun fixture(block: (Path) -> Unit) {
        val path = Files.createTempDirectory("wire-desktop-encryption-test")
        try {
            block(path)
        } finally {
            path.toFile().deleteRecursively()
        }
    }
}

object MigrationInterruptionProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val secret = ("x'" + "1f".repeat(32) + "'").encodeToByteArray()
        DesktopDatabaseMigration.prepare(Path.of(args.single()), secret) { Runtime.getRuntime().halt(23) }
    }
}
