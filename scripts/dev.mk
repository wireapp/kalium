# dev related targets, ie: sql migrations, detekt, etc.
# sql delight
db/verify-global-migration:
	./gradlew :persistence:verifyCommonMainGlobalDatabaseMigration

# the delete of 2.db and the migrations files from 1 -> 33 is a workaround for the following issue:
# https://github.com/cashapp/sqldelight/issues/4154
# TL,DR: the 33.sqm use UPDPATE table AS alias syntax which is a valid SQLtite syntax but sqldelight doesn't support it
# it result to false nigaive when validating the migration
# and need to be reverted as soon as https://github.com/cashapp/sqldelight/issues/4154 is fixed
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
