# Configuration

Method logging is configured on three levels. The `@MethodLogging` annotation configures a single method,
`@MethodLoggingConfig` provides defaults for all methods of a class, and a `MethodLoggingConfigurer` bean provides
global defaults and replaces global components. This page explains how the levels interact and lists every option with
its default value.

## Resolution of Settings

Each attribute of `@MethodLogging` has a default that means "not set": `DEFAULT` for levels and visibilities and the
constant `MethodLogging.DEFAULT_VALUE`, which is the string `"<DEFAULT>"`, for names and formats. A setting that is not
set on the method is taken from `@MethodLoggingConfig` of the class. For the logger field name, the levels and the line
number, `@MethodLoggingConfig` can again leave the setting unset, and the value is then taken from the configurer.

```java title="StockService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import static de.sayayi.lib.methodlogging.Level.TRACE;
import static de.sayayi.lib.methodlogging.Visibility.SHOW;

@Component
class GlobalDefaults implements MethodLoggingConfigurer
{
  @Override
  public Level defaultEntryExitLevel() {
    return Level.DEBUG;
  }
}

@Service
@MethodLoggingConfig(elapsedTime = SHOW)
public class StockService
{
  @MethodLogging(entryExitLevel = TRACE)
  public int available(String isbn) {
    return 7;
  }

  @MethodLogging
  public void restock(String isbn, int quantity) {
  }
}
// available: logged at TRACE, with elapsed time
// restock:   logged at DEBUG, with elapsed time
```

