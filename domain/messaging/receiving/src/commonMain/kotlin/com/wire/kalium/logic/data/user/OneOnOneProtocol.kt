/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.persistence.dao.conversation.ConversationEntity

// The backend's MLS 1:1 has no team. A team group with one other member is also typed OneOnOne locally,
// but it is a separate group and must never stand in for the peer's 1:1.
fun Conversation.isBackendOneOnOne(): Boolean = type == Conversation.Type.OneOnOne && teamId == null
fun ConversationEntity.isBackendOneOnOne(): Boolean = type == ConversationEntity.Type.ONE_ON_ONE && teamId == null

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
