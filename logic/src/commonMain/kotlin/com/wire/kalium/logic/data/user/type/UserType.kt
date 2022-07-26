package com.wire.kalium.logic.data.user.type;

enum class UserType {

    /** Team member*/
    INTERNAL,

    // TODO(user-metadata): for now External will not be implemented
    /**Team member with limited permissions */
    EXTERNAL,

    /**
     * Any user on another backend using the Wire application,
     */
    FEDERATED,

    /**
     * Any user in wire.com using the Wire application or,
     * A temporary user that joined using the guest web interface,
     * from inside the backend network or,
     * A temporary user that joined using the guest web interface,
     * from outside the backend network
     */
    GUEST,

    /**
     * A user on the same backend,
     * when current user doesn't belongs to any team
     */
    NONE;
}
