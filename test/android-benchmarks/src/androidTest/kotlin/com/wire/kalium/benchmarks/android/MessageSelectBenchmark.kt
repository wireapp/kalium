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
@file:Suppress("MagicNumber")

package com.wire.kalium.benchmarks.android

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures `MessageDAO.getMessagesByConversationAndVisibility` against an encrypted (SQLCipher)
 * user database on a real device. Mirrors `MessagesNoPragmaTuneBenchmark.queryMessagesBenchmark`
 * from `:test:benchmarks`.
 *
 * Run with:
 *     ./gradlew :test:android-benchmarks:connectedReleaseAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MessageSelectBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: UserDatabaseBuilder

    @Before
    fun setUp() {
        BenchmarkDb.delete(context)
        db = BenchmarkDb.build(context)
        runBlocking {
            db.conversationDAO.insertConversations(listOf(BenchmarkEntities.conversation))
            db.userDAO.upsertUser(BenchmarkEntities.userOne)
            db.userDAO.upsertUser(BenchmarkEntities.userTwo)
            val messages = BenchmarkEntities.generateRandomMessages(SEED_MESSAGES)
            db.messageDAO.insertOrIgnoreMessages(messages)
        }
    }

    @After
    fun tearDown() {
        BenchmarkDb.delete(context)
    }

    @Test
    fun queryFirstPage() {
        benchmarkRule.measureRepeated {
            runBlocking {
                db.messageDAO.getMessagesByConversationAndVisibility(
                    conversationId = BenchmarkEntities.conversationId,
                    limit = PAGE_SIZE,
                    offset = 0,
                    visibility = listOf(MessageEntity.Visibility.VISIBLE)
                ).first()
            }
        }
    }

    private companion object {
        const val SEED_MESSAGES = 5_000
        const val PAGE_SIZE = 1_000
    }
}
