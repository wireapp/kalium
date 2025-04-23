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
package com.wire.kalium.logic.util.arrangement.eventHandler

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

internal interface FeatureConfigEventReceiverArrangement {

    val featureConfigEventReceiver: FeatureConfigEventReceiver

    suspend fun withFeatureConfigEventReceiverArrangement(
        result: Either<CoreFailure, Unit>,
        event: Matcher<Event.FeatureConfig> = AnyMatcher(valueOf())
    )
}

internal class FeatureConfigEventReceiverArrangementImpl : FeatureConfigEventReceiverArrangement {

    override val featureConfigEventReceiver: FeatureConfigEventReceiver = mock(FeatureConfigEventReceiver::class)

    override suspend fun withFeatureConfigEventReceiverArrangement(
        result: Either<CoreFailure, Unit>,
        event: Matcher<Event.FeatureConfig>
    ) {
        coEvery {
            featureConfigEventReceiver.onEvent(matches { event.matches(it) }, any())
        }.returns(result)
    }
}
