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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public interface ConversationEventReceiver : EventReceiver<Event.Conversation> {
    public suspend fun flushPendingSideEffects(): Either<CoreFailure, Unit> = Either.Right(Unit)
}

@InternalKaliumApi
public interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

@InternalKaliumApi
public interface FederationEventReceiver : EventReceiver<Event.Federation>

@InternalKaliumApi
public interface MeetingEventReceiver : EventReceiver<Event.Meeting>

@InternalKaliumApi
public interface TeamEventReceiver : EventReceiver<Event.Team>

@InternalKaliumApi
public interface UserEventReceiver : EventReceiver<Event.User>

@InternalKaliumApi
public interface UserPropertiesEventReceiver : EventReceiver<Event.UserProperty>
