/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.network.exceptions.KaliumException.InvalidRequestError
import com.wire.kalium.network.exceptions.isMeetingNotFound

fun CoreFailure.isMeetingNotFound(): Boolean =
    ((this as? NetworkFailure.ServerMiscommunication)?.kaliumException as? InvalidRequestError)?.isMeetingNotFound() == true
