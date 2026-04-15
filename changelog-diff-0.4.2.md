# Version [0.4.2](https://github.com/jgremmen/spring-method-logging/releases/tag/0.4.2) (2024-05-22)

## Breaking Changes

### `MessageContext` Replaced by `MessageSupport`

The `messageContext()` method in `MethodLoggingConfigurer` has been renamed to `messageSupport()` and now
returns `MessageSupport` instead of `MessageContext`. This follows the API migration in the `message-format`
library starting with version 0.8.0.

Before:

```java
@Override
public MessageContext messageContext() {
  return new MessageContext(
      DefaultFormatterService.getSharedInstance(),
      new MessageFactory(new LRUMessagePartNormalizer(64)));
}
```

After:

```java
@Override
public MessageSupport messageSupport() {
  return MessageSupportFactory.create(
      new DefaultFormatterService(resourceLoader.getClassLoader(), 128),
      MessageFactory.NO_CACHE_INSTANCE);
}
```

Any class implementing `MethodLoggingConfigurer` that overrides `messageContext()` must rename the method
to `messageSupport()` and adjust the return type.


### `MethodLoggerFactory.from()` Signature Changed

The `from` method in `MethodLoggerFactory` now requires a third parameter:

Before:

```java
@NotNull MethodLogger from(Field loggerField, @NotNull Object obj);
```

After:

```java
@NotNull MethodLogger from(Field loggerField, @NotNull Object obj, @NotNull Class<?> type);
```

The `type` parameter provides the ultimate target class of the Spring bean. All custom implementations
of `MethodLoggerFactory` must be updated to accept this parameter.

`MethodLoggerFactory` is now also annotated with `@FunctionalInterface`, which allows it to be
implemented as a lambda expression.


### `GenericMethodLoggerFactory` Removed

The `GenericMethodLoggerFactory` class has been removed. It is replaced by `AutoDetectLoggerFactory`,
which provides the same functionality with additional logger framework support.

Before:

```java
methodLoggerFactoryDelegate.setFactory(new GenericMethodLoggerFactory());
```

After:

```java
methodLoggerFactoryDelegate.setFactory(new AutoDetectLoggerFactory());
```

For cases where only a specific logger framework is needed, use one of the dedicated factories:

- `JCLLoggerFactory` for Apache Commons Logging
- `JULLoggerFactory` for java.util.logging
- `Log4j2LoggerFactory` for Apache Log4j2
- `Slf4jLoggerFactory` for SLF4J


### Logger Classes Renamed and Made Package-Private

The following logger classes have been renamed and are no longer part of the public API:

- `Slf4JMethodLogger` is now `Slf4jLogger` (package-private)
- `Log4J2MethodLogger` is now `Log4j2Logger` (package-private)
- `JULMethodLogger` is now `JULLogger` (package-private)

The public static factory fields (e.g. `Slf4JMethodLogger.FIELD_FACTORY`) have been removed. Use the
corresponding `MethodLoggerFactory` implementation instead:

Before:

```java
methodLoggerFactoryDelegate.setFactory(Slf4JMethodLogger.FIELD_FACTORY);
```

After:

```java
methodLoggerFactoryDelegate.setFactory(new Slf4jLoggerFactory(false));
```


### `CutOffFormatter` Removed

The `CutOffFormatter` class and its service registration file have been removed. The `cutoff` format
parameter is no longer available. Use the `clip` formatter from the `message-format` library instead:

Before: `%{value,cutoff,8}`

After: `%{value,clip,clip-size:8}`


### Default Log Levels Resolved Through Configurer

The default values for `entryExitLevel`, `parameterLevel`, and `resultLevel` in `@MethodLoggingConfig`
have changed from fixed levels (`INFO`, `DEBUG`, `DEBUG`) to `Level.DEFAULT`. The actual defaults are now
resolved through the corresponding `MethodLoggingConfigurer` methods:

- `defaultEntryExitLevel()` -- returns `Level.INFO`
- `defaultParameterLevel()` -- returns `Level.DEBUG`
- `defaultResultLevel()` -- returns `Level.DEBUG`

The effective default behavior is the same as before. The difference is that these defaults can now be
changed globally by overriding the configurer methods.

Similarly, `MethodLoggingConfig.lineNumber()` now defaults to `Visibility.DEFAULT` instead of
`Visibility.SHOW`. The actual default is resolved through `MethodLoggingConfigurer.defaultLineNumber()`,
which returns `Visibility.SHOW`.


