/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.client

import com.wire.kalium.persistence.client.ClientRegistrationStorage
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * This provider checks if the client is capable of async notifications.
 * It does this by checking if the client has consumable notifications capability.
 *
 * At some point Legacy notifications will be deprecated and removed from the codebase.
 */
internal class IsClientAsyncNotificationsCapableProviderImpl(
    private val clientRegistrationStorage: ClientRegistrationStorage,
    coroutineScope: CoroutineScope
) : IsClientAsyncNotificationsCapableProvider {

    private val _lastValue = MutableStateFlow(false)
    override val isClientAsyncNotificationsCapableFlow: Flow<Boolean> = _lastValue.asStateFlow()
    override fun isClientAsyncNotificationsCapable(): Boolean = _lastValue.value

    init {
        coroutineScope.launch {
            clientRegistrationStorage.observeHasConsumableNotifications().collect {
                _lastValue.value = it
            }
        }
    }
}

@Mockable
internal interface IsClientAsyncNotificationsCapableProvider {
    val isClientAsyncNotificationsCapableFlow: Flow<Boolean>
    fun isClientAsyncNotificationsCapable(): Boolean
}
