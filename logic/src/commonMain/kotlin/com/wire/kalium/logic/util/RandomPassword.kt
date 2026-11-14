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

import androidx.annotation.IntRange

public class RandomPassword {
    public operator fun invoke(): String = invoke(minLength = DEFAULT_MIN_LENGTH, maxLength = DEFAULT_MAX_LENGTH)

    /**
     * Generates a password with a length in the inclusive [minLength]..[maxLength] range, defaulting to 15..20.
     *
     * @throws IllegalArgumentException if [minLength] is less than 8 or [maxLength] is less than [minLength].
     */
    public operator fun invoke(
        @IntRange(from = MIN_ALLOWED_LENGTH.toLong()) minLength: Int = DEFAULT_MIN_LENGTH,
        maxLength: Int = DEFAULT_MAX_LENGTH
    ): String {
        require(minLength >= MIN_ALLOWED_LENGTH) { "minLength must be at least $MIN_ALLOWED_LENGTH" }
        require(maxLength >= minLength) { "maxLength must be at least minLength" }

        val secureRandom = SecureRandom()

        val passwordLength = secureRandom.nextInt(maxLength - minLength + 1) + minLength

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

        internal const val DEFAULT_MIN_LENGTH = 15
        internal const val DEFAULT_MAX_LENGTH = 20
        internal const val FIXED_CHAR_COUNT = 4
        private const val MIN_ALLOWED_LENGTH = 8
    }

}
