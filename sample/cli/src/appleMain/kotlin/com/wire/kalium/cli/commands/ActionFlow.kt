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
package com.wire.kalium.cli.commands

import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

@Suppress("ComplexMethod", "LongMethod")
internal fun actionFlow(userSession: UserSessionScope): Flow<InputAction> {
    var command: Command? = null
    val draftBuffer = StringBuilder()
    var cursorPosition = 0

    return merge(
        flowOf(InputAction.UpdateDraft(draftBuffer.toString(), cursorPosition)),
        inputFlow().mapNotNull {
            if (it == Input.Character('q')) {
                InputAction.Quit
            } else {
                when (it) {
                    Input.Character('\n') -> {
                        val message = draftBuffer.toString()
                        draftBuffer.clear()
                        cursorPosition = 0

                        command?.let {
                            InputAction.RunCommand(it)
                        } ?: run {
                            InputAction.SendText(message)
                        }
                    }

                    Input.Character('\t') -> {
                        command?.nextResult()
                        InputAction.UpdateDraft(draftBuffer.toString(), cursorPosition, command?.resultDescription())
                    }

                    is Input.Character -> {
                        draftBuffer.insert(cursorPosition, it.char)
                        val message = draftBuffer.toString()

                        if (message.startsWith("/")) {
                            val components = message.split(" ", limit = 2)
                            if (components.size == 2) {
                                val action = components[0].drop(1)
                                val query = components[1]

                                command = command?.let { command ->
                                    if (command.name == action) {
                                        command.also { command.query = query }
                                    } else {
                                        null
                                    }
                                } ?: run {
                                    Command.find(action, query, userSession)
                                }
                            }
                        }

                        InputAction.UpdateDraft(draftBuffer.toString(), ++cursorPosition, command?.resultDescription())
                    }

                    is Input.ArrowLeft -> {
                        if (cursorPosition > 0) {
                            InputAction.UpdateDraft(draftBuffer.toString(), --cursorPosition)
                        } else {
                            null
                        }
                    }

                    is Input.ArrowRight -> {
                        if (cursorPosition < draftBuffer.length) {
                            InputAction.UpdateDraft(draftBuffer.toString(), ++cursorPosition)
                        } else {
                            null
                        }
                    }

                    is Input.Backspace, is Input.DeleteKey -> {
                        if (!draftBuffer.isEmpty()) {
                            draftBuffer.deleteAt(--cursorPosition)
                            InputAction.UpdateDraft(draftBuffer.toString(), cursorPosition)
                        } else {
                            null
                        }
                    }

                    else -> null
                }
            }
        }
    )
}
