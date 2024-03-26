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
package com.wire.kalium.monkeys

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.actions.Action
import com.wire.kalium.monkeys.model.ActionConfig
import com.wire.kalium.monkeys.model.Event
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

@Suppress("TooGenericExceptionCaught", "LongParameterList")
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
suspend fun start(
    testCase: String,
    actions: List<ActionConfig>,
    coreLogic: CoreLogic,
    monkeyPool: MonkeyPool,
    conversationPool: ConversationPool,
    producer: SendChannel<Event>
) {
    actions.forEach { actionConfig ->
        CoroutineScope(Dispatchers.Default).launch {
            val actionName = actionConfig.type::class.serializer().descriptor.serialName
            do {
                val tags = listOf(Tag.of("testCase", testCase))
                try {
                    logger.i("Running action $actionName: ${actionConfig.description} ${actionConfig.count} times")
                    repeat(actionConfig.count.toInt()) {
                        val startTime = System.currentTimeMillis()
                        MetricsCollector.time("t_$actionName", tags) {
                            Action.fromConfig(actionConfig, producer).execute(coreLogic, monkeyPool, conversationPool)
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
            } while (MonkeyApplication.isActive.get() && actionConfig.repeatInterval > 0u)
            logger.i("Task for action $actionName finished")
        }
    }
}

suspend fun runSetup(
    actions: List<ActionConfig>,
    coreLogic: CoreLogic,
    monkeyPool: MonkeyPool,
    conversationPool: ConversationPool,
    producer: SendChannel<Event>
) {
    actions.forEach { actionConfig ->
        Action.fromConfig(actionConfig, producer).execute(coreLogic, monkeyPool, conversationPool)
    }
}
