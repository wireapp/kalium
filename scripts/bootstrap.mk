# initial setup/bootstrap related targets
SHELL := /bin/bash
AVS_VERSION := $(shell awk -F'"' '/^avs = / { print $$2; exit }' gradle/libs.versions.toml)

ifeq ($(strip $(AVS_VERSION)),)
$(error Could not resolve the AVS version from gradle/libs.versions.toml)
endif

ifeq ($(JAVA_HOME),)
	export JAVA_HOME := $(shell /usr/libexec/java_home)
endif

OS := $(shell uname -s | tr A-Z a-z)

# override avs lib name and extension for linux
# osx 	=>	avs.framework.osx.10.1.41.zip
# linux =>	avs.linux.10.1.41.tar.bz2
AVS_LIB_NAME := avs.framework.osx.$(AVS_VERSION).zip
ifeq ($(OS), linux)
	AVS_LIB_NAME = avs.linux.$(AVS_VERSION).tar.bz2
endif

AVS_ARTIFACT_FILE := avs.framework

NATIVE_TARBALLS = native/.tarballs
NATIVE_LIBS = native/libs
NATIVE := native/.libs.stamp native/.tarballs.stamp

AVS_FRAMEWORK_URL := https://github.com/wireapp/wire-avs/releases/download/$(AVS_VERSION)/$(AVS_LIB_NAME)
AVS_FRAMEWORK_ZIP := $(NATIVE_TARBALLS)/$(AVS_LIB_NAME)
AVS_FRAMEWORK_UNZIP := native/avs.framework_$(AVS_VERSION)
AVS_FRAMEWORK_ARTIFACT := $(NATIVE_LIBS)/$(AVS_ARTIFACT_FILE)
AVS_JNA_LIB_NAME := libavs.dylib
AVS_JNA_LIB_TARGET := $(AVS_ARTIFACT_FILE)/avs

# override framework location for linux
# osx	=> ./native/avs.framework_10.1.41/Carthage/Build/iOS/avs.framework
# linux => ./native/avs.framework_10.1.41/avscore/lib
AVS_FRAMEWORK_LOCATION := $(AVS_FRAMEWORK_UNZIP)/Carthage/Build/iOS/avs.framework
ifeq ($(OS), linux)
	AVS_FRAMEWORK_LOCATION = $(AVS_FRAMEWORK_UNZIP)/avscore/lib
	AVS_JNA_LIB_NAME = libavs.so
	AVS_JNA_LIB_TARGET = $(AVS_ARTIFACT_FILE)/libavs.so
endif

AVS_JNA_LIB := $(NATIVE_LIBS)/$(AVS_JNA_LIB_NAME)

all: $(AVS_FRAMEWORK_ARTIFACT) $(AVS_JNA_LIB)

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

# change extraction binary wrt os
# osx 	=> unzip $AVS_FRAMEWORK_ZIP -d $AVS_FRAMEWORK_UNZIP
# linux	=> tar -xjf $AVS_FRAMEWORK_ZIP -C $AVS_FRAMEWORK_UNZIP
$(AVS_FRAMEWORK_UNZIP): $(AVS_FRAMEWORK_ZIP)
ifeq ($(OS), linux)
	mkdir -p "$@" && tar -xjf "$<" -C "$@"
else
	unzip "$<" -d "$@"
endif

$(AVS_FRAMEWORK_LOCATION): $(AVS_FRAMEWORK_UNZIP)

$(AVS_FRAMEWORK_ARTIFACT): $(AVS_FRAMEWORK_LOCATION)
	cp -r "$<" "$@"

$(AVS_JNA_LIB): $(AVS_FRAMEWORK_ARTIFACT)
	ln -sfn "$(AVS_JNA_LIB_TARGET)" "$@"

setup/pre-push-hook:
	mkdir -p .git/hooks
	cp .githooks/pre-push .git/hooks/pre-push
