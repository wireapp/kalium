package com.wire.kalium.logic.data.logout

/**
 * Describes a reason that led to a logout.
 */
@Suppress("ClassNaming")
sealed class LogoutReason {
    sealed class SelfInitiated: LogoutReason()
    sealed class Automatic: LogoutReason()
        /**
         * User initiated the logout manually and opted to not delete user data.
         */
        object SELF_SOFT_LOGOUT: SelfInitiated()

        /**
         * User initiated the logout manually and opted to delete user data.
         */
        object SELF_HARD_LOGOUT: SelfInitiated()

        /**
         * User deleted this client from another client.
         */
        object REMOVED_CLIENT: Automatic()

        /**
         * User delete their account.
         */
        object DELETED_ACCOUNT: Automatic()

        /**
         * Session Expired.
         */
        object SESSION_EXPIRED: Automatic()
}
