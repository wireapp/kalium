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
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.wire.kalium.benchmarks.fixtures.BenchmarkEntities
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.draft.MessageDraftEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.datetime.Instant

/** Deterministic data shared by page and write comparisons. All setup is outside measured blocks. */
internal class ConversationListFixture {
    private val arguments = InstrumentationRegistry.getArguments()
    val count = arguments.getString("conversationListCount", "5000").toInt().also { require(it >= 1000) }
    val historySize = arguments.getString("conversationListHistory", "20").toInt().also { require(it >= 5) }
    val withTypeIndex = arguments.getString("conversationListTypeIndex", "false").toBooleanStrict()

    // Distinct dates keep legacy name ordering and V2 ID ordering comparable.
    val conversations = List(count) { index ->
        BenchmarkEntities.conversation.copy(
            id = QualifiedIDEntity("list-${index.toString().padStart(6, '0')}", "wire.com"),
            name = "${if (index % 7 == 0) "Needle " else ""}Conversation ${index.toString().padStart(6, '0')}",
            type = when (index % 20) {
                1 -> ConversationEntity.Type.CHANNEL
                2, 3 -> ConversationEntity.Type.ONE_ON_ONE
                else -> ConversationEntity.Type.GROUP
            },
            archived = index % 10 == 9,
            lastModifiedDate = Instant.fromEpochMilliseconds(1_700_000_000_000L + (count - index) * (historySize + 1L)),
            lastReadDate = Instant.fromEpochMilliseconds(
                1_700_000_000_000L + (count - index) * (historySize + 1L) - if (index % 3 == 0) 5 else 0,
            ),
        )
    }

    suspend fun seed(db: UserDatabaseBuilder, context: Context) {
        db.userDAO.upsertUsers(BenchmarkEntities.users)
        db.conversationDAO.insertConversations(conversations)
        conversations.forEachIndexed { index, conversation ->
            val otherId = if (conversation.type == ConversationEntity.Type.ONE_ON_ONE) {
                QualifiedIDEntity("list-user-$index", "wire.com").also { id ->
                    db.userDAO.upsertUser(BenchmarkEntities.userOne.copy(
                        id = id, name = conversation.name, activeOneOnOneConversationId = conversation.id,
                    ))
                }
            } else BenchmarkEntities.userOne.id
            db.memberDAO.insertMembersWithQualifiedId(listOf(
                MemberEntity(BenchmarkDb.selfUserId, MemberEntity.Role.Admin),
                MemberEntity(otherId, MemberEntity.Role.Member),
            ), conversation.id)
            db.messageDAO.insertOrIgnoreMessages(List(historySize) { n -> historyMessage(conversation, n, otherId) },
                withUnreadEvents = false)
            if (index % 17 == 0) {
                db.messageDraftDAO.upsertMessageDraft(MessageDraftEntity(conversation.id, "Draft $index", null, null, emptyList()))
            }
        }
        configureSqlCipher(context, db)
    }

    private fun configureSqlCipher(context: Context, db: UserDatabaseBuilder) {
        BenchmarkDb.withSqlCipher(context) { sql ->
            sql.beginTransaction()
            try {
                val types = listOf("MESSAGE", "MENTION", "REPLY", "MISSED_CALL", "KNOCK")
                conversations.filterIndexed { index, _ -> index % 3 == 0 }.forEach { conversation ->
                    types.forEachIndexed { n, type ->
                        val messageIndex = historySize - 5 + n
                        sql.execSQL("INSERT OR REPLACE INTO UnreadEvent(id, type, conversation_id, creation_date) VALUES (?, ?, ?, ?)",
                            arrayOf<Any>("history-$messageIndex", type, conversation.id.toString(),
                                conversation.lastModifiedDate.toEpochMilliseconds() - historySize + 1 + messageIndex))
                    }
                }
                if (withTypeIndex) {
                    sql.execSQL(
                        "CREATE INDEX benchmark_conversation_type_recency ON Conversation(type, last_modified_date DESC, qualified_id ASC)"
                    )
                }
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
            sql.execSQL("ANALYZE")
            sql.rawQuery("SELECT COUNT(*) FROM Message", emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == count.toLong() * historySize)
            }
            sql.rawQuery("SELECT COUNT(*) FROM UnreadEvent", emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == ((count + 2L) / 3) * 5)
            }
            sql.rawQuery("SELECT sqlite_version(), (SELECT COUNT(*) FROM sqlite_master WHERE name = 'benchmark_conversation_type_recency')",
                emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(1) == if (withTypeIndex) 1 else 0)
                Log.i(
                    "ConversationListBenchmark",
                    "SQLite=${cursor.getString(0)}, SQLCipher=${db.debugExtension.sqlCipherVersion()}, " +
                        "conversations=$count, history=$historySize, typeIndex=$withTypeIndex"
                )
            }
        }
    }

    fun incomingMessage(): MessageEntity.Regular = historyMessage(conversations.first(), historySize, BenchmarkEntities.userOne.id)
        .copy(
            id = "measured-incoming",
            date = Instant.fromEpochMilliseconds(conversations.first().lastModifiedDate.toEpochMilliseconds() + 1000),
        )

    suspend fun restoreConversationDate(db: UserDatabaseBuilder) {
        conversations.first().let { db.conversationDAO.updateConversationModifiedDate(it.id, it.lastModifiedDate) }
    }

    private fun historyMessage(conversation: ConversationEntity, n: Int, sender: QualifiedIDEntity) = MessageEntity.Regular(
        id = "history-$n",
        conversationId = conversation.id,
        date = Instant.fromEpochMilliseconds(conversation.lastModifiedDate.toEpochMilliseconds() - historySize + 1 + n),
        senderUserId = sender,
        senderClientId = "benchmark-client",
        status = MessageEntity.Status.SENT,
        visibility = MessageEntity.Visibility.VISIBLE,
        content = if (n % 11 == 0) MessageEntityContent.Knock(false)
            else MessageEntityContent.Text("Message $n: a deterministic conversation preview with enough text to exercise decoding."),
        editStatus = MessageEntity.EditStatus.NotEdited,
        senderName = "Benchmark sender",
        readCount = 0,
    )
}
