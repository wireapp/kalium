/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class AppleMissedCallNotificationPolicyTest {

    @Test
    fun givenEarlierMatchingSignalIsNoLongerPending_whenSelectingMissedSource_thenIdentityDoesNotMove() {
        val original = signal("original", submittedGeneration = "app-generation")
        val later = signal("later")
        val result = ReceivedCallResult(
            type = ReceivedCallType.MISSED,
            conversationValue = CONVERSATION.value,
            conversationDomain = CONVERSATION.domain,
            callerValue = CALLER.value,
            callerDomain = CALLER.domain,
            callerClientId = null,
            messageTimeSeconds = CALL_TIME.epochSeconds,
            shouldRing = false,
            isVideoCall = false,
        )

        assertSame(original, callSignalSource(listOf(original, later), setOf("later"), result))
    }

    @Test
    fun givenVisibleUnreadMissedCall_whenEvaluatingPolicy_thenNotificationIsEligible() {
        assertTrue(eligible())
        assertTrue(eligible(conversation = conversation(mutedStatus = ConversationEntity.MutedStatus.MENTIONS_MUTED)))
        assertTrue(
            eligible(
                conversation = conversation(mutedStatus = ConversationEntity.MutedStatus.MENTIONS_MUTED),
                availability = UserAvailabilityStatusEntity.BUSY,
            )
        )
        assertTrue(
            eligible(
                conversation = conversation(mutedStatus = ConversationEntity.MutedStatus.MENTIONS_MUTED),
                availability = UserAvailabilityStatusEntity.AWAY,
            )
        )
        assertTrue(
            eligible(
                conversation = conversation(
                    mlsVerificationStatus = ConversationEntity.VerificationStatus.DEGRADED,
                    proteusVerificationStatus = ConversationEntity.VerificationStatus.DEGRADED,
                )
            )
        )
    }

    @Test
    fun givenConversationOrReadStateSuppression_whenEvaluatingPolicy_thenNotificationIsIneligible() {
        assertFalse(eligible(conversation = null))
        assertFalse(eligible(conversation = conversation(archived = true)))
        assertFalse(eligible(conversation = conversation(lastReadDate = CALL_TIME)))
        assertFalse(eligible(conversation = conversation(lastNotificationDate = CALL_TIME)))
        assertFalse(eligible(caller = SELF.toDao()))
    }

    @Test
    fun givenMuteOrAvailabilitySuppression_whenEvaluatingPolicy_thenNotificationIsIneligible() {
        assertFalse(eligible(conversation = conversation(mutedStatus = ConversationEntity.MutedStatus.ALL_MUTED)))
        assertFalse(
            eligible(
                conversation = conversation(mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED)
            )
        )
        assertFalse(eligible(availability = UserAvailabilityStatusEntity.BUSY))
        assertFalse(eligible(availability = UserAvailabilityStatusEntity.AWAY))
    }

    private fun eligible(
        conversation: ConversationEntity? = conversation(),
        caller: QualifiedIDEntity = CALLER,
        availability: UserAvailabilityStatusEntity? = UserAvailabilityStatusEntity.NONE,
    ) = missedCallShouldNotify(conversation, SELF, caller, CALL_TIME, availability)

    private fun signal(id: String, submittedGeneration: String? = null) = CallSignalRecord(
        id = id,
        messageId = id,
        conversationValue = CONVERSATION.value,
        conversationDomain = CONVERSATION.domain,
        senderValue = CALLER.value,
        senderDomain = CALLER.domain,
        senderClientId = "client-$id",
        payload = "{}",
        timeMillis = CALL_TIME.toEpochMilliseconds(),
        isGroup = false,
        isMLS = false,
        storedAtMillis = CALL_TIME.toEpochMilliseconds(),
        submittedGeneration = submittedGeneration,
        ready = true,
    )

    @Suppress("LongParameterList")
    private fun conversation(
        mutedStatus: ConversationEntity.MutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
        lastNotificationDate: Instant? = null,
        lastReadDate: Instant = Instant.DISTANT_PAST,
        archived: Boolean = false,
        mlsVerificationStatus: ConversationEntity.VerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus: ConversationEntity.VerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
    ) = ConversationEntity(
        id = CONVERSATION,
        name = "Conversation",
        type = ConversationEntity.Type.ONE_ON_ONE,
        teamId = null,
        protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
        mutedStatus = mutedStatus,
        creatorId = CALLER.value,
        lastNotificationDate = lastNotificationDate,
        lastModifiedDate = Instant.DISTANT_PAST,
        lastReadDate = lastReadDate,
        access = emptyList(),
        accessRole = emptyList(),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = archived,
        archivedInstant = null,
        mlsVerificationStatus = mlsVerificationStatus,
        proteusVerificationStatus = proteusVerificationStatus,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        channelAccess = null,
        channelAddPermission = null,
        wireCell = null,
        historySharingRetentionSeconds = 0,
    )

    private companion object {
        val SELF = UserId("self", "example.com")
        val CALLER = QualifiedIDEntity("caller", "example.com")
        val CONVERSATION = QualifiedIDEntity("conversation", "example.com")
        val CALL_TIME = Instant.fromEpochSeconds(100)
    }
}

private fun UserId.toDao() = QualifiedIDEntity(value, domain)
