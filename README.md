# kalium
Kalium

## How to build

### Dependencies

- [libsodium](https://github.com/jedisct1/libsodium)
- [cryptobox-c](https://github.com/wireapp/cryptobox-c)

#### Building on macOS 12

On macOS get libsodium from [homebrew](https://brew.sh). No drama.

Then run `brew list libsodium` and note down the path

```
brew install libsodium
git clone https://github.com/wireapp/cryptobox-c
cd cryptobox && make compile-release && cd -
git clone https://github.com/wireapp/cryptobox4j
cd cryptobox4j
mkdir -p build/lib
export JAVA_HOME=$(/usr/libexec/java_home)
```

in the Makefile of cryptobox4j change the target compile-native
to include the locations of cryptobox-c and libsodium

it should look something like this

```
compile-native:
	$(CC) -std=c99 -g -Wall src/cryptobox-jni.c \
	    -I"${JAVA_HOME}/include" \
	    -I"${JAVA_HOME}/include/$(JAVA_OS)" \
	    -Ibuild/include \
            -I../cryptobox-c/src \
            -L../cryptobox-c/target/release \
	    -Lbuild/lib \
	    -lsodium \
	    -lcryptobox \
	    -shared \
	    -fPIC \
	    -Wl,$(OPT_SONAME),$(LIBCRYPTOBOX_JNI) \
	    -o build/lib/$(LIBCRYPTOBOX_JNI)
```

finally run

```
make compile-native
```

in the java VM options add `-Djava.library.path='path/to/libsodium:/path/to/cryptobox-c:/path/to/cryptobox-jni`
