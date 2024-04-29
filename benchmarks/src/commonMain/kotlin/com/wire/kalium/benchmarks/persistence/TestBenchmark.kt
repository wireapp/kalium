/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.benchmarks.persistence.DBTestSetup.conversationEntity
import com.wire.kalium.benchmarks.persistence.DBTestSetup.conversationId
import com.wire.kalium.benchmarks.persistence.DBTestSetup.userEntity1
import com.wire.kalium.benchmarks.persistence.DBTestSetup.userEntity2
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt

@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Fork(1)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
class TestBenchmark {

    @Benchmark
    fun messageInsertionWithoutTuningBenchmark(dbState: DBState, blackHole: Blackhole) = runBlocking {
        val messagesToInsert = generateRandomMessages(5000)
        blackHole.consume(dbState.db.messageDAO.insertOrIgnoreMessages(messagesToInsert))
    }

    @Benchmark
    fun queryMessagesWithoutTuningBenchmark(dbState: DBState, blackHole: Blackhole) = runBlocking {
        val messages = dbState.db.messageDAO.getMessagesByConversationAndVisibility(
            conversationId,
            MESSAGES_COUNT,
            0,
            listOf(MessageEntity.Visibility.VISIBLE)
        )

        val messagesCount = messages.first().size
        blackHole.consume(messagesCount)
    }

    companion object {

        const val MESSAGES_COUNT = 1000

        private fun generateRandomMessages(count: Int = MESSAGES_COUNT): List<MessageEntity> {
            val users = listOf(userEntity1, userEntity2)
            return buildList {
                repeat(count) {
                    add(
                        MessageEntity.Regular(
                            id = it.toString(),
                            conversationId = conversationId,
                            date = Instant.fromEpochMilliseconds(it.toLong()),
                            senderUserId = users.random().id,
                            status = MessageEntity.Status.entries.toTypedArray().random(),
                            visibility = MessageEntity.Visibility.VISIBLE,
                            content = generateRandomMessageContent(),
                            senderClientId = Random.nextLong(2_000).toString(),
                            editStatus = MessageEntity.EditStatus.NotEdited,
                            senderName = "senderName",
                            readCount = 0
                        )
                    )
                }
            }
        }

        private fun generateRandomMessageContent() = when (Random.nextInt(0..3)) {
            0 -> MessageEntityContent.Unknown(typeName = null, Random.nextBytes(1000))
            1 -> MessageEntityContent.Text(Random.nextBytes(100).toString())
            2 -> MessageEntityContent.Asset(
                1000,
                assetName = "test name",
                assetMimeType = "MP4",
                assetOtrKey = byteArrayOf(1),
                assetSha256Key = byteArrayOf(1),
                assetId = "assetId",
                assetToken = "",
                assetDomain = "domain",
                assetEncryptionAlgorithm = "",
                assetWidth = 111,
                assetHeight = 111,
                assetDurationMs = 10,
                assetNormalizedLoudness = byteArrayOf(1),
            )

            else -> MessageEntityContent.Knock(Random.nextBoolean())
        }


        @State(value = Scope.Benchmark)
        class DBState {
            private val selfUserId = UserIDEntity("selfValue", "selfDomain")
            lateinit var db: UserDatabaseBuilder
            private val UserIDEntity.databaseFile
                get() = java.nio.file.Files.createTempDirectory("test-storage").toFile().resolve("test-$domain-$value.db")

            @Setup(Level.Trial)
            fun setUp() {
                db = userDatabaseBuilder(
                    platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(selfUserId.databaseFile)),
                    userId = selfUserId,
                    passphrase = null,
                    dispatcher = Dispatchers.IO,
                    enableWAL = true
                )

                setupData()
            }

            private fun setupData() = runBlocking {
                db.conversationDAO.insertConversations(listOf(conversationEntity))
                db.userDAO.upsertUser(userEntity1)
                db.userDAO.upsertUser(userEntity2)

                val messagesToInsert = generateRandomMessages(MESSAGES_COUNT)
                db.messageDAO.insertOrIgnoreMessages(messagesToInsert)
            }

            @TearDown(Level.Trial)
            fun tearDown() {
                selfUserId.databaseFile.delete()
            }
        }
    }
}


