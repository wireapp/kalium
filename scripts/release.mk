# later we can expand this to have more targets, ie: docs, changelog, build aar, etc.
# dokka
doc/generate-docs:
	./gradlew dokkaHtml
