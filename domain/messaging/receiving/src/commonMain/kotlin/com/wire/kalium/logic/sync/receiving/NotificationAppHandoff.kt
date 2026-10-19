/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

/**
 * The original notification stays unprocessed in Events. EventGatherer / EventProcessor
 * consume it when the app owns the account. This is not an acknowledgement and the NSE
 * must not advance past it. No plaintext or credentials cross the host result boundary.
 */
enum class NotificationAppWork { LEGAL_HOLD, MLS_SUBCONVERSATION, CALL_MODERATION, ACCOUNT_INVALIDATION }

internal class NotificationAppHandoff(val work: NotificationAppWork) : Exception(work.name)

enum class PendingAccountInvalidation { REMOVED_CLIENT, DELETED_ACCOUNT }

/**
 * Terminal account events take precedence over ordinary ordered work: an invalid
 * account cannot continue receiving. Scan all locally stored pending rows, even
 * behind a handoff, without decrypting, using network/auth, or acknowledging any row.
 * The caller already owns this account's database/lease. Client IDs are account scoped.
 */
suspend fun pendingAccountInvalidation(
    database: com.wire.kalium.persistence.db.UserDatabaseBuilder,
    user: com.wire.kalium.logic.data.user.UserId,
    clientId: String,
): PendingAccountInvalidation? {
    var after = 0L
    var removed = false
    while (true) {
        val page = database.notificationEventStore.pendingAfter(after)
        if (page.isEmpty()) break
        for (event in page) {
            val payload = com.wire.kalium.network.api.authenticated.notification.EventResponseToStore(
                event.eventId,
                event.payload,
                event.transient
            ).toEventResponse().payload.orEmpty()
            val deleted = payload.any {
                it is com.wire.kalium.network.api.authenticated.notification.EventContentDTO.User.UserDeleteDTO &&
                    it.userId.value == user.value && it.userId.domain == user.domain
            }
            if (deleted) return PendingAccountInvalidation.DELETED_ACCOUNT
            val clientRemoved = payload.any {
                it is com.wire.kalium.network.api.authenticated.notification.EventContentDTO.User.ClientRemoveDTO &&
                    it.client.clientId == clientId
            }
            if (clientRemoved) removed = true
        }
        after = page.last().id
    }
    return if (removed) PendingAccountInvalidation.REMOVED_CLIENT else null
}
