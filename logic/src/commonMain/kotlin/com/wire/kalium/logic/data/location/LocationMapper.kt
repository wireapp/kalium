package com.wire.kalium.logic.data.location

import com.wire.kalium.network.api.user.client.LocationDTO

class LocationMapper {
    fun fromLocationDTO(locationDTO: LocationDTO?): Location? {
        return locationDTO?.let {
            Location(latitude = it.latitude, longitude = it.longitude)
        } ?: run {
            null
        }
    }
}
