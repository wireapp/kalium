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

package com.wire.kalium.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measures the production DAO write, including recency and unread maintenance, with stable table sizes. */
@RunWith(AndroidJUnit4::class)
class ConversationListWriteBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    @get:Rule
    val database = BenchmarkDbRule()
    private val db get() = database.db
    private val fixture = ConversationListFixture()

    @Before
    fun setUp() = runBlocking {
        fixture.seed(db, database.context)
    }

    @Test
    fun insertIncomingMessage() {
        val message = fixture.incomingMessage()
        benchmarkRule.measureRepeated {
            runBlocking { db.messageDAO.insertOrIgnoreMessage(message, updateConversationModifiedDate = true) }
            runWithMeasurementDisabled {
                runBlocking {
                    check(db.messageDAO.getMessageById(message.id, message.conversationId) != null)
                    check(db.conversationDAO.getConversationById(message.conversationId)?.lastModifiedDate == message.date)
                    db.messageDAO.deleteMessage(message.id, message.conversationId)
                    fixture.restoreConversationDate(db)
                }
            }
        }
    }
}
