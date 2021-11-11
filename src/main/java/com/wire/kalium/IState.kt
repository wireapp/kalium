package com.wire.kalium

import com.wire.kalium.backend.models.NewBot
import java.io.IOException

interface IState {
    @Throws(IOException::class)
    fun saveState(newBot: NewBot): Boolean

    @Throws(IOException::class)
    fun getState(): NewBot

    @Throws(IOException::class)
    fun removeState(): Boolean
}
