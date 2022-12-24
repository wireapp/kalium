# initial setup/bootstrap related targets
SHELL := /bin/bash
CRYPTOBOX_C_VERSION := v1.1.3
CRYPTOBOX4J_VERSION := 1.2.2
LIBSODIUM_VERSION := 1.0.18-RELEASE
AVS_VERSION := 8.2.16

ifeq ($(JAVA_HOME),)
	export JAVA_HOME := $(shell /usr/libexec/java_home)
endif

OS := $(shell uname -s | tr A-Z a-z)

ifeq ($(OS),darwin)
  LIBNAME_FLAG=-install_name
  LIBCRYPTOBOX_ARTIFACT_FILE := libcryptobox.dylib
  LIBCRYPTOBOX_JNI_ARTIFACT_FILE := libcryptobox-jni.dylib
  LIBSODIUM_ARTIFACT_FILE := libsodium.dylib
endif
ifeq ($(OS),linux)
  LIBNAME_FLAG=-soname
  LIBCRYPTOBOX_ARTIFACT_FILE := libcryptobox.so
  LIBCRYPTOBOX_JNI_ARTIFACT_FILE := libcryptobox-jni.so
  LIBSODIUM_ARTIFACT_FILE := libsodium.so
endif

AVS_ARTIFACT_FILE := avs.framework

NATIVE_TARBALLS = native/.tarballs
NATIVE_LIBS = native/libs
NATIVE := native/.libs.stamp native/.tarballs.stamp

CRYPTOBOX_C_URL := https://github.com/wireapp/cryptobox-c/archive/refs/tags/$(CRYPTOBOX_C_VERSION).tar.gz
CRYPTOBOX_C_TAR_GZ := $(NATIVE_TARBALLS)/cryptobox-c_$(CRYPTOBOX_C_VERSION).tar.gz
CRYPTOBOX_C_CODE := native/cryptobox-c_$(CRYPTOBOX_C_VERSION)
CRYPTOBOX_C_ARTIFACT := $(NATIVE_LIBS)/$(LIBCRYPTOBOX_ARTIFACT_FILE)

LIBSODIUM_URL := https://github.com/jedisct1/libsodium/archive/refs/tags/$(LIBSODIUM_VERSION).tar.gz
LIBSODIUM_TAR_GZ := $(NATIVE_TARBALLS)/libsodium_$(LIBSODIUM_VERSION).tar.gz
LIBSODIUM_CODE := native/libsodium_$(LIBSODIUM_VERSION)
LIBSODIUM_ARTIFACT := $(NATIVE_LIBS)/$(LIBSODIUM_ARTIFACT_FILE)

CRYPTOBOX4J_URL = https://github.com/wireapp/cryptobox4j/archive/refs/tags/$(CRYPTOBOX4J_VERSION).tar.gz
CRYPTOBOX4J_TAR_GZ :=  $(NATIVE_TARBALLS)/cryptobox4j_$(CRYPTOBOX4J_VERSION).tar.gz
CRYPTOBOX4J_CODE := native/cryptobox4j_$(CRYPTOBOX4J_VERSION)
CRYPTOBOX4J_ARTIFACT= $(NATIVE_LIBS)/$(LIBCRYPTOBOX_JNI_ARTIFACT_FILE)

AVS_FRAMEWORK_URL := https://github.com/wireapp/wire-avs/releases/download/$(AVS_VERSION)/avs.framework.osx.$(AVS_VERSION).zip
AVS_FRAMEWORK_ZIP := $(NATIVE_TARBALLS)/avs.framework.osx.$(AVS_VERSION).zip
AVS_FRAMEWORK_UNZIP := native/avs.framework_$(AVS_VERSION)
AVS_FRAMEWORK_LOCATION := $(AVS_FRAMEWORK_UNZIP)/Carthage/Build/iOS/avs.framework
AVS_FRAMEWORK_ARTIFACT := $(NATIVE_LIBS)/$(AVS_ARTIFACT_FILE)

