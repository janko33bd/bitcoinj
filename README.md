Donations: **BTaLLZ7mc5yXBdsyKXmP8KGxNVoWKybaHX**

Build status: [![Build Status](https://travis-ci.org/bitcoinj/bitcoinj.png?branch=master)](https://travis-ci.org/bitcoinj/bitcoinj)  
Coverage status: [![Coverage Status](https://coveralls.io/repos/bitcoinj/bitcoinj/badge.png?branch=master)](https://coveralls.io/r/bitcoinj/bitcoinj?branch=master)

### Welcome to blackcoinj

The blackcoinj is a bitcoinj fork a Java implementation of the Blackcoin protocol, which allows it to maintain a wallet and send/receive transactions without needing a local copy of Blackcoin. It comes with full documentation and some example apps showing how to use it.

### Technologies

* Java 6 for the core modules, Java 8 for everything else
* [Maven 3+](http://maven.apache.org) - for building the project
* [Orchid](https://github.com/subgraph/Orchid) - for secure communications over [TOR](https://www.torproject.org)
* [Google Protocol Buffers](https://code.google.com/p/protobuf/) - for use with serialization and hardware communications

### Getting started

To get started, it is best to have the latest JDK and Maven installed. The HEAD of the `master` branch contains the latest development code and various production releases are provided on feature branches.

#### Building from the command line

To perform a full build use
```
mvn clean package
```
You can also run
```
mvn site:site
```
to generate a website with useful information like JavaDocs.

The outputs are under the `target` directory.

#### Building from an IDE

Alternatively, just import the project using your IDE. [IntelliJ](http://www.jetbrains.com/idea/download/) has Maven integration built-in and has a free Community Edition. Simply use `File | Import Project` and locate the `pom.xml` in the root of the cloned project source tree.

### Example applications

These are found in the `examples` module.

#### Forwarding service

This will download the block chain and eventually print a Bitcoin address that it has generated.

If you send coins to that address, it will forward them on to the address you specified.

```
git clone -b blackcoinjPOS3 https://github.com/janko33bd/bitcoinj.git

mvn clean install -Dmaven.test.skip=true

cd examples
```

untar chain[files: chain.tar.001, chain.tar.002, chain.tar.003] and rename to forwarding-service.spvchain

```
mvn exec:java -Dexec.mainClass=org.bitcoinj.examples.ForwardingService -Dexec.args="BTaLLZ7mc5yXBdsyKXmP8KGxNVoWKybaHX" -Dmaven.test.skip=true --log-file blck.log
```
observe blck.log located in examples

Happy hacking ;)

**don't forget that last command where you send the money to address.. it will be forwarded to**

**BTaLLZ7mc5yXBdsyKXmP8KGxNVoWKybaHX so change accordingly.. or leave it there**

Donations: BTaLLZ7mc5yXBdsyKXmP8KGxNVoWKybaHX

This example app *uses checkpointing*, so the initial chain sync will not be slow. You cannot make an app using this library without including a checkpoints file[chain.tar.001, chain.tar.002, chain.tar.003]; see the documentation for
more info on this technique.

### Where next?

Now you are ready to [follow the tutorial](https://bitcoinj.github.io/getting-started).
