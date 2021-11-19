package com.wire.kalium.models.backend.json

sealed interface JsonProvider {
    val rawJson: String
}

data class FaultyJsonProvider(override val rawJson: String) : JsonProvider

data class ValidJsonProvider<Serializable : Any>(
    val serializableData: Serializable,
    private val jsonProvider: (Serializable) -> String
) : JsonProvider {
    override val rawJson: String = jsonProvider(serializableData)
}
