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
import com.wire.kalium.monkeys.pool.MonkeyPool
import io.micrometer.core.instrument.Tag
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
    suspend fun start(testCase: String, actions: List<ActionConfig>, coreLogic: CoreLogic, monkeyPool: MonkeyPool) {
        actions.forEach { actionConfig ->
            CoroutineScope(Dispatchers.Default).launch {
                while (this.isActive) {
                    val actionName = actionConfig.type::class.serializer().descriptor.serialName
                    val tags = listOf(Tag.of("testCase", testCase))
                    try {
                        logger.i("Running action $actionName: ${actionConfig.description} ${actionConfig.count} times")
                        repeat(actionConfig.count.toInt()) {
                            val startTime = System.currentTimeMillis()
                            MetricsCollector.time("t_$actionName", tags) {
                                Action.fromConfig(actionConfig).execute(coreLogic, monkeyPool)
                            }
                            MetricsCollector.count("c_$actionName", tags)
                            logger.d("Action $actionName took ${System.currentTimeMillis() - startTime} milliseconds")
                        }
                    } catch (e: Exception) {
                        logger.e("Error in action ${actionConfig.description}:", e)
                        if (e.cause != null) {
                            logger.e("Cause for error in ${actionConfig.description}:", e.cause)
                        }
                        MetricsCollector.count("c_errors", tags.plusElement(Tag.of("action", actionName)))
                    }
                    delay(actionConfig.repeatInterval.toLong())
                }
            }
        }
    }

    suspend fun runSetup(actions: List<ActionConfig>, coreLogic: CoreLogic, monkeyPool: MonkeyPool) {
        actions.forEach {
            Action.fromConfig(it).execute(coreLogic, monkeyPool)
        }
    }
}
