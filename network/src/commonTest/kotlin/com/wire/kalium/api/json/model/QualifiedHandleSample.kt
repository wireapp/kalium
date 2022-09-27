package com.wire.kalium.api.json.model

import com.wire.kalium.network.api.base.model.QualifiedHandle

object QualifiedHandleSample {
    val one = QualifiedHandle("someDomain", "someHandle")
    val two = QualifiedHandle("anotherDomain", "anotherHandle")
}
