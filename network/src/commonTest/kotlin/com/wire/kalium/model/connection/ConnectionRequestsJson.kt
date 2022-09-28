package com.wire.kalium.model.connection

import com.wire.kalium.api.json.ValidJsonProvider

object ConnectionRequestsJson {

    val validEmptyBody = ValidJsonProvider(String) {
        """
            {
                "size":500
            }
        """.trimIndent()
    }

    val validPagingState = ValidJsonProvider("PAGING_STATE_1234") {
        """
            {
                "paging_state": "$it",
                "size":500
            }
        """.trimIndent()
    }

    val validConnectionStatusUpdate = ValidJsonProvider("accepted") {
        """
            {
                "status": "$it"  
            }
        """.trimIndent()
    }
}
