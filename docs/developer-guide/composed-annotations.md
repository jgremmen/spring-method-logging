# Composed Annotations

Applications often repeat the same combination of method logging settings, such as auditing methods that log at INFO
level with elapsed time, or parameters that are always masked. A composed annotation captures such a combination under a
name of its own. This page shows how to define composed annotations for methods and parameters.

Composed `@MethodLogging` annotations are supported in [proxy mode](../user-guide/advice-modes.md#proxy-mode) only. In
AspectJ mode, only `@MethodLogging` placed directly on the method is recognized. Composed `@ParamLog` annotations work
in both modes, as the parameters of a logged method are analyzed the same way in either mode.

## Composing @MethodLogging

A composed annotation is an annotation that is itself annotated with `@MethodLogging`. Both `@MethodLogging` and
`@ParamLog` allow this, as their targets include annotation types. Spring's merged annotation support finds the
meta-annotation and applies its attributes to the method.

The bookstore audits operations on customer accounts. Every audited method shows the elapsed time, hides the line number
and never logs a parameter named `password`:

```java title="Audited.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
@MethodLogging(elapsedTime = SHOW, lineNumber = HIDE,
               exclude = "password")
public @interface Audited
{
  @AliasFor(annotation = MethodLogging.class,
            attribute = "entryExitLevel")
  Level level() default Level.DEFAULT;
}
// @Audited logs with elapsed time and without parameter "password"
```

The attribute `level` is declared with `@AliasFor`, so a method can override the entry/exit level of the composed
annotation. Its default `Level.DEFAULT` keeps the level of the class or the global configuration. Any attribute of
`@MethodLogging` can be exposed this way. For string attributes, the constant `MethodLogging.DEFAULT_VALUE` serves as
the default that means "not set".

```java title="AccountAdminService.java"
package com.example.bookstore;

import org.springframework.stereotype.Service;

import static de.sayayi.lib.methodlogging.Level.DEBUG;

@Service
public class AccountAdminService
{
  @Audited
  public void register(String email, String password) {
  }

  @Audited(level = DEBUG)
  public void rename(String email, String newEmail) {
  }
}
// INFO  > register(email=ada@example.com)
// INFO  < register (elapsed 11ms)
// DEBUG > rename(email=ada@example.com,newEmail=ada@example.org)
// DEBUG < rename (elapsed 0ms)
```

### Excluded Names in Composed Annotations

A name excluded directly with `@MethodLogging(exclude = ...)` must match a parameter of the method, as explained on the
[Parameters](../user-guide/parameters.md#excluding-by-name) page. For names excluded by a composed annotation, this
check is skipped if parameter names are available. `@Audited` can therefore be placed on `rename`, which has no
`password` parameter. If parameter names are not available, the check applies to composed annotations as well, and
`@Audited` on `rename` fails the analysis, because `password` cannot be matched against `arg0` and `arg1`.

/// warning | Exclusion by name depends on parameter names
A composed annotation that hides a sensitive parameter by name protects it only if the class is compiled with parameter
names. For sensitive values, `@ParamLog(exclude = true)` or a composed parameter annotation is more robust.
///

## Composing @ParamLog

A composed parameter annotation is annotated with `@ParamLog`. The bookstore masks bank account numbers wherever they
appear:

```java title="Masked.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.ParamLog;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(PARAMETER)
@Retention(RUNTIME)
@ParamLog("****")
public @interface Masked {
}
// a @Masked parameter is logged as ****
```

The format `****` contains no parameter, so the value itself is never formatted. Unlike an excluded parameter, the
masked parameter still appears in the log, which shows that it was passed.

```java title="RefundService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RefundService
{
  @MethodLogging
  public void refund(long orderId, @Masked String iban,
                     BigDecimal amount) {
  }
}
// INFO  > refund(orderId=42,iban=****,amount=19.99):14
```

Attributes of the composed parameter annotation can override those of `@ParamLog` with `@AliasFor`, in the same way as
for `@MethodLogging`:

```java title="Bracketed.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(PARAMETER)
@Retention(RUNTIME)
@ParamLog(format = "[%{value}]", inline = false)
public @interface Bracketed
{
  @AliasFor(annotation = ParamLog.class, attribute = "name")
  String name() default "";
}
// logs the parameter separately, enclosed in brackets
```

A composed parameter annotation counts as a `@ParamLog` annotation in every respect. It is inherited from interface and
superclass methods like `@ParamLog`, and a parameter carrying it is not passed to
`MethodLoggingConfigurer.excludeMethodParameter`, as described on the
[Parameters](../user-guide/parameters.md#excluding-by-type) page.
