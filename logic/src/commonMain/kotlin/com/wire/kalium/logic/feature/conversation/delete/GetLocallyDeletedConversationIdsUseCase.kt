/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.wire.kalium.logic.feature.conversation.delete

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId

/** Returns conversations hidden locally whose durable deletion still needs to finish. */
public fun interface GetLocallyDeletedConversationIdsUseCase {
    public suspend operator fun invoke(): Either<CoreFailure, List<ConversationId>>
}

internal class GetLocallyDeletedConversationIdsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
) : GetLocallyDeletedConversationIdsUseCase {
    override suspend fun invoke(): Either<CoreFailure, List<ConversationId>> =
        conversationRepository.getLocallyDeletedConversationIds()
}
