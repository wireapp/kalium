/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.user.UserRepository

/** Refreshes the stored self profile from the backend, preserving fetch failures for callers to retry. */
public class RefreshSelfUserUseCase internal constructor(
    private val userRepository: UserRepository,
) {
    public suspend operator fun invoke(): Result = userRepository.fetchSelfUser().fold(
        { Result.Failure(it) },
        { Result.Success },
    )

    public sealed class Result {
        public data object Success : Result()
        public data class Failure(val coreFailure: CoreFailure) : Result()
    }
}
