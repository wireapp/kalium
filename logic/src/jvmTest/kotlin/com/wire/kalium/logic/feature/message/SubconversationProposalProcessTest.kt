/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PendingSubconversationProposalsImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.userDatabaseBuilder
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SubconversationProposalProcessTest {
    @Test
    fun killedOwnerLeavesScheduledProposalForNextProcess() = runBlocking {
        val directory = Files.createTempDirectory("subgroup-proposal-kill-").toFile()
        try {
            val classpath = generateSequence(javaClass.classLoader) { it.parent }
                .filterIsInstance<URLClassLoader>().flatMap { it.urLs.asSequence() }
                .map { File(it.toURI()).path }.toList() + System.getProperty("java.class.path").split(File.pathSeparator)
            val arguments = File(directory, "java.args")
            arguments.writeText("-cp\n\"${classpath.distinct().joinToString(File.pathSeparator)}\"\n" +
                "${SubconversationProposalProcessProbe::class.java.name}\n\"${directory.path}\"\n")
            val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "@${arguments.path}")
                .redirectErrorStream(true).redirectOutput(File(directory, "child.log")).start()
            try {
                withTimeout(30_000) {
                    while (!File(directory, "ready").exists()) {
                        check(process.isAlive) { File(directory, "child.log").readText() }
                        delay(20)
                    }
                }
                process.destroyForcibly()
                assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                val db = proposalProcessDatabase(directory)
                try {
                    val pending = PendingSubconversationProposalsImpl(db.metadataDAO)
                    val record = pending.observe().first().single()
                    assertEquals(GroupID("durable-subgroup"), record.group)
                    assertEquals(Instant.parse("2020-01-01T00:00:00Z"), record.deadline)
                    pending.acknowledge(record)
                    assertTrue(pending.observe().first().isEmpty())
                } finally { db.close() }
            } finally { if (process.isAlive) process.destroyForcibly().waitFor() }
        } finally { directory.deleteRecursively() }
    }
}

internal fun proposalProcessDatabase(directory: File) = userDatabaseBuilder(
    PlatformDatabaseData(StorageData.FileBacked(File(directory, "account.db"))),
    UserIDEntity("synthetic-proposal-owner", "test"), null, Dispatchers.IO,
)

object SubconversationProposalProcessProbe {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val directory = File(args.single())
        val db = proposalProcessDatabase(directory)
        PendingSubconversationProposalsImpl(db.metadataDAO).schedule(GroupID("durable-subgroup"), Instant.parse("2020-01-01T00:00:00Z"))
        File(directory, "ready").writeText("ready")
        // Parent kills this synthetic owner with its SQLite handle still open, before ACK.
        kotlinx.coroutines.awaitCancellation()
    }
}
