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
    >&2 echo "Missing DB schema(s) detected listed above. Make sure you run the appropriate ./gradlew generate[*]DatabaseSchema and commit the generated files to the repository.$MISSING_FILES"
    exit 1
fi
