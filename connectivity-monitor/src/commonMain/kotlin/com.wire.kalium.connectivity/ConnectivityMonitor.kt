package com.wire.kalium.connectivity

import kotlinx.coroutines.flow.StateFlow

expect class ConnectivityMonitor {
    val connectionType: StateFlow<ConnectionType>
}
