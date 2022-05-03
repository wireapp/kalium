package com.wire.kalium.logic.feature.call

enum class AvsCallBackError(val value: Int) {
    NONE(0),
    INVALID_ARGUMENT(1)
}

enum class AvsSFTError(val value: Int) {
    NONE(0),
    NO_RESPONSE_DATA(1)
}
