package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.LibsodiumInitializer

object LibsodiumInitializer {
    internal suspend fun initializeLibsodiumIfNeeded() {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
    }
}
