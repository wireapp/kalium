#!/bin/bash -e

#
# Wire
# Copyright (C) 2025 Wire Swiss GmbH
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see http://www.gnu.org/licenses/.
#
parent_path=$(
  cd "$(dirname "${BASH_SOURCE[0]}")" || exit
  pwd -P
)
cd "$parent_path/.." || exit

FILE_URL="https://repo1.maven.org/maven2/pro/streem/pbandk/protoc-gen-pbandk-jvm/0.16.0/protoc-gen-pbandk-jvm-0.16.0-jvm8.jar"

# Define the name of the file to save
FILE_NAME="protoc-gen-pbandk.jar"

# Download the file
echo "Downloading $FILE_NAME..."
curl -L -o "$FILE_NAME" "$FILE_URL"

# Check if the download was successful
if [ $? -eq 0 ]; then
    echo "Downloaded $FILE_NAME successfully."

    # Add execution permission
    chmod +x "$FILE_NAME"
    mv "$FILE_NAME" build
    echo "Added execution permission to $FILE_NAME."
else
    echo "Failed to download $FILE_NAME."
fi
