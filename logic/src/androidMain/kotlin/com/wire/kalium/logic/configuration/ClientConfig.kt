package com.wire.kalium.logic.configuration

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType

actual class ClientConfig(private val context: Context) {

    actual fun deviceType(): DeviceType {
        val screenSize = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return if (screenSize >= Configuration.SCREENLAYOUT_SIZE_LARGE)
            DeviceType.Tablet
        else
            DeviceType.Phone
    }

    actual fun deviceModelName(): String = "${Build.BRAND} ${Build.MODEL}"

    actual fun deviceName(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    } else {
        deviceModelName()
    }

    actual fun clientType(): ClientType = ClientType.Permanent

}
