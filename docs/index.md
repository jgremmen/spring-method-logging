---
icon: material/home-outline
---

# Spring Method Logging

Spring Method Logging is an extension for the Spring Framework that logs the entry and exit of methods, together with
their parameters, return values, exceptions and execution time. A method is logged by annotating it with
`@MethodLogging`. The logging statements that would otherwise be written by hand at the beginning and end of a method
are produced by the library instead.

This manual documents version 0.6.0. It addresses Java developers who use the library in Spring applications and
developers who extend it, for example by connecting it to a logging facade of their own.

## The Problem

Tracing which methods of an application are called, with which arguments and with which outcome, is a common diagnostic
need. The examples in this manual use a small bookstore application, whose orders are represented by a record:

```java title="Order.java"
package com.example.bookstore;

public record Order(long id, String isbn, int quantity) {
}
// toString() of an order: Order[id=42, isbn=0441013597, quantity=2]
```

Writing the trace statements by hand leads to repetitive code like the following:

```java title="ManualOrderService.java"
package com.example.bookstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManualOrderService
{
  private static final Logger log =
      LoggerFactory.getLogger(ManualOrderService.class);

  public long placeOrder(Order order)
  {
    log.info("> placeOrder(order={})", order);
    try {
      var id = order.id();
      log.debug("result = {}", id);
      return id;
    } finally {
      log.info("< placeOrder");
    }
  }
}
// logs the entry, the result and the exit of placeOrder by hand
```

The actual business logic is the single statement `order.id()`. The logging code has to be kept in sync with the method
signature, it does not report exceptions, and every developer formats it slightly differently.

## The Solution

With Spring Method Logging, the same method only carries an annotation. The library intercepts the call, formats the
parameters and the result, and writes the messages to the logging framework of the application:

```java title="OrderService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class OrderService
{
  @MethodLogging
  public long placeOrder(Order order) {
    return order.id();
  }
}
// placeOrder(new Order(42, "0441013597", 2)) logs:
// INFO  > placeOrder(order=Order[id=42, isbn=0441013597, quantity=2]):11
// DEBUG result = 42
// INFO  < placeOrder:11
```

The number after the colon is the source line of the method. The [Getting Started](getting-started.md) page shows the
complete setup of this example.

## Features

The library logs the method entry with the parameter values and the method exit with the execution time and the
exception thrown by the method, if any. The return value is logged in between. Each part can be switched on or off, and
each part has its own log level.

Parameters are logged inline as part of the entry message or on separate lines. Individual parameters can be renamed,
formatted or excluded from logging, which is essential for passwords, tokens and other sensitive values. All messages
are formatted with the [message-format](https://central.sonatype.com/artifact/de.sayayi.lib/message-format) library, so
formats can be customized per method, per class or globally.

Messages are written through the logger field of the logged class, if it has one, so method logging and the regular log
statements of the class share the same logger. SLF4J, Logback, Log4j2, Java Util Logging, the JDK platform logger, JBoss
Logging, Tinylog and Apache Commons Logging are supported out of the box. A custom [method logger
factory](developer-guide/custom-loggers.md) connects any other logging API.

Methods are intercepted with Spring AOP proxies by default. Alternatively, the AspectJ mode weaves the logging into the
classes, so calls within the same class and calls on objects not managed by Spring are logged as well.

Configuration errors are detected when the application context starts. Failures of the logging itself never affect the
logged method.

## How It Works

`@EnableMethodLogging` on a Spring configuration class registers the method logging infrastructure in the application
context. In the default proxy mode, Spring wraps each bean with a `@MethodLogging` method in a proxy. A call through the
proxy reaches a method interceptor, which logs the entry, invokes the method, logs the result and finally logs the exit.

Before a method is logged for the first time, the library analyzes it. The analysis merges the settings of the method
annotation with the class-level defaults and the global configuration, determines the parameter names, parses the
message formats and locates the logger field. The result is cached, so later invocations only format and write the
messages. The [Configuration](user-guide/configuration.md) page explains how settings are resolved.

## Requirements

The library requires Java 21 or later and Spring Framework 5.3 (from 5.3.26), 6 (from 6.0.5) or 7. The logging
frameworks and the AspectJ runtime are optional dependencies, which are only needed when they are used. The
[Getting Started](getting-started.md#dependencies) page lists the supported versions.

## Where to Continue

The [Getting Started](getting-started.md) page sets up a first logged method. The User Guide explains the
[logged messages](user-guide/logging-methods.md), [parameter logging](user-guide/parameters.md),
[message formats](user-guide/message-formats.md), [configuration](user-guide/configuration.md),
[logger fields](user-guide/loggers.md), the [advice modes](user-guide/advice-modes.md) and the
[module system](user-guide/java-modules.md). The [Developer Guide](developer-guide/index.md) gives an overview of the
integration points and covers [composed annotations](developer-guide/composed-annotations.md) and
[custom method loggers](developer-guide/custom-loggers.md). The Reference section documents
[error handling](reference/error-handling.md), [thread safety](reference/thread-safety.md),
[troubleshooting](reference/troubleshooting.md) and a [glossary](reference/glossary.md). The
[version update](changelog/version-0.6.0.md) pages describe the changes of each release, including the migration from
earlier versions.

The [Javadoc](https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/) documents every type and attribute of
the public API.
