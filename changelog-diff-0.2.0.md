# Version [0.2.0](https://github.com/jgremmen/spring-method-logging/releases/tag/0.2.0) (2022-10-12)

## Breaking Changes

### Default Logger Field Name Changed

The default value of `MethodLoggingConfig.loggerFieldName()` has been changed from `"logger"` to `"log"`.
Classes that relied on the previous default must either rename their logger field to `log` or explicitly set
`loggerFieldName` in the annotation.

Previously, the following code worked without explicit configuration:

```java
@MethodLoggingConfig
public class MyService {
  private static final Logger logger = LoggerFactory.getLogger(MyService.class);

  @MethodLogging
  public void doWork() { ... }
}
```

Starting with version 0.2.0, the interceptor looks for a field named `log` by default. To keep
using a field named `logger`, specify it explicitly:

```java
@MethodLoggingConfig(loggerFieldName = "logger")
public class MyService {
  private static final Logger logger = LoggerFactory.getLogger(MyService.class);

  @MethodLogging
  public void doWork() { ... }
}
```

Alternatively, implement `MethodLoggingConfigurer` and override `defaultLoggerFieldName()` to apply the
change globally:

```java
@Configuration
@EnableMethodLogging
public class AppConfig implements MethodLoggingConfigurer {
  @Override
  public @NotNull String defaultLoggerFieldName() {
    return "logger";
  }
}
```

Internally, the `loggerFieldName` attribute now uses the sentinel value `"<DEFAULT>"` which resolves to
the value returned by `MethodLoggingConfigurer.defaultLoggerFieldName()`.


## New Features

### Configurable Default Logger Field Name

A new method `MethodLoggingConfigurer.defaultLoggerFieldName()` allows configuring the default logger field
name at the global level. This value is used when neither `@MethodLoggingConfig` nor `@MethodLogging`
specify an explicit `loggerFieldName`. If not overridden, the default is `"log"`.

This value can be overridden at the class level using `@MethodLoggingConfig(loggerFieldName = "...")` or
at the method level using `@MethodLogging(loggerFieldName = "...")`.


## Bug Fixes

- Fixed class accessibility of internal Spring bean classes that prevented proper bean registration.
