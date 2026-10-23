/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.message

/** Delivery and read receipts advance a successful send; cleanup must never downgrade them. */
internal fun Message.Status.isSuccessfullySent(): Boolean =
    this == Message.Status.Sent || this == Message.Status.Delivered || this is Message.Status.Read
