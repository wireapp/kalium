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
import com.wire.kalium.monkeys.pool.MonkeyPool
import kotlinx.coroutines.delay

class SendRequestAction(val config: ActionType.SendRequest) : Action() {
    override suspend fun execute(coreLogic: CoreLogic) {
        val monkeys = MonkeyPool.randomLoggedInMonkeysFromDomain(this.config.originDomain, this.config.userCount)
        monkeys.forEach { origin ->
            val targets = MonkeyPool.randomLoggedInMonkeysFromDomain(this.config.targetDomain, this.config.targetUserCount)
            targets.forEach { origin.sendRequest(it) }
            delay(this.config.delayResponse.toLong())
            if (this.config.shouldAccept) {
                targets.forEach { it.acceptRequest(origin) }
            } else {
                targets.forEach { it.rejectRequest(origin) }
            }
        }
    }
}
