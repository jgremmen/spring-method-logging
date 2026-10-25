# Custom Method Loggers

The built-in factories cover the common logging frameworks. An application that logs through an API of its own, such as
a company-wide logging facade, connects it to method logging with a custom method logger and a custom method logger
factory. The same mechanism serves to capture log messages in tests. This page explains the two interfaces involved and
the base class that simplifies writing a factory.

## The Extension Points

Two interfaces of the package `de.sayayi.lib.methodlogging` form the extension point.

A [`MethodLogger`][jd-logger] writes messages to a logging API. It has two abstract methods: `isLogEnabled(Level)` tells
whether a level is enabled, and `log(Level, String)` writes a message. A third method, `log(Level, String,
StackTraceElement)`, receives the [source location](../user-guide/loggers.md#source-locations) of the logged method in
addition. Its default implementation ignores the location and calls `log(Level, String)`. The level passed to these
methods is never `Level.DEFAULT`, and the message is never `null`.

A [`MethodLoggerFactory`][jd-factory] provides the method logger for an invocation. Its method `from(Field loggerField,
Object obj, Class<?> type)` receives the logger field, which is `null` if the class has none, the object whose method is
invoked and its class. It must return a method logger and never `null`. If no logging is desired for the invocation, it
returns the constant `MethodLogger.NO_OP`, a method logger that enables no level. A second method, `validate(Field
loggerField, Class<?> type)`, is called once when a method is analyzed. It throws an `IllegalStateException` if the
factory cannot provide a method logger for the logger field, so configuration errors are detected at startup. Its
default implementation accepts any logger field.

The factory is installed with `methodLoggerFactory()` of the
[configurer](../user-guide/configuration.md#global-configuration). It replaces the `AutoDetectLoggerFactory` for all
logged methods.

## Connecting a Logging Facade

The bookstore logs through its own facade `ShopLog`, which adds the name of the shop to every message. A logger field of
this type is not recognized by the `AutoDetectLoggerFactory`, which logs such classes with a Commons Logging fallback
logger and reports a warning. A custom factory makes method logging use the facade.

```java title="ShopLog.java"
package com.example.bookstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShopLog
{
  private final Logger logger;

  private ShopLog(Class<?> type) {
    logger = LoggerFactory.getLogger(type);
  }

  public static ShopLog of(Class<?> type) {
    return new ShopLog(type);
  }

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  public void debug(String message) {
    logger.debug("[bookstore] {}", message);
  }

  public void info(String message) {
    logger.info("[bookstore] {}", message);
  }
}
// the logging facade used by the bookstore classes
```

### Step 1: Write the Method Logger

The method logger maps the levels of method logging to the facade. The facade has no TRACE level, so `TRACE` is reported
as disabled and its messages are dropped.

```java title="ShopMethodLogger.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLogger;

final class ShopMethodLogger implements MethodLogger
{
  private final ShopLog log;

  ShopMethodLogger(ShopLog log) {
    this.log = log;
  }

  @Override
  public boolean isLogEnabled(Level level)
  {
    return switch (level) {
      case DEBUG -> log.isDebugEnabled();
      case INFO -> log.isInfoEnabled();
      default -> false;
    };
  }

  @Override
  public void log(Level level, String message)
  {
    switch (level) {
      case DEBUG -> log.debug(message);
      case INFO -> log.info(message);
      default -> { }
    }
  }
}
// writes method logging messages through the ShopLog facade
```

### Step 2: Write the Factory

[`AbstractMethodLoggerFactory`][jd-abstract] implements `from` and `validate` and leaves only the creation of method
loggers to its subclass. A subclass implements two methods. `createMethodLogger(Class)` creates the method logger for a
class without logger field. `createMethodLogger(Field, Object)` creates the method logger for the logger held by the
field. The optional method `getLoggerType()` returns the logger type the factory supports; the base class then rejects
logger fields whose declared type can never hold such a logger.

```java title="ShopMethodLoggerFactory.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.logger.AbstractMethodLoggerFactory;

import java.lang.reflect.Field;

public final class ShopMethodLoggerFactory
    extends AbstractMethodLoggerFactory
{
  public ShopMethodLoggerFactory() {
    super(true);
  }

  @Override
  protected Class<?> getLoggerType() {
    return ShopLog.class;
  }

  @Override
  protected MethodLogger createMethodLogger(Class<?> type) {
    return new ShopMethodLogger(ShopLog.of(type));
  }

  @Override
  protected MethodLogger createMethodLogger(Field loggerField,
                                            Object obj)
  {
    try {
      return loggerField.get(obj) instanceof ShopLog log
          ? new ShopMethodLogger(log)
          : MethodLogger.NO_OP;
    } catch (IllegalAccessException ex) {
      return MethodLogger.NO_OP;
    }
  }
}
// provides ShopLog-based method loggers for all logged methods
```

The constructor argument `true` creates a method logger for classes without logger field. With `false`, the analysis of
a method of such a class fails with an `IllegalStateException`. Because `getLoggerType()` returns `ShopLog`, a logger
field declared, for example, as `java.util.logging.Logger` fails the analysis, as it can never hold a `ShopLog`. A
logger field holding `null` or another object disables logging for the invocation.

The base class caches the method loggers it obtains. For a `static final` logger field, `createMethodLogger(Field,
Object)` is called once and the result is cached per field. For a class without logger field,
`createMethodLogger(Class)` is called once per class. For all other fields, `createMethodLogger(Field, Object)` is
called on every invocation, as the content of the field may change. When a method logger is requested concurrently for
the first time, the creation methods may be called more than once; the first cached result is used.

`createMethodLogger(Field, Object)` can return the constant `CLASS_METHOD_LOGGER` to use the method logger for the
target class instead, as created by `createMethodLogger(Class)`. The base class then returns the method logger for the
class of the object whose method is invoked, also when a `static final` logger field is inherited by several classes.

### Step 3: Install the Factory

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@EnableMethodLogging
public class ShopConfiguration implements MethodLoggingConfigurer
{
  @Override
  public MethodLoggerFactory methodLoggerFactory() {
    return new ShopMethodLoggerFactory();
  }
}
// all logged methods use ShopMethodLoggerFactory
```

A class with a `ShopLog` field named `log` is now logged through the facade:

```java title="GiftCardService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class GiftCardService
{
  private static final ShopLog log = ShopLog.of(GiftCardService.class);

  @MethodLogging
  public String issue(int value) {
    return "GC-" + value;
  }
}
// INFO  [bookstore] > issue(value=25):13
// DEBUG [bookstore] result = GC-25
// INFO  [bookstore] < issue:13
```

## Passing the Source Location

A logging API that accepts an explicit source location overrides `log(Level, String, StackTraceElement)`. The location
contains the class name and method name of the logged method, its source file and the line of its first statement. The
file name is `null` and the line number is negative if they are not available. Method logging always calls this method;
the two-argument variant is only reached through the default implementation.

```java title="LocatingMethodLogger.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLogger;

final class LocatingMethodLogger implements MethodLogger
{
  @Override
  public boolean isLogEnabled(Level level) {
    return true;
  }

  @Override
  public void log(Level level, String message) {
    System.out.println(level + " " + message);
  }

  @Override
  public void log(Level level, String message,
                  StackTraceElement location)
  {
    System.out.println(level + " " + message + " at "
        + location.getClassName() + "." + location.getMethodName());
  }
}
// prints each message followed by the class and method logged
```

## Capturing Messages in Tests

As `MethodLoggerFactory` is a functional interface, a factory can be written as a lambda. A factory that collects the
messages in a list allows tests to assert what a method logs:

```java title="RecordingConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
@ComponentScan
@EnableMethodLogging
public class RecordingConfiguration implements MethodLoggingConfigurer
{
  public static final List<String> MESSAGES =
      new CopyOnWriteArrayList<>();

  private static final MethodLogger RECORDER = new MethodLogger() {
    @Override
    public boolean isLogEnabled(Level level) {
      return true;
    }

    @Override
    public void log(Level level, String message) {
      MESSAGES.add(level + " " + message);
    }
  };

  @Override
  public MethodLoggerFactory methodLoggerFactory() {
    return (loggerField, obj, type) -> RECORDER;
  }
}
// every message of method logging is appended to MESSAGES
```

A test invokes a logged method and compares `MESSAGES` with the expected messages, for example `["INFO >
issue(value=25):13", "DEBUG result = GC-25", "INFO < issue:13"]`.

## Rules for Implementations

Method loggers and factories are called concurrently by all threads that invoke logged methods, and method loggers
returned by a factory may be cached and shared. Both must therefore be thread-safe; the [Thread
Safety](../reference/thread-safety.md) page has the details.

An exception thrown by a factory or a method logger does not affect the logged method. Method logging reports it and
continues, as described on the [Error Handling](../reference/error-handling.md#failures-while-logging) page. A method
logger can therefore not abort a method by throwing an exception.

`from` is called on every invocation of a logged method, before the level is checked. It is kept fast, typically by
caching method loggers, which `AbstractMethodLoggerFactory` already does.

[jd-logger]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLogger.html
[jd-factory]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLoggerFactory.html
[jd-abstract]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/logger/AbstractMethodLoggerFactory.html
