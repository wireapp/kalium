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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

object ActionScheduler {
    @Suppress("TooGenericExceptionCaught")
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    suspend fun start(testCases: List<TestCase>, coreLogic: CoreLogic) {
        testCases.flatMap { it.actions }.forEach { actionConfig ->
            CoroutineScope(Dispatchers.Default).launch {
                while (this.isActive) {
                    try {
                        val actionName = actionConfig.type::class.serializer().descriptor.serialName
                        logger.i("Running action $actionName: ${actionConfig.description} ${actionConfig.count} times")
                        repeat(actionConfig.count.toInt()) {
                            val startTime = System.currentTimeMillis()
                            Action.fromConfig(actionConfig).execute(coreLogic)
                            logger.d("Action $actionName took ${System.currentTimeMillis() - startTime} milliseconds")
                        }
                    } catch (e: Exception) {
                        logger.e("Error in action ${actionConfig.description}", e)
                    }
                    delay(actionConfig.repeatInterval.toLong())
                }
            }
        }
    }

    suspend fun runSetup(actions: List<ActionConfig>, coreLogic: CoreLogic) {
        actions.forEach {
            Action.fromConfig(it).execute(coreLogic)
        }
    }
}
