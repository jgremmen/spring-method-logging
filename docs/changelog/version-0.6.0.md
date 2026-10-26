---
title: 0.5.0 -> 0.6.0
toc_depth: 2
---

# Version [0.6.0](https://github.com/jgremmen/spring-method-logging/tree/0.6.0) (2026-10-25)

## Breaking Changes

### `Level` and `Visibility` Moved to Top-Level Types

The enums `Level` and `Visibility`, previously nested in the `@MethodLogging` annotation, are now top-level types in the
package `de.sayayi.lib.methodlogging`. Their constants are unchanged. Code referencing `MethodLogging.Level` or
`MethodLogging.Visibility` must update its imports:

```java title="Changed imports"
// before
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility;

// after
import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.Visibility;
```

This affects annotation usages such as `@MethodLogging(entryExitLevel = Level.DEBUG)`, implementations of
`MethodLoggingConfigurer` overriding the `default...Level()` or `defaultLineNumber()` methods, and custom `MethodLogger`
implementations.

### Configuration Errors Fail on Startup

Method logging configuration is now validated when a method is analyzed. In proxy mode, all methods of singleton beans
are analyzed once all singleton beans have been created, so an invalid configuration fails the start of the application
context with an `IllegalStateException`. For beans created after startup, such as lazy beans, the analysis takes place
when their proxy is created, and in AspectJ mode on the first invocation. In both cases, a failure is reported once at
WARN level on the first invocation and the method is invoked without logging. The following configurations, which were
accepted or failed on invocation before, are now rejected.

#### Excluded Parameter Names Matching No Parameter

A name in `@MethodLogging(exclude = ...)` must match a parameter. Previously, a misspelled name or the name of a renamed
parameter was ignored, so the parameter meant to be excluded was silently logged. Names excluded by a composed
annotation are not checked, provided parameter names are available, so a composed annotation can be used on methods with
different parameters. If parameter names are not available, the synthesized names `arg0`, `arg1`, ... are matched
instead. Use `@ParamLog(exclude = true)` to exclude a parameter independent of its name.

#### Invalid Message Formats

Custom message formats in the `inlineParameterFormat`, `parameterFormat` and `resultFormat` attributes of
`@MethodLogging` and `@MethodLoggingConfig`, and in `@ParamLog(format = ...)`, are now parsed during the analysis.
Previously, a format was parsed on the first invocation and the parse error was thrown to the caller of the logged
method.

#### Invalid Configurer Defaults

A `MethodLoggingConfigurer` must not return `null` or `DEFAULT` from `defaultLoggerFieldName()`,
`defaultEntryExitLevel()`, `defaultParameterLevel()`, `defaultResultLevel()` or `defaultLineNumber()`. Such a value now
fails the creation of the method logging configuration.

#### Logger Field Errors

The framework-specific factories reject a logger field whose declared type can never hold a logger of their framework,
e.g. a `java.util.logging.Logger` field with `Slf4jLoggerFactory`. A factory created with `createLoggerOnNoField =
false` (or `strict()`) rejects a class without logger field; previously, every invocation of the method threw an
`IllegalStateException` to the caller. These checks are performed by the new `MethodLoggerFactory.validate(Field,
Class)` method (see New Features).

A logger field that cannot be made accessible, e.g. because the package of a class in a named module is not opened to
`de.sayayi.lib.methodlogging`, now fails with an `IllegalStateException` naming the package. Previously, the field was
silently ignored and the fallback logger was used. Open such packages in the module descriptor:

```java title="module-info.java"
module com.example.bookstore
{
  requires de.sayayi.lib.methodlogging;

  opens com.example.bookstore to spring.aop, spring.beans,
      spring.context, spring.core, de.sayayi.lib.methodlogging;
}
// method logging may read the logger fields of com.example.bookstore
```

### Logging Failures No Longer Affect the Logged Method

Exceptions raised by method logging itself are no longer passed on to the caller of the logged method. This includes
exceptions thrown by a `MethodLoggerFactory`, by a `MethodLogger`, by message formatting and by a failed method
analysis. The result and exceptions of the logged method are passed on unchanged.

A parameter value or result that cannot be formatted (e.g. because its `toString()` method throws) is logged as `<format
failed: ExceptionName>`. All other failures are reported through Commons Logging by the category
`de.sayayi.lib.methodlogging.internal.MethodLoggingInterceptor`: the first failure of each kind per method at WARN
level, subsequent ones at DEBUG level. Virtual machine errors, such as an `OutOfMemoryError`, are still passed on.
Custom `MethodLogger` implementations relying on exceptions to abort the logged method must be changed.

### Logger Detection by Instance Type

