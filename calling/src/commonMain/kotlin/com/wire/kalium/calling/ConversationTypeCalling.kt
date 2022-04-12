package com.wire.kalium.calling

enum class ConversationTypeCalling(val avsValue: Int) {
    OneOnOne(avsValue = 0),
    Group(avsValue = 1),
    Conference(avsValue = 2)
}
