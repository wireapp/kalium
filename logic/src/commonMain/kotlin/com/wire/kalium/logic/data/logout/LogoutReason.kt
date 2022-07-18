package com.wire.kalium.logic.data.logout

/**
 * Describes a reason that led to a logout.
 */
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
    DELETED_ACCOUNT;
}
