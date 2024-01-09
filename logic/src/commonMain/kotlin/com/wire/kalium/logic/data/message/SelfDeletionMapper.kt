/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.data.message

import com.wire.kalium.persistence.config.SelfDeletionTimerEntity

internal object SelfDeletionMapper { // TODO rename and refactor
    fun TeamSelfDeleteTimer.toSelfDeletionTimerEntity(): SelfDeletionTimerEntity = when (this) {
        is TeamSelfDeleteTimer.Disabled -> SelfDeletionTimerEntity.Disabled
        is TeamSelfDeleteTimer.Enabled -> SelfDeletionTimerEntity.Enabled
        is TeamSelfDeleteTimer.Enforced -> SelfDeletionTimerEntity.Enforced(enforcedDuration)
    }

    fun SelfDeletionTimerEntity.toTeamSelfDeleteTimer(): TeamSelfDeleteTimer = when (this) {
        SelfDeletionTimerEntity.Disabled -> TeamSelfDeleteTimer.Disabled
        is SelfDeletionTimerEntity.Enabled -> TeamSelfDeleteTimer.Enabled
        is SelfDeletionTimerEntity.Enforced -> TeamSelfDeleteTimer.Enforced(enforcedDuration)
    }
}
