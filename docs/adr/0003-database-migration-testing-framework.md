# 3. Database Migration Testing Framework

Date: 2025-11-18

## Status

Accepted

## Context

Database migrations in Kalium are critical operations that modify both schema structure and transform existing user data. Previously, migration testing was inconsistent and ad-hoc, with most migrations only tested through integration tests that ran the entire migration chain. This approach had several problems:

- **Insufficient coverage**: Migrations were not tested individually with realistic data scenarios
- **Late failure detection**: Migration bugs were only discovered after the migration ran on production databases
- **Data loss risk**: No systematic verification that all data was correctly transformed during migrations
- **Debugging difficulty**: When a migration failed, it was hard to pinpoint which specific data transformation was incorrect
- **No regression testing**: Once a migration was released, we had no automated way to verify it continued working correctly

The consolidation of 10 system message content tables into a single table (Migration 120) highlighted the need for a robust testing framework that could:

1. Load pre-migration database schemas from actual schema files
2. Insert realistic test data into old schema structures
3. Execute migration SQL in isolation
4. Verify data integrity and completeness after migration
5. Test edge cases like NULL values, complex data types, and large datasets

## Decision

We created the `SchemaMigrationTest` framework, a comprehensive testing infrastructure for database migrations that involve both schema changes and data transformations.

### Architecture

The framework consists of three main components:

#### 1. Base Test Class: `SchemaMigrationTest.kt`

A base class that provides:

```kotlin
abstract class SchemaMigrationTest {
    protected fun runMigrationTest(
        schemaVersion: Int,
        setupOldSchema: (SqlDriver) -> Unit,
        migrationSql: () -> String,
        verifyNewSchema: (SqlDriver) -> Unit
    )
}
```

**Key capabilities:**
- Loads pre-migration schema from `.db` files stored in `src/commonTest/kotlin/com/wire/kalium/persistence/schemas/`
- Creates temporary test databases using JDBC SQLite driver
- Executes multi-statement migration SQL
- Provides helper methods for common database operations

**Helper methods:**
- `executeInsert(sql)` - Insert data with simple SQL
- `countRows(tableName)` - Get row count from a table
- `querySingleValue(sql, mapper)` - Query and extract a single value
- `executeQuery(sql, mapper)` - General-purpose query execution

#### 2. Schema Files

Pre-migration database schemas stored as SQLite `.db` files:

```
persistence/src/commonTest/kotlin/com/wire/kalium/persistence/schemas/
├── 124.db
└── ...
```

These files are **snapshots of the actual schema** before a migration runs, generated using:

```bash
./gradlew :persistence:generateCommonMainUserDatabaseInterface
cp persistence/src/commonMain/db_user/schemas/124.db \
   persistence/src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db
```

#### 3. Migration Tests

Individual test classes that extend `SchemaMigrationTest` and test specific migrations:

```kotlin
class Migration120Test : SchemaMigrationTest() {
    @Test
    fun testMemberChangeContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = 120,
            setupOldSchema = { driver ->
                // Insert test data into old schema
                driver.executeInsert("""
                    INSERT INTO MessageMemberChangeContent
                    (message_id, conversation_id, member_change_list, member_change_type)
                    VALUES ('msg-1', 'conv-1', '["user1", "user2"]', 'ADDED')
                """)
            },
            migrationSql = {
                // Read actual migration SQL from file
                File("src/commonMain/db_user/migrations/120.sqm").readText()
            },
            verifyNewSchema = { driver ->
                // Verify the migrated data
                val count = driver.countRows("MessageSystemContent")
                assertEquals(1, count)

                var contentType: String? = null
                var list1: String? = null

                driver.executeQuery(null, """
                    SELECT content_type, list_1
                    FROM MessageSystemContent
                    WHERE message_id = 'msg-1'
                """.trimIndent(), { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        list1 = cursor.getString(1)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("MEMBER_CHANGE", contentType)
                assertEquals("""["user1", "user2"]""", list1)

                // Verify old table was dropped
                assertTableDoesNotExist(driver, "MessageMemberChangeContent")
            }
        )
    }
}
```

