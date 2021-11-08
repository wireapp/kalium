package com.wire.kalium.factories

import kotlin.Throws
import java.io.IOException
import java.util.UUID
import com.wire.kalium.state.State

interface StorageFactory {
    @Throws(IOException::class)
    open fun create(botId: UUID?): State?
}
