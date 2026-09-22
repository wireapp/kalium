/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The backend event time, not local receiving time, starts the app's commit delay. */
fun proposalCommitDeadline(receivedAt: Instant, commitDelay: Long): Instant = receivedAt + commitDelay.seconds

/** Native commit includes transport; a failed send must leave the persisted timer available. */
suspend fun commitPendingMLSProposals(context: MlsCoreCryptoContext, groupId: GroupID, conversations: ConversationDAO) =
    wrapMLSRequest { context.commitPendingProposals(groupId.value) }.flatMap {
        wrapStorageRequest { conversations.clearProposalTimer(groupId.value) }
    }
