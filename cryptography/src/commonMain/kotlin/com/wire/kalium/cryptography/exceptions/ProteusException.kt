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

package com.wire.kalium.cryptography.exceptions

open class ProteusException(message: String?, val code: Code, val intCode: Int?, cause: Throwable? = null) : Exception(message, cause) {

    constructor(message: String?, code: Int, cause: Throwable? = null) : this(
        message,
        fromNativeCode(code),
        code,
        cause
    )

    enum class Code {
        /**
         * A requested session was not found.
         */
        SESSION_NOT_FOUND,

        /**
         * The remote identity of a session changed.
         *
         *
         * Usually the user should be informed and the session reinitialised.
         * If the remote fingerprint was previously verified, it will need to be
         * verified anew in order to exclude any potential MITM.
         */
        REMOTE_IDENTITY_CHANGED,

        /**
         * The signature of a decrypted message is invalid.
         *
         *
         * The message being decrypted is incomplete or has otherwise been
         * tampered with.
         */
        INVALID_SIGNATURE,

        /**
         * A message is invalid.
         *
         *
         * The message is well-formed but cannot be decrypted, e.g.
         * because the message is used to initialise a session but does not
         * contain a [PreKey] or the used session does not contain the
         * appropriate key material for decrypting the message. The problem
         * should be reported to the user, as it might be necessary for both
         * peers to re-initialise their sessions.
         */
        INVALID_MESSAGE,

        /**
         * A message is a duplicate.
         *
         *
         * The message being decrypted is a duplicate of a message that has
         * previously been decrypted with the same session. The message can
         * be safely discarded.
         */
        DUPLICATE_MESSAGE,

        /**
         * A message is too recent.
         *
         *
         * There is an unreasonably large gap between the last decrypted
         * message and the message being decrypted, i.e. there are too many
         * intermediate messages missing. The message should be dropped.
         */
        TOO_DISTANT_FUTURE,

        /**
         * A message is too old.
         *
         *
         * The message being decrypted is unreasonably old and cannot
         * be decrypted any longer due to the key material no longer being available.
         * The message should be dropped.
         */
        OUTDATED_MESSAGE,

        /**
         * A message or key could not be decoded.
         *
         *
         * The message or key being decoded is either malformed or
         * otherwise encoded in a way such it cannot be understood.
         */
        DECODE_ERROR,

        /**
         * An internal storage error occurred.
         *
         *
         * An error occurred while loading or persisting key material.
         * The operation may be retried a limited number of times.
         */
        STORAGE_ERROR,

        /**
         * A CBox has been opened with an incomplete or mismatching identity
         * using [CryptoBox.openWith].
         *
         *
         * This is typically a programmer error.
         */
        IDENTITY_ERROR,

        /**
         * An attempt was made to initialise a new session using [CryptoBox.initSessionFromMessage]
         * whereby the prekey corresponding to the prekey ID in the message could not be found.
         */
        PREKEY_NOT_FOUND,

        /**
         * A panic occurred. This is a last resort error raised form native code to
         * signal a severe problem, like a violation of a critical invariant, that
         * would otherwise have caused a crash. Client code can choose to handle
         * these errors more gracefully, preventing the application from crashing.
         *
         *
         * Note that any [CryptoSession]s which might have been involved in a
         * computation leading to a panic must no longer be used as their in-memory
         * state may be corrupt. Such sessions should be closed and may be subsequently
         * reloaded to retry the operation(s).
         */
        PANIC,

        /**
         * An unspecified error occurred.
         */
        UNKNOWN_ERROR,

        /**
         * Local files were not found.
         */
        LOCAL_FILES_NOT_FOUND;
    }

    companion object {

        const val SESSION_NOT_FOUND_INT = 2

        @Suppress("MagicNumber")
        fun fromNativeCode(code: Int): Code {
            return when (code) {
                1 -> Code.STORAGE_ERROR
                2 -> Code.SESSION_NOT_FOUND
                3 -> Code.DECODE_ERROR
                4 -> Code.REMOTE_IDENTITY_CHANGED
                5 -> Code.INVALID_SIGNATURE
                6 -> Code.INVALID_MESSAGE
                7 -> Code.DUPLICATE_MESSAGE
                8 -> Code.TOO_DISTANT_FUTURE
                9 -> Code.OUTDATED_MESSAGE
                13 -> Code.IDENTITY_ERROR
                14 -> Code.PREKEY_NOT_FOUND
                15 -> Code.PANIC
                else -> Code.UNKNOWN_ERROR
            }
        }

        /**
         * Those error codes are mapped directly from [com.wire.crypto.ProteusException]:
         * - [Code.SESSION_NOT_FOUND]
         * - [Code.REMOTE_IDENTITY_CHANGED]
         * - [Code.DUPLICATE_MESSAGE]
         *
         * See the mapping: [com.wire.kalium.cryptography.ProteusClientCoreCryptoImpl.Companion.mapProteusExceptionToErrorCode]
         *
         * [Mapping sources](https://github.com/wireapp/proteus/blob/2.x/crates/proteus-traits/src/lib.rs)
         *
         * [Mapping source README](https://github.com/wireapp/wire-web-core/blob/7383e108f5e9d15d0b82c41ed504964667463cfc/packages/proteus/README.md)
         */
        fun fromProteusCode(code: Int): Code {
            @Suppress("MagicNumber")
            return when (code) {
                501 -> Code.STORAGE_ERROR
                3, 301, 302, 303 -> Code.DECODE_ERROR
                206, 207, 210 -> Code.INVALID_SIGNATURE
                200, 201, 202, 205, 213 -> Code.INVALID_MESSAGE
                211, 212 -> Code.TOO_DISTANT_FUTURE
                208 -> Code.OUTDATED_MESSAGE
                300 -> Code.IDENTITY_ERROR
                101 -> Code.PREKEY_NOT_FOUND
                5 -> Code.PANIC
                else -> Code.UNKNOWN_ERROR
            }
        }
    }
}

class ProteusStorageMigrationException(override val message: String, val rootCause: Throwable? = null) :
    ProteusException(message, Int.MIN_VALUE, null)
