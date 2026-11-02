/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.cells

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.network.exceptions.KaliumException

/** Diagnostic types and status only; server messages may contain filenames or signed URLs. */
internal fun NetworkFailure.diagnosticSummary(): String {
    val error = (this as? NetworkFailure.ServerMiscommunication)?.kaliumException
    val code = when (error) {
        is KaliumException.InvalidRequestError -> error.errorResponse.code
        is KaliumException.ServerError -> error.errorResponse.code
        else -> null
    }
    val cause = (error as? KaliumException.GenericError)?.cause
    return "${this::class.simpleName} exception=${error?.let { it::class.simpleName }} " +
        "status=$code cause=${cause?.let { it::class.simpleName }}"
}
