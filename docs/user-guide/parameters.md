# Parameters

Parameter values are often the most useful part of a method log, and at the same time the most dangerous one. This page
explains how parameters are named, how they are placed in the log, how their format is changed and how they are excluded
from logging.

All parameters of a logged method are logged by default. The annotation [`@ParamLog`][jd-paramlog] on a parameter
controls how that parameter is logged. Its attributes are `format` (with the alias `value`), `inline`, `name` and
`exclude`.

## Parameter Names

The name in front of each parameter value is determined in the following order:

1. The name given by `@ParamLog(name = ...)`, if it is not empty.
2. The parameter name discovered by Spring from the class file.
3. The synthesized name of the Java reflection API, `arg0`, `arg1` and so on, if no name can be discovered.

Spring discovers parameter names only from classes compiled with `-parameters`, or, in Spring Framework versions before
6.1, from the debug information. The [Getting Started](../getting-started.md#compiling-with-parameter-names) page shows
the compiler configuration.

When a method implements an interface method or overrides a superclass method, the names are discovered from the most
specific declaration that provides them, starting with the executed method itself. An explicit name is useful when the
logged name should differ from the name in the code, or when the class is compiled without parameter names:

```java title="CatalogService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

@Service
public class CatalogService
{
  @MethodLogging
  public String lookup(@ParamLog(name = "isbn") String s,
                       @ParamLog(name = "lang") String l) {
    return "Dune";
  }
}
// INFO  > lookup(isbn=0441013597,lang=en):13
```

## Inline and Separate Parameters

By default, parameters are logged inline, as part of the entry message. With `@ParamLog(inline = false)`, a parameter is
logged in a message of its own, directly after the entry message. Separately logged parameters keep the entry message
readable when a value is long, such as a document, a list or a request body.

```java title="ReviewService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

@Service
public class ReviewService
{
  @MethodLogging
  public void addReview(String isbn, int stars,
                        @ParamLog(inline = false) String text) {
  }
}
// INFO  > addReview(isbn=0441013597,stars=5):13
// DEBUG parameter 'text' = A classic. The world building is superb.
// INFO  < addReview:13
```

Inline and separately logged parameters can be mixed freely. Both are logged only if the parameter level is enabled, as
explained on the [Logging Methods](logging-methods.md#log-levels) page. The formats of both kinds of messages are
configurable, see [Message Formats](message-formats.md#parameter-and-result-formats).

## Formatting a Parameter Value

The `format` attribute replaces the format of the parameter value. In the format, the parameter value is available as
the variable `value`, written `%{value}`. The default format is `%{value}`, which formats the value with the formatter
the message support provides for its type. As `value` is an alias of `format`, the format can be given without the
attribute name.

```java title="CouponService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CouponService
{
  @MethodLogging
  public int redeem(@ParamLog("%{value,null:'<none>'}") String coupon,
                    @ParamLog("%{value,format:size}") List<Long> orders,
                    @ParamLog("[%{value}]") String note) {
    return 0;
  }
}
// redeem(null, List.of(41L, 42L), "gift") logs:
// INFO  > redeem(coupon=<none>,orders=2,note=[gift]):16
```

The first format replaces a `null` coupon by `<none>`; without it, `null` is formatted as an empty value. The second
format logs the number of orders instead of the order numbers. The third format encloses the value in brackets. The
format syntax is that of the message-format library and is introduced on the [Message Formats](message-formats.md) page.

An invalid format is detected when the method is analyzed, which usually happens while the application context starts.
The [Error Handling](../reference/error-handling.md#configuration-errors) page describes the consequences.

## Excluding Parameters

A parameter is excluded from logging if its value contains sensitive information, has no meaningful string
representation or produces far too much output. Three mechanisms exist, which suit different situations.

/// danger | Sensitive values must be excluded explicitly
Method logging logs every parameter that is not excluded. Passwords, tokens, keys, payment data and personal data end up
in the log files, which are usually less protected than the application data. Such parameters must be excluded with one
of the mechanisms below before the method is annotated.
///

The following method is an unsafe example. It logs the password in plain text:

/// warning | Unsafe example
```java title="CustomerService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class CustomerService
{
  @MethodLogging
  public void register(String email, String password) {
  }
}
// UNSAFE: INFO  > register(email=ada@example.com,password=s3cr3t):11
```
///

### Excluding by Annotation

`@ParamLog(exclude = true)` excludes the annotated parameter. If `exclude` is `true`, all other attributes of the
annotation are ignored. This is the recommended mechanism for sensitive values, because it does not depend on parameter
names and stays correct when the parameter is renamed.

```java title="CustomerService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

@Service
public class CustomerService
{
  @MethodLogging
  public void register(String email,
                       @ParamLog(exclude = true) String password) {
  }
}
// INFO  > register(email=ada@example.com):13
```

### Excluding by Name

The `exclude` attribute of `@MethodLogging` lists the names of parameters to be excluded. It is convenient when a method
has several parameters to exclude, or when the parameter annotations cannot be changed.

```java title="TokenService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class TokenService
{
  @MethodLogging(exclude = { "secret", "refreshToken" })
  public boolean verify(String user, String secret,
                        String refreshToken) {
    return true;
  }
}
// INFO  > verify(user=ada):12
```

A name matches a parameter if it equals the name given by `@ParamLog(name = ...)` or the discovered name of the
parameter in the executed method or in any method it overrides. A parameter named differently in an interface and its
implementation can therefore be excluded by either name. A parameter annotated with `@ParamLog` is excluded as well if
its name is listed.

If parameter names are not available, the synthesized names `arg0`, `arg1` and so on are matched instead. Excluding
`secret` then no longer works, because the parameter is called `arg1`. For this reason, every listed name must match a
parameter, otherwise the method analysis fails with an `IllegalStateException`:

```java title="Misspelled parameter name"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class LoginService
{
  @MethodLogging(exclude = "pasword")
  public void login(String user, String password) {
  }
}
// the application context fails to start:
// IllegalStateException: Excluded parameter 'pasword' matches no
//   parameter of public void com.example.bookstore.LoginService
//   .login(java.lang.String,java.lang.String)
```

The check makes a misspelled or outdated name visible instead of silently logging the value it was meant to hide. The
names are checked even if the method has no parameters or parameter logging is disabled with `parameters = HIDE`. Names
excluded by a [composed annotation](../developer-guide/composed-annotations.md) are not checked if parameter names are
available, so the composed annotation can be used on methods with different parameters.

### Excluding by Type

Some types should never be logged, wherever they appear. A credit card number, an `InputStream` or a large binary
document is excluded by type through the global configurer. The method
[`excludeMethodParameter(ResolvableType)`][jd-configurer] of `MethodLoggingConfigurer` is asked for each parameter that
is neither of a primitive type, nor annotated with `@ParamLog`, nor already excluded by name. It is not asked at all for
a method whose parameter logging is disabled with `parameters = HIDE`. The following configurer excludes credit cards,
streams and byte arrays:

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

import java.io.InputStream;

@Configuration
@ComponentScan
@EnableMethodLogging
public class ShopConfiguration implements MethodLoggingConfigurer
{
  @Override
  public boolean excludeMethodParameter(ResolvableType type)
  {
    var parameterClass = type.toClass();

    return CreditCard.class.isAssignableFrom(parameterClass) ||
        InputStream.class.isAssignableFrom(parameterClass) ||
        byte[].class == parameterClass;
  }
}
// excludes credit cards, streams and byte arrays from all methods
```

```java title="PaymentService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

record CreditCard(String number, String holder) {
}

@Service
public class PaymentService
{
  @MethodLogging
  public boolean charge(CreditCard card, BigDecimal amount) {
    return true;
  }
}
// INFO  > charge(amount=19.99):16
```

The parameter type is passed as a Spring `ResolvableType`, resolved against the bean class. A generic parameter type
declared in a superclass, such as `T` in `save(T entity)`, is therefore passed as the concrete type of the bean.

A parameter annotated with `@ParamLog` is always logged regardless of `excludeMethodParameter`, unless it is excluded by
its own annotation or by name. Annotating a parameter is therefore the way to log an individual parameter of a type that
is excluded globally. Parameters of primitive types, such as `int` or `boolean`, are never passed to the method; arrays,
including `byte[]`, are.

/// tip | Excluding by type keeps exclusions consistent
A type-based exclusion applies to every method, including methods written later. For types that are sensitive by nature,
such as a class holding credentials, it is more reliable than annotating each parameter.
///

## Inherited Parameter Annotations

Java does not inherit annotations on method parameters. Method logging nevertheless takes `@ParamLog` from the
corresponding parameter of the interface or superclass methods that the executed method implements or overrides. An
annotation on the overriding method takes precedence over an inherited one. An exclusion is never overridden: if any of
these methods excludes the parameter with `@ParamLog(exclude = true)`, the parameter is excluded, even if the overriding
method annotates it without exclusion.

```java title="AccountService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;

public interface AccountService
{
  @MethodLogging
  void changePassword(String email,
                      @ParamLog(exclude = true) String newPassword);
}
// the new password is excluded in every implementation
```

```java title="DefaultAccountService.java"
package com.example.bookstore;

import org.springframework.stereotype.Service;

@Service
public class DefaultAccountService implements AccountService
{
  @Override
  public void changePassword(String email, String newPassword) {
  }
}
// INFO  > changePassword(email=ada@example.com):10
```

This makes an interface the natural place to define the logging contract of a service, including the exclusions that
every implementation must respect.

## Side Effects of Formatting

Parameters are formatted before the method is invoked, and the result is formatted before it is returned to the caller.
Formatting a value executes code of that value, such as its `toString()` method. With the default message support, it
can execute more than that:

- A `Supplier`, including its primitive variants such as `IntSupplier`, is evaluated by calling its `get` method. Its
  side effects occur once more, before the method is invoked.
- An `Iterable` is iterated completely. An iterable that can be iterated only once, such as a `DirectoryStream`, is
  consumed, so the method cannot iterate it anymore. A lazily evaluated iterable, such as Spring's `ObjectProvider`,
  resolves its elements. An infinite iterable never returns.
- Collections, arrays and maps are formatted with all of their elements. An `OutOfMemoryError` raised while formatting a
  very large value is passed on, so the method is not invoked or its result is lost.

/// warning | Exclude parameters whose formatting has side effects
Parameters of these types are excluded with one of the mechanisms above, and such results are hidden with
`@MethodLogging(result = HIDE)`. Alternatively, a message support without these formatters avoids the effect for all
methods, see [Message Formats](message-formats.md#custom-message-support).
///

The formatting only takes place if the parameter or result level is enabled. If a value cannot be formatted because its
`toString()` method throws an exception, the value is logged as `<format failed: ExceptionName>` and the method is
invoked normally.

[jd-paramlog]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/annotation/ParamLog.html
[jd-configurer]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/MethodLoggingConfigurer.html
