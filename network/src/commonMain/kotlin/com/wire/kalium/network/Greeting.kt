package com.wire.kalium.network

import com.wire.kalium.Platform

class Greeting {
    fun greet(): String {
        return "Hello, from ${Platform().platform}!"
    }
}
