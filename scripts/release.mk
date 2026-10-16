# release related targets, ie: docs, changelog, build aar, etc.
# dokka
.PHONY: doc/generate-kdocs doc/test-kdocs

doc/generate-kdocs:
	./gradlew :dokkaGeneratePublicationHtml

doc/test-kdocs:
	python3 scripts/check-kdocs.py
