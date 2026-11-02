/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.user.AccountRepository

/** Updates the profile status. Null and blank values clear it on all clients. */
public class UpdateSelfTextStatusUseCase internal constructor(
    private val accountRepository: AccountRepository,
) {
    public suspend operator fun invoke(textStatus: String?): Result {
        val normalizedStatus = textStatus?.takeUnless { it.isBlank() }
        if (normalizedStatus != null && !normalizedStatus.hasValidLength()) {
            return Result.Failure(CoreFailure.Unknown(IllegalArgumentException("Text status exceeds $MAX_LENGTH Unicode code points")))
        }
        return accountRepository.updateSelfUserTextStatus(normalizedStatus).fold(
            { Result.Failure(it) },
            { Result.Success },
        )
    }

    private fun String.hasValidLength(): Boolean {
        var index = 0
        var count = 0
        while (index < length) {
            if (++count > MAX_LENGTH) return false
            index += if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) 2 else 1
        }
        return true
    }

    public companion object {
        public const val MAX_LENGTH: Int = 256
    }

    public sealed class Result {
        public data object Success : Result()
        public data class Failure(val coreFailure: CoreFailure) : Result()
    }
}
