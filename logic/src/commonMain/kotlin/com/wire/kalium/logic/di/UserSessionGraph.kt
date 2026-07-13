/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.feature.conversation.ConversationDependencies
import com.wire.kalium.logic.feature.conversation.ConversationEntryPoints
import com.wire.kalium.logic.feature.conversation.ConversationUseCaseBindings
import com.wire.kalium.logic.feature.message.MessageDependencies
import com.wire.kalium.logic.feature.message.MessageEntryPoints
import com.wire.kalium.logic.feature.message.MessageUseCaseBindings
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes

internal object UserSessionLifetime

@DependencyGraph(
    scope = UserSessionLifetime::class,
    bindingContainers = [ConversationUseCaseBindings::class, MessageUseCaseBindings::class]
)
internal interface UserSessionGraph : ConversationEntryPoints, MessageEntryPoints, CellsFeatureGraph.Factory {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Includes conversationDependencies: ConversationDependencies,
            @Includes messageDependencies: MessageDependencies,
        ): UserSessionGraph
    }
}
