# Message Formats

The parameter and result messages are produced from message formats, which can be replaced per method or per class. The
entry and exit prefixes can be replaced per class. This page introduces the format syntax, lists the variables available
in each format and shows how values of different types are formatted.

## The message-format Library

Method logging formats its messages with the [message-format](https://lib.sayayi.de/message-format/) library. A message
format is a text with embedded parameters. A parameter is written as `%{name}` and is replaced by the formatted value of
the variable `name`. Leading and trailing spaces of a message format are not part of the formatted message. The
formatter used depends on the type of the value, as described in [Formatting of Values](#formatting-of-values).

A parameter can carry additional entries, separated by commas, which select a named formatter, configure a formatter or
map specific values to a text:

| Format                     | Effect                                                                 |
|----------------------------|------------------------------------------------------------------------|
| `%{value}`                 | the value, formatted by the formatter for its type                     |
| `%{value,null:'<none>'}`   | `<none>` if the value is `null`, the formatted value otherwise         |
| `%{value,empty:'-'}`       | `-` if the value is empty, such as an empty string or collection       |
| `%{value,format:size}`     | the size of the value, such as the length of a list or string          |

A post formatter, written `%(name,'message',...)`, transforms the result of a nested message. The post formatter `clip`,
for example, limits the length of its message:

```java title="Clipping a value"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

@Service
public class NewsletterService
{
  @MethodLogging
  public void send(
      @ParamLog("%(clip,'%{value}',clip:12,clip-suffix-text:'...')")
      String subject) {
  }
}
// send("Autumn reading list for 2026") logs:
// INFO  > send(subject=Autumn re...):14
```

The full syntax, including the available formatters and their configuration keys, is documented by the message-format
library. Formats are parsed when a method is analyzed; a format that cannot be parsed fails the analysis, see [Error
Handling](../reference/error-handling.md#configuration-errors).

## Parameter and Result Formats

Four formats are involved in logging parameters and results. Each one provides its own variables:

| Format                  | Set with                                       | Variables                 | Default                                 |
|-------------------------|------------------------------------------------|---------------------------|-----------------------------------------|
| parameter value format  | `@ParamLog(format)`                            | `value`                   | `%{value,null:'(null)'}`                |
| inline parameter format | `inlineParameterFormat`                        | `parameter`, `value`, `inline` | `%{parameter}=%{value}`            |
| parameter format        | `parameterFormat`                              | `parameter`, `value`, `inline` | `parameter '%{parameter}' = %{value}` |
| result format           | `resultFormat`                                 | `result`                  | `result = %{result,null:'(null)'}`      |

The parameter value format is applied first. Its variable `value` holds the parameter value as passed to the method. The
inline parameter format and the parameter format then build the text for one parameter. In these formats, `parameter` is
the parameter name and `value` is the text produced by the parameter value format. The inline parameter format applies
to parameters logged inline, the parameter format to [separately logged
parameters](parameters.md#inline-and-separate-parameters). The boolean variable `inline` is `true` in the inline
parameter format and `false` in the parameter format. It allows one format to be used for both, for example in a
[composed annotation](../developer-guide/composed-annotations.md), with a map entry such as
`%{inline,true:'',false:'separately '}`. In the result format, `result` holds the return value of the method.

The defaults are available as the constants `DEFAULT_INLINE_PARAMETER_FORMAT`, `DEFAULT_PARAMETER_FORMAT` and
`DEFAULT_RESULT_FORMAT` of [`@MethodLoggingConfig`][jd-config].

The following example changes all three method-level formats:

```java title="RecommendationService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecommendationService
{
  @MethodLogging(inlineParameterFormat = "%{parameter}: %{value}",
                 parameterFormat = "with %{parameter} = %{value}",
                 resultFormat = "recommended: %{result}")
  public List<String> recommend(String customer,
                                @ParamLog(inline = false)
                                List<String> history) {
    return List.of("Hyperion", "Foundation");
  }
}
// recommend("Ada", List.of("Dune", "Emma")) logs:
// INFO  > recommend(customer: Ada):18
// DEBUG with history = [Dune, Emma]
// DEBUG recommended: [Hyperion, Foundation]
// INFO  < recommend:18
```

The formats are set on a method with `@MethodLogging` or for all methods of a class with `@MethodLoggingConfig`, as
described on the [Configuration](configuration.md) page. There is no global setting for them; a [composed
annotation](../developer-guide/composed-annotations.md) is the way to share formats across classes.

## Entry and Exit Prefixes

The entry message starts with `> ` and the exit message with `< `. Both prefixes are plain texts, not message formats.
They are set for all methods of a class with the attributes `methodEntryPrefix` and `methodExitPrefix` of
`@MethodLoggingConfig`; `@MethodLogging` has no such attributes.

```java title="WishlistService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import org.springframework.stereotype.Service;

@Service
@MethodLoggingConfig(methodEntryPrefix = "ENTER ",
                     methodExitPrefix = "LEAVE ")
public class WishlistService
{
  @MethodLogging
  public void add(String customer, String isbn) {
  }
}
// INFO  ENTER add(customer=Ada,isbn=0441013597):14
// INFO  LEAVE add:14
```

## Formatting of Values

Unless a format specifies otherwise, each value is formatted by the formatter the message support provides for its type.
With the default message support, values are formatted as follows:

| Value                             | Example value               | Formatted as          |
|-----------------------------------|-----------------------------|-----------------------|
| `null`                            | `null`                      | empty text; `(null)` with the default parameter value and result formats |
| `null` element of a collection    | `Arrays.asList(1, null, 3)` | `[1, 3]`, the element is left out |
| string                            | `"Dune"`                    | `Dune`, without quotes |
| record or other object            | `new Order(42, "0441013597", 2)` | `Order[id=42, isbn=0441013597, quantity=2]` |
| collection or array               | `List.of(1, 2, 3)`          | `[1, 2, 3]`           |
| collection with 25 elements       | `List.of(1, 2, ..., 25)`    | `[1, 2, ..., 20, …]`  |
| map                               | `Map.of("Dune", 2)`         | `{Dune=2}`            |
| number                            | `new BigDecimal("1234.5")`  | `1234.5`              |
| date                              | `LocalDate.of(2026, 10, 25)` | `2026-10-25`         |
| `java.util.Date`                  | `new Date(0)`               | `1970-01-01T00:00:00Z`, as an instant in UTC |
| enum constant                     | `Genre.SCIENCE_FICTION`     | `SCIENCE_FICTION`, the name of the constant |
| path                              | `Path.of("/var/shop")`      | `/var/shop`           |
| `Optional`                        | `Optional.of(7)`            | `7`                   |
| empty `Optional`                  | `Optional.empty()`          | like `null`, `(null)` with the default formats |
| `Supplier`                        | `() -> "Dune"`              | `Dune`, by calling `get()` |

Collections, arrays and maps are logged with at most 20 elements; an ellipsis (`…`) marks the elements left out.
Enum constants are logged by their name, even if the enum overrides `toString()`. A `java.util.Date` is converted to an
instant first. Numbers, dates and all other values without a formatter of their own are logged with their `toString()`
method, so the output does not depend on the locale of the server. Locale-dependent formatting requires a
[custom message support](#custom-message-support).

Values are written to the log verbatim. In particular, line breaks contained in a value are not escaped.

/// danger | Log injection
A parameter value containing line breaks, such as a user name `"ada\nINFO  > refund(amount=1000)"`, produces additional
lines in a plain text log that look like genuine log messages. Parameters carrying unchecked user input are either
excluded, formatted with a format that limits them, or logged with a log format of the logging framework that escapes
line breaks or produces structured output. Exception messages in the exit message are written verbatim as well; they are
hidden with [`exceptionMessage = HIDE`](logging-methods.md#exit-message).
///

## Custom Message Support

The message support creates and formats all messages of method logging. By default, method logging uses a message
support of its own, which formats values as shown in the table above. Besides the formatters for these types, it
provides the named formatters `bool`, `choice` and `size` and the post formatters `case` and `clip`. The method
`messageSupport()` of the [`MethodLoggingConfigurer`](configuration.md#global-configuration) replaces it. Returning
`null`, which is the default implementation, keeps the default message support.

The `DefaultFormatterService` of message-format formats many more types, among them numbers and dates according to the
locale of the message support. It does not enclose collections in brackets and does not limit their elements. A fixed
locale keeps the log output independent of the server configuration:

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.MessageSupportFactory;
import de.sayayi.lib.message.formatter.DefaultFormatterService;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
@ComponentScan
@EnableMethodLogging
public class ShopConfiguration implements MethodLoggingConfigurer
{
  @Override
  public MessageSupport messageSupport()
  {
    return MessageSupportFactory
        .create(new DefaultFormatterService())
        .setLocale(Locale.US);
  }
}
// BigDecimal 1234.5 is logged as 1,234.5 on every server
```

The default message support encloses collections and arrays in brackets and maps in braces with the formatters
[`WrapIterableOrArrayFormatter`][jd-wrap-iterable] and [`WrapMapFormatter`][jd-wrap-map] of the package
`de.sayayi.lib.methodlogging.format`. Added to a custom formatter service, they produce the same enclosing there:

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.MessageSupportFactory;
import de.sayayi.lib.message.formatter.DefaultFormatterService;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.format.WrapIterableOrArrayFormatter;
import de.sayayi.lib.methodlogging.format.WrapMapFormatter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
@ComponentScan
@EnableMethodLogging
public class ShopConfiguration implements MethodLoggingConfigurer
{
  @Override
  public MessageSupport messageSupport()
  {
    final var formatterService = new DefaultFormatterService();

    formatterService.addFormatter(new WrapIterableOrArrayFormatter());
    formatterService.addFormatter(new WrapMapFormatter());

    return MessageSupportFactory
        .create(formatterService)
        .setLocale(Locale.US);
  }
}
// List.of(1, 2) is logged as [1, 2], Map.of("Dune", 2) as {Dune=2}
```

Both formatters only add the enclosing characters; the elements are formatted by the formatters of the formatter
service, here with US number formatting. Unlike the default message support, the `DefaultFormatterService` does not
limit the number of elements.

Like the default message support, the `DefaultFormatterService` evaluates suppliers and iterates iterables, with the
side effects described on the [Parameters](parameters.md#side-effects-of-formatting) page. The
`GenericFormatterService` of message-format formats every value with its `toString()` method instead. A message support
based on it never evaluates or iterates a value beyond what `toString()` does:

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.MessageSupportFactory;
import de.sayayi.lib.message.formatter.GenericFormatterService;
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
  public MessageSupport messageSupport() {
    return MessageSupportFactory.create(new GenericFormatterService());
  }
}
// List.of(1, 2) is logged as [1, 2], a Supplier by its toString()
```

With the generic formatter service, values are formatted by `toString()` only: a list is logged as `[1, 2]` and a map
as `{Dune=2}`, with all of their elements, an `Optional` as `Optional[7]` and an array by its type and identity hash,
such as `[I@7ba18f1b`. The map entry `null:'<none>'` still works, and `empty:'-'` still applies to an empty string,
but no longer to an empty collection, which is logged as `[]`. Named formatters are
not available, so `%{value,format:size}` logs the `toString()` of the value instead of its size. Post formatters are not
registered either: a format using `clip` fails when it is formatted, and the parameter value is logged as
`<format failed: NullPointerException>`. Custom formats are therefore reviewed when the formatter service is replaced.

The message support is used concurrently by all threads invoking logged methods. It is configured completely before it
is returned from `messageSupport()` and not changed afterwards.

[jd-config]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/annotation/MethodLoggingConfig.html
[jd-wrap-iterable]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/format/WrapIterableOrArrayFormatter.html
[jd-wrap-map]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/format/WrapMapFormatter.html