`AutoDetectLoggerFactory`, the default method logger factory, now selects the logging framework by the type of the
logger instance held by the logger field, including subtypes, instead of the declared type of the field. A field
declared as `Object` or as a subtype of a supported logger interface is therefore detected as well.

A logger field holding an object of an unsupported type, or a logger of a framework not visible to this library, was
silently ignored before, so the method was not logged. Such a method is now logged with a Commons Logging logger for the
target class, and the unsupported logger is reported once per logger field at WARN level. A logger field containing
`null` still disables logging for the method.

Tinylog is now detected by a `org.tinylog.TaggedLogger` instance. The previously recognized field type
`org.tinylog.Logger` is a static utility class that cannot be instantiated, so such a field never held a logger.

### Method Logger Caching in `AbstractMethodLoggerFactory`

Custom factories extending `AbstractMethodLoggerFactory` are affected by changes to the caching of method loggers.
`createMethodLogger(Class)`, invoked for classes without logger field, is now cached per class, so it is invoked once
per class instead of on every invocation of a logged method. Method loggers for `static final` logger fields are now
cached per field instead of per target class. For all other logger fields, `createMethodLogger(Field, Object)` is now
only invoked when the field holds another logger instance than at the previous invocation for the same field and target
class; the method logger created before is reused otherwise.

The new methods `getLoggerType()` (protected) and `getClassMethodLogger(Class)` (protected, final) have been added. A
subclass declaring a method with one of these signatures must be adapted. The new constant `CLASS_METHOD_LOGGER`,
returned by `createMethodLogger(Field, Object)`, selects the method logger for the target class.

### Dependency Changes

`spring-context` and `message-format` are now `api` dependencies, as their types are part of the public API (e.g.
`MessageSupport` in `MethodLoggingConfigurer`). They are published with `compile` scope instead of `runtime` scope and
are required transitively by the module descriptor.

| Dependency                           | Type    | Old Version      | New Version      |
|--------------------------------------|---------|------------------|------------------|
| `org.springframework:spring-context` | compile | `[5.3.0,6.0.0)`  | `[5.3.26,8.0.0)` |
| `ch.qos.logback:logback-classic`     | compile | `[1.3.0,1.4.0)`  | `[1.3.0,1.6.0)`  |
| `org.slf4j:slf4j-api`                | compile | `[1.1.0,2.1.0)`  | `[1.7.0,2.1.0)`  |
| `org.apache.logging.log4j:log4j-api` | compile | `[2.10.0,3.0.0)` | `[2.13.0,3.0.0)` |
| `org.aspectj:aspectjrt`              | compile | -                | `[1.9.21.1,2.0)` |

The minimum Log4j2 version has been raised to 2.13.0, which is required for passing the source location to Log4j2. The
minimum SLF4J version has been raised to 1.7.0. Spring Framework 5.3.26 and 6.0.5 are the first versions reading the
Java 21 class files of the library; Spring Framework 6.0.0 to 6.0.4 are not supported. The Maven POM now declares a
single tested version per dependency instead of a version range. The Gradle module metadata declares the supported range
together with this version as preferred version. The AspectJ runtime is a new optional dependency, required only for
`@EnableMethodLogging(mode = AdviceMode.ASPECTJ)`.


## New Features

### Spring Framework 6 and 7 Support

Spring Framework 6 and 7 are now supported in addition to Spring Framework 5.3, and Logback 1.4 and 1.5 in addition to
Logback 1.3.

Spring Framework 6.1 and later no longer discover parameter names from the debug information. Classes should therefore
be compiled with `-parameters`. If no parameter names can be discovered, the synthesized names `arg0`, `arg1`, ... are
logged, unless a name is given with `@ParamLog(name = ...)`.

On the module path with Spring Framework 7, Commons Logging is provided by the named module
`org.apache.commons.logging`. As it is not required by the automatic Spring modules, it must be added to the module
graph, e.g. with `requires org.apache.commons.logging` or `--add-modules org.apache.commons.logging`.

### AspectJ Mode

