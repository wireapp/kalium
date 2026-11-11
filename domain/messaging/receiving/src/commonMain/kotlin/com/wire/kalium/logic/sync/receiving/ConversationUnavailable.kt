/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isAccessDenied
import com.wire.kalium.network.exceptions.isConversationNotFound
import io.ktor.http.HttpStatusCode

/**
 * `404 no-conversation` or `403 access-denied`: this account can no longer read the conversation.
 * A queued event for it can never succeed, so receiving skips it instead of failing the whole batch.
 */
fun CoreFailure.isConversationUnavailable(): Boolean {
    val error = (this as? NetworkFailure.ServerMiscommunication)?.kaliumException as? KaliumException.InvalidRequestError
        ?: return false
    return error.errorResponse.code == HttpStatusCode.NotFound.value && error.isConversationNotFound() ||
        error.errorResponse.code == HttpStatusCode.Forbidden.value && error.isAccessDenied()
}
