package com.wire.kalium.calling

enum class ConversationTypeCalling(val avsValue: Int) {
    OneOnOne(avsValue = 0),
    @Deprecated(message = "The Group config has been deprecated after moving to Calling 2.0. Please use Conference instead.")
    Group(avsValue = 1),
    Conference(avsValue = 2),
    Unknown(avsValue = -1)
}
