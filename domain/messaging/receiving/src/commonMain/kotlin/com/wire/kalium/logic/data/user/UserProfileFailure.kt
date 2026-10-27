/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ServerError
import com.wire.kalium.common.error.isNotFound

/** Existing connection-receiving policy, including the profile's team lookup. */
fun CoreFailure.isUserNotFoundFailure(): Boolean =
    ((this as? NetworkFailure.ServerMiscommunication)?.reason as? ServerError.InvalidRequest)
        ?.isNotFound() == true
