/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.pendingConversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserProfiles
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReceivedMembershipHandlerTest {
    @Test
    fun deletedMlsPeerIsRetainedAndDeletionWinsOverFetchedProfileOnReplay() = runTest {
        withTestUserDatabase(self.toDao()) { database ->
            val db = database.builder
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(self.toDao(), peer.toDao()))
            val profile = checkNotNull(db.userDAO.getUserDetailsByQualifiedID(peer.toDao())).toSimpleEntity().copy(
                name = "Teammate", hasIncompleteMetadata = false, activeOneOnOneConversationId = conversation
            )
            db.userDAO.upsertUser(profile)
            db.conversationDAO.insertConversation(connection.pendingConversation().copy(
                type = ConversationEntity.Type.ONE_ON_ONE,
                protocolInfo = ConversationEntity.ProtocolInfo.MLS(
                    "group", ConversationEntity.GroupState.ESTABLISHED, 1u, time,
                    ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
                ),
            ))
            db.memberDAO.insertMembersWithQualifiedId(
                listOf(MemberEntity(self.toDao(), MemberEntity.Role.Member), MemberEntity(peer.toDao(), MemberEntity.Role.Member)),
                conversation,
            )
            var persistedMessages = 0
            repeat(2) {
                db.writeMessageEffects {
                    ReceivedMembershipHandler(
                        this,
                        PersistMessageUseCase {
                            persistedMessages++
                            Either.Right(Unit)
                        },
                        self,
                        false,
                        mutableListOf(),
                    ).leave(event, UserProfiles(others = listOf(profile)))
                }
            }

            assertEquals(setOf(self.toDao(), peer.toDao()), db.memberDAO.getConversationMembers(conversation).toSet())
            assertTrue(checkNotNull(db.userDAO.getUserDetailsByQualifiedID(peer.toDao())).deleted)
            assertEquals(peer.toDao(), db.conversationDAO.getConversationDetailsById(conversation)?.otherUserId)
            assertEquals(0, persistedMessages)
        }
    }

    @Test
    fun groupDeletionOrdinaryLeaveAndSelfDeletionRemoveMembership() = runTest {
        val cases = listOf(
            ConversationEntity.Type.GROUP to event,
            ConversationEntity.Type.ONE_ON_ONE to event.copy(reason = MemberLeaveReason.Removed),
            ConversationEntity.Type.ONE_ON_ONE to event.copy(reason = MemberLeaveReason.Left),
            ConversationEntity.Type.ONE_ON_ONE to event.copy(removedList = listOf(self)),
        )
        for ((type, leave) in cases) {
            withTestUserDatabase(self.toDao()) { database ->
                val db = database.builder
                db.userDAO.insertOrIgnoreIncompleteUsers(listOf(self.toDao(), peer.toDao()))
                db.conversationDAO.insertConversation(connection.pendingConversation().copy(
                    type = type,
                    protocolInfo = ConversationEntity.ProtocolInfo.MLS(
                        "group", ConversationEntity.GroupState.ESTABLISHED, 1u, time,
                        ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
                    ),
                ))
                db.memberDAO.insertMembersWithQualifiedId(
                    listOf(MemberEntity(self.toDao(), MemberEntity.Role.Member), MemberEntity(peer.toDao(), MemberEntity.Role.Member)),
                    conversation,
                )
                var persistedMessages = 0
                db.writeMessageEffects {
                    ReceivedMembershipHandler(
                        this,
                        PersistMessageUseCase {
                            persistedMessages++
                            Either.Right(Unit)
                        },
                        self,
                        false,
                        mutableListOf(),
                    ).leave(leave, UserProfiles())
                }

                val expected = setOf(self.toDao(), peer.toDao()) - leave.removedList.map { it.toDao() }.toSet()
                assertEquals(expected, db.memberDAO.getConversationMembers(conversation).toSet())
                assertEquals(1, persistedMessages)
            }
        }
    }

    private companion object {
        val self = UserId("self", "wire.com")
        val peer = UserId("peer", self.domain)
        val conversation = QualifiedIDEntity("conversation", self.domain)
        val time = Instant.parse("2026-11-23T10:00:00Z")
        val connection = ConnectionEntity(
            conversation.value, self.value, time, conversation, peer.toDao(), ConnectionEntity.State.ACCEPTED, peer.value
        )
        val event = Event.Conversation.MemberLeave(
            "leave", conversation.toModel(), peer, listOf(peer), time, MemberLeaveReason.UserDeleted
        )
    }
}
