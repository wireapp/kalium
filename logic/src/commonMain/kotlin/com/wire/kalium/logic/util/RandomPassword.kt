/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.util

internal class RandomPassword {
    internal operator fun invoke(): String {

        val secureRandom = SecureRandom()

        val passwordLength = secureRandom.nextInt(MAX_LENGTH - MIN_LENGTH + 1) + MIN_LENGTH

        return buildList {
            add(lowercase[secureRandom.nextInt(lowercase.size)])
            add(uppercase[secureRandom.nextInt(uppercase.size)])
            add(digits[secureRandom.nextInt(digits.size)])
            add(specialChars[secureRandom.nextInt(specialChars.size)])

            repeat(passwordLength - FIXED_CHAR_COUNT) {
                add(allCharacters[secureRandom.nextInt(allCharacters.size)])
            }
        }.shuffled().joinToString("")
    }

    internal companion object Companion {
        internal val lowercase: List<Char> = ('a'..'z').shuffled()
        internal val uppercase: List<Char> = ('A'..'Z').shuffled()
        internal val digits: List<Char> = ('0'..'9').shuffled()
        internal val specialChars: List<Char> = "!@#$%^&*()_+[]{}|;:,.<>?-".toList().shuffled()

        internal val allCharacters: List<Char> = (lowercase + uppercase + digits + specialChars).shuffled()

        internal const val MIN_LENGTH = 15
        internal const val MAX_LENGTH = 20
        internal const val FIXED_CHAR_COUNT = 4
    }

}
