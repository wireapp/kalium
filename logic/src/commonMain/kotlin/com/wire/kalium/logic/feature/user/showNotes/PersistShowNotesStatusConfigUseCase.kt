/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.user.showNotes

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE
import com.wire.kalium.logic.data.properties.UserPropertyRepository

/**
 * UseCase allowing to persist whether the "Notes to self" conversation is shown.
 * The value is stored as a user property, so it is synced to the user's other devices.
 */
public interface PersistShowNotesStatusConfigUseCase {
    public suspend operator fun invoke(enabled: Boolean): ShowNotesConfigResult
}

internal class PersistShowNotesStatusConfigUseCaseImpl(
    private val userPropertyRepository: UserPropertyRepository,
) : PersistShowNotesStatusConfigUseCase {

    private val logger by lazy { kaliumLogger.withFeatureId(LOCAL_STORAGE) }

    override suspend fun invoke(enabled: Boolean): ShowNotesConfigResult {
        val result = when (enabled) {
            true -> userPropertyRepository.setShowNotesEnabled()
            false -> userPropertyRepository.deleteShowNotesProperty()
        }

        return result.fold({
            logger.e("Failed trying to update Show Notes configuration flag")
            ShowNotesConfigResult.Failure(it)
        }) {
            ShowNotesConfigResult.Success
        }
    }
}

public sealed class ShowNotesConfigResult {
    public data object Success : ShowNotesConfigResult()
    public data class Failure(val cause: CoreFailure) : ShowNotesConfigResult()
}