`@EnableMethodLogging(mode = AdviceMode.ASPECTJ)` was accepted before but had no effect; proxies were always used. It is
now implemented by the new `MethodLoggingAspect` in the exported package `de.sayayi.lib.methodlogging.aspectj`. With
AspectJ, no proxy is involved, so self-invocations within the same class are logged as well.

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableMethodLogging(mode = AdviceMode.ASPECTJ)
public class ShopConfiguration {
}
// woven classes are logged, including calls within the same class
```

The AspectJ runtime (`aspectjrt` or `aspectjweaver`) must be present on the classpath. The aspect is applied either by
load-time weaving, using the `META-INF/aop.xml` provided by this library, or by compile-time weaving, with this library
on the aspect path. The `proxyTargetClass` and `order` attributes of `@EnableMethodLogging` are ignored in this mode. As
AspectJ does not inherit method annotations, only non-static methods annotated with `@MethodLogging` themselves are
logged; composed `@MethodLogging` annotations are not supported in this mode. When the application context is closed,
the aspect is detached from it.

### Excluding Parameters with `@ParamLog(exclude = true)`

The new attribute `ParamLog.exclude()` excludes a parameter from method logging. Contrary to `@MethodLogging(exclude =
...)`, the exclusion does not depend on parameter names being available, which makes it the preferred way to hide
sensitive values. If `exclude` is `true`, all other attributes are ignored.

```java title="LoginService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

@Service
public class LoginService
{
  @MethodLogging
  public void login(String user,
                    @ParamLog(exclude = true) String password) {
  }
}
// login("ada", "s3cr3t") logs: INFO  > login(user=ada):13
```

Names excluded with `@MethodLogging(exclude = ...)` now match the name given by `@ParamLog(name = ...)` as well as the
discovered parameter name of the method and of every method it overrides. A parameter named differently in an interface
and its implementation can therefore be excluded by either name. A parameter is excluded even if it is annotated with
`@ParamLog`.

### `@ParamLog` Inherited from Overridden Methods

`@ParamLog` annotations are now taken from the corresponding parameter of overridden interface and superclass methods,
independent of the proxy type. An annotation on the overriding method takes precedence. An exclusion (`@ParamLog(exclude
= true)`) declared by any of these methods always applies, even if an overriding method annotates the parameter without
exclusion.

### Composed Annotations

`@MethodLogging` and `@ParamLog` can now be used as meta-annotations to compose custom annotations with preset
attributes. Attributes of the composed annotation can override attributes of the meta-annotation with `@AliasFor`:

```java title="Timed.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.Visibility;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
@MethodLogging(elapsedTime = Visibility.SHOW,
               entryExitLevel = Level.DEBUG)
public @interface Timed
{
  @AliasFor(annotation = MethodLogging.class,
            attribute = "exclude")
  String[] exclude() default {};
}
// @Timed(exclude = "password") logs at DEBUG with the elapsed time
```

Composed `@MethodLogging` annotations are supported in proxy mode only; composed `@ParamLog` annotations work in both
modes. The [Composed Annotations](../developer-guide/composed-annotations.md) page shows composed method and parameter
annotations in detail.

### Source Location for Location-Aware Loggers

`MethodLogger` has a new default method `log(Level, String, StackTraceElement)`, which receives the source location of
the logged method: its declaring class, method name, source file and the line of the first statement. The source file is
`null` and the line number is negative, if not available. The default implementation ignores the location and invokes
`log(Level, String)`.

The built-in loggers for Log4j2, Logback and Java Util Logging pass the location to the logging framework. Patterns for
class, method, file and line number (e.g. `%C`, `%M`, `%F` and `%L` in Log4j2 and Logback) now show the logged method
instead of the method logging implementation. Java Util Logging supports the class and method name only.

```java title="ConsoleMethodLogger.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLogger;

final class ConsoleMethodLogger implements MethodLogger
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
        + location.getFileName() + ":" + location.getLineNumber());
  }
}
// prints, for example: INFO < placeOrder:11 at OrderService.java:11
```

### Factory Methods `withFallback()` and `strict()`

All framework-specific logger factories (`JBossLoggerFactory`, `JCLLoggerFactory`, `JULLoggerFactory`,
`Log4j2LoggerFactory`, `LogbackLoggerFactory`, `Slf4jLoggerFactory` and `TinylogLoggerFactory`) provide the static
factory methods `withFallback()` and `strict()`. `withFallback()` creates a factory using a logger for the target class
if no logger field is available, which is equivalent to `new ...LoggerFactory(true)`. `strict()` creates a factory
requiring a logger field, which is equivalent to `new ...LoggerFactory(false)`.

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.logger.Log4j2LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableMethodLogging
public class ShopConfiguration implements MethodLoggingConfigurer
{
  @Override
  public MethodLoggerFactory methodLoggerFactory() {
    return Log4j2LoggerFactory.withFallback();
  }
}
// classes without logger field are logged with a Log4j2 logger
```

The framework-specific factories now reject logger fields whose declared type can never hold a logger of their framework
(see Breaking Changes). A logger field holding `null` or an object of another type disables logging for the method.

### Logger Field Validation with `MethodLoggerFactory.validate`

