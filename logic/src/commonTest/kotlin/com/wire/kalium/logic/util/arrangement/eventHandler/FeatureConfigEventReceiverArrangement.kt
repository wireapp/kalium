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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

internal interface FeatureConfigEventReceiverArrangement {

    val featureConfigEventReceiver: FeatureConfigEventReceiver

    suspend fun withFeatureConfigEventReceiverArrangement(
        result: Either<CoreFailure, Unit>,
        event: (Event.FeatureConfig) -> Boolean = { true }
    )
}

internal class FeatureConfigEventReceiverArrangementImpl : FeatureConfigEventReceiverArrangement {

    override val featureConfigEventReceiver: FeatureConfigEventReceiver = mock<FeatureConfigEventReceiver>(mode = MockMode.autoUnit)

    override suspend fun withFeatureConfigEventReceiverArrangement(
        result: Either<CoreFailure, Unit>,
        event: (Event.FeatureConfig) -> Boolean
    ) {
        everySuspend {
            featureConfigEventReceiver.onEvent(any(), matches { event(it) }, any())
        }.returns(result)
    }
}