### Testing Strategy

We categorize migrations into two types, each with different testing approaches:

#### Type 1: Schema-only Migrations

Migrations that only modify database structure without transforming existing data.

**Example**: Adding a new column with a default value

**Testing approach**: Use simpler unit tests (like `EventMigration109Test` style)

**When to use**:
- Adding new tables
- Adding columns with default values
- Creating indexes
- Adding constraints

#### Type 2: Schema + Content Migrations

Migrations that both modify database structure AND transform existing data.

**Example**: Consolidating multiple tables into one (Migration 120)

**Testing approach**: Use `SchemaMigrationTest` framework (this ADR)

**When to use**:
- Consolidating tables
- Transforming data formats
- Moving data between tables
- Complex multi-step migrations

### Test Coverage Requirements

For Schema + Content migrations, each test class should include:

1. **Individual content type tests**: One test per data type being migrated
2. **Data integrity tests**: Verify ALL fields are correctly migrated, including NULL values
3. **Multiple messages test**: Test migrating several different message types simultaneously
4. **Index creation tests**: Verify indexes are created correctly
5. **Old table cleanup tests**: Verify old tables are dropped
6. **Edge case tests**: Empty strings, NULL values, special characters, large datasets

**Example from Migration120Test:**
- 11 individual content type tests
- 6 comprehensive data integrity tests
- 1 multi-message migration test
- 1 index creation test
- Total: 19+ test cases

### Working with Different Data Types

The framework supports all SQLite data types:

**TEXT**:
```kotlin
driver.executeInsert("""
    INSERT INTO Table (id, name)
    VALUES ('id-1', 'Test Name')
""")
```

**INTEGER** (including booleans as 0/1):
```kotlin
driver.executeInsert("""
    INSERT INTO Table (id, is_enabled)
    VALUES ('id-1', 1)  -- true
""")
```

**BLOB**:
```kotlin
val testData = byteArrayOf(0x01, 0x02, 0x03)
driver.execute(null, """
    INSERT INTO Table (id, data) VALUES (?, ?)
""".trimIndent(), 2) {
    bindString(0, "id-1")
    bindBytes(1, testData)
}
```

**Reading BLOB data**:
```kotlin
var blob: ByteArray? = null
driver.executeQuery(null, "SELECT data FROM Table WHERE id = 'id-1'", { cursor ->
    if (cursor.next().value == true) {
        blob = cursor.getBytes(0)
    }
}, 0)
```

## Consequences

### Positive

- **Early bug detection**: Migration bugs are caught during development, not in production
- **Data integrity guarantee**: Systematic verification ensures no data loss during migrations
- **Regression prevention**: Migrations remain tested even after release
- **Realistic testing**: Uses actual schema files and migration SQL, not mocks
- **Fast feedback**: Tests run in milliseconds using in-memory SQLite databases
- **Documentation**: Tests serve as executable documentation of migration behavior
- **Confidence**: Developers can refactor migrations knowing tests will catch breaks
- **Reusable infrastructure**: Base class and helpers make writing new tests straightforward

### Negative

- **Manual schema management**: Developers must remember to copy schema files before running migrations
- **Storage overhead**: Schema `.db` files must be committed to the repository (typically 50-100 KB each)
- **JVM-only**: Tests only run on JVM target, not on Android or iOS
- **Maintenance burden**: Each migration requires significant test code (Migration120Test is ~1000 lines)
- **Slower CI builds**: More comprehensive tests increase CI build time
- **Learning curve**: Developers need to understand SQLite JDBC API and framework patterns

### Mitigation Strategies

1. **Helper methods**: Base class provides common operations to reduce boilerplate
2. **Template tests**: Migration120Test serves as a template for future migration tests
3. **CI integration**: Automated checks verify schema files exist before allowing PR merge
4. **Code review checklist**: Ensure all migration PRs include corresponding tests

