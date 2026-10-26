# Developer Guide

The annotations cover the configuration of individual methods and classes. Some requirements reach further: an
application logs through an API of its own, a team wants to reuse a combination of settings under one name, or a type
must never appear in the log, wherever it is passed. This guide describes the integration points the library provides
for such requirements and how they fit together.

## Integration Points

All integration points belong to the exported packages `de.sayayi.lib.methodlogging`,
`de.sayayi.lib.methodlogging.annotation`, `de.sayayi.lib.methodlogging.format` and `de.sayayi.lib.methodlogging.logger`,
or to the message-format library:

| Integration point                     | Kind                       | Purpose                                                  |
|---------------------------------------|----------------------------|----------------------------------------------------------|
| [`MethodLoggingConfigurer`][jd-configurer] | interface, Spring bean | global defaults and installation of the components below |
| [`MethodLoggerFactory`][jd-factory]   | functional interface       | provides the method logger for each invocation           |
| [`MethodLogger`][jd-logger]           | interface                  | writes the messages to a logging API                     |
| [`AbstractMethodLoggerFactory`][jd-abstract] | abstract class      | base class for factories, with caching and validation    |
| composed annotation                   | annotation                 | presets of `@MethodLogging` or `@ParamLog` under a name  |
| `MessageSupport`                      | message-format type        | parsing and formatting of all messages                   |
| [`WrapIterableOrArrayFormatter`][jd-wrap-iterable], [`WrapMapFormatter`][jd-wrap-map] | parameter formatters | brackets and braces of the default message support for a custom one |

The configurer is the hub. A single bean implementing `MethodLoggingConfigurer` installs the method logger factory and
the message support, sets the global defaults and excludes parameter types from logging. Composed annotations need no
installation, as Spring's merged annotation support recognizes them on any logged method.

## Combining the Integration Points

The bookstore connects its logging facade, fixes the locale of the log output and never logs a `CreditCard`. All three
customizations are made by one configurer:

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.MessageSupportFactory;
import de.sayayi.lib.message.formatter.DefaultFormatterService;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

import java.util.Locale;

@Configuration
@ComponentScan
@EnableMethodLogging
public class ShopConfiguration implements MethodLoggingConfigurer
{
  @Override
  public MethodLoggerFactory methodLoggerFactory() {
    return new ShopMethodLoggerFactory();
  }

  @Override
  public MessageSupport messageSupport()
  {
    return MessageSupportFactory
        .create(new DefaultFormatterService())
        .setLocale(Locale.US);
  }

  @Override
  public boolean excludeMethodParameter(ResolvableType type) {
    return CreditCard.class.isAssignableFrom(type.toClass());
  }
}
// ShopLog fields are used, numbers use US formatting, cards are hidden
```

`ShopMethodLoggerFactory` is developed step by step on the [Custom Method Loggers](custom-loggers.md) page, `CreditCard`
is the record from [Excluding by Type](../user-guide/parameters.md#excluding-by-type), and the message support is
explained in [Custom Message Support](../user-guide/message-formats.md#custom-message-support).

The configurer methods `methodLoggerFactory()` and `messageSupport()` are invoked once, when the method logging
configuration is created. `excludeMethodParameter` is invoked during the analysis of each logged method. The
[Configuration](../user-guide/configuration.md#when-the-configurer-is-created) page describes when this happens.

## Pages of This Guide

The [Composed Annotations](composed-annotations.md) page defines annotations that preset method and parameter settings.
The [Custom Method Loggers](custom-loggers.md) page connects a logging API that the built-in factories do not support
and shows how to capture the messages of method logging in tests.

Implementations of these interfaces are called concurrently by all threads that invoke logged methods. The
requirements are listed on the [Thread Safety](../reference/thread-safety.md) page, and the handling of exceptions they
throw on the [Error Handling](../reference/error-handling.md#failures-while-logging) page.

/// note | Internal classes
The package `de.sayayi.lib.methodlogging.internal` is not exported and is not part of the API. Its public types are
public only because Spring or the `MethodLoggingAspect` access them, and are not intended to be used by applications.
///

[jd-configurer]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLoggingConfigurer.html
[jd-factory]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLoggerFactory.html
[jd-logger]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLogger.html
[jd-abstract]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/logger/AbstractMethodLoggerFactory.html
[jd-wrap-iterable]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/format/WrapIterableOrArrayFormatter.html
[jd-wrap-map]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/format/WrapMapFormatter.html
