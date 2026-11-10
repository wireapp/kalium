/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.featureConfig

import kotlin.time.Clock

public fun MLSMigrationModel.isUpcomingOrOngoing(): Boolean =
    status == Status.ENABLED && endTime?.let { Clock.System.now() > it } != true
