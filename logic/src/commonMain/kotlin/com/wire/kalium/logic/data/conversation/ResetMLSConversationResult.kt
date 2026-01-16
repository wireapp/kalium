/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Result type for MLS conversation reset operations that return Unit on success.
 * This provides a Swift-friendly API while maintaining backward compatibility with Either for internal use.
 */
public sealed class ResetMLSConversationResult {
    /**
     * Indicates the reset MLS conversation operation completed successfully.
     */
    public data object Success : ResetMLSConversationResult()

    /**
     * Indicates the reset MLS conversation operation failed.
     * @param error The error that occurred during the operation.
     */
    public data class Failure(val error: CoreFailure) : ResetMLSConversationResult()

    /**
     * Converts this result to an Either type for internal Kalium use or JVM/Android clients.
     * This function is hidden from iOS/Swift to maintain a clean Swift API.
     *
     * @return Either.Right(Unit) for Success, Either.Left(error) for Failure
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public fun toEither(): Either<CoreFailure, Unit> =
        when (this) {
            is Success -> Either.Right(Unit)
            is Failure -> Either.Left(error)
        }
}
