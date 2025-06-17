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

package com.wire.kalium.logic.configuration

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.wire.kalium.logic.data.client.DeviceType
import android.provider.Settings
import com.wire.kalium.logic.data.client.ClientType

actual class ClientConfigImpl(private val context: Context) : ClientConfig {

    actual override fun deviceType(): DeviceType {
        val screenSize = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return if (screenSize >= Configuration.SCREENLAYOUT_SIZE_LARGE)
            DeviceType.Tablet
        else
            DeviceType.Phone
    }

    actual override fun deviceModelName(): String = "${Build.BRAND} ${Build.MODEL}"

    actual override fun deviceName(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    } else {
        deviceModelName()
    }

    actual override fun clientType(): ClientType = ClientType.Permanent

}
