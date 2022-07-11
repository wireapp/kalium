package com.wire.kalium.logic.data.logout

/**
 * Describes a reason that led to a logout.
 */
enum class LogoutReason {
    /**
     * User initiated the logout manually.
     */
    USER_INTENTION,

    /**
     * The session has expired. The server rejects
     * the credentials and there's no way around a re-authentication.
     * It's appropriate to warn the user and ask for a new login.
     */
    EXPIRED_SESSION;
}
