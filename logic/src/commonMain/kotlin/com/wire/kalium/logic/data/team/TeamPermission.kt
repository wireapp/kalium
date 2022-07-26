package com.wire.kalium.logic.data.team

@Suppress("MagicNumber")
enum class TeamPermission(val code: Int) {
    CREATE_CONVERSATION(0x0001),
    DELETE_CONVERSATION(0x0002),
    ADD_TEAM_MEMBER(0x0004),
    REMOVE_TEAM_MEMBER(0x0008),
    ADD_REMOVE_CONV_MEMBER(0x0010),
    MODIFY_CONV_METADATA(0x0020),
    GET_BILLING(0x0040),
    SET_BILLING(0x0080),
    SET_TEAM_DATA(0x0100),
    GET_MEMBER_PERMISSIONS(0x0200),
    GET_TEAM_CONVERSATIONS(0x0400),
    DELETE_TEAM(0x0800),
    SET_MEMBER_PERMISSIONS(0x1000);
}

enum class TeamRole(val value: Int) {
    ExternalPartner(
        TeamPermission.CREATE_CONVERSATION.code +
                TeamPermission.GET_TEAM_CONVERSATIONS.code
    ),
    Member(
        ExternalPartner.value +
                TeamPermission.DELETE_CONVERSATION.code +
                TeamPermission.ADD_REMOVE_CONV_MEMBER.code +
                TeamPermission.MODIFY_CONV_METADATA.code +
                TeamPermission.GET_MEMBER_PERMISSIONS.code
    ),
    Admin(
        Member.value +
                TeamPermission.ADD_TEAM_MEMBER.code +
                TeamPermission.REMOVE_TEAM_MEMBER.code +
                TeamPermission.SET_TEAM_DATA.code +
                TeamPermission.SET_MEMBER_PERMISSIONS.code
    ),
    Owner(
        Admin.value +
                TeamPermission.GET_BILLING.code +
                TeamPermission.SET_BILLING.code +
                TeamPermission.DELETE_TEAM.code
    )

}
