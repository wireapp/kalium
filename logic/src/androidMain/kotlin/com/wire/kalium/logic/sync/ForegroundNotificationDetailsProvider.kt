package com.wire.kalium.logic.sync

import androidx.annotation.DrawableRes

/**
 * Provide resources that will be displayed when Kalium
 * needs to display a Foreground notification due to some work being done.
 */
interface ForegroundNotificationDetailsProvider {
    @DrawableRes
    fun getSmallIconResId(): Int
}
