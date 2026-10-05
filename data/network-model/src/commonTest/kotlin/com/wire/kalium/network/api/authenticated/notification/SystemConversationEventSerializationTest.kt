/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.network.api.authenticated.notification

import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.tools.KtxSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class SystemConversationEventSerializationTest {
    @Test
    fun regularReminderPreservesItsFields() {
        assertEquals(
            EventContentDTO.Conversation.AdminlessDeleteReminderDTO(
                conversation = "conversation", data = AdminlessDeleteReminderData(DELETION_TIME), from = "user",
                qualifiedConversation = CONVERSATION, qualifiedFrom = SENDER, teamId = "team", time = TIME, via = "user",
            ),
            decode("adminless-reminder", SENDER_FIELD, REMINDER_DATA),
        )
    }

    @Test
    fun systemReminderAcceptsMissingNullAndPresentSenders() {
        for ((sender, expected) in SENDERS) {
            assertEquals(
                EventContentDTO.Conversation.SystemAdminlessDeleteReminderDTO(
                    conversation = "conversation", data = AdminlessDeleteReminderData(DELETION_TIME), from = "user",
                    qualifiedConversation = CONVERSATION, qualifiedFrom = expected, teamId = "team", time = TIME, via = "user",
                ),
                decode("system.adminless-reminder", sender, REMINDER_DATA),
            )
        }
    }

    @Test
    fun systemDeletionAndMembershipAcceptMissingNullAndPresentSenders() {
        for ((sender, expected) in SENDERS) {
            val deletion = assertIs<EventContentDTO.Conversation.SystemDeletedConversationDTO>(decode("system.delete", sender))
            assertEquals(CONVERSATION, deletion.qualifiedConversation)
            assertEquals(expected, deletion.qualifiedFrom)
            val member = assertIs<EventContentDTO.Conversation.SystemMemberUpdateDTO>(
                decode("system.member-update", sender, ROLE_DATA)
            )
            assertEquals(expected, member.qualifiedFrom)
            assertEquals(SENDER, member.roleChange.qualifiedUserId)
            assertEquals("wire_admin", member.roleChange.role)
        }
    }

    private fun decode(type: String, sender: String, data: String = "") = KtxSerializer.json.decodeFromString<EventContentDTO>("""{
        "type":"conversation.$type","conversation":"conversation","from":"user","via":"user","team":"team",
        "qualified_conversation":{"id":"conversation","domain":"wire.test"},
        $sender $data "time":"$TIME"
    }""")

    private companion object {
        val CONVERSATION = ConversationId("conversation", "wire.test")
        val SENDER = UserId("user", "wire.test")
        val TIME = Instant.parse("2026-07-16T12:00:00Z")
        val DELETION_TIME = Instant.parse("2026-07-20T12:00:00Z")
        const val SENDER_FIELD = """"qualified_from":{"id":"user","domain":"wire.test"},"""
        val SENDERS = listOf("" to null, """"qualified_from":null,""" to null, SENDER_FIELD to SENDER)
        val REMINDER_DATA = """"data":{"deletion_scheduled_for":"$DELETION_TIME"},"""
        const val ROLE_DATA = """"data":{"target":"user","qualified_target":{"id":"user","domain":"wire.test"},
            "conversation_role":"wire_admin","otr_muted_ref":null,"otr_muted_status":null,
            "otr_archived":null,"otr_archived_ref":null},"""
    }
}
