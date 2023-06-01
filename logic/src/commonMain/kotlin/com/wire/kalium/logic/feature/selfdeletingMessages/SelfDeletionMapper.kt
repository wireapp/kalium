/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.selfdeletingMessages

import com.wire.kalium.persistence.config.SelfDeletionTimerEntity

object SelfDeletionMapper { // TODO rename and refactor
    fun SelfDeletionTimer.toSelfDeletionTimerEntity(): SelfDeletionTimerEntity = when (this) {
        is SelfDeletionTimer.Disabled -> SelfDeletionTimerEntity.Disabled
        is SelfDeletionTimer.Enabled -> SelfDeletionTimerEntity.Enabled(userDuration)
        is SelfDeletionTimer.Enforced -> SelfDeletionTimerEntity.Enforced(enforcedDuration)
    }

    fun SelfDeletionTimerEntity.toSelfDeletionTimerStatus(): SelfDeletionTimer = when (this) {
        is SelfDeletionTimerEntity.Disabled -> SelfDeletionTimer.Disabled
        is SelfDeletionTimerEntity.Enabled -> SelfDeletionTimer.Enabled(userDuration)
        is SelfDeletionTimerEntity.Enforced -> SelfDeletionTimer.Enforced.ByTeam(enforcedDuration)
    }
}
