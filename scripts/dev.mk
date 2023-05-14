# dev related targets, ie: sql migrations, detekt, etc.
# sql delight
db/verify-global-migration:
	./gradlew :persistence:verifyCommonMainGlobalDatabaseMigration

db/verify-user-migration:
	rm persistence/src/commonMain/db_user/schemas/2.db
	for i in {1..33}; \
	do \
		rm persistence/src/commonMain/db_user/migrations/$$i.sqm; \
	done
	./gradlew :persistence:verifyCommonMainUserDatabaseMigration

db/verify-all-migrations:
	rm persistence/src/commonMain/db_user/schemas/2.db
	for i in {1..33}; \
	do \
		rm persistence/src/commonMain/db_user/migrations/$$i.sqm; \
	done
	./gradlew :persistence:verifySqlDelightMigration

# detekt
detekt/run-verify:
	./gradlew clean detekt
