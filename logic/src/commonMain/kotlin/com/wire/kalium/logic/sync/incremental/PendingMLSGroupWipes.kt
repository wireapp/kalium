/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiving.PendingConversationCleanup
import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType

/**
 * Wipes MLS groups whose conversations were deleted in SQL before their native wipe committed.
 * Runs before incremental sync gathers events, so no event in flight can still want the group;
 * groups a conversation still uses are skipped. Without an MLS client the rows stay queued.
 */
internal suspend fun wipePendingMLSGroups(
    cleanup: PendingConversationCleanup,
    pendingActionDAO: PendingActionDAO,
    transactionProvider: CryptoTransactionProvider,
) {
    if (pendingActionDAO.getByActionType(PendingActionType.WIPE_MLS_GROUP).isEmpty()) return
    transactionProvider.transaction("pending-local-conversation-cleanup") { context ->
        Either.Right(context.mls?.let { cleanup.wipePending(it) }.orEmpty())
    }.getOrFail {
        throw KaliumSyncException("Pending local conversation cleanup failed", it)
    }.let { cleanup.committed(it) }
}
