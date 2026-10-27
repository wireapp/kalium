/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ServerError.InvalidRequest
import com.wire.kalium.common.error.isMeetingNotFound

fun CoreFailure.isMeetingNotFound(): Boolean =
    ((this as? NetworkFailure.ServerMiscommunication)?.reason as? InvalidRequest)?.isMeetingNotFound() == true
