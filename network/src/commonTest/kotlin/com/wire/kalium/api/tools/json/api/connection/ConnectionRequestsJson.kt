package com.wire.kalium.api.tools.json.api.connection

import com.wire.kalium.api.tools.json.ValidJsonProvider

object ConnectionRequestsJson {

    val validEmptyBody = ValidJsonProvider(String) {
        """
            {
            }
        """.trimIndent()
    }

    val validPagingState = ValidJsonProvider(String) {
        """
            {
                "paging_state": "PAGING_STATE_1234"                            
            }
        """.trimIndent()
    }

    val validConnectionStatusUpdate = ValidJsonProvider(String) {
        """
            {
                "status": "accepted"                            
            }
        """.trimIndent()
    }
}
