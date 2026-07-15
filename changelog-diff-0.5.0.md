# Version [0.5.0](https://github.com/jgremmen/spring-method-logging/tree/0.5.0) (2026-07-15)

## Breaking Changes

### Java 21 Required

The minimum Java version has been raised from 8 to 21. The project now uses Java 21 as its toolchain language level. 
This means that all source code is compiled with Java 21 features (switch expressions, `var`, text blocks, etc.) and 
consumers must run on a Java 21+ runtime.

If your project still targets Java 8 or 11, you need to upgrade your JDK before adopting this version.

### Java Module System Support

A `module-info.java` has been added, declaring the module `de.sayayi.lib.methodlogging`. Only the following packages
are exported:

- `de.sayayi.lib.methodlogging`
- `de.sayayi.lib.methodlogging.annotation`
- `de.sayayi.lib.methodlogging.logger`

Classes in the `de.sayayi.lib.methodlogging.internal` package are no longer accessible from outside the module. If your
code previously referenced internal classes such as `EnableMethodLoggingSelector`, `AnnotationMethodLoggingSource`, 
`MethodLoggingInterceptor`, `MethodLoggingConfiguration`, `MethodDef` or `ParameterDef`, you must remove those 
references. None of these classes were part of the public API and should not have been used directly.

The `EnableMethodLoggingSelector` class has been removed entirely. Its functionality (importing `AutoProxyRegistrar` 
and `MethodLoggingConfiguration`) is now handled directly by the `@EnableMethodLogging` annotation via `@Import`. No 
action is required unless you referenced `EnableMethodLoggingSelector` explicitly.

### Message Format Minimum Version Raised to 0.23.0

The `message-format` dependency version range has changed from `[0.8.0,)` to `[0.23,0.24)`. This is a significant 
change that requires upgrading the message-format library to at least version 0.23.0.

The `MessageFactory.NO_CACHE_INSTANCE` constant is no longer used when constructing the default `MessageSupport`. The 
default message support is now created via `MessageSupportFactory.create(DefaultFormatterService)` without an explicit
cache parameter. If you override `MethodLoggingConfigurer.messageSupport()`, review your implementation against the
message-format 0.23.x API.

### Dependency Changes

| Dependency                           | Type    | Old Version      | New Version      |
|--------------------------------------|---------|------------------|------------------|
| `de.sayayi.lib:message-format`       | compile | `[0.8.0,)`       | `[0.23,0.24)`    |
| `org.slf4j:slf4j-api`                | compile | `[2.0.0,2.1.0)`  | `[1.1.0,2.1.0)`  |
| `org.apache.logging.log4j:log4j-api` | compile | `[2.17.1,3.0.0)` | `[2.10.0,3.0.0)` |
| `org.jboss.logging:jboss-logging`    | compile | -                | `[3.3.2,4.0.0)`  |
| `org.tinylog:tinylog-api`            | compile | -                | `[2.0.0,2.9)`    |

Note: The SLF4J and Log4j2 version ranges have been widened to accept older versions. This is not a breaking change in
itself, but listed here for visibility. The JBoss Logging and Tinylog dependencies are new optional compile dependencies
required only when using the corresponding logger factory.


## New Features

### Tinylog Logging Support

Tinylog is now supported as a logging backend. Since Tinylog uses a static logger API without logger instances, the
implementation is modelled as a singleton.

To use Tinylog explicitly, register a `TinylogLoggerFactory` bean:

```java
@Configuration
@EnableMethodLogging
public class LoggingConfig implements MethodLoggingConfigurer
{
  @Override
  public MethodLoggerFactory methodLoggerFactory()
  {
    return new TinylogLoggerFactory(true);
  }
}
```

When using the default `AutoDetectLoggerFactory`, Tinylog is detected automatically if `org.tinylog.Logger` is found as
the logger field type.

### JBoss Logging Support

JBoss Logging is now supported as a logging backend. A `JBossLoggerFactory` and corresponding `JBossLogger` 
implementation have been added.

To use JBoss Logging explicitly:

```java
@Configuration
@EnableMethodLogging
public class LoggingConfig implements MethodLoggingConfigurer
{
  @Override
  public MethodLoggerFactory methodLoggerFactory()
  {
    return new JBossLoggerFactory(true);
  }
}
```

When using the default `AutoDetectLoggerFactory`, JBoss Logging is detected automatically if `org.jboss.logging.Logger` 
is found as the logger field type.


## Bug Fixes

- Elapsed time formatting for durations of one hour or more with zero minutes no longer appends a superfluous `0m` 
  suffix (e.g. `1h0m` is now displayed as `1h`). The minutes component is only shown when minutes are greater than zero
  or when the remaining seconds round up to at least 30 seconds.
- The `@Language("MessageFormat")` annotation was missing on `ParamLog.value()`, which prevented IDE support (syntax 
  highlighting, validation) for message format strings passed via the `value` attribute.
