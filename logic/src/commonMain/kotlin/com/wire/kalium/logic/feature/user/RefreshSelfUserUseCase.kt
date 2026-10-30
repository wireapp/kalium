/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserRepository

/** Refreshes the stored self profile from the backend, preserving fetch failures for callers to retry. */
public class RefreshSelfUserUseCase internal constructor(
    private val userRepository: UserRepository,
) {
    public suspend operator fun invoke(): Either<CoreFailure, Unit> = userRepository.fetchSelfUser()
}
