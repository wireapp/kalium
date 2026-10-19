`SearchUsersByNameUseCase` and `SearchUsersByHandleUseCase` replace
`skipRemoteSearch` with `onlySelfTeamAndDomain`. When `true`, the new parameter
restricts searches to the self user's team and domain instead of skipping remote
search. Callers using the `skipRemoteSearch` named argument must update it to
`onlySelfTeamAndDomain` and review the changed behavior, including calls that pass
the Boolean positionally.

`excludingMembersOfConversation` and `customDomain` now default to `null`,
allowing callers to omit these arguments.

The handle use case also renames `excludingConversation` to
`excludingMembersOfConversation`. Ordinary searches keep their default behavior;
restricted searches return no candidates when the self user has no team. Both
local and remote candidates must match the self team and domain, even when a
custom remote domain is supplied.

The persistence `SearchDAO` consolidates its exclusion variants into
`getKnownContacts`, `searchByName`, and `searchByHandle`, each accepting optional
conversation, team, and domain filters. Replace the removed DAO methods with
these methods and pass `excludeConversationId` when needed. The logic search
repository remains internal.

ABI: the use-case JVM descriptors remain unchanged; KMP default-argument metadata
is additive. Persistence DAO signatures change, so direct DAO consumers must
update calls and rebuild. Source: callers using the renamed arguments must
migrate. Behavior: `true` now includes remote search with team/domain restrictions
instead of selecting only locally known contacts.
