package com.wire.kalium.backend.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import javax.validation.constraints.NotNull

@JsonIgnoreProperties(ignoreUnknown = true)
class Version {
    @JsonProperty
    var version: @NotNull String? = null
}
