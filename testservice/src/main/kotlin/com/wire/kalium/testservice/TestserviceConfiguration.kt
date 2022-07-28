package com.wire.kalium.testservice

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.Configuration

class TestserviceConfiguration(private var template: String? = null, private var defaultName: String? = "Stranger") : Configuration() {

    @JsonProperty
    fun getTemplate(): String? {
        return template
    }

    @JsonProperty
    fun setTemplate(template: String?) {
        this.template = template
    }

    @JsonProperty
    fun getDefaultName(): String? {
        return defaultName
    }

    @JsonProperty
    fun setDefaultName(name: String) {
        defaultName = name
    }

}
