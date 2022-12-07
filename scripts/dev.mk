# sql delight
db/verify-global-migration:
	./gradlew :persistence:verifyCommonMainGlobalDatabaseMigration

db/verify-user-migration:
	./gradlew :persistence:verifyCommonMainUserDatabaseMigration

db/verify-all-migrations:
	./gradlew :persistence:verifySqlDelightMigration

db/verify-all-schemas:
	./gradlew :persistence:generateCommonMainUserDatabaseSchema
	./gradlew :persistence:generateCommonMainGlobalDatabaseSchema
	./persistence/verifySchemas.sh

# detekt
detekt/run-verify:
	./gradlew clean detekt
