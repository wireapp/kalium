# initial setup/bootstrap related targets
SHELL := /bin/bash
AVS_VERSION := 9.7.1
CORE_CRYPTO_VERSION := 9.1.1

ifeq ($(JAVA_HOME),)
	export JAVA_HOME := $(shell /usr/libexec/java_home)
endif

OS := $(shell uname -s | tr A-Z a-z)

AVS_ARTIFACT_FILE := avs.framework

NATIVE_TARBALLS = native/.tarballs
NATIVE_LIBS = native/libs
NATIVE := native/.libs.stamp native/.tarballs.stamp

AVS_FRAMEWORK_URL := https://github.com/wireapp/wire-avs/releases/download/$(AVS_VERSION)/avs.framework.osx.$(AVS_VERSION).zip
AVS_FRAMEWORK_ZIP := $(NATIVE_TARBALLS)/avs.framework.osx.$(AVS_VERSION).zip
AVS_FRAMEWORK_UNZIP := native/avs.framework_$(AVS_VERSION)
AVS_FRAMEWORK_LOCATION := $(AVS_FRAMEWORK_UNZIP)/Carthage/Build/iOS/avs.framework
AVS_FRAMEWORK_ARTIFACT := $(NATIVE_LIBS)/$(AVS_ARTIFACT_FILE)

all: $(AVS_FRAMEWORK_ARTIFACT)

.PHONY: clean-native
clean-native:
	@echo "Removing native dir"
	@rm -rf native

native/.libs.stamp:
	mkdir -p "$(NATIVE_LIBS)"
	touch "$@"

native/.tarballs.stamp:
	mkdir -p "$(NATIVE_TARBALLS)"
	touch "$@"

$(AVS_FRAMEWORK_ZIP): $(NATIVE)
	curl -L "$(AVS_FRAMEWORK_URL)" --output "$@"

$(AVS_FRAMEWORK_UNZIP): $(AVS_FRAMEWORK_ZIP)
	unzip "$<" -d "$@"

$(AVS_FRAMEWORK_LOCATION): $(AVS_FRAMEWORK_UNZIP)

$(AVS_FRAMEWORK_ARTIFACT): $(AVS_FRAMEWORK_LOCATION)
	cp -r "$<" "$@"

setup/pre-push-hook:
	mkdir -p .git/hooks
	cp .githooks/pre-push .git/hooks/pre-push

# CoreCrypto iOS frameworks
.PHONY: setup/core-crypto-ios clean-core-crypto-ios
setup/core-crypto-ios:
	@./scripts/download-core-crypto-ios.sh $(CORE_CRYPTO_VERSION)

clean-core-crypto-ios:
	@echo "Removing CoreCrypto iOS frameworks"
	@rm -rf cryptography/frameworks
