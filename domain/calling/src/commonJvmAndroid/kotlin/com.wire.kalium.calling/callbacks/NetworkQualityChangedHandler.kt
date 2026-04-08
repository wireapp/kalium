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

package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

/**
 * Callback interface for handling network quality changes in a call.
 *
 *    "qualityInfoJson": {
 *      "quality": int,         one of: NORMAL=1, MEDIUM=2, POOR=3, NETWORK_PROBLEM=4, RECONNECTING=5
 *      "tx": int,              percentage of up packets lost
 *      "rx": int,              percentage of down packets lost
 *      "loss": {
 *        "tx": int,            percentage of up packets lost
 *        "rx": int,            percentage of down packets lost
 *      },
 *      "jitter": {
 *        "audio": {
 *          "tx": int,          audio up jitter in milliseconds
 *          "rx": int           audio down jitter in milliseconds
 *        },
 *        "video": {
 *          "tx": int,          video up jitter in milliseconds
 *          "rx": int           video down jitter in milliseconds
 *        },
 *      },
 *      "connection": {
 *        "protocol": string,   one of: UDP, TCP, Unknown
 *        "candidate": string,  one of: Relay, Host, Srflx, Prflx, Unknown
 *      },
 *      "peer": string,         one of: Server, User, Unknown
 *    }
 */
interface NetworkQualityChangedHandler : Callback {
    fun onNetworkQualityChanged(
        conversationId: String,
        userId: String?,
        clientId: String?,
        qualityInfoJson: String?,
        arg: Pointer?
    )
}
