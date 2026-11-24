/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.kmmSettings

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import com.russhwolf.settings.PropertiesSettings
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import java.io.ByteArrayOutputStream
import java.util.Properties

/** Uses the same driver and transaction as the account table, exclusively for protected JVM profiles. */
internal class ProtectedJvmPreferences(private val database: GlobalDatabaseBuilder) {
    private val driver = database.sqlDriver
    private val transactions = object : TransacterImpl(driver) {}

    init {
        // Keep the protected-only table out of the common schema and Android migrations.
        driver.execute(
            null,
            "CREATE TABLE IF NOT EXISTS JvmProtectedPreferences (id INTEGER PRIMARY KEY CHECK(id = 1), value BLOB NOT NULL)",
            0,
        )
    }

    suspend fun <T> inSessionTransaction(action: suspend () -> T): T = database.inTransaction(action)

    fun <T> read(action: (KaliumPreferences) -> T): T = action(preferences(load()))

    fun <T> update(action: (KaliumPreferences) -> T): T = transactions.transactionWithResult {
        // SQLDelight starts a deferred transaction. Take the writer slot before reading the current value.
        driver.execute(null, "INSERT INTO JvmProtectedPreferences VALUES(1, X'') ON CONFLICT(id) DO NOTHING", 0)
        val properties = load()
        val result = action(preferences(properties))
        save(properties)
        result
    }

    private fun preferences(properties: Properties): KaliumPreferences = KaliumPreferencesSettings(PropertiesSettings(properties))

    private fun load(): Properties {
        val bytes = driver.executeQuery(null, "SELECT value FROM JvmProtectedPreferences WHERE id = 1", { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getBytes(0) else null)
        }, 0).value
        return Properties().apply {
            if (bytes != null) {
                try {
                    bytes.inputStream().use(::load)
                } finally {
                    bytes.fill(0)
                }
            }
        }
    }

    private fun save(properties: Properties) {
        val bytes = ByteArrayOutputStream().use { output ->
            properties.store(output, null)
            output.toByteArray()
        }
        try {
            driver.execute(
                null,
                "UPDATE JvmProtectedPreferences SET value = ? WHERE id = 1",
                1,
            ) { bindBytes(0, bytes) }
        } finally {
            bytes.fill(0)
        }
    }
}
