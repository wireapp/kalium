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

import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.notificationToken.PushTokenUpdater
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides

internal interface UserSessionPushUpdaterBindings {
    @Provides
    @UserSessionBinding("pushTokenUpdater")
    fun userSessionProvidePushTokenUpdater(
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("notificationTokenRepository")
        notificationTokenRepository: Provider<NotificationTokenDataSource>,
        @UserSessionBinding("pushTokenRepository")
        pushTokenRepository: Provider<PushTokenRepository>,
        @UserSessionBinding("globalSessionRepository")
        sessionRepository: Provider<SessionRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): PushTokenUpdater = PushTokenUpdater(
        clientRepository(),
        notificationTokenRepository(),
        pushTokenRepository(),
        sessionRepository(),
        userId(),
    )
}
