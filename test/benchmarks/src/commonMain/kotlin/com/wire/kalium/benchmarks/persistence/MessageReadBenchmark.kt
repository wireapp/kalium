@file:Suppress("MagicNumber")

package com.wire.kalium.benchmarks.persistence

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.openjdk.jmh.annotations.Level
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 10)
class MessageReadBenchmark {

    @Benchmark
    fun inboxPagingFirstPageBenchmark(dbState: DBState, blackHole: Blackhole) = runBlocking {
        blackHole.consume(
            dbState.loadInboxPage(
                startingOffset = 0
            ).data.size
        )
    }

    @Benchmark
    fun inboxPagingDeepPageBenchmark(dbState: DBState, blackHole: Blackhole) = runBlocking {
        blackHole.consume(
            dbState.loadInboxPage(
                startingOffset = MessageReadBenchmarkData.InboxDeepOffset.toLong()
            ).data.size
        )
    }

    @Benchmark
    fun messagePagingFirstPageBenchmark(dbState: DBState, blackHole: Blackhole) = runBlocking {
        blackHole.consume(
            dbState.loadHotConversationPage(
                startingOffset = 0
            ).data.size
        )
    }

    @Benchmark
    fun messagePagingDeepPageBenchmark(dbState: DBState, blackHole: Blackhole) = runBlocking {
        blackHole.consume(
            dbState.loadHotConversationPage(
                startingOffset = MessageReadBenchmarkData.MessageDeepOffset.toLong()
            ).data.size
        )
    }

    @Benchmark
    fun localMarkAsReadBenchmark(
        dbState: DBState,
        invocationState: MarkReadInvocationState,
        blackHole: Blackhole
    ) = runBlocking {
        val hasUnreadEvents = dbState.db.conversationDAO.updateReadDateAndGetHasUnreadEvents(
            dbState.markReadConversationId,
            invocationState.unreadTimestamp
        )
        blackHole.consume(hasUnreadEvents)
    }

    @State(Scope.Benchmark)
    class DBState {
        data class MarkReadProbe(
            val messageId: String,
            val timestamp: Instant,
        )

        private val selfUserId = UserIDEntity("self", "benchmark.wire.com")
        private lateinit var tempDirectory: java.io.File

        lateinit var db: UserDatabaseBuilder
        lateinit var hotConversationId: com.wire.kalium.persistence.dao.ConversationIDEntity
        lateinit var markReadConversationId: com.wire.kalium.persistence.dao.ConversationIDEntity
        lateinit var markReadSenderId: com.wire.kalium.persistence.dao.UserIDEntity
        private var nextMarkReadMessageEpochMs: Long = 0L

        @Setup(Level.Trial)
        fun setUp() {
            tempDirectory = java.nio.file.Files.createTempDirectory("message-read-benchmark").toFile()
            val databaseFile = tempDirectory.resolve("message-read.db")

            db = userDatabaseBuilder(
                platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(databaseFile)),
                userId = selfUserId,
                passphrase = null,
                dispatcher = Dispatchers.IO,
                enableWAL = true
            )

            runBlocking {
                val seededContext = MessageReadBenchmarkData.seed(db)
                hotConversationId = seededContext.hotConversationId
                markReadConversationId = seededContext.markReadConversationId
                markReadSenderId = seededContext.markReadSenderId
                nextMarkReadMessageEpochMs = seededContext.markReadNextMessageEpochMs
                println("Seeded message-read benchmark DB in ${seededContext.seedDuration}")
            }
        }

        suspend fun loadInboxPage(startingOffset: Long): PagingSource.LoadResult.Page<Int, ConversationDetailsWithEventsEntity> {
            val pagingSource = db.conversationDAO.platformExtensions.getPagerForConversationDetailsWithEventsSearch(
                queryConfig = ConversationExtensions.QueryConfig(
                    fromArchive = false,
                    onlyInteractionEnabled = false,
                    conversationFilter = ConversationFilterEntity.ALL,
                    strictMlsFilter = true
                ),
                pagingConfig = PagingConfig(MessageReadBenchmarkData.PageSize),
                startingOffset = startingOffset
            )

            return pagingSource.loadRefreshPage()
        }

        suspend fun loadHotConversationPage(startingOffset: Long): PagingSource.LoadResult.Page<Int, MessageEntity> {
            val pagingSource = db.messageDAO.platformExtensions.getPagerForConversation(
                conversationId = hotConversationId,
                visibilities = listOf(MessageEntity.Visibility.VISIBLE),
                pagingConfig = PagingConfig(MessageReadBenchmarkData.PageSize),
                startingOffset = startingOffset
            )

            return pagingSource.loadRefreshPage()
        }

        suspend fun prepareMarkReadInvocation(): MarkReadProbe {
            val messageEpochMs = nextMarkReadMessageEpochMs
            val timestamp = MessageReadBenchmarkData.insertUnreadMessageForMarkRead(
                db = db,
                conversationId = markReadConversationId,
                senderId = markReadSenderId,
                messageEpochMs = messageEpochMs,
            )
            nextMarkReadMessageEpochMs += 1_000L
            return MarkReadProbe(
                messageId = "mark-read-$messageEpochMs",
                timestamp = timestamp
            )
        }

        @TearDown(Level.Trial)
        fun tearDown() {
            tempDirectory.deleteRecursively()
        }
    }

    @State(Scope.Thread)
    class MarkReadInvocationState {
        lateinit var messageId: String
        lateinit var unreadTimestamp: Instant

        @Setup(Level.Invocation)
        fun setUp(dbState: DBState) {
            val probe = runBlocking {
                dbState.prepareMarkReadInvocation()
            }
            messageId = probe.messageId
            unreadTimestamp = probe.timestamp
        }

        @TearDown(Level.Invocation)
        fun tearDown(dbState: DBState) {
            runBlocking {
                dbState.db.messageDAO.deleteMessage(messageId, dbState.markReadConversationId)
            }
        }
    }
}
