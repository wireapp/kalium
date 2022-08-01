package com.wire.kalium.persistence.model

enum class LogoutReason {
    /**
     * User initiated the logout manually.
     */
    SELF_LOGOUT,

    /**
     * User deleted this client from another client.
     */
    REMOVED_CLIENT,

    /**
     * User delete their account.
     */
    DELETED_ACCOUNT,

    /**
     * Session Expired.
     */
    SESSION_EXPIRED;
}
