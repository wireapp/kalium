package com.wire.kalium.logic.data.logout

/**
 * Describes a reason that led to a logout.
 */
@Suppress("ClassNaming")
enum class LogoutReason {
    /**
     * User initiated the logout manually and opted to not delete user data.
     */
    SELF_SOFT_LOGOUT,

    /**
     * User initiated the logout manually and opted to delete user data.
     */

    SELF_HARD_LOGOUT,

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
    SESSION_EXPIRED
}
