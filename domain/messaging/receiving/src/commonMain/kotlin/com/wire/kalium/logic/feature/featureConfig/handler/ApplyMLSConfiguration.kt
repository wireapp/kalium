/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.SupportedProtocol

/** Shares the app's settings order while leaving crypto transaction ownership with the caller. */
public suspend fun UserConfigRepository.applyMLSConfiguration(
    configuration: MLSModel,
    requirePreviousProtocols: Boolean = false,
    updateSupportedProtocols: suspend () -> Either<CoreFailure, Unit>,
): Either<CoreFailure, Unit> {
    val previous = getSupportedProtocols()
    if (requirePreviousProtocols && previous is Either.Left && previous.value !is StorageFailure.DataNotFound) return previous
    val changed = previous.getOrElse(setOf(SupportedProtocol.PROTEUS)) != configuration.supportedProtocols
    val enabled = configuration.status == Status.ENABLED
    return setMLSEnabled(enabled && SupportedProtocol.MLS in configuration.supportedProtocols)
        .flatMap { setDefaultProtocol(if (enabled) configuration.defaultProtocol else SupportedProtocol.PROTEUS) }
        .flatMap { setSupportedProtocols(configuration.supportedProtocols) }
        .flatMap { if (changed) updateSupportedProtocols() else Either.Right(Unit) }
        .flatMap { configuration.supportedCipherSuite?.let { setSupportedCipherSuite(it) } ?: Either.Right(Unit) }
}
