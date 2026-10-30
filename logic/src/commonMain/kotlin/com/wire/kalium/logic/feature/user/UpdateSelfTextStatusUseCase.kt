/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.AccountRepository

/** Updates the profile status. Null and blank values clear it on all clients. */
public class UpdateSelfTextStatusUseCase internal constructor(
    private val accountRepository: AccountRepository,
) {
    public suspend operator fun invoke(textStatus: String?): Either<CoreFailure, Unit> {
        val normalizedStatus = textStatus?.takeUnless { it.isBlank() }
        if (normalizedStatus != null && !normalizedStatus.hasValidLength()) {
            return Either.Left(CoreFailure.Unknown(IllegalArgumentException("Text status exceeds $MAX_LENGTH Unicode code points")))
        }
        return accountRepository.updateSelfUserTextStatus(normalizedStatus)
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
}
