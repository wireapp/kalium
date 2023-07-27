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
package com.wire.kalium.monkeys

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.actions.Action
import com.wire.kalium.monkeys.importer.ActionConfig
import com.wire.kalium.monkeys.importer.TestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object ActionScheduler {
    suspend fun start(testCases: List<TestCase>, coreLogic: CoreLogic) {
        testCases.flatMap { it.actions }.forEach {
            CoroutineScope(Dispatchers.Default).launch {
                while (this.isActive) {
                    Action.fromConfig(it).execute(coreLogic)
                    delay(it.repeatDuration.toLong())
                }
            }
        }
    }

    suspend fun schedule(config: ActionConfig, waitTime: ULong, coreLogic: CoreLogic) {
        delay(waitTime.toLong())
        Action.fromConfig(config).execute(coreLogic)
    }

    suspend fun runSetup(actions: List<ActionConfig>, coreLogic: CoreLogic) {
        actions.forEach {
            Action.fromConfig(it).execute(coreLogic)
        }
    }
}
