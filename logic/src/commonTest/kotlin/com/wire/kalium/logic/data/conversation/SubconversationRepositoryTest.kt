/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.SubconversationDAO
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SubconversationRepositoryTest {
    private val api = mock<ConversationApi>()
    private val conversation = QualifiedID("conversation", "one.example")
    private val sub = SubconversationId("conference")
    private val first = GroupID("first-native-group")
    private val next = GroupID("next-native-group")

    @Test
    fun mappingSurvivesNewOwnerAndRetainsQualifiedConversationIdentity() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("subconversation", "test").toDao(), TestKaliumDispatcher.default)
        try {
            SubconversationRepositoryImpl(api, db.builder.subconversationDAO).insertSubconversation(conversation, sub, first)
            val owner = SubconversationRepositoryImpl(api, db.builder.subconversationDAO)
            assertEquals(first, owner.getSubconversationInfo(conversation, sub))
            assertTrue(owner.containsSubconversation(first))
            assertNull(owner.getSubconversationInfo(conversation.copy(domain = "two.example"), sub))
            owner.insertSubconversation(conversation.copy(domain = "two.example"), sub, next)
            assertEquals(first, SubconversationRepositoryImpl(api, db.builder.subconversationDAO).getSubconversationInfo(conversation, sub))
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun replacementAndDeletionPersistAcrossOwnerRecreation() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("subconversation-replace", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val initial = SubconversationRepositoryImpl(api, db.builder.subconversationDAO)
            initial.insertSubconversation(conversation, sub, first)
            initial.insertSubconversation(conversation, sub, next)
            val resumed = SubconversationRepositoryImpl(api, db.builder.subconversationDAO)
            assertEquals(next, resumed.getSubconversationInfo(conversation, sub))
            assertFalse(resumed.containsSubconversation(first))
            resumed.deleteSubconversation(conversation, sub)
            assertNull(SubconversationRepositoryImpl(api, db.builder.subconversationDAO).getSubconversationInfo(conversation, sub))
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun failedWriteDoesNotPublishUncommittedMapping() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("subconversation-failure", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val storage = db.builder.subconversationDAO
            SubconversationRepositoryImpl(api, storage).insertSubconversation(conversation, sub, first)
            val failing = object : SubconversationDAO by storage {
                override suspend fun upsert(conversationId: QualifiedIDEntity, subconversationId: String, groupId: String) {
                    error("Storage unavailable")
                }
                override suspend fun delete(conversationId: QualifiedIDEntity, subconversationId: String) { error("Storage unavailable") }
            }
            assertFails { SubconversationRepositoryImpl(api, failing).insertSubconversation(conversation, sub, next) }
            assertFails { SubconversationRepositoryImpl(api, failing).deleteSubconversation(conversation, sub) }
            assertEquals(first, SubconversationRepositoryImpl(api, storage).getSubconversationInfo(conversation, sub))
        } finally { db.builder.close(); db.delete() }
    }
}
