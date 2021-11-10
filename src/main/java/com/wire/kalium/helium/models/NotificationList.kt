package com.wire.kalium.helium.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import javax.validation.constraints.NotNull

@JsonIgnoreProperties(ignoreUnknown = true)
class NotificationList {
    @JsonProperty("has_more")
    var hasMore: @NotNull Boolean? = null

    @JsonProperty
    var notifications: @NotNull MutableList<Event>? = ArrayList<Event>()
}
