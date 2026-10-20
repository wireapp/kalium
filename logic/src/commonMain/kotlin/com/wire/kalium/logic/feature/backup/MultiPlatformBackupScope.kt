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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.backup

import kotlinx.coroutines.CoroutineDispatcher

/** Compatibility facade over its own fresh operation graph. */
public class MultiPlatformBackupScope internal constructor(private val api: LegacyMultiPlatformBackupApi) {
    public val create: CreateMPBackupUseCase
        get() = api.mpBackupCreate

    public val restore: RestoreMPBackupUseCase
        get() = api.mpBackupRestore

    /** Dispatch progress on the caller-selected dispatcher; the property keeps its default. */
    public fun restore(progressDispatcher: CoroutineDispatcher): RestoreMPBackupUseCase =
        api.mpRestoreFactory.create(progressDispatcher)
}