### Best Practices

When writing migration tests:

1. **Export schema files BEFORE running the migration**
   ```bash
   ./gradlew :persistence:generateCommonMainUserDatabaseInterface
   cp persistence/src/commonMain/db_user/schemas/124.db \
      persistence/src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db
   ```

2. **Read migration SQL from actual `.sqm` files**
   ```kotlin
   private fun getMigration120Sql(): String {
       return File("src/commonMain/db_user/migrations/120.sqm").readText()
   }
   ```
   This ensures tests always use the real migration SQL

3. **Test each content type separately**
   ```kotlin
   @Test fun testMemberChangeContentMigration()
   @Test fun testFailedToDecryptContentMigration()
   @Test fun testConversationRenamedContentMigration()
   ```

4. **Verify ALL fields, not just the happy path**
   ```kotlin
   // Verify used fields have correct values
   assertEquals("MEMBER_CHANGE", contentType)
   assertEquals("""["user1", "user2"]""", list1)

   // Verify unused fields are NULL
   assertNull(text1)
   assertNull(integer1)
   assertNull(blob1)
   ```

5. **Always verify old tables are dropped**
   ```kotlin
   assertTableDoesNotExist(driver, "MessageMemberChangeContent")
   ```

6. **Test with realistic data**
   - Use actual user IDs, conversation IDs
   - Include special characters, emojis
   - Test edge cases: empty strings, NULL values, large datasets

7. **Use descriptive test names**
   ```kotlin
   @Test fun testMemberChangeDataIntegrity_AllFieldsPreserved()
   @Test fun testAllSystemMessageTypesNoDataLoss()
   ```

### Running the Tests

```bash
# Run all migration tests for migration 120
./gradlew :persistence:jvmTest --tests "*Migration120Test"

# Run a specific test
./gradlew :persistence:jvmTest --tests "*Migration120Test.testMemberChangeContentMigration"

# Run all schema migration tests
./gradlew :persistence:jvmTest --tests "*migration*"
```

### Example: Creating a New Migration Test

**Step 1**: Export the schema file BEFORE applying the migration
```bash
./gradlew :persistence:generateCommonMainUserDatabaseInterface
cp persistence/src/commonMain/db_user/schemas/121.db \
   persistence/src/commonTest/kotlin/com/wire/kalium/persistence/schemas/121.db
```

**Step 2**: Create the test class
```kotlin
package com.wire.kalium.persistence.dao.migration

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration121Test : SchemaMigrationTest() {

    companion object {
        private const val MESSAGE_ID = "test-message-id"
        private const val CONVERSATION_ID = "test-conversation-id"
        private const val MIGRATION_NAME = 121
    }

    private fun getMigration121Sql(): String {
        val migrationFile = File("src/commonMain/db_user/migrations/$MIGRATION_NAME.sqm")
        if (!migrationFile.exists()) {
            error("Migration file not found: ${migrationFile.absolutePath}")
        }
        return migrationFile.readText()
    }

    @Test
    fun testMyDataMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Insert test data into old schema
                driver.executeInsert("""
                    INSERT INTO OldTable (id, value)
                    VALUES ('$MESSAGE_ID', 'test-value')
                """)
            },
            migrationSql = { getMigration121Sql() },
            verifyNewSchema = { driver ->
                // Verify the migrated data
                val count = driver.countRows("NewTable")
                assertEquals(1, count)

                var migratedValue: String? = null
                driver.executeQuery(null, """
                    SELECT value FROM NewTable WHERE id = '$MESSAGE_ID'
                """.trimIndent(), { cursor ->
                    if (cursor.next().value) {
                        migratedValue = cursor.getString(0)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("test-value", migratedValue)

                // Verify old table was dropped
                assertTableDoesNotExist(driver, "OldTable")
            }
        )
    }
}
```

**Step 3**: Run the test
```bash
./gradlew :persistence:jvmTest --tests "*Migration121Test"
```

### Complete Example: Migration 120 - System Message Consolidation

