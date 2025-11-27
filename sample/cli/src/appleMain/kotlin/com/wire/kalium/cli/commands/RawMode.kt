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

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.STDOUT_FILENO
import platform.posix.TCSAFLUSH
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios

fun Terminal.setRawMode(enabled: Boolean) = memScoped {
    val termios = alloc<termios>()
    if (tcgetattr(STDOUT_FILENO, termios.ptr) != 0) {
        return@memScoped
    }

    if (enabled) {
        termios.c_lflag = termios.c_lflag and ICANON.inv().convert()
        termios.c_lflag = termios.c_lflag and ECHO.inv().convert()
    } else {
        termios.c_lflag = termios.c_lflag or ICANON.convert()
        termios.c_lflag = termios.c_lflag or ECHO.convert()
    }

    tcsetattr(0, TCSAFLUSH, termios.ptr)
}

inline fun <T> Terminal.withRawMode(block: () -> T): T {
    setRawMode(true)
    val result = block()
    setRawMode(false)
    return result
}
