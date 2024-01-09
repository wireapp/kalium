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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.mordant.terminal.Terminal
import com.wire.kalium.cli.listConversations
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.conversation.GetConversationUseCase
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.posix.SIGWINCH
import platform.posix.STDIN_FILENO
import platform.posix.TCSAFLUSH
import platform.posix.VMIN
import platform.posix.VTIME
import platform.posix.signal
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios

sealed class PosixSignal {
    data object WindowChanged : PosixSignal()
}

private val signalFlow = MutableSharedFlow<PosixSignal>()

data class ViewState(
    val title: String,
    val messages: List<Message>,
    val input: String,
    val inputInfo: String?,
    val cursorPosition: Int
)

class InteractiveCommand : CliktCommand(name = "interactive") {

    private val terminal = Terminal()
    private val userSession by requireObject<UserSessionScope>()
    private var currentConversationId: ConversationId? = null
    private var finished = false

    private fun render(viewState: ViewState) {
        print(
            buildString {
                append(
                    terminal.cursor.getMoves {
                        setPosition(0, 0)
                    }
                )
                append(
                    terminal.render(
                        conversation(
                            viewState.input,
                            viewState.inputInfo,
                            viewState.title,
                            viewState.messages,
                            terminal.info.height
                        )
                    )
                )
                append(
                    terminal.cursor.getMoves {
                        startOfLine()
                        right(viewState.cursorPosition + 2)
                    }
                )
            }
        )
    }

    override fun run() = runBlocking {
        signal(
            SIGWINCH,
            staticCFunction<Int, Unit> {
                runBlocking {
                    signalFlow.emit(PosixSignal.WindowChanged)
                }
            }
        )

        while (!finished) {
            displayConversation(currentConversationId ?: userSession.selectConversation().id)
        }
    }

    private suspend fun UserSessionScope.selectConversation(): Conversation {
        syncManager.waitUntilLive()
        val conversations = listConversations()
        val selectedConversationIndex =
            terminal.prompt("Enter conversation index", promptSuffix = ": ")
                ?.toInt() ?: throw PrintMessage("Index must be an integer")

        return conversations[selectedConversationIndex]
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun displayConversation(conversationId: ConversationId) {
        terminal.withRawMode {
            terminal.cursor.move {
                setPosition(0, 0)
                clearScreen()
            }

            GlobalScope.launch(Dispatchers.Default) {
                combine(
                    userSession.messages.getRecentMessages(conversationId, limit = 100),
                    userSession.conversations.getConversationDetails(conversationId)
                        .mapNotNull { if (it is GetConversationUseCase.Result.Success) it.conversation.name else null },
                    actionFlow(userSession)
                        .onEach {
                            when (it) {
                                is InputAction.Quit -> {
                                    finished = true
                                    cancel()
                                }
                                is InputAction.SendText -> userSession.messages.sendTextMessage(
                                    conversationId,
                                    it.draft
                                )
                                is InputAction.RunCommand -> {
                                    when (it.command) {
                                        is Command.Jump -> {
                                            it.command.selection?.let {
                                                currentConversationId = it.conversation.id
                                                cancel()
                                            }
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }
                        .map { if (it is InputAction.SendText) InputAction.UpdateDraft("", 0) else it }
                        .filterIsInstance<InputAction.UpdateDraft>()
                ) { messages, conversationName, updateDraft ->
                    render(
                        ViewState(
                            conversationName,
                            messages.reversed(),
                            updateDraft.draft,
                            updateDraft.description,
                            updateDraft.cursorPosition
                        )
                    )
                }.collect()
            }.join()
        }
    }

    @Suppress("UnusedPrivateMember")
    private fun enableReadTimeout() = memScoped {
        val termios = alloc<termios>()
        if (tcgetattr(STDIN_FILENO, termios.ptr) != 0) {
            return@memScoped
        }

        termios.c_cc[VMIN] = 0u
        termios.c_cc[VTIME] = 1u

        tcsetattr(STDIN_FILENO, TCSAFLUSH, termios.ptr)
    }
}