Migration 120 consolidated 10 separate system message content tables into a single `MessageSystemContent` table. The test suite demonstrates comprehensive coverage with the following test types:

#### Basic Migration Tests (11 tests)

Each old table gets its own test to verify correct migration:

```kotlin
@Test
fun testMemberChangeContentMigration() = runTest(dispatcher) {
    runMigrationTest(
        schemaVersion = 120,
        setupOldSchema = { driver ->
            driver.executeInsert("""
                INSERT INTO MessageMemberChangeContent
                (message_id, conversation_id, member_change_list, member_change_type)
                VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', '["user1", "user2"]', 'ADDED')
            """)
        },
        migrationSql = { getMigration120Sql() },
        verifyNewSchema = { driver ->
            val count = driver.countRows("MessageSystemContent")
            assertEquals(1, count)

            var contentType: String? = null
            var list1: String? = null
            var enum1: String? = null

            driver.executeQuery(null, """
                SELECT content_type, list_1, enum_1
                FROM MessageSystemContent
                WHERE message_id = '$MESSAGE_ID'
            """.trimIndent(), { cursor ->
                if (cursor.next().value) {
                    contentType = cursor.getString(0)
                    list1 = cursor.getString(1)
                    enum1 = cursor.getString(2)
                }
                QueryResult.Unit
            }, 0)

            assertEquals("MEMBER_CHANGE", contentType)
            assertEquals("""["user1", "user2"]""", list1)
            assertEquals("ADDED", enum1)

            assertTableDoesNotExist(driver, "MessageMemberChangeContent")
        }
    )
}
```

Similar tests cover:
- `testFailedToDecryptContentMigration` - Tests BLOB and error code migration
- `testConversationRenamedContentMigration` - Tests text field migration
- `testReceiptModeContentMigration` - Tests boolean field migration from two separate tables
- `testTimerChangedContentMigration` - Tests integer field migration
- `testFederationTerminatedContentMigration` - Tests list and enum migration
- `testProtocolChangedContentMigration` - Tests enum-only migration
- `testLegalHoldContentMigration` - Tests complex list with enums
- `testAppsEnabledChangedContentMigration` - Tests boolean field migration
- `testMultipleSystemMessagesMigration` - Tests migrating all types together
- `testIndexCreation` - Verifies indexes are created correctly

#### Data Integrity Tests (6 tests)

Verify ALL fields are correctly migrated, including NULL values for unused fields:

```kotlin
@Test
fun testMemberChangeDataIntegrity_AllFieldsPreserved() = runTest(dispatcher) {
    runMigrationTest(
        schemaVersion = 120,
        setupOldSchema = { driver ->
            driver.executeInsert("""
                INSERT INTO MessageMemberChangeContent
                (message_id, conversation_id, member_change_list, member_change_type)
                VALUES ('$MESSAGE_ID', '$CONVERSATION_ID',
                        '["user1", "user2"]', 'FEDERATION_REMOVED')
            """)
        },
        migrationSql = { getMigration120Sql() },
        verifyNewSchema = { driver ->
            var messageId: String? = null
            var conversationId: String? = null
            var contentType: String? = null
            var text1: String? = null
            var integer1: Long? = null
            var boolean1: Long? = null
            var list1: String? = null
            var enum1: String? = null
            var blob1: ByteArray? = null

            driver.executeQuery(null, """
                SELECT message_id, conversation_id, content_type,
                       text_1, integer_1, boolean_1, list_1, enum_1, blob_1
                FROM MessageSystemContent
                WHERE message_id = '$MESSAGE_ID'
            """.trimIndent(), { cursor ->
                if (cursor.next().value) {
                    messageId = cursor.getString(0)
                    conversationId = cursor.getString(1)
                    contentType = cursor.getString(2)
                    text1 = cursor.getString(3)
                    integer1 = cursor.getLong(4)
                    boolean1 = cursor.getLong(5)
                    list1 = cursor.getString(6)
                    enum1 = cursor.getString(7)
                    blob1 = cursor.getBytes(8)
                }
                QueryResult.Unit
            }, 0)

            // Verify PKs
            assertEquals(MESSAGE_ID, messageId)
            assertEquals(CONVERSATION_ID, conversationId)

            // Verify content type
            assertEquals("MEMBER_CHANGE", contentType)

            // Verify used fields
            assertEquals("""["user1", "user2"]""", list1)
            assertEquals("FEDERATION_REMOVED", enum1)

            // Verify unused fields are NULL
            assertNull(text1)
            assertNull(integer1)
            assertNull(boolean1)
            assertNull(blob1)
        }
    )
}
```