The configurer sets the global default entry/exit level to `DEBUG`. It is a bean of the application context, which is
what makes it effective, as explained in [Global Configuration](#global-configuration). `@MethodLoggingConfig` on
`StockService` shows the elapsed time for all its methods. `available` overrides the entry/exit level and takes the
elapsed time from the class. `restock` sets nothing and takes the entry/exit level from the configurer.

If a class has no `@MethodLoggingConfig` annotation, the defaults of the `@MethodLoggingConfig` attributes apply, as if
the class was annotated with `@MethodLoggingConfig` without attributes. The annotation is looked up on the class of the
bean, including its superclasses and interfaces. For a method declared in a superclass, the annotation of the bean class
applies, not that of the declaring class.

## All Options

The following table lists every setting, where it can be configured and its effective default. A dash means that the
setting cannot be configured on that level.

| Setting                  | `@MethodLogging`        | `@MethodLoggingConfig` default | Configurer default                  |
|--------------------------|-------------------------|--------------------------------|-------------------------------------|
| logger field name        | `loggerFieldName`       | taken from configurer          | `defaultLoggerFieldName()`: `"log"` |
| entry/exit level         | `entryExitLevel`        | taken from configurer          | `defaultEntryExitLevel()`: `INFO`   |
| parameter level          | `parameterLevel`        | taken from configurer          | `defaultParameterLevel()`: `DEBUG`  |
| result level             | `resultLevel`           | taken from configurer          | `defaultResultLevel()`: `DEBUG`     |
| line number              | `lineNumber`            | taken from configurer          | `defaultLineNumber()`: `SHOW`       |
| elapsed time             | `elapsedTime`           | `HIDE`                         | -                                   |
| parameters               | `parameters`            | `SHOW`                         | -                                   |
| result                   | `result`                | `SHOW`                         | -                                   |
| inline parameter format  | `inlineParameterFormat` | `%{parameter}=%{value}`        | -                                   |
| parameter format         | `parameterFormat`       | `parameter '%{parameter}' = %{value}` | -                            |
| result format            | `resultFormat`          | `result = %{result}`           | -                                   |
| entry prefix             | -                       | `methodEntryPrefix`: `"> "`    | -                                   |
| exit prefix              | -                       | `methodExitPrefix`: `"< "`     | -                                   |
| excluded parameters      | `exclude`: by name, none | -                             | `excludeMethodParameter(type)`: none |

The attributes of `@MethodLoggingConfig` have the same names as those of `@MethodLogging`. The settings of `@ParamLog`
apply to a single parameter and are not inherited from any other level:

| `@ParamLog` attribute | Default                              | Effect                                        |
|-----------------------|--------------------------------------|-----------------------------------------------|
| `format`, `value`     | empty, which means `%{value}`        | format of the parameter value                 |
| `inline`              | `true`                               | logged inline or as a separate message        |
| `name`                | empty, which means the discovered name | name logged for the parameter               |
| `exclude`             | `false`                              | excludes the parameter, ignoring the others   |

Two global components complete the configuration:

| Component             | Configurer method       | Default when `null` is returned                                        |
|-----------------------|-------------------------|------------------------------------------------------------------------|
| message support       | `messageSupport()`      | default message support, see [Message Formats](message-formats.md#formatting-of-values) |
| method logger factory | `methodLoggerFactory()` | `AutoDetectLoggerFactory`                                              |

The meaning of each setting is explained on the pages [Logging Methods](logging-methods.md),
[Parameters](parameters.md), [Message Formats](message-formats.md) and [Logger Fields and Frameworks](loggers.md).

## Class-Level Defaults

`@MethodLoggingConfig` is placed on a class to configure all of its logged methods at once. It does not cause any method
to be logged by itself; only methods annotated with `@MethodLogging` are logged.

```java title="ReportService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import org.springframework.stereotype.Service;

import static de.sayayi.lib.methodlogging.Level.DEBUG;
import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.Visibility.SHOW;

@Service
@MethodLoggingConfig(entryExitLevel = DEBUG, elapsedTime = SHOW,
                     lineNumber = HIDE, result = HIDE)
public class ReportService
{
  @MethodLogging
  public byte[] monthlySales(int year, int month) {
    return new byte[1024];
  }

  @MethodLogging(result = SHOW)
  public long totalOrders(int year) {
    return 1234;
  }
}
// DEBUG > monthlySales(year=2026,month=10)
// DEBUG < monthlySales (elapsed 4ms)
// DEBUG > totalOrders(year=2026)
// DEBUG result = 1234
// DEBUG < totalOrders (elapsed 0ms)
```

The class hides results, because its reports are large binary documents. The method returning a single number shows its
result again.

## Global Configuration

The interface [`MethodLoggingConfigurer`][jd-configurer] provides the global defaults. All its methods have default
implementations, so an implementation overrides only what it changes. The configurer is a bean of the application
context. Usually the configuration class that carries `@EnableMethodLogging` implements it:

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.Visibility;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@EnableMethodLogging
public class ShopConfiguration implements MethodLoggingConfigurer
{
  @Override
  public String defaultLoggerFieldName() {
    return "logger";
  }

  @Override
  public Level defaultEntryExitLevel() {
    return Level.DEBUG;
  }

  @Override
  public Visibility defaultLineNumber() {
    return Visibility.HIDE;
  }
}
// all methods: logger field "logger", DEBUG level, no line numbers
```

Any other bean implementing the interface works as well, for example one declared with `@Bean` or `@Component`. At most
one configurer is used. If the application context contains several, one of them must be marked with `@Primary`;
otherwise the application context fails to start with a `NoUniqueBeanDefinitionException`. Without a configurer, all
default implementations apply.

The default methods `defaultLoggerFieldName()`, `defaultEntryExitLevel()`, `defaultParameterLevel()`,
`defaultResultLevel()` and `defaultLineNumber()` must not return `null` or `DEFAULT`. Such a value fails the start of
the application context with an `IllegalStateException`. An empty logger field name is allowed and disables the logger
field lookup.

### When the Configurer Is Created

The configurer bean is not created together with the method logging infrastructure. It is obtained when the first logged
method is analyzed or when all singleton beans have been created, whichever comes first. The configurer can therefore
depend on other beans, including beans with logged methods, and these dependencies are fully initialized and proxied.

The methods `messageSupport()`, `methodLoggerFactory()` and the five default methods are invoked once, when the
configuration is created. The method `excludeMethodParameter(ResolvableType)` is invoked during the analysis of each
logged method, once for each parameter it applies to, as described in
[Excluding by Type](parameters.md#excluding-by-type).

Methods invoked while the configurer is being created are not logged. Waiting for the configurer could deadlock, as
creating it may require locks of the bean factory that the invoking thread holds. A logged method that the configurer
invokes during its own creation is therefore executed without logging.

## Enabling Method Logging

[`@EnableMethodLogging`][jd-enable] has three attributes:

| Attribute          | Default                          | Effect                                                             |
|--------------------|----------------------------------|--------------------------------------------------------------------|
| `mode`             | `AdviceMode.PROXY`               | proxies or AspectJ weaving, see [Proxy and AspectJ Mode](advice-modes.md) |
| `proxyTargetClass` | `false`                          | `true` creates subclass (CGLIB) proxies instead of interface proxies |
| `order`            | `Ordered.LOWEST_PRECEDENCE`      | order of the method logging advice among other advice              |

`proxyTargetClass` and `order` apply to the proxy mode only and are ignored in AspectJ mode. Setting `proxyTargetClass`
to `true` affects all beans that Spring proxies automatically, not only those with logged methods; beans with
`@Transactional` methods, for example, are proxied by subclassing as well.

The `order` decides the nesting of advice when several apply to one method. Advice with a lower order value runs outside
of advice with a higher value. With the default, method logging and a transaction advice that has the default order as
well have no defined relative order. A higher precedence places method logging outside, so the exit message and the
elapsed time include the commit of the transaction:

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.*;

@Configuration
@ComponentScan
@EnableTransactionManagement
@EnableMethodLogging(order = Ordered.HIGHEST_PRECEDENCE)
public class ShopConfiguration {
}
// method logging wraps the transaction; elapsed time includes commit
```

[jd-configurer]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLoggingConfigurer.html
[jd-enable]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/annotation/EnableMethodLogging.html
