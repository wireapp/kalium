package com.wire.kalium.connectivity

/**
 * Represents how the device is connected to the internet.
 * It is currently limited to [UNAVAILABLE] and [FULLY_AVAILABLE].
 *
 * In the future it can be expanded to add more possibilities,
 * especially for Mobile clients, where we can have limiting factors:
 * - Mobile Data
 * - Roaming
 * - Wi-fi
 */
enum class ConnectionType {
    UNAVAILABLE,
    FULLY_AVAILABLE
}
