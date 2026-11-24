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
package com.wire.kalium.cells.domain.usecase.download

import kotlinx.coroutines.CancellationException

/**
 * Cancel a cell file download with this, e.g. `job.cancel(CellDownloadPausedException())`, to pause it rather than
 * drop it: what was downloaded so far is kept, and the next download of the same file resumes from there.
 *
 * Meant for when the device lost its connection: the request would otherwise only fail once it times out.
 */
public class CellDownloadPausedException : CancellationException("Cell file download paused")

/** Whether this cancellation, or one it was caused by, is a [CellDownloadPausedException]. */
internal fun CancellationException.isDownloadPaused(): Boolean =
    generateSequence<Throwable>(this) { it.cause }.any { it is CellDownloadPausedException }
