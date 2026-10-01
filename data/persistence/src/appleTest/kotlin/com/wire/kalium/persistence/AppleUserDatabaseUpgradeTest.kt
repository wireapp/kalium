/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import kotlinx.cinterop.toKString
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.getenv
import com.wire.kalium.persistence.util.FileNameUtil
import kotlin.test.assertTrue

class AppleUserDatabaseUpgradeTest : UserDatabaseUpgradeTest() {
    override suspend fun withDatabase(
        fixture: String?,
        explicitMigration: Boolean,
        block: suspend (open: () -> UserDatabaseBuilder) -> Unit,
    ) {
        val files = NSFileManager.defaultManager
        val root = "${NSTemporaryDirectory()}kalium-user-upgrade-${NSUUID.UUID().UUIDString}"
        val name = FileNameUtil.userDBName(userId)
        val data = PlatformDatabaseData(StorageData.FileBacked(root), useGradleSafeSqliterLogging = true)
        assertTrue(files.createDirectoryAtPath(root, true, null, null))
        try {
            fixture?.let {
                val resources = checkNotNull(getenv("KALIUM_USER_UPGRADE_FIXTURES")?.toKString()) { "Missing SQLite fixture directory" }
                assertTrue(files.copyItemAtPath("$resources/$it.db", "$root/$name", null))
            }
            if (explicitMigration) {
                // Open at the fixture's real version so the explicit path, not automatic upgrade, does the work.
                val fixtureVersion = checkNotNull(fixture).takeLast(3).toLong()
                val schema = object : SqlSchema<QueryResult.Value<Unit>> by UserDatabaseSchema.synchronous() {
                    override val version = fixtureVersion
                }
                val driver = databaseDriver(root, name, schema) { useGradleSafeSqliterLogging = true }
                try {
                    assertTrue(driver.migrate(UserDatabaseSchema))
                    assertTrue(driver.migrate(UserDatabaseSchema))
                } finally {
                    driver.close()
                }
            }
            block { userDatabaseBuilder(data, userId, null, dispatcher) }
        } finally {
            assertTrue(files.removeItemAtPath(root, null))
        }
    }
}
