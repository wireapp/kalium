/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:Suppress("MagicNumber")

package com.wire.kalium.benchmarks.android

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures message keyset reads against a production-shaped, SQLCipher-encrypted database.
 * Planner statistics are collected after bulk seeding, matching a mature optimized database.
 */
@RunWith(AndroidJUnit4::class)
class MessageKeysetDepthBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: UserDatabaseBuilder
    private lateinit var users: List<UserEntity>

    @Before
    fun setUp() {
        BenchmarkDb.delete(context)
        db = BenchmarkDb.build(context)
        runBlocking {
            users = buildList(USER_COUNT) {
                add(BenchmarkEntities.userOne)
                repeat(USER_COUNT - 1) { index ->
                    add(
                        BenchmarkEntities.userOne.copy(
                            id = QualifiedIDEntity("benchmark-user-$index", BENCHMARK_DOMAIN),
                            name = "Benchmark User $index",
                        )
                    )
                }
            }
            db.userDAO.upsertUsers(users)
            db.conversationDAO.insertConversations(listOf(BenchmarkEntities.conversation))
            seedMessages()
            db.databaseOptimizer.optimizeAllTables()
        }
    }

    @After
    fun tearDown() {
        BenchmarkDb.delete(context)
    }

    @Test
    fun queryFirstPage() = measureDepth(0)

    @Test
    fun queryLightDepth() = measureDepth(100)

    @Test
    fun queryDeepDepth() = measureDepth(5_000)

    @Test
    fun queryVeryDeepDepth() = measureDepth(15_000)

    private fun measureDepth(depth: Int) {
        val startingMessageId = if (depth == 0) null else messageId(MESSAGE_COUNT - 1 - depth)
        val pagingSource = db.messageDAO.platformExtensions.getPagerForConversation(
            conversationId = BenchmarkEntities.conversationId,
            visibilities = listOf(MessageEntity.Visibility.VISIBLE),
            pagingConfig = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            startingMessageId = startingMessageId,
            initialItemsBeforeStart = 0,
        ).extractPagingSource()

        benchmarkRule.measureRepeated {
            val result = runBlocking {
                pagingSource.load(PagingSource.LoadParams.Refresh(null, PAGE_SIZE, false))
            }
            check(result is PagingSource.LoadResult.Page && result.data.size == PAGE_SIZE)
        }
    }

    private suspend fun seedMessages() {
        val chunk = ArrayList<MessageEntity>(INSERT_CHUNK_SIZE)
        repeat(MESSAGE_COUNT) { index ->
            val sender = users[index % users.size]
            chunk += MessageEntity.Regular(
                id = messageId(index),
                conversationId = BenchmarkEntities.conversationId,
                date = Instant.fromEpochMilliseconds(index.toLong()),
                senderUserId = sender.id,
                status = MessageEntity.Status.SENT,
                visibility = MessageEntity.Visibility.VISIBLE,
                content = MessageEntityContent.Text("Controlled benchmark message $index"),
                senderClientId = "benchmark-client",
                editStatus = MessageEntity.EditStatus.NotEdited,
                senderName = sender.name,
                readCount = 0,
            )
            if (chunk.size == INSERT_CHUNK_SIZE) {
                db.messageDAO.insertOrIgnoreMessages(chunk.toList())
                chunk.clear()
            }
        }
        if (chunk.isNotEmpty()) db.messageDAO.insertOrIgnoreMessages(chunk)
    }

    @Suppress("UNCHECKED_CAST")
    private fun KaliumPager<MessageEntity>.extractPagingSource(): PagingSource<Any, MessageEntity> {
        val field = javaClass.getDeclaredField("pagingSource")
        field.isAccessible = true
        return field.get(this) as PagingSource<Any, MessageEntity>
    }

    private fun messageId(index: Int): String = index.toString().padStart(8, '0')

    private companion object {
        const val BENCHMARK_DOMAIN = "benchmark.wire.com"
        const val USER_COUNT = 2_000
        const val MESSAGE_COUNT = 20_000
        const val PAGE_SIZE = 50
        const val INSERT_CHUNK_SIZE = 1_000
    }
}
