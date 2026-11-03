/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.user.showNotes

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * UseCase allowing to observe whether the user chose to show the "Notes to self" conversation.
 * The value is synced across the user's devices. Disabled by default.
 */
public interface ObserveShowNotesEnabledUseCase {
    public suspend operator fun invoke(): Flow<Boolean>
}

internal class ObserveShowNotesEnabledUseCaseImpl(
    private val userPropertyRepository: UserPropertyRepository,
) : ObserveShowNotesEnabledUseCase {

    override suspend fun invoke(): Flow<Boolean> =
        userPropertyRepository.observeShowNotesStatus().map { result ->
            result.fold({ false }, { it })
        }
}
