/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState

/** Build policies shared by the processor and its effects; match the app's KaliumConfigs. */
data class ReceivingOptions(
    val guestRoomLink: Boolean = true,
    val selfDeletingMessages: Boolean = true,
    val drivePermissionsEnabled: Boolean = false,
    val linkPreviewEnabled: Boolean = false,
    val fileRestrictionState: BuildFileRestrictionState = BuildFileRestrictionState.NoRestriction,
)
