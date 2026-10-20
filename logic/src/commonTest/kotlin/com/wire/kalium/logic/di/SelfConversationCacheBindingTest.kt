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

import com.wire.kalium.logic.cache.MLSSelfConversationIdProvider
import com.wire.kalium.logic.cache.MLSSelfConversationIdProviderImpl
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProvider
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProviderImpl
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.cache.SelfConversationIdProviderImpl
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import dev.mokkery.mock
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Exercises the production constructor aliases and qualified cache adapters. */
@DependencyGraph(UserLifetime::class)
internal interface SelfConversationCacheGraph : UserSessionConstructorBindings {
    @get:UserSessionBinding("selfConversationIdProvider")
    val self: SelfConversationIdProvider

    @get:UserSessionBinding("mlsSelfConversationIdProvider")
    val mls: MLSSelfConversationIdProvider

    @get:UserSessionBinding("proteusSelfConversationIdProvider")
    val proteus: ProteusSelfConversationIdProvider

    val freshSelf: SelfConversationIdProviderImpl
    val freshMls: MLSSelfConversationIdProviderImpl
    val freshProteus: ProteusSelfConversationIdProviderImpl

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Includes repositories: SelfConversationRepositoryInputs,
            @Provides @UserSessionBinding("clientRepository") clients: ClientRepository,
        ): SelfConversationCacheGraph
    }
}

internal interface SelfConversationRepositoryInputs {
    @get:UserSessionBinding("conversationRepository")
    val repository: ConversationRepository
}

class SelfConversationCacheBindingTest {
    @Test
    fun qualifiedCachesArePerGraphWhileImplementationRequestsStayFresh() {
        val captured = mutableListOf<ConversationRepository>()
        val inputs = object : SelfConversationRepositoryInputs {
            override val repository: ConversationRepository
                get() = mock<ConversationRepository>().also(captured::add)
        }
        val factory = createGraphFactory<SelfConversationCacheGraph.Factory>()
        val first = factory.create(inputs, mock())
        assertEquals(0, captured.size)
        assertSame(first.self, first.self)
        assertSame(first.mls, first.mls)
        assertSame(first.proteus, first.proteus)
        assertEquals(2, captured.size)
        assertSame(captured[0], (first.mls as MLSSelfConversationIdProviderImpl).conversationRepository)
        assertSame(captured[1], (first.proteus as ProteusSelfConversationIdProviderImpl).conversationRepository)
        assertNotSame(captured[0], captured[1])
        assertNotSame(first.freshSelf, first.freshSelf)
        assertNotSame(first.freshMls, first.freshMls)
        assertNotSame(first.freshProteus, first.freshProteus)
        val second = factory.create(inputs, mock())
        assertNotSame(first.self, second.self)
        assertNotSame(first.mls, second.mls)
        assertNotSame(first.proteus, second.proteus)
        assertEquals(8, captured.size)
    }
}
