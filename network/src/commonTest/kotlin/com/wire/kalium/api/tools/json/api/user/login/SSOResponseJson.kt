package com.wire.kalium.api.tools.json.api.user.login

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.login.SSOResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object SSOResponseJson {
    private val jsonProvider = { serializable: SSOResponse ->
        buildJsonObject {
            with(serializable) {
                uri?.let { put("uri", it) }
                xml?.let {
                    putJsonObject("xml") {
                        putJsonObject("iD") {
                            putJsonObject("fromXmlText") {
                                it.id.iD.fromXmlText
                            }
                        }
                        putJsonObject("issueInstant") {
                            put("time", it.issueInstant.time)
                        }
                        put("issuer", it.issuer)
                        it.nameIDPolicy?.let {
                            putJsonObject("nameIDPolicy") {
                                put("allowCreate", it.allowCreate)
                                put("format", it.format.toString())

                                it.spNameQualifier?.let {
                                    putJsonObject("spNameQualifier") {
                                        putJsonObject("fromXmlText") {
                                            it.fromXmlText
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    val valid = ValidJsonProvider(
        SSOResponse(uri = "sso.login.de", null), jsonProvider
    )

}
