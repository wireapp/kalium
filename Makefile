ifeq ($(JAVA_HOME),)
JAVA_HOME := $(shell /usr/libexec/java_home)
endif
SHELL := /bin/bash
CRYPTOBOX_C_VERSION := "v1.1.3"
CRYPTOBOX4J_VERSION := "1.1.1"
LIBSODIUM_VERSION := "1.0.18-RELEASE"
LIBCRYPTOBOX_ARTIFACT_FILE := libcryptobox.dylib
LIBCRYPTOBOX_JNI_ARTIFACT_FILE := libcryptobox-jni.dylib
LIBSODIUM_ARTIFACT_FILE := libsodium.dylib

all: install-rust prepare-native cryptobox-c libsodium cryptobox4j copy-all-libs

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
		-I"${JAVA_HOME}/include/darwin" \
		-Ibuild/include \
		-I../cryptobox-c/src \
		-L../cryptobox-c/target/release/ \
		-lcryptobox \
		-L/usr/local/lib/ \
		-lsodium \
		-shared \
		-fPIC \
		-Wl,-install_name,${LIBCRYPTOBOX_JNI_ARTIFACT_FILE} \
		-o build/lib/${LIBCRYPTOBOX_JNI_ARTIFACT_FILE}

copy-all-libs:
	cd native && \
	cp cryptobox4j/build/lib/${LIBCRYPTOBOX_JNI_ARTIFACT_FILE} libs/ && \
	cp cryptobox-c/target/release/${LIBCRYPTOBOX_ARTIFACT_FILE} libs/ && \
	cp libsodium/src/libsodium/.libs/${LIBSODIUM_ARTIFACT_FILE} libs/