### Dependency Changes

| Dependency | Type | 0.2.0 | 0.4.2 |
|---|---|---|---|
| `de.sayayi.lib:message-format` | runtime | `0.6.3` | `[0.8.0,)` |
| `org.springframework:spring-context` | runtime | `5.3.23` | `[5.3.0,6.0.0)` |
| `org.jetbrains:annotations-java5` | compile | `23.0.0` (as `org.jetbrains:annotations`) | `24.1.+` |
| `org.slf4j:slf4j-api` | compile | `1.7.36` | `[2.0.0,2.1.0)` |
| `org.apache.logging.log4j:log4j-api` | compile | `2.19.0` | `[2.17.1,3.0.0)` |

The JetBrains annotations artifact has changed from `org.jetbrains:annotations` to
`org.jetbrains:annotations-java5`. The `message-format` library has been upgraded to version 0.8.0 or
later, which includes significant API changes (e.g. `MessageContext` replaced by `MessageSupport`).

Version ranges are now used for most dependencies, allowing greater flexibility in dependency resolution.


## New Features

### `AbstractMethodLoggerFactory`

A new abstract base class `AbstractMethodLoggerFactory` simplifies creating custom method logger
factories. It provides:

- Caching for loggers obtained from static final fields
- Handling of the case where no logger field is available on the target class

The `createLoggerOnNoField` constructor parameter controls the behavior when no logger field is found.
If set to `true`, the factory creates a logger based on the target class. If set to `false`, an
`IllegalStateException` is thrown.

Subclasses implement two methods:

```java
public class MyLoggerFactory extends AbstractMethodLoggerFactory {
  public MyLoggerFactory(boolean createLoggerOnNoField) {
    super(createLoggerOnNoField);
  }

  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz) {
    // create a logger when no field is available
  }

  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField,
      @NotNull Object obj) {
    // create a logger from the field
  }
}
```


### `AutoDetectLoggerFactory`

A new `AutoDetectLoggerFactory` dynamically selects the appropriate logger implementation based on the
type of the logger field. This is now the default factory when no custom factory is configured via
`MethodLoggingConfigurer.methodLoggerFactory()`.

Supported logger frameworks:

- Apache Commons Logging (`org.apache.commons.logging.Log`)
- JDK logging (`java.util.logging.Logger`)
- Log4j2 (`org.apache.logging.log4j.Logger`)
- SLF4J (`org.slf4j.Logger`)

When no logger field is found on the target class, the factory falls back to Apache Commons Logging (JCL).


### Framework-Specific Logger Factories

Dedicated logger factory classes are available for each supported framework:

- `JCLLoggerFactory` -- Apache Commons Logging
- `JULLoggerFactory` -- JDK logging
- `Log4j2LoggerFactory` -- Apache Log4j2
- `Slf4jLoggerFactory` -- SLF4J

Each factory constructor accepts a `boolean createLoggerOnNoField` parameter.


### Apache Commons Logging (JCL) Support

Support for the Apache Commons Logging framework (used internally by Spring) has been added. This is now
the default fallback logger when no logger field is found on the target class.


### Configurable Default Log Levels and Line Number Visibility

The `MethodLoggingConfigurer` interface has been expanded with the following methods for global
configuration of defaults:

```java
@Configuration
@EnableMethodLogging
public class AppConfig implements MethodLoggingConfigurer {
  @Override
  public Level defaultEntryExitLevel() {
    return Level.DEBUG;  // default: INFO
  }

  @Override
  public Level defaultParameterLevel() {
    return Level.TRACE;  // default: DEBUG
  }

  @Override
  public Level defaultResultLevel() {
    return Level.TRACE;  // default: DEBUG
  }

  @Override
  public Visibility defaultLineNumber() {
    return Visibility.HIDE;  // default: SHOW
  }
}
```


### Parameter Exclusion

A new method `MethodLoggingConfigurer.excludeMethodParameter(ResolvableType)` allows excluding
specific parameter types from logging. This method is queried for non-primitive, non-array types
on method parameters that do not have a `@ParamLog` annotation.

```java
@Override
public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType) {
  return HttpServletRequest.class.isAssignableFrom(methodParameterType.toClass());
}
```


### Method Logging for Interface-Proxied Beans

Method logging now resolves the most specific method on the target class, which enables correct
annotation detection for interface-proxied Spring beans.


## Bug Fixes

- Fixed method logging for interface-proxied beans where the `@MethodLogging` annotation on the
  implementation class was not detected.
