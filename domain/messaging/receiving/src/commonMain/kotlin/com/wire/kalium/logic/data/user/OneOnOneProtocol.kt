/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

/** The team default does not override protocols supported by both participants. */
fun selectOneOnOneProtocol(
    self: Set<SupportedProtocol>,
    other: Set<SupportedProtocol>,
): Either<CoreFailure, SupportedProtocol> {
    val common = self.intersect(other)
    return when {
        SupportedProtocol.MLS in common -> Either.Right(SupportedProtocol.MLS)
        SupportedProtocol.PROTEUS in common -> Either.Right(SupportedProtocol.PROTEUS)
        SupportedProtocol.MLS in self -> Either.Left(CoreFailure.NoCommonProtocolFound.OtherNeedToUpdate)
        else -> Either.Left(CoreFailure.NoCommonProtocolFound.SelfNeedToUpdate)
    }
}
