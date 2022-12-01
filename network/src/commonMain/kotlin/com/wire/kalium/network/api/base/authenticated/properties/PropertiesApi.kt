package com.wire.kalium.network.api.base.authenticated.properties

import com.wire.kalium.network.utils.NetworkResponse

interface PropertiesApi {

    suspend fun setProperty(propertyKey: PropertyKey, propertyValue: Any): NetworkResponse<Unit>
    suspend fun deleteProperty(propertyKey: PropertyKey): NetworkResponse<Unit>

    enum class PropertyKey(val key: String) {
        WIRE_RECEIPT_MODE("WIRE_RECEIPT_MODE")
        // TODO map other event like -ie. 'labels'-
    }
}
