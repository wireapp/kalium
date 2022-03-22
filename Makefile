JAVA_HOME := $(shell /usr/libexec/java_home)
CRYPTOBOX_C_VERSION := "v1.1.3"
LIBSODIUM_VERSION := "1.0.18-RELEASE"
ARTIFACTS_PATH := native/libs
LIBCRYPTOBOX_ARTIFACT_FILE := libcryptobox.dylib
LIBCRYPTOBOX_JNI_ARTIFACT_FILE := libcryptobox-jni.dylib

ifneq ("$(wildcard native/libs/libcryptobox.dylib)","")
    CRYPTOBOX_EXISTS = TRUE
else
    CRYPTOBOX_EXISTS = FALSE
endif

ifneq ("$(wildcard native/libs/libcryptobox-jni.dylib)","")
    CRYPTOBOX_JNI_EXISTS = TRUE
else
    CRYPTOBOX_JNI_EXISTS = FALSE
endif

ifeq ($(CRYPTOBOX_JNI_EXISTS)$(CRYPTOBOX_JNI_EXISTS), TRUETRUE)
all:
	@echo "Both cryptobox.dylib and cryptobox-jni.dylib were detected. Skipping build"
else
all: install-rust prepare-native cryptobox-c libsodium cryptobox4j copy-all-libs
endif

.PHONY: install-rust
install-rust:
	brew install rust

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
	git clone https://github.com/wireapp/cryptobox4j.git

libsodium:
	@echo "Getting libsodium"
	cd native && \
	rm -rf libsodium && \
	git clone https://github.com/jedisct1/libsodium.git && \
	cd libsodium && \
	git checkout ${LIBSODIUM_VERSION} && \
	./configure && \
	make && make install

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
	cp cryptobox-c/target/release/${LIBCRYPTOBOX_ARTIFACT_FILE} libs/
