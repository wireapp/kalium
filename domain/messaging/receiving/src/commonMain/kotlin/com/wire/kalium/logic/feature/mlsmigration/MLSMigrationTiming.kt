/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import kotlin.time.Clock

public fun MLSMigrationModel.hasMigrationStarted(): Boolean =
    status == Status.ENABLED && startTime?.let { Clock.System.now() > it } ?: false

public fun MLSMigrationModel.hasMigrationEnded(): Boolean =
    status == Status.ENABLED && endTime?.let { Clock.System.now() > it } ?: false

/**
 * Whether the migration is enabled and hasn't ended, including before its start date.
 */
public fun MLSMigrationModel.isUpcomingOrOngoing(): Boolean =
    status == Status.ENABLED && !hasMigrationEnded()
