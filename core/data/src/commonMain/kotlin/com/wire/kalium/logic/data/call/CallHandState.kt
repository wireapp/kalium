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

package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedID
import kotlin.time.Instant

/** A hand belongs to a joined device, not all devices belonging to its user. */
data class CallHandIdentity(val userId: QualifiedID, val clientId: String)

/**
 * Ephemeral state for one local participation. Ordering is observation order, not server order.
 * Timestamps reject ordinary replay but cannot prove call membership: the wire event has no call ID.
 */
data class CallHandState private constructor(
    val generation: String,
    val startedAt: Instant,
    val raised: List<CallHandIdentity>,
    private val joinedAt: Map<CallHandIdentity, Instant>,
    private val versions: Map<CallHandIdentity, Version>,
) {
    private data class Version(val date: Instant, val messageIds: Set<String>) {
        fun rejects(incomingDate: Instant, messageId: String): Boolean =
            incomingDate < date || (incomingDate == date && messageId in messageIds)
    }

    fun contains(identity: CallHandIdentity): Boolean = identity in joinedAt

    /** Connectivity/media changes retain identity; only removal from the roster clears the hand. */
    fun withParticipants(identities: Set<CallHandIdentity>, now: Instant): CallHandState = copy(
        raised = raised.filter { it in identities },
        joinedAt = identities.associateWith { joinedAt[it] ?: now },
        versions = versions.filterKeys { it in identities },
    )

    fun receive(identity: CallHandIdentity, isRaised: Boolean, date: Instant, messageId: String): CallHandState {
        val cutoff = joinedAt[identity]
        val previous = versions[identity]
        val beforeParticipation = cutoff == null || date < maxOf(startedAt, cutoff)
        if (beforeParticipation || previous?.rejects(date, messageId) == true) return this
        val ids = if (date == previous?.date) previous.messageIds + messageId else setOf(messageId)
        val ordered = when {
            !isRaised -> raised - identity
            identity in raised -> raised
            else -> raised + identity
        }
        return copy(raised = ordered, versions = versions + (identity to Version(date, ids)))
    }

    companion object {
        fun start(generation: String, now: Instant, participants: Set<CallHandIdentity>): CallHandState =
            CallHandState(generation, now, emptyList(), participants.associateWith { now }, emptyMap())
    }
}
