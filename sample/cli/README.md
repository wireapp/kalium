# Cli 
Command-line interface application for testing and debugging

## Build Commands

```bash
# Clean build
./gradlew clean

# JVM CLI
./gradlew sample:cli:jvmJar -Djava.library.path=$LD_LIBRARY_PATH

# JVM unit tests
./gradlew sample:cli:jvmTest