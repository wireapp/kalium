package com.wire.kalium.calling

enum class CallClosedReason(val avsValue: Int) {
    NORMAL(avsValue = 0),
    ERROR(avsValue = 1),
    TIMEOUT(avsValue = 2),
    LOST_MEDIA(avsValue = 3),
    CANCELLED(avsValue = 4),
    ANSWERED_ELSEWHERE(avsValue = 5),
    IOERROR(avsValue = 6),
    STILL_ONGOING(avsValue = 7),
    TIMEOUT_ECONN(avsValue = 8),
    DATA_CHANNEL(avsValue = 9),
    REJECTED(avsValue = 10),
    UNKNOWN(avsValue = -1);

    companion object {
        fun fromInt(value: Int) = values().firstOrNull {
            it.avsValue == value
        } ?: UNKNOWN
    }
}
