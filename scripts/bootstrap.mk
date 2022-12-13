# initial setup/bootstrap related targets
SHELL := /bin/bash
CRYPTOBOX_C_VERSION := "v1.1.3"
CRYPTOBOX4J_VERSION := "1.1.1"
LIBSODIUM_VERSION := "1.0.18-RELEASE"

ifeq ($(JAVA_HOME),)
	JAVA_HOME := $(shell /usr/libexec/java_home)
endif

OS := $(shell uname -s | tr A-Z a-z)

ifeq ($(OS),darwin)
  LIBNAME_FLAG=-install_name
  LIBCRYPTOBOX_ARTIFACT_FILE := libcryptobox.dylib
  LIBCRYPTOBOX_JNI_ARTIFACT_FILE := libcryptobox-jni.dylib
  LIBSODIUM_ARTIFACT_FILE := libsodium.dylib
  AVS_ARTIFACT_FILE := avs.framework
endif
ifeq ($(OS),linux)
  LIBNAME_FLAG=-soname
  LIBCRYPTOBOX_ARTIFACT_FILE := libcryptobox.so
  LIBCRYPTOBOX_JNI_ARTIFACT_FILE := libcryptobox-jni.so
  LIBSODIUM_ARTIFACT_FILE := libsodium.so
  AVS_ARTIFACT_FILE := avs.framework
endif

all: install-rust prepare-native cryptobox-c libsodium cryptobox4j avs-download copy-all-libs

.PHONY: install-rust
install-rust:
	@curl https://sh.rustup.rs -sSf | sh -s -- -y
	@source "${HOME}/.cargo/env"

.PHONY: clean-native
clean-native:
	@echo "Removing native dir"
	@rm -rf native

.PHONY: prepare-native
prepare-native: clean-native
	@echo "Creating native dir"
	@mkdir -p native/libs

.PHONY: cryptobox-c
cryptobox-c: prepare-native
	@echo "Cloning and building cryptobox-c version ${CRYPTOBOX_C_VERSION}"
	cd native && \
	rm -rf cryptobox-c && \
	git clone https://github.com/wireapp/cryptobox-c.git && \
	cd cryptobox-c && \
	git checkout ${CRYPTOBOX_C_VERSION} && \
	make compile-release

.PHONY: cryptobox4j-clone
cryptobox4j-clone:
	@echo "Cloning and building cryptobox4j"
	cd native && \
	rm -rf cryptobox4j  && \
	git clone https://github.com/wireapp/cryptobox4j.git && \
	cd cryptobox4j && \
	git checkout ${CRYPTOBOX4J_VERSION}

libsodium:
	@echo "Getting libsodium"
	cd native && \
	rm -rf libsodium && \
	git clone https://github.com/jedisct1/libsodium.git && \
	cd libsodium && \
	git checkout ${LIBSODIUM_VERSION} && \
	./configure && \
	make

cryptobox4j: cryptobox4j-clone cryptobox4j-compile

cryptobox4j-compile: cryptobox4j-clone
	cd native/cryptobox4j && \
	mkdir -p build/lib && \
	JAVA_HOME=$(/usr/libexec/java_home) && \
	cc -std=c99 -g -Wall src/cryptobox-jni.c \
		-I"${JAVA_HOME}/include" \
		-I"${JAVA_HOME}/include/${OS}" \
		-Ibuild/include \
		-I../cryptobox-c/src \
		-L../cryptobox-c/target/release/ \
		-lcryptobox \
		-L/usr/local/lib/ \
		-lsodium \
		-shared \
		-fPIC \
		-Wl,${LIBNAME_FLAG},${LIBCRYPTOBOX_JNI_ARTIFACT_FILE} \
		-o build/lib/${LIBCRYPTOBOX_JNI_ARTIFACT_FILE}

.PHONY: avs-download-osx
avs-download-osx: prepare-native
	@echo "Download AVS for OSX"
	cd native && \
	mkdir avs && \
	cd avs && \
	curl -L https://github.com/wireapp/wire-avs/releases/download/8.2.16/avs.framework.osx.8.2.16.zip --output avs.framework.zip && \
	unzip avs.framework.zip && \
	mv Carthage/Build/iOS/avs.framework .

.PHONY: avs-download
avs-download:
ifeq ($(OS),darwin)
avs-download: avs-download-osx
endif
ifeq ($(OS),linux)
avs-download: avs-download-osx
endif

copy-all-libs:
	cd native && \
	cp cryptobox4j/build/lib/${LIBCRYPTOBOX_JNI_ARTIFACT_FILE} libs/ && \
	cp cryptobox-c/target/release/${LIBCRYPTOBOX_ARTIFACT_FILE} libs/ && \
	cp libsodium/src/libsodium/.libs/${LIBSODIUM_ARTIFACT_FILE} libs/ && \
	cp -R avs/${AVS_ARTIFACT_FILE} libs/
