`SearchUsersByNameUseCase` and `SearchUsersByHandleUseCase` replace
`skipRemoteSearch` with `onlySelfTeamAndDomain`. When `true`, the new parameter
restricts searches to the self user's team and domain instead of skipping remote
search. Callers using the `skipRemoteSearch` named argument must update it to
`onlySelfTeamAndDomain` and review the changed behavior, including calls that pass
the Boolean positionally.

`excludingMembersOfConversation` and `customDomain` now default to `null`,
allowing callers to omit these arguments.
