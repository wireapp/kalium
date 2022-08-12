package com.wire.kalium.logic.configuration

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.wire.kalium.logic.data.client.DeviceType
import android.provider.Settings
import com.wire.kalium.logic.data.client.ClientType

actual class ClientConfigImpl(private val context: Context) : ClientConfig {

    override fun deviceType(): DeviceType {
        if ((context.resources.configuration.screenLayout
                    and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        )
            return DeviceType.Tablet
        return DeviceType.Phone
    }

    override fun deviceModelName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun deviceName(): String = Settings.Secure.getString(context.contentResolver, bluetoothName) ?: run { deviceModelName() }

    override fun clientType(): ClientType = ClientType.Permanent

    private companion object {
        private const val bluetoothName = "bluetooth_name"
    }
}
