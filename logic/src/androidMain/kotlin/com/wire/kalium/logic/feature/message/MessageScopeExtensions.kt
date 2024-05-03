/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.feature.asset.GetPaginatedFlowOfAssetMessageByConversationIdUseCase
import com.wire.kalium.logic.feature.asset.ObservePaginatedAssetImageMessages

val MessageScope.getPaginatedFlowOfMessagesByConversation
    get() = GetPaginatedFlowOfMessagesByConversationUseCase(dispatcher, messageRepository)

val MessageScope.getPaginatedFlowOfMessagesBySearchQueryAndConversation
    get() = GetPaginatedFlowOfMessagesBySearchQueryAndConversationIdUseCase(dispatcher, messageRepository)

val MessageScope.getPaginatedFlowOfAssetMessageByConversationId
    get() = GetPaginatedFlowOfAssetMessageByConversationIdUseCase(dispatcher, messageRepository)

val MessageScope.observePaginatedImageAssetMessageByConversationId
    get() = ObservePaginatedAssetImageMessages(dispatcher, messageRepository)
