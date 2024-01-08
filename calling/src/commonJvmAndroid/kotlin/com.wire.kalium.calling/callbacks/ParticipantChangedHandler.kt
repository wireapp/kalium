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

interface ParticipantChangedHandler : Callback {
    /**Example of `data`
     {
         "convid": "df371578-65cf-4f07-9f49-c72a49877ae7",
         "members": [
             {
                 "userid": "3f49da1d-0d52-4696-9ef3-0dd181383e8a",
                 "clientid": "24cc758f602fb1f4",
                 "aestab": 1,
                 "vrecv": 0,
                 "muted": 0
             }
         ]
   }**/
    fun onParticipantChanged(remoteConversationId: String, data: String, arg: Pointer?)
}
