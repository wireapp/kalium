package com.wire.kalium.connectivity

import kotlinx.coroutines.flow.StateFlow

actual class ConnectivityMonitor {
    actual val connectionType: StateFlow<ConnectionType>
        get() = TODO("Not yet implemented")
}
