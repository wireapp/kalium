# release related targets, ie: docs, changelog, build aar, etc.
# dokka
doc/generate-kdocs:
	./gradlew --no-daemon --no-parallel dokkaHtmlMultiModule
