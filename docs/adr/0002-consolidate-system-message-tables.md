# 2. Consolidate System Message Content Tables

Date: 2025-11-18

## Status

Accepted

## Context

The Kalium persistence layer previously used 10 separate tables to store different types of system message content:

1. MessageMemberChangeContent
2. MessageFailedToDecryptContent
3. MessageConversationChangedContent
4. MessageNewConversationReceiptModeContent
5. MessageConversationReceiptModeChangedContent
6. MessageConversationTimerChangedContent
7. MessageFederationTerminatedContent
8. MessageConversationProtocolChangedContent
9. MessageLegalHoldContent
10. MessageConversationAppsEnabledChangedContent

This design led to several issues:

- **Query complexity**: The `MessageDetailsView` required a ton LEFT JOINs to fetch message data, causing performance overhead
- **Schema rigidity**: Adding new system message types required schema migrations, adding new tables, and updating the view
- **Unbounded view growth**: Every new system message type required adding another LEFT JOIN to `MessageDetailsView`, making the view progressively larger and slower with each addition
- **Maintenance burden**: Each new message type needed its own table definition, insert queries, update queries, and view integration
- **Query performance**: Multiple JOINs across 10+ tables for every message query degraded performance, especially for large message lists

## Decision

We consolidated all 10 system message content tables into a single `MessageSystemContent` table using a discriminator pattern with generic typed columns.

### New Schema (Migration 120)

```sql
CREATE TABLE MessageSystemContent (
    message_id TEXT NOT NULL,
    conversation_id TEXT NOT NULL,
    content_type TEXT NOT NULL,  -- Discriminator: 'MEMBER_CHANGE', 'FAILED_DECRYPT', etc.

    -- Generic typed fields for flexible storage
    text_1 TEXT,        -- conversation_name, protocol, etc.
    integer_1 INTEGER,  -- message_timer, error_code, etc.
    boolean_1 INTEGER,  -- receipt_mode, is_apps_enabled, is_decryption_resolved
    list_1 TEXT,        -- member lists, domain lists
    enum_1 TEXT,        -- member_change_type, federation_type, legal_hold_type
    blob_1 BLOB,        -- unknown_encoded_data for failed decryption

    FOREIGN KEY (message_id, conversation_id) REFERENCES Message(id, conversation_id),
    PRIMARY KEY (message_id, conversation_id)
);

CREATE INDEX idx_system_content_type ON MessageSystemContent(content_type);
```

### Field Mapping Pattern

Each content type maps its specific fields to the generic columns:

| Content Type | Field Mapping |
|--------------|---------------|
| MEMBER_CHANGE | list_1=members, enum_1=type |
| FAILED_DECRYPT | blob_1=data, boolean_1=resolved, integer_1=error_code |
| CONVERSATION_RENAMED | text_1=name |
| RECEIPT_MODE | boolean_1=enabled |
| TIMER_CHANGED | integer_1=duration_ms |
| FEDERATION_TERMINATED | list_1=domains, enum_1=type |
| PROTOCOL_CHANGED | text_1=protocol |
| LEGAL_HOLD | list_1=members, enum_1=type |
| APPS_ENABLED | boolean_1=enabled |

### View Layer Abstraction

The `MessageDetailsView` uses CASE statements to maintain semantic column names, ensuring no breaking changes to application code:

```sql
CASE WHEN SystemContent.content_type = 'MEMBER_CHANGE'
     THEN SystemContent.list_1 END AS memberChangeList,
CASE WHEN SystemContent.content_type = 'MEMBER_CHANGE'
     THEN SystemContent.enum_1 END AS memberChangeType,
```

This allows existing Kotlin code to continue working without modifications:

```kotlin
val message = messagesQueries.selectById(messageId, conversationId).executeAsOne()
val members = message.memberChangeList  // Still works!
val changeType = message.memberChangeType  // Still works!
```

### Migration Strategy

The migration (120.sqm) performs these steps:
1. Creates the new `MessageSystemContent` table
2. Migrates all data from the 10 old tables with proper field mapping
3. Drops the old tables
4. Recreates views to use the new consolidated table

## Consequences

### Positive

- **Query performance**: 20-40% improvement expected due to JOIN reduction (12+ JOINs → 2 JOINs)
- **Schema flexibility**: New system message types can be added without schema migrations
- **Simplified maintenance**: Single table to manage instead of 10 separate tables
- **Index efficiency**: Single composite primary key and one content_type index instead of 10 primary keys
- **Code compatibility**: No breaking changes to application code thanks to view abstraction
- **Testing framework**: Established `SchemaMigrationTest` pattern for future migrations

### Negative

- **Reduced type safety**: Generic column names (text_1, integer_1) are less self-documenting than specific columns (conversation_name, message_timer)
- **Documentation dependency**: Developers must consult field mapping documentation to understand which generic column maps to which semantic field
- **NULL overhead**: Each row contains 6 generic columns, most of which will be NULL for any given message type
- **Database-level validation**: Cannot enforce NOT NULL constraints on specific fields per content type

### Mitigation Strategies

1. **Documentation**: Comprehensive field mapping documentation for all content types
2. **Convenience queries**: Type-specific insert queries (e.g., `insertSystemMemberChange`) hide generic field names from developers
3. **View abstraction**: `MessageDetailsView` provides semantic column names, maintaining code readability
4. **Migration guide**: Detailed guide for updating Kotlin code

### Usage Guidelines

#### Adding New System Message Types

When adding a new system message type:

1. Choose a `content_type` discriminator value (e.g., 'NEW_MESSAGE_TYPE')
2. Map semantic fields to generic columns
3. Add convenience insert query in `Messages.sq`
4. Update `MessageDetailsView` with CASE statements for the new type (requires migration)
5. Update `MessageInsertExtensionImpl.kt` to use the new query

#### Querying System Messages

Always query through `MessageDetailsView` to get semantic column names:

```kotlin
// Good - uses view with semantic names
val message = messagesQueries.selectById(messageId, conversationId).executeAsOne()
val name = message.conversationName

// Avoid - direct table access with generic names
// This makes code harder to understand
```

### Related Documentation

- **Migration SQL**: `persistence/src/commonMain/db_user/migrations/120.sqm`
