package com.wire.kalium.persistence.util

inline fun <reified T> T?.requireField(fieldName: String): T = requireNotNull(this) {
    "Field $fieldName null when unpacking db content"
}
