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
package com.wire.kalium.benchmarks.persistence

import com.wire.kalium.benchmarks.fixtures.BenchmarkEntities
import com.wire.kalium.benchmarks.fixtures.MessageFixture
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

internal fun database(
    directory: java.io.File,
    userId: UserIDEntity = BenchmarkEntities.selfUser.id,
): UserDatabaseBuilder = userDatabaseBuilder(
    platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(directory)),
    userId = userId,
    passphrase = null,
    dispatcher = Dispatchers.IO,
    enableWAL = true,
)

@State(Scope.Benchmark)
class MessageDatabase {
    private lateinit var directory: java.io.File
    lateinit var db: UserDatabaseBuilder
    internal val fixture = MessageFixture()

    @Setup(Level.Trial)
    fun setUp() {
        directory = Files.createTempDirectory("message-benchmark").toFile()
        db = database(directory)
        runBlocking { fixture.seed(db) }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        if (::db.isInitialized) db.nuke()
        if (::directory.isInitialized) directory.deleteRecursively()
    }
}
