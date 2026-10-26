# Logger Fields and Frameworks

Method logging does not write log messages itself. It hands them to a method logger, which forwards them to a logging
framework. This page explains how the method logger is chosen, how logger fields are found and which logging frameworks
are supported.

## Method Loggers and Factories

For each invocation of a logged method, a [`MethodLoggerFactory`][jd-factory] provides the [`MethodLogger`][jd-logger]
that receives the messages. The factory gets the logger field of the class, if there is one, together with the bean and
its class. A method logger is an adapter: it checks whether a level is enabled and logs a message at a level, using the
API of one logging framework.

The factory is configured globally with `methodLoggerFactory()` of the
[`MethodLoggingConfigurer`](configuration.md#global-configuration). Without configuration, the `AutoDetectLoggerFactory`
is used, which supports all built-in logging frameworks. The library also provides a factory for each framework, and
custom factories can be written for any other logging API, as described on the [Custom Method
Loggers](../developer-guide/custom-loggers.md) page.

## Logger Fields

Most classes declare a logger field for their own log statements. Method logging uses the logger held by this field, so
the messages of method logging and of the class appear under the same logger name and follow the same level
configuration.

The logger field is looked up by name. The name is `log` unless it is configured otherwise with the `loggerFieldName`
attribute of `@MethodLogging` or `@MethodLoggingConfig`, or globally with `defaultLoggerFieldName()` of the configurer.
The field is searched in the class of the bean first and then in its superclasses, up to but excluding `Object`. Static
and instance fields of any visibility are found. If no field with the name exists, the factory is invoked without logger
field. An empty name disables the lookup, so the factory is always invoked without logger field.

The lookup takes place once, when the method is analyzed. The value of the field is read on each invocation, or once for
`static final` fields, see [Performance](#performance).

The following base class follows the convention of many Spring classes and declares an instance field named `logger` of
the Commons Logging type `Log`:

```java title="AbstractRepository.java"
package com.example.bookstore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class AbstractRepository
{
  protected final Log logger = LogFactory.getLog(getClass());
}
// each subclass instance has a logger named after its own class
```

```java title="OrderRepository.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import org.springframework.stereotype.Repository;

@Repository
@MethodLoggingConfig(loggerFieldName = "logger")
public class OrderRepository extends AbstractRepository
{
  @MethodLogging
  public Order findById(long id) {
    return new Order(id, "0441013597", 2);
  }
}
// logged with the logger com.example.bookstore.OrderRepository
```

The field is declared in the superclass and found there. As the field is initialized with `getClass()`, the messages are
logged under the name of the subclass.

## Automatic Detection

[`AutoDetectLoggerFactory`][jd-autodetect], the default factory, selects the logging framework by the type of the object
held by the logger field. Subtypes of the listed types are detected as well, so the declared type of the field does not
matter; a field declared as `Object` works.

| Logging framework             | Detected logger type               |
|-------------------------------|------------------------------------|
| Logback                       | `ch.qos.logback.classic.Logger`    |
| SLF4J                         | `org.slf4j.Logger`                 |
| Log4j2                        | `org.apache.logging.log4j.Logger`  |
| Java Util Logging             | `java.util.logging.Logger`         |
| Apache Commons Logging        | `org.apache.commons.logging.Log`   |
| JBoss Logging                 | `org.jboss.logging.Logger`         |
| Tinylog                       | `org.tinylog.TaggedLogger`         |
| JDK platform logger           | `java.lang.System.Logger`          |

An SLF4J logger backed by Logback is an instance of the Logback logger type and is therefore handled as a Logback
logger, which supports [source locations](#source-locations). Logback is checked before SLF4J for this reason.

Depending on the content of the logger field, the factory behaves as follows:

| Situation                                              | Behavior                                                   |
|--------------------------------------------------------|------------------------------------------------------------|
| field holds a logger of a detected type                | messages go to that logger                                 |
| no logger field                                        | messages go to a Commons Logging logger for the bean class |
| field holds `null`                                     | the method is not logged                                   |
| field holds an object of another type                  | messages go to a Commons Logging logger for the bean class |
| field holds a logger of a framework not visible to the library | messages go to a Commons Logging logger for the bean class |

In the last two cases, a warning is logged once per logger field, naming the field and the type of the object it holds.
The last case occurs when the logging framework is loaded by a class loader that the library cannot see, for example in
some application server setups.

The JDK platform logger is supported by this factory only; there is no dedicated factory for it:

```java title="CustomerService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

@Service
public class CustomerService
{
  private static final System.Logger log =
      System.getLogger(CustomerService.class.getName());

  @MethodLogging
  public void register(String email,
                       @ParamLog(exclude = true) String password) {
  }
}
// entry and exit are logged through the JDK platform logger
```

## Framework-Specific Factories

Each supported logging framework has a factory of its own, except the JDK platform logger: `Slf4jLoggerFactory`,
`LogbackLoggerFactory`, `Log4j2LoggerFactory`, `JULLoggerFactory`, `JCLLoggerFactory`, `JBossLoggerFactory` and
`TinylogLoggerFactory`. They require the respective framework to be present at runtime. A framework-specific factory is
useful when an application uses a single framework and wants its fallback logger to be of that framework as well, or
wants configuration errors to be detected at startup.

Each factory is created with one of two static methods. `withFallback()` creates a factory that logs with a logger for
the bean class if no logger field is available. `strict()` creates a factory that requires a logger field. The public
constructors taking a boolean are equivalent: `true` corresponds to `withFallback()` and `false` to `strict()`.

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.logger.Log4j2LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@EnableMethodLogging
public class ShopConfiguration implements MethodLoggingConfigurer
{
  @Override
  public MethodLoggerFactory methodLoggerFactory() {
    return Log4j2LoggerFactory.withFallback();
  }

  @Override
  public String defaultLoggerFieldName() {
    return "logger";
  }
}
// logs through the Log4j2 field "logger", or a Log4j2 logger per class
```

The framework-specific factories, except the Tinylog factory, behave as follows:

| Situation                                              | Behavior                                                   |
|--------------------------------------------------------|------------------------------------------------------------|
| field holds a logger of the framework                  | messages go to that logger                                 |
| no logger field, `withFallback()`                      | messages go to a logger of the framework for the bean class |
| no logger field, `strict()`                            | the method analysis fails                                  |
| declared field type can never hold a logger of the framework | the method analysis fails                            |
| field holds `null` or an object of another type        | the method is not logged                                   |

A failing method analysis usually stops the application context from starting, as described on the [Error
Handling](../reference/error-handling.md#configuration-errors) page. The check of the declared field type rejects, for
example, a field of type `java.util.logging.Logger` with the `Slf4jLoggerFactory`. A field declared as `Object` passes
the check, as it could hold an SLF4J logger; if it holds something else at runtime, the method is not logged and no
warning is issued.

`LogbackLoggerFactory` creates its fallback logger through SLF4J. If SLF4J is not bound to Logback, the fallback is a
plain SLF4J logger.

### Tinylog

Tinylog logs through a static API and has no logger instances, except for tagged loggers. The `TinylogLoggerFactory`
logs with a `TaggedLogger` held by the logger field. In all other cases, including a field holding `null` or an object
of another type, it logs with the static Tinylog logger. Its strict variant only rejects a class without logger field;
the type of the field is not checked.

```java title="InvoiceService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;
import org.tinylog.Logger;
import org.tinylog.TaggedLogger;

@Service
public class InvoiceService
{
  private static final TaggedLogger log = Logger.tag("billing");

  @MethodLogging
  public String createInvoice(long orderId) {
    return "INV-" + orderId;
  }
}
// logged with the Tinylog tag "billing"
```

## Source Locations

Logging frameworks can print the class, method, file and line from which a message was logged, for example with the
pattern conversions `%C`, `%M`, `%F` and `%L` of Log4j2 and Logback. For messages of method logging, the place from
which a message is logged lies inside the library, which is not helpful. Method loggers therefore receive the source
location of the logged method: its class, its name, its source file and the line of its first statement.

Whether the location is used depends on the logging framework:

| Method logger                                                        | Location in the log output                        |
|----------------------------------------------------------------------|---------------------------------------------------|
| Log4j2 logger                                                        | class, method, file and line of the logged method |
| Logback logger, from `AutoDetectLoggerFactory` or `LogbackLoggerFactory` | class, method, file and line of the logged method |
| Java Util Logging logger                                             | class and method of the logged method             |
| SLF4J logger from `Slf4jLoggerFactory`, Commons Logging, JBoss Logging, Tinylog, JDK platform logger | a location inside the library |

An SLF4J logger backed by Logback reports correct locations with the `AutoDetectLoggerFactory`, which detects it as a
Logback logger, but not with the `Slf4jLoggerFactory`, which uses the SLF4J API only.

The fallback logger of the `AutoDetectLoggerFactory` is a Commons Logging logger, which does not support locations. A
class without logger field is therefore logged with a location inside the library, even if Commons Logging forwards to
Log4j2 or Logback. A logger field of the framework type provides correct locations. So does the fallback logger of
`Log4j2LoggerFactory`, `LogbackLoggerFactory` or `JULLoggerFactory` created with `withFallback()`, which is a logger of
the respective framework.

## Level Mapping

The three levels of method logging map to the levels of the logging frameworks as follows:

| Method logging | Log4j2, Logback, SLF4J, Commons Logging, JBoss Logging, Tinylog | Java Util Logging | JDK platform logger |
|----------------|-----------------------------------------------------------------|-------------------|---------------------|
| `TRACE`        | `TRACE`                                                         | `FINEST`          | `TRACE`             |
| `DEBUG`        | `DEBUG`                                                         | `FINE`            | `DEBUG`             |
| `INFO`         | `INFO`                                                          | `INFO`            | `INFO`              |

The JDK platform logger forwards to Java Util Logging by default, which maps `TRACE` to `FINER` and `DEBUG` to `FINE` on
its own.

## Performance

The factories of the library cache method loggers where possible. For a `static final` logger field, the method logger
is created once and cached for the field. For a class without logger field, the fallback logger is created once per
class. For all other fields, the value can change, so the field is read on every invocation. The method logger is
reused as long as the field holds the same logger instance, so a new one is only created when the field is reassigned.

/// tip | Prefer static final logger fields
A `static final` logger field avoids reading the field by reflection on each invocation. For frequently called methods,
it is the most efficient choice.
///

The level check is the first thing done for an invocation. If the entry/exit level is disabled, the cost of method
logging is that of obtaining the method logger and checking the level.

[jd-factory]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLoggerFactory.html
[jd-logger]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLogger.html
[jd-autodetect]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/logger/AutoDetectLoggerFactory.html
