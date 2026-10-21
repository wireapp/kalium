/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.notification

/** Message meaning only; callers retain notification eligibility and preview privacy decisions. */
enum class NotificationMessageKind { TEXT, PICTURE, FILE, PING, LOCATION, MISSED_CALL }
