# release related targets, ie: docs, changelog, build aar, etc.
# dokka
doc/generate-kdocs:
	./gradlew -Dorg.gradle.jvmargs=-Xmx8g --stacktrace dokkaHtmlMultiModule
