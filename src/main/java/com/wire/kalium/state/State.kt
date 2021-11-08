package com.wire.kalium.state

import kotlin.Throws
import java.io.IOException
import com.wire.kalium.backend.models.NewBot

interface State {
    @Throws(IOException::class)
    open fun saveState(newBot: NewBot?): Boolean
    @Throws(IOException::class)
    open fun getState(): NewBot?
    @Throws(IOException::class)
    open fun removeState(): Boolean
}
