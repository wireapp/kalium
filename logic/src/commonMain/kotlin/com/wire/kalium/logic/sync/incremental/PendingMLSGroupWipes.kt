/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiving.MLSReceiveRecoveryStore
import com.wire.kalium.logic.sync.receiving.PendingConversationCleanup
import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import com.wire.kalium.persistence.db.UserDatabaseBuilder

internal suspend fun finishPendingConversationCleanup(
    database: UserDatabaseBuilder,
    clientId: String?,
    transactionProvider: CryptoTransactionProvider,
    deleteFile: (String) -> Unit,
) {
    val cleanup = PendingConversationCleanup(database, deleteFile)
    cleanup.discardLegacyRequests()
    if (clientId != null) {
        MLSReceiveRecoveryStore(database.metadataDAO, database.notificationEventStore, clientId)
            .drainDeletions { groupId -> cleanup.queueWipe(groupId) }
    }
    wipePendingMLSGroups(cleanup, database.pendingActionDAO, transactionProvider)
}

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
