package com.wire.kalium.calling

enum class CallType(val avsValue: Int) {
    NORMAL(avsValue = 0),
    VIDEO(avsValue = 1),
}
