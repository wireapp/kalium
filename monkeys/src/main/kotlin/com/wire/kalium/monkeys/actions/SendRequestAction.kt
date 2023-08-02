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
package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.importer.ActionType
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.pool.MonkeyPool
import kotlinx.coroutines.delay

class SendRequestAction(val userCount: UserCount, val config: ActionType.SendRequest) : Action() {
    override suspend fun execute(coreLogic: CoreLogic) {
        val monkeys = MonkeyPool.randomLoggedInMonkeysFromDomain(config.originDomain, this.userCount)
        monkeys.forEach { origin ->
            val target = MonkeyPool.randomLoggedInMonkeysFromDomain(config.targetDomain, UserCount.single())[0]
            delay(this.config.delayResponse.toLong())
            if (this.config.shouldAccept) {
                target.acceptRequest(origin)
            } else {
                target.rejectRequest(origin)
            }
        }
    }
}
