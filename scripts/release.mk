# release related targets, ie: docs, changelog, build aar, etc.
# dokka
.PHONY: doc/generate-kdocs doc/test-kdocs

doc/generate-kdocs:
	# Temporary: consume shared Apple CInterop metadata for this docs invocation only.
	./gradlew :dokkaGeneratePublicationHtml -Pkotlin.mpp.enableCInteropCommonization=true

doc/test-kdocs:
	python3 scripts/check-kdocs.py
