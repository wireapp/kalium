package com.wire.kalium.calling

enum class CallType(val value: Int) {
    NORMAL(value = 0),
    VIDEO(value = 1),
    FORCED_AUDIO(value = 2)
}
