package com.wire.kalium.logger

fun obfuscateLogMessage(message: String): String {
    val ipV4 = ".*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*".toRegex()
    val ipV4Exact = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}".toRegex()

    val password = ".*\"password\":\\s*\"([^\"]*)\".*".toRegex()
    val passwordExact = "\"password\":\\s*\"([^\"]*)\"".toRegex()

    val ipV6 =
        ".*(?:^|(?<=\\s))(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))(?=\\s|\$).*".toRegex()
    val ipV6Exact =
        "(?:^|(?<=\\s))(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))(?=\\s|\$)".toRegex()


    val uuid = ".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*".toRegex()
    val uuidExact = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".toRegex()

    val clientId = ".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}.*".toRegex()
    val clientIdExact = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}".toRegex()

    if (uuid.containsMatchIn(message)) {
        return message.replace(uuidExact) { matchResult ->
            matchResult.value.split("-")[0]
        }
    }

    if (clientId.containsMatchIn(message)) {
        return message.replace(clientIdExact) { matchResult ->
            matchResult.value.take(4)
        }
    }

    if (password.containsMatchIn(message)) {
        return message.replace(passwordExact, "password:******")
    }


    // ipV4
    if (ipV4.containsMatchIn(message)) {
        var originalString: List<String>
        return message.replace(ipV4Exact)
        { matchResult ->
            originalString = matchResult.value.split(".")
            originalString[2].toCharArray().forEachIndexed { index, _ ->
                originalString[2].toCharArray()[index] = 'x'
            }
            originalString[3].toCharArray().forEachIndexed { index, _ ->
                originalString[3].toCharArray()[index] = 'x'
            }
            originalString.toString()
        }

    }

    // IP V6
    if (ipV6.containsMatchIn(message)) {
        var originalString: List<String>
        return message.replace(ipV6Exact)
        { matchResult ->
            originalString = matchResult.value.split(":")
            originalString[3].toCharArray().forEachIndexed { index, _ ->
                originalString[3].toCharArray()[index] = 'x'
            }
            originalString[4].toCharArray().forEachIndexed { index, _ ->
                originalString[4].toCharArray()[index] = 'x'
            }
            originalString[5].toCharArray().forEachIndexed { index, _ ->
                originalString[5].toCharArray()[index] = 'x'
            }

            originalString[6].toCharArray().forEachIndexed { index, _ ->
                originalString[6].toCharArray()[index] = 'x'
            }
            originalString[7].toCharArray().forEachIndexed { index, _ ->
                originalString[7].toCharArray()[index] = 'x'
            }
            originalString.toString()
        }
    }

    return message
}
