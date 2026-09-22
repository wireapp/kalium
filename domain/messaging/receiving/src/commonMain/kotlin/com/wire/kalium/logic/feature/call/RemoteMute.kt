/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId

/** The app's target policy, including its existing domain-independent UUID match. */
fun MessageContent.Calling.Targets?.targetsClient(
    selfUserId: UserId,
    selfClientId: String,
): Boolean = this?.domainToUserIdToClients?.values.orEmpty().any { userClients ->
    userClients[selfUserId.value].orEmpty().any { client -> client == selfClientId }
}
