#!/bin/sh

MISSING_FILES=""
for dbDir in db_global db_user
do
    RESULT=$(git ls-files --other --exclude-standard | grep ".*$dbDir.*.db$")
    if [ -n "${RESULT}" ]; then
        RESULT="\n${RESULT}"
    fi
    MISSING_FILES="${MISSING_FILES}${RESULT}"
done

if [ -n "${MISSING_FILES}" ]
then
    >&2 echo "Missing or Modified DB schema(s) detected: $MISSING_FILES"
    >&2 echo "- If you have modified the DB schema. Make sure you have added a new migration!"
    >&2 echo "- If have added a new migration. Make sure you run the appropriate ./gradlew generate[*]DatabaseSchema and commit the generated files to the repository."
    >&2 echo "- If you are modifying a migration... ☢️ That's dangerous. Are you sure about that? ☢️ "
    exit 1
fi