all: $(CRYPTOBOX_C_ARTIFACT) $(LIBSODIUM_ARTIFACT) $(CRYPTOBOX4J_ARTIFACT) $(AVS_FRAMEWORK_ARTIFACT)

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

$(CRYPTOBOX_C_TAR_GZ): $(NATIVE)
	curl -L "$(CRYPTOBOX_C_URL)" --output "$@"

$(CRYPTOBOX_C_CODE)/.stamp: $(CRYPTOBOX_C_TAR_GZ)
	rm -rf "$(CRYPTOBOX_C_CODE)"
	mkdir -p "$(CRYPTOBOX_C_CODE)"
	tar -xC "$(CRYPTOBOX_C_CODE)" --strip-components=1 -f "$<"
	touch "$@"

$(CRYPTOBOX_C_ARTIFACT): $(CRYPTOBOX_C_CODE)/.stamp
	make -C $(CRYPTOBOX_C_CODE) compile-release
	cp "$(CRYPTOBOX_C_CODE)/target/release/$(LIBCRYPTOBOX_ARTIFACT_FILE)" "$@"


$(LIBSODIUM_TAR_GZ): $(NATIVE)
	curl -L "$(LIBSODIUM_URL)" --output "$@"

$(LIBSODIUM_CODE)/.stamp: $(LIBSODIUM_TAR_GZ)
	rm -rf "$(LIBSODIUM_CODE)"
	mkdir -p "$(LIBSODIUM_CODE)"
	tar -xC "$(LIBSODIUM_CODE)" --strip-components=1 -f "$<"
	touch "$@"


$(LIBSODIUM_ARTIFACT): $(LIBSODIUM_CODE)/.stamp
	cd $(LIBSODIUM_CODE) && ./configure
	make -C $(LIBSODIUM_CODE)
	cp  "$(LIBSODIUM_CODE)/src/libsodium/.libs/$(LIBSODIUM_ARTIFACT_FILE)" "$@"


$(CRYPTOBOX4J_TAR_GZ): $(NATIVE)
	curl -L "$(CRYPTOBOX4J_URL)" --output "$@"

$(CRYPTOBOX4J_CODE)/.stamp: $(CRYPTOBOX4J_TAR_GZ)
	rm -rf "$(CRYPTOBOX4J_CODE)"
	mkdir -p "$(CRYPTOBOX4J_CODE)"
	tar -xC "$(CRYPTOBOX4J_CODE)" --strip-components=1 -f "$<"
	touch "$@"

$(CRYPTOBOX4J_ARTIFACT): $(CRYPTOBOX4J_CODE)/.stamp $(CRYPTOBOX_C_ARTIFACT) $(LIBSODIUM_ARTIFACT)
	cc -std=c99 -g -Wall $(CRYPTOBOX4J_CODE)/src/cryptobox-jni.c \
		-I"$(JAVA_HOME)/include" \
		-I"$(JAVA_HOME)/include/$(OS)" \
		-I"$(CRYPTOBOX_C_CODE)/src" \
		-L"$(NATIVE_LIBS)" \
		-lcryptobox \
		-lsodium \
		-shared \
		-fPIC \
		-Wl,$(LIBNAME_FLAG),$(LIBCRYPTOBOX_JNI_ARTIFACT_FILE) \
		-o "$@"

$(AVS_FRAMEWORK_ZIP): $(NATIVE)
	curl -L "$(AVS_FRAMEWORK_URL)" --output "$@"

$(AVS_FRAMEWORK_UNZIP): $(AVS_FRAMEWORK_ZIP)
	unzip "$<" -d "$@"

$(AVS_FRAMEWORK_LOCATION): $(AVS_FRAMEWORK_UNZIP)
	
$(AVS_FRAMEWORK_ARTIFACT): $(AVS_FRAMEWORK_LOCATION)
	cp -r "$<" "$@"
