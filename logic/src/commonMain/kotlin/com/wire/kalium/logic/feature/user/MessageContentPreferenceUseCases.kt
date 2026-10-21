/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.MessageContentPreference
import com.wire.kalium.logic.configuration.UserConfigRepository

/** Reads this account’s saved choice without applying notification policy; absence and failure remain distinct. */
public class GetMessageContentPreferenceUseCase internal constructor(private val repository: UserConfigRepository) {
    // Retain the existing public result type for consumer compatibility.
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend operator fun invoke(): Either<StorageFailure, MessageContentPreference> = repository.getMessageContentPreference()
}

/** Saves this account’s choice; success means persistence completed, and cancellation propagates. */
public class SetMessageContentPreferenceUseCase internal constructor(private val repository: UserConfigRepository) {
    // Retain the existing public result type for consumer compatibility.
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend operator fun invoke(enabled: Boolean): Either<StorageFailure, Unit> = repository.setMessageContentPreference(enabled)
}
