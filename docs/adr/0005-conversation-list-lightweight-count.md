# 5. Lightweight COUNT for Faster Conversation List Loading

Date: 2025-12-03

## Status

Accepted

## Context

On Android clients with a large number of conversations and heavy message history, the conversation
list was slow to load.  
Profiling showed a significant delay during the COUNT phase executed before the Paging source loads
items.

The existing COUNT query used the `ConversationDetails` view, which performs many joins, user
metadata checks, visibility rules, and sorting-related logic.  
This resulted in:

- creation of temp B-trees
- expensive scans over large joined structures
- noticeable TTI spikes on older devices and devices with large databases

Most of this logic is needed for the conversation list itself, but **not** for the COUNT used by the
Paging library. Paging only requires an **upper bound**, not the exact filtered set.

## Decision

Use a **lightweight COUNT** that operates directly on the `Conversation` table, but only when:

- `searchQuery` is empty (no text search filtering required)
- no metadata-dependent visibility logic is needed for COUNT

The new `countConversations` query is equivalent to the core filters used by the real paging SELECT
and matches:

- exclude conversations of type SELF
- apply conversationFilter (ALL, GROUPS, ONE_ON_ONE, CHANNELS)
- apply `archived`
- apply `deleted_locally`
- apply protocol and MLS state (strict MLS, ESTABLISHED, PENDING_AFTER_RESET)

However, it intentionally does not perform the additional visibility logic from
`ConversationDetails`, such as:

- 1:1 metadata checks (missing name, missing otherUserId)
- userDeleted or defederated logic
- CONNECTION_PENDING visibility rules
- isActive calculation from Member/User tables
- interactionEnabled filtering
- favorites and folder membership filtering

These rules remain enforced by the actual SELECT used for loading pages.

As a result, the lightweight COUNT may return a superset of rows, but this is acceptable because the
Paging source uses the full SELECT query for real data.  
The COUNT only provides a high-level upper bound.

## Consequences

### Benefits

- Significant reduction of TTI spikes on conversation list screen
- COUNT no longer triggers expensive joins or temporary B-trees
- More stable performance on large accounts and older hardware
- No functional changes to conversation visibility or ordering
- Paging logic remains correct because only the real SELECT determines item membership

### Trade-offs

- COUNT may return a slightly higher number than the actual number of displayed conversations
- Search queries still use the full COUNT because search relies on fields only available in the
  ConversationDetails view

### Technical Notes

Below is the lightweight COUNT query introduced:

```
SELECT COUNT(*)
FROM Conversation
WHERE
    type IS NOT 'SELF'
    AND CASE
        WHEN :conversationFilter = 'ALL' THEN 1 = 1
        WHEN :conversationFilter = 'GROUPS' THEN (type = 'GROUP' AND is_channel = 0)
        WHEN :conversationFilter = 'ONE_ON_ONE' THEN type = 'ONE_ON_ONE'
        WHEN :conversationFilter = 'CHANNELS' THEN (type = 'GROUP' AND is_channel = 1)
        ELSE 1 = 0
    END
    AND archived = :fromArchive
    AND deleted_locally = 0
    AND (
        protocol IN ('PROTEUS','MIXED')
        OR (
            protocol = 'MLS'
            AND (
                :strict_mls = 0
                OR mls_group_state IN ('ESTABLISHED','PENDING_AFTER_RESET')
            )
        )
    );
```
