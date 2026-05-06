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
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Measures `MessageDAO.insertOrIgnoreMessages` against an encrypted (SQLCipher) user database on
 * a real device. Mirrors `MessagesNoPragmaTuneBenchmark.messageInsertionBenchmark` from
 * `:test:benchmarks`, but uses the production encryption path so the numbers reflect what a
 * production Android client actually pays.
 *
 * Run with:
 *     ./gradlew :test:android-benchmarks:connectedReleaseAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MessageInsertBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: UserDatabaseBuilder
    private var nextMessageId: Int = 0

    @Before
    fun setUp() {
        BenchmarkDb.delete(context)
        db = BenchmarkDb.build(context)
        runBlocking {
            db.conversationDAO.insertConversations(listOf(BenchmarkEntities.conversation))
            db.userDAO.upsertUser(BenchmarkEntities.userOne)
            db.userDAO.upsertUser(BenchmarkEntities.userTwo)
            // Seed the table with a realistic baseline so the measured batch is not hitting an empty DB.
            val seed = BenchmarkEntities.generateRandomMessages(BASELINE_MESSAGES, idOffset = nextMessageId)
            db.messageDAO.insertOrIgnoreMessages(seed)
            nextMessageId += BASELINE_MESSAGES
        }
    }

    @After
    fun tearDown() {
        BenchmarkDb.delete(context)
    }

    @Test
    fun insertBatch() {
        benchmarkRule.measureRepeated {
            val batch = runWithMeasurementDisabled {
                val generated = BenchmarkEntities.generateRandomMessages(
                    count = BATCH_SIZE,
                    idOffset = nextMessageId
                )
                nextMessageId += BATCH_SIZE
                generated
            }
            runBlocking {
                db.messageDAO.insertOrIgnoreMessages(batch)
            }
        }
    }

    private companion object {
        const val BASELINE_MESSAGES = 1_000
        const val BATCH_SIZE = 5_000
    }
}
