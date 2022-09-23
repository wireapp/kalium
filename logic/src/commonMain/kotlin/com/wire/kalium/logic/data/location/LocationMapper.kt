package com.wire.kalium.logic.data.location

import com.wire.kalium.network.api.base.model.LocationResponse

class LocationMapper {
    fun fromLocationResponse(locationResponse: LocationResponse): Location =
        with(locationResponse) { Location(latitude = latitude, longitude = longitude) }
}
