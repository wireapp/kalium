/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.persistence.db

import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

class JvmUserDatabaseUpgradeTest : UserDatabaseUpgradeTest() {
    override suspend fun withDatabase(
        fixture: String?,
        explicitMigration: Boolean,
        block: suspend (open: () -> UserDatabaseBuilder) -> Unit,
    ) {
        val root = Files.createTempDirectory("kalium-user-upgrade").toFile()
        val data = PlatformDatabaseData(StorageData.FileBacked(root))
        try {
            fixture?.let { File("src/commonTest/resources/user-database-upgrade/$it.db").copyTo(File(root, "main.db")) }
            if (explicitMigration) {
                val driver = userDatabaseDriverByPath(data, File(root, "main.db").absolutePath, null, false)
                try {
                    assertTrue(driver.migrate(UserDatabaseSchema))
                    assertTrue(driver.migrate(UserDatabaseSchema))
                } finally {
                    driver.close()
                }
            }
            block { userDatabaseBuilder(data, userId, null, dispatcher) }
        } finally {
            root.deleteRecursively()
        }
    }
}
