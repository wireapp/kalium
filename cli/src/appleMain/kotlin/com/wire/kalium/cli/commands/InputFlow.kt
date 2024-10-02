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

// import kotlinx.cinterop.ByteVar
// import kotlinx.cinterop.addressOf
// import kotlinx.cinterop.alloc
// import kotlinx.cinterop.memScoped
// import kotlinx.cinterop.ptr
// import kotlinx.cinterop.usePinned
// import kotlinx.cinterop.value
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.delay
// import kotlinx.coroutines.flow.Flow
// import kotlinx.coroutines.flow.flow
// import kotlinx.coroutines.flow.flowOn
// import platform.posix.STDIN_FILENO
// import platform.posix.read
// import platform.posix.ssize_t

// @Suppress("MagicNumber")
// fun inputFlow(): Flow<Input> = flow {
//     while (true) {
//         emit(readChar())
//         delay(100) // TODO jacob avoid this hack by enabling read timeout
//     }
// }.flowOn(Dispatchers.Default)
//
// @Suppress("ComplexMethod", "TooGenericExceptionThrown", "MagicNumber")
// fun readChar(): Input =
//     memScoped {
//         val byte = alloc<ByteVar>()
//         var numBytesRead: ssize_t
//         while (read(STDIN_FILENO, byte.ptr, 1).also { numBytesRead = it } != 1L) {
//             if (numBytesRead == -1L) { throw RuntimeException("Failed to read input") }
//         }
//
//         val char = byte.value.toInt().toChar()
//         if (char == '\u001b') {
//             val sequence = ByteArray(3)
//             sequence.usePinned {
//                 if (read(STDIN_FILENO, it.addressOf(0), 1) != 1L) { return Input.Character('\u001b') }
//                 if (read(STDIN_FILENO, it.addressOf(1), 1) != 1L) { return Input.Character('\u001b') }
//             }
//
//             if (sequence[0].toInt().toChar() == '[') {
//                 when (sequence[1].toInt().toChar()) {
//                     in '0'..'9' -> {
//                         sequence.usePinned {
//                             if (read(STDIN_FILENO, it.addressOf(2), 1) != 1L) { return Input.Character('\u001b') }
//                         }
//                         if (sequence[2].toInt().toChar() == '~') {
//                             when (sequence[1].toInt().toChar()) {
//                                 '1', '7' -> return Input.HomeKey
//                                 '3', '8' -> return Input.DeleteKey
//                                 '4' -> return Input.EndKey
//                                 '5' -> return Input.PageUp
//                                 '6' -> return Input.PageDown
//                             }
//                         }
//                     }
//                     'A' -> return Input.ArrowUp
//                     'B' -> return Input.ArrowDown
//                     'C' -> return Input.ArrowRight
//                     'D' -> return Input.ArrowLeft
//                 }
//             }
//         }
//
//         @Suppress("MagicNumber")
//         when (char.code) {
//             127 -> return Input.Backspace
//             else -> return Input.Character(char)
//         }
//     }
//
// sealed class Input {
//     data class Character(val char: Char) : Input()
//     data object ArrowUp : Input()
//     data object ArrowDown : Input()
//     data object ArrowLeft : Input()
//     data object ArrowRight : Input()
//     data object HomeKey : Input()
//     data object EndKey : Input()
//     data object DeleteKey : Input()
//     data object PageUp : Input()
//     data object PageDown : Input()
//     data object Backspace : Input()
// }
