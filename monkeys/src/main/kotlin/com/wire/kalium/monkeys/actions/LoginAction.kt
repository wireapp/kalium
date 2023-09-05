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
import com.wire.kalium.monkeys.logger
import com.wire.kalium.monkeys.pool.MonkeyPool
import kotlinx.coroutines.delay

class LoginAction(val config: ActionType.Login) : Action() {
    override suspend fun execute(coreLogic: CoreLogic, monkeyPool: MonkeyPool) {
        val monkeys = monkeyPool.randomLoggedOutMonkeys(this.config.userCount)
        logger.i("Logging ${monkeys.count()} monkeys in")
        monkeys.forEach { it.login(coreLogic, monkeyPool::loggedIn) }
        if (config.duration > 0u) {
            delay(config.duration.toLong())
            monkeys.forEach { it.logout(monkeyPool::loggedOut) }
        }
    }
}
