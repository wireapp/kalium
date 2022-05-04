package com.wire.kalium.logic.data.call

data class AvsClient(
    val userId: String,
    val clientId: String
)

data class AvsClientList(
    val clients: List<AvsClient>
) {
    fun toJsonString(): String {
        val clientsListContent = clients.joinToString(separator = ",") {
            """{"userid": "${it.userId}","clientid": "${it.clientId}"}"""
        }
        return """{"clients":[$clientsListContent]}"""
    }
}
