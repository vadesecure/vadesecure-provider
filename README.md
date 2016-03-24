# Vade Retro Provider

## Overview

The Vade Redreo Provider library offers tools to manage keys and certificates dynamically over a DAO architecture. It provides the following services (names), pluggable into the Java Cryptography Architecture and the Java Secure Socket Extension:
* the Vade Retro keystore
* the Vade Retro X509 key manager factory
* the Vade Retro SSL context, managing SNI over default SSL protocols.

## Requirements

The provider must run at least on Java 8 JVM.

## Project structure

This project is divided into 2 sub projects:
* SSL provider, the core library that gives all the services
* SSL provider Keystore SQL, a SQL implementation of the DAO.