Similar comprehensive tests for:
- `testFailedDecryptDataIntegrity_AllFieldsPreserved`
- `testConversationRenamedDataIntegrity_AllFieldsPreserved`
- `testAllReceiptModeTypesDataIntegrity`
- `testFederationTerminatedDataIntegrity_ComplexListPreserved`
- `testLegalHoldDataIntegrity_ComplexMemberList`
- `testAllSystemMessageTypesNoDataLoss` - Tests all 10 types together with full field verification

Total: **19+ test cases** covering every migration scenario, edge case, and data integrity requirement.

### Troubleshooting Common Issues

#### Schema file not found
```
Error: Schema file not found: /com/wire/kalium/persistence/schemas/119.db
```

**Solution**: Make sure the schema file exists at:
`persistence/src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db`

Generate it with:
```bash
./gradlew :persistence:generateCommonMainUserDatabaseInterface
cp persistence/src/commonMain/db_user/schemas/124.db \
   persistence/src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db
```

#### Migration SQL fails
```
Error: Failed to execute migration statement: ...
```

**Solution**:
1. Check that your migration SQL is valid SQLite
2. Ensure the old tables exist in the schema file
3. Verify foreign key constraints are satisfied
4. Test each SQL statement individually

#### Table already exists
```
Error: table MessageSystemContent already exists
```

**Solution**: Make sure your migration SQL uses `CREATE TABLE IF NOT EXISTS` or the schema file is from before the migration. The schema file should be generated BEFORE you write the migration SQL.

#### BLOB data not migrating correctly
```kotlin
// Wrong - string binding for BLOB
driver.execute(null, "INSERT INTO Table (data) VALUES (?)", 1) {
    bindString(0, testData.toString()) // Wrong!
}

// Correct - bytes binding for BLOB
driver.execute(null, "INSERT INTO Table (data) VALUES (?)", 1) {
    bindBytes(0, testData) // Correct!
}
```

#### Boolean values not working
SQLite doesn't have a native boolean type. Use INTEGER with 0/1:
```kotlin
// Insert
driver.executeInsert("INSERT INTO Table (is_enabled) VALUES (1)")  // true

// Query - returns Long, not Boolean
val isEnabled: Long? = cursor.getLong(0)  // 0 or 1
assertEquals(1L, isEnabled)  // true
```

### Future Improvements

Potential enhancements to the framework:

1. **Automatic schema file management**: Git hooks or Gradle tasks to automatically copy schema files
2. **Multi-platform support**: Extend tests to run on Android and iOS targets
3. **Performance testing**: Measure migration execution time with large datasets
4. **Data generation helpers**: Factory methods for creating realistic test data
5. **Migration comparison**: Tools to diff schemas before/after migration
6. **Visual regression**: Generate schema diagrams before/after migration for documentation

### Related Files

- **Example Test**: `persistence/src/jvmTest/kotlin/com/wire/kalium/persistence/dao/migration/Migration120Test.kt` - Reference implementation with 19+ test cases
- **Base Class**: `persistence/src/jvmTest/kotlin/com/wire/kalium/persistence/dao/migration/SchemaMigrationTest.kt` - Framework implementation
- **Related ADR**: ADR 0002 - Consolidate System Message Content Tables
- **Migration File**: `persistence/src/commonMain/db_user/migrations/124.sqm` - Actual migration SQL
