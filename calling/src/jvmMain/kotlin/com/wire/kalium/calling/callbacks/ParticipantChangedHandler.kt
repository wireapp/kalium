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
    fun onParticipantChanged(conversationId: String, data: String, arg: Pointer?)
}
