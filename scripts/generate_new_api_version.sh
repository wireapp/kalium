#!/bin/bash

# Makes all paths relative to project root so it can be run from anywhere
parent_path=$(
  cd "$(dirname "${BASH_SOURCE[0]}")" || exit
  pwd -P
)
cd "$parent_path/.." || exit

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <previousApiVersion> <currentApiVersion> <newApiVersion>"
  echo "Example: $0 5 6 7"
  exit 1
fi

# Validate that all parameters are integers
if ! [[ "$1" =~ ^[0-9]+$ && "$2" =~ ^[0-9]+$ && "$3" =~ ^[0-9]+$ ]]; then
  echo "Error: All parameters must be integers."
  exit 1
fi

# sometimes we have lower case (i.e. imports) sometimes upper case (i.e. class names)
previousApiVersionLower="v$1"
currentApiVersionLower="v$2"
newApiVersionLower="v$3"

previousApiVersionUpper="V$1"
currentApiVersionUpper="V$2"
newApiVersionUpper="V$3"

copy_api_files() {
  local source_dir=$1
  local target_dir=$2

  mkdir -p "$target_dir"

  for file in "$source_dir"/*.kt; do
    if [[ -f "$file" ]]; then
      content=$(cat "$file")
      # package name changed from previous to current version
      new_content=$(echo "$content" | sed "s/com\.wire\.kalium\.network\.api\.$currentApiVersionLower\./com.wire.kalium.network.api.$newApiVersionLower./g")
      # class name changed from previous to current version
      new_content=$(echo "$new_content" | sed "s/\(class .*\)$currentApiVersionUpper/\1$newApiVersionUpper/g")
      # imports changed from previous to current version
      new_content=$(echo "$new_content" | sed "s/com\.wire\.kalium\.network\.api\.$previousApiVersionLower\./com.wire.kalium.network.api.$currentApiVersionLower./g")
      # imports class names changed from previous to current version
      new_content=$(echo "$new_content" | sed "s/\(import com\.wire\.kalium\.network\.api\.$currentApiVersionLower\.\)\(.*\)$previousApiVersionUpper/\1\2$currentApiVersionUpper/g")
      # class names in extension definition changed from previous to current version
      new_content=$(echo "$new_content" | sed "s/\(: \)\(.*\)$previousApiVersionUpper/\1\2$currentApiVersionUpper/g")
      # class names in inheritance changed from previous to current version
      new_content=$(echo "$new_content" | sed "s/\(: \)\(.*\)$previousApiVersionUpper/\1\2$currentApiVersionUpper/g")
      # Make class definitions empty inside {}
      new_content=$(echo "$new_content" | perl -0777 -pe "s|({[\W\w]*\})|\2|g")
      # Remove all private val or val
      new_content=$(echo "$new_content" | sed "s/\(private \)*val //g")
      # New file name with newApiVersion
      new_filename=$(basename "$file" | sed "s/$currentApiVersionUpper/$newApiVersionUpper/g")
      echo "$new_content" >"$target_dir/$new_filename"
      echo "Created $new_filename"
    fi
  done
}

SOURCE_DIR_UNAUTH="network/src/commonMain/kotlin/com/wire/kalium/network/api/$currentApiVersionLower/unauthenticated"
TARGET_DIR_UNAUTH="network/src/commonMain/kotlin/com/wire/kalium/network/api/$newApiVersionLower/unauthenticated"

SOURCE_DIR_AUTH="network/src/commonMain/kotlin/com/wire/kalium/network/api/$currentApiVersionLower/authenticated"
TARGET_DIR_AUTH="network/src/commonMain/kotlin/com/wire/kalium/network/api/$newApiVersionLower/authenticated"

copy_api_files "$SOURCE_DIR_UNAUTH" "$TARGET_DIR_UNAUTH"
copy_api_files "$SOURCE_DIR_AUTH" "$TARGET_DIR_AUTH"

copy_container_files() {
  local source_file="$1"
  local target_file="${source_file//$currentApiVersionUpper/$newApiVersionUpper}"
  target_file="${target_file//$currentApiVersionLower/$newApiVersionLower}"
  if [[ -f "$source_file" ]]; then
    mkdir -p "$(dirname "$target_file")"

    # Read the content of the file
    content=$(cat "$source_file")

    # Perform replacements on the content
    new_content="${content//$currentApiVersionUpper/$newApiVersionUpper}"
    new_content="${new_content//$currentApiVersionLower/$newApiVersionLower}"

    # Save the modified content back to the target file
    echo "$new_content" >"$target_file"

    echo "Created $target_file"
  else
    exit 1
  fi
}

copy_container_files "network/src/commonMain/kotlin/com/wire/kalium/network/api/$currentApiVersionLower/authenticated/networkContainer/AuthenticatedNetworkContainer$currentApiVersionUpper.kt"
copy_container_files "network/src/commonMain/kotlin/com/wire/kalium/network/api/$currentApiVersionLower/unauthenticated/networkContainer/UnauthenticatedNetworkContainer$currentApiVersionUpper.kt"

echo

# Add the new API version to DevelopmentApiVersions if it does not already contain it
if ! grep -q "$3" network/src/commonMain/kotlin/com/wire/kalium/network/BackendMetaDataUtil.kt; then
  sed -i '' "s/\(val DevelopmentApiVersions = setOf(.*\))/\1, $3)/" network/src/commonMain/kotlin/com/wire/kalium/network/BackendMetaDataUtil.kt
  echo "Added $3 to DevelopmentApiVersions in BackendMetaDataUtil.kt"
else
  echo "$3 is already in DevelopmentApiVersions in BackendMetaDataUtil.kt"
fi

echo "!!!!!!!"
echo "You must add the new API version to the list of supported API versions in the AuthenticatedNetworkContainer.create() and UnauthenticatedNetworkContainer.create() methods."
echo "Check the generated files for unused parameters."
echo "!!!!!!!"
