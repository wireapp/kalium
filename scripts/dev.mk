# dev related targets, ie: sql migrations, detekt, etc.
# sql delight
db/verify-global-migration:
	./gradlew :persistence:verifyCommonMainGlobalDatabaseMigration

db/verify-user-migration:
	./gradlew :persistence:verifyCommonMainUserDatabaseMigration

db/verify-all-migrations:
	./gradlew :persistence:verifySqlDelightMigration

# detekt
detekt/run-verify:
	./gradlew clean detekt
