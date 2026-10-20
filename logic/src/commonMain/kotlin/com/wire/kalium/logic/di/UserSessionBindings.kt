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
@file:OptIn(InternalKaliumApi::class, DelicateKaliumApi::class)

package com.wire.kalium.logic.di

import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi

/** Each binding calls qualified providers at the original read position, including inside closures.
 * Cached roots retain one instance per user graph. Owner eager vals resolve these bindings at their
 * unchanged declarations; provider parameters themselves must never force their values eagerly.
 */
internal interface UserSessionBindings : UserSessionApi, UserSessionConstructionBindings, UserSessionCallOperationBindings

/** Contains no entrypoints; narrow generated test roots can exercise the production providers. */
internal interface UserSessionConstructionBindings :
    UserSessionPushUpdaterBindings,
    UserSessionConstructorBindings,
    UserSessionResourceBindings,
    UserSessionSyncBindings,
    UserSessionEventBindings,
    UserSessionDeferredBindings,
    UserSessionFactoryBindings
