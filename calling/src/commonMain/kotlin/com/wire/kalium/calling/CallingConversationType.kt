package com.wire.kalium.calling

enum class CallingConversationType(val avsValue: Int) {
    OneOnOne(avsValue = 0),
    Group(avsValue = 1),
    Conference(avsValue = 2)
}
