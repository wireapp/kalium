package com.wire.kalium.logger

fun obfuscateLogMessage(message: String): String {
    val ipV4 = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\n"
    val password = "\"password\":\\s*\"([^\"]*)\"\n"
    val ipV6 = "^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a"
    val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    val clientId = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}"

    if (message.contains(ipV4)) {
        val m = message.split(".")
        m[2].replaceRange(0, m[2].length, "MOMOMO")
        m[3].replaceRange(0, m[3].length, "MOMOMO")
        return message
    }
//     if (message.contains(password)) {
//
//     }
//     if (message.contains(ipV6)) {
//
//     }
//     if (message.contains(uuid)) {
//
//     }
//     if (message.contains(clientId)) {
//
//     }

    return message
}
