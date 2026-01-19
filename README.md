jnasmartcardio
===
(Previously known as jna2pcsc.) A re-implementation of the [`javax.smartcardio` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/). It allows you to communicate to a smart card (at the APDU level) from within Java.

This library allows you to transmit and receive application protocol data units (APDUs) specified by ISO/IEC 7816-3 to a smart card.

This java library is built on top of the WinSCard native library that comes with the operating system (or libpcsclite1 installed on Linux), which in turn communicates to the myriad USB smart card readers, contactless card readers, and dongles.

Protocols built on top of APDUs include PKCS#15 public key authentication, EMV credit/debit transaction, GSM SIM cellular subscriber information, CAC U.S. military identification, Mifare Classic or DESfire transit payment, and any number of custom protocols.

Alternatives
---

First, if you are using smart cards for authentication and it comes with a PKCS#11 native library (or is supported by opensc-pkcs11), you should probably use the SunPKCS11 KeyStore provider instead of implementing the PKCS#15 client protocol yourself.

Once you have decided on APDU communication, you may wonder why this library exists, given that the JRE already comes with an implementation of `javax.smartcardio`. What’s wrong with it? There are a couple reasons you might consider switching to a JNA solution:

* The default smartcardio library only calls `SCardEstablishContext` once. If the daemon isn’t up yet, then your process will never be able to connect to it again. This is a big problem because on Windows, macOS, and new versions of pcscd, the daemon is not started until a reader is plugged in, and it quits when there are no more readers.
* It’s easier to fix bugs in this project than it is to fix bugs in the libraries that are bundled with the JRE. Anybody can create and comment on issues.

Installation
---

Requires Java 11 or above (JDK 17+ to build).