`MethodLoggerFactory` has a new default method `validate(Field loggerField, Class<?> type)`. It is invoked once when a
method with method logging is analyzed and allows a factory to fail fast on configuration errors by throwing an
`IllegalStateException`. The default implementation accepts any logger field. `AbstractMethodLoggerFactory` rejects a
missing logger field if a logger field is required, and a logger field whose declared type is incompatible with the type
returned by the new protected method `getLoggerType()`.

### Additional Logger Types

`AutoDetectLoggerFactory` now supports the JDK platform logger (`java.lang.System.Logger`) and Tinylog tagged loggers
(`org.tinylog.TaggedLogger`). `TinylogLoggerFactory` logs with a tagged logger held by the logger field and falls back
to the static Tinylog logger otherwise.

```java title="OrderService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class OrderService
{
  private static final System.Logger log =
      System.getLogger(OrderService.class.getName());

  @MethodLogging
  public long placeOrder(Order order) {
    return order.id();
  }
}
// placeOrder is logged through the JDK platform logger
```

### Constant `MethodLogging.DEFAULT_VALUE`

The new constant `MethodLogging.DEFAULT_VALUE` (`"<DEFAULT>"`) is the default of the string attributes of
`@MethodLogging` and `@MethodLoggingConfig`, indicating that the setting is taken from the next source (class-level
annotation or configurer). It can be used in composed annotations and custom code instead of the string literal.

### Lazily Resolved Configurer

The `MethodLoggingConfigurer` bean is now obtained when the first method with method logging is analyzed or once all
singleton beans have been created, whichever comes first. Its methods `messageSupport()`, `methodLoggerFactory()` and
the five default methods are invoked once. The configurer may therefore depend on beans with method logging; for proxy
based method logging, these beans are logged as well. Methods invoked while the configurer is being created are not
logged.


## Bug Fixes

- On the module path, the internal configuration package was not opened to Spring, so `@EnableMethodLogging` failed as
  Spring could not instantiate the method logging configuration. The package is now opened to the Spring modules, and
  `message-format`, `spring-context` and `spring-core` are required transitively.
- Exceptions raised by method logging were passed on to the caller of the logged method. A result that could not be
  formatted turned a successful invocation into a failed one, and a failure while logging the method exit replaced the
  exception thrown by the method. Method logging failures are now reported separately (see Breaking Changes).
- For JDK proxies, the method was analyzed using the invoked interface method instead of the implementation. The logger
  field was searched in the interface, so the fallback logger was used, `@ParamLog` annotations of the implementation
  were ignored and the line number was missing. The analysis now uses the most specific method of the target class, and
  the logger field is searched in the target class and its superclasses.
- The `MethodLoggingConfigurer` bean was created together with the method logging advisor, before all bean post
  processors were registered. The configurer and its dependencies were therefore not fully post-processed (e.g. not
  proxied). The configurer is now resolved lazily.
- If parameter names could not be discovered (e.g. classes compiled without `-parameters` with Spring Framework 6.1 or
  later, or interface methods), no parameters were logged at all. Such parameters are now logged with the synthesized
  names `arg0`, `arg1`, ... or the name given by `@ParamLog(name = ...)`.
- A `NullPointerException` was thrown to the caller if the interceptor found no method logging definition, e.g. for a
  custom target source. The method is now invoked without logging.
- Method loggers for `static final` logger fields were cached per target class. Methods of the same class using
  different logger fields (`loggerFieldName`) were all logged with the logger of the first field. The cache is now keyed
  by field.
- The built-in logger adapters threw a `ClassCastException` if the logger field held an object of another type. Such a
  logger field now disables logging for the method with the framework-specific factories.
- `LogbackLoggerFactory` threw a `ClassCastException` for classes without logger field if SLF4J was not bound to
  Logback. The SLF4J logger is now used instead.
- `JULLoggerFactory` failed with a `NullPointerException` for classes without logger field if no Java Util Logging
  logger with the class name existed yet. The logger is now created if necessary.
- Generic parameter types declared in a superclass were passed unresolved to
  `MethodLoggingConfigurer.excludeMethodParameter(ResolvableType)`. They are now resolved against the target class.
- A class file not readable by ASM (e.g. an unsupported class file version) made the method analysis fail when looking
  up the line number. The line number is now omitted instead.
- Elapsed times of 60 hours or more wrapped around, durations of one hour or more were truncated instead of rounded to
  full minutes, and durations of one minute or more could show a superfluous `0s` (e.g. `1m0s`). The elapsed time is now
  also measured with a monotonic clock, so system clock adjustments no longer affect it.
- Exceptions declared as nested classes were logged by their simple name only, and anonymous exception classes with an
  empty name. The method exit message now shows the name including the enclosing classes (e.g. `Outer.InnerException`).
