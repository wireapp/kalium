package com.wire.kalium.logic.data.call

data class AvsClient(
    val userId: String,
    val clientId: String
)

data class AvsClientList(
    val clients: List<AvsClient>
) {
    fun toJsonString(): String {
        val start = "{"
        var startClients = "clients:["

        clients.forEachIndexed { index, avsClient ->
            startClients += if (index > 0) "," else ""
            startClients += "{userid: ${avsClient.userId},clientid: ${avsClient.clientId}}"
        }

        val endClients = "]"
        val end = "}"

        return "$start$startClients$endClients$end"
    }
}