Releases are published to [mvn.javacard.pro](https://mvn.javacard.pro). Add the repository and dependency to your project's pom.xml:

```xml
<repositories>
    <repository>
        <id>javacard-pro</id>
        <url>https://mvn.javacard.pro/maven/</url>
    </repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.github.martinpaljak</groupId>
    <artifactId>apdu4j-jnasmartcardio</artifactId>
    <version>26.01.12</version>
</dependency>
```

To build from source, run the following command to compile, jar, and install to your local Maven repository. Don't forget to also modify your own project's pom.xml to depend on the same SNAPSHOT version.

```shell
./mvnw install
```

Once you have jnasmartcardio in your classpath, there are 3 ways to use this smartcard provider instead of the one that is bundled with JRE:

1. Modify &lt;java_home&gt;/conf/security/java.security; replace `security.provider.9=sun.security.smartcardio.SunPCSC` with `security.provider.9=jnasmartcardio.Smartcardio`. Then use `TerminalFactory.getDefault()`.
2. Create a file override.java.security, then add system property -Djava.security.properties=override.java.security. This should be a file that contains a line like the above. But make sure that you override the same numbered line as the existing SunPCSC in your JRE; otherwise, you may disable some other factory too! Then use `TerminalFactory.getDefault()`
3. Explicitly reference the Smartcardio class at compile time. There are a few variations of this:
    * `Security.addProvider(new Smartcardio());` `TerminalFactory.getInstance("PC/SC", null, Smartcardio.PROVIDER_NAME);`
    * `Security.insertProviderAt(new Smartcardio(), 1);` `TerminalFactory.getInstance("PC/SC", null);`
    * `TerminalFactory.getInstance("PC/SC", null, new Smartcardio());`

Once you have a TerminalFactory, you call `cardTerminals = factory.terminals();`; see [javax.smartcardio API javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/package-summary.html).

Changelog
---
See [CHANGES.md](CHANGES.md).

Caveats
---
This library requires JNA to talk to the native libraries (winscard.dll, libpcsc.so, or PCSC).

Differences from JRE
---
Some things to keep in mind which are different from JRE:

Generally, all methods will throw a JnaPCSCException if the daemon/service is off (when there are no readers). On Windows, the service is stopped immediately when there are no more readers.

### TerminalFactory

[TerminalFactory.terminals()](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/TerminalFactory.html#terminals%28%29) will (re-)establish connection with the PCSC daemon/service. If the service is not running, terminals() will throw an unchecked exception EstablishContextException.

### JnaCardTerminals

JnaCardTerminals owns the SCardContext native handle, and you should call cardTerminals.close() to clean up. Unfortunately, close() does not exist on the base class, so this library also closes it in its finalizer.

To make the implementation simpler, the caller must be able to handle spurious wakeups when calling [waitForChange(long)](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/CardTerminals.html#waitForChange%28long%29). In other words, `list(State.CARD_REMOVAL)` and `list(State.CARD_INSERTION)` might both be empty lists after waitForChange returns.

[list(State)](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/CardTerminals.html#list%28javax.smartcardio.CardTerminals.State%29) with `CARD_INSERTION`/`CARD_REMOVAL` always reflects the result of the previous waitForChange call. If there was no previous waitForChange call, it returns an empty list; I do _not_ return the current `CARD_PRESENT`/`CARD_ABSENT` value as the JRE does because this would be inconsistent with the internal waitForChange state.

As well as waking up when a card is inserted/removed, waitForChange will also wake up when a card reader is plugged in/unplugged. However, on Windows, when all readers are unplugged the service will immediately exit, so waitForChange will throw an exception instead of returning.

### JnaCardTerminal

[connect(String protocol)](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/CardTerminal.html#connect%28java.lang.String%29) supports exactly the same connection modes as the JRE does: T=0, T=1, T=*, and T=DIRECT (T=CL is mentioned in the smartcardio documentation but is not accepted). Unlike the JRE, it does not return the same connection when you connect twice.

If the protocol is prepended with `EXCLUSIVE;` the usual `SCARD_SHARE_SHARED` mode shall be replaced with `SCARD_SHARE_EXCLUSIVE`.
This allows to use a safely locked reader on Windows 8+ where otherwise a transaction initiated with `SCardBeginTransaction` (`beginExclusive()`) would be closed
after 5 seconds and `SCARD_W_RESET_CARD` returned. See [SCardBeginTransaction documentation](https://learn.microsoft.com/en-us/windows/win32/api/winscard/nf-winscard-scardbegintransaction).

### JnaCard

[beginExclusive()](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/Card.html#beginExclusive%28%29) simply calls SCardBeginTransaction. It does not use thread-local storage, as the JRE does.

[disconnect(boolean reset)](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/Card.html#disconnect%28boolean%29) passes the reset parameter directly to SCardDisconnect.

### JnaCardChannel

[transmit(CommandAPDU command)](https://docs.oracle.com/en/java/javase/17/docs/api/java.smartcardio/javax/smartcardio/CardChannel.html#transmit%28javax.smartcardio.CommandAPDU%29) currently has a response limit of 8192 bytes.

Transmit does the following automatically:

* Sets the channel number in the class byte (CLA)
* If T=0 and Lc ≠ 0 and Le ≠ 0, then the Le byte is removed as required.
* If sw=61xx, then Get Response is automatically sent until the entire response is received.
* If sw=6cxx, then the request is re-sent with the right Le byte.

However, keep in mind:

* If T=0, then you must not send a Command APDU with extended Lc/Le. User is responsible for using Envelope commands if needed to turn T=1 commands into T=0 commands.
* You may perform your own command chaining (e.g. if command is too long to fit in one Command APDU). You must put the command chaining bits in the correct position within the CLA byte, depending on the channel number.
* If you are using secure messaging, you must put the secure messaging bits in the right position within the CLA byte, depending on the channel number.

License
---
This code is released under [CC0](http://creativecommons.org/publicdomain/zero/1.0/legalcode); it is a “universal donor” in the hope that others can find it useful and contribute back.