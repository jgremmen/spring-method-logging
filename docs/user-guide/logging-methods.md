# Logging Methods

A method annotated with `@MethodLogging` produces up to four kinds of messages for each invocation: the entry message,
the separately logged parameters, the result message and the exit message. This page explains each message, the options
that control it and the log levels that decide whether it is written.

## The Messages of an Invocation

The following service of the bookstore is used throughout this page. It is a Spring bean, so its annotated methods are
logged when they are invoked through the bean.

```java title="OrderService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.springframework.stereotype.Service;

import java.util.List;

import static de.sayayi.lib.methodlogging.Visibility.SHOW;

@Service
public class OrderService
{
  @MethodLogging
  public long placeOrder(Order order) {
    return order.id();
  }

  @MethodLogging
  public void cancelOrder(long orderId, String reason) {
    throw new IllegalStateException("order " + orderId + " shipped");
  }

  @MethodLogging(elapsedTime = SHOW)
  public List<String> findTitles(String customer,
                                 @ParamLog(inline = false) int limit)
  {
    return List.of("Dune", "Emma");
  }
}
// defines three logged methods with different outcomes
```

Invoking the three methods produces the following messages. The log level is shown in front of each message.

```java title="Invoking the methods"
orderService.placeOrder(new Order(42, "0441013597", 2));
// INFO  > placeOrder(order=Order[id=42, isbn=0441013597, quantity=2]):16
// DEBUG result = 42
// INFO  < placeOrder:16

orderService.cancelOrder(42, "customer request");
// INFO  > cancelOrder(orderId=42,reason=customer request):21
// INFO  < cancelOrder:21 -> IllegalStateException(order 42 shipped)

orderService.findTitles("Ada", 10);
// INFO  > findTitles(customer=Ada):28
// DEBUG parameter 'limit' = 10
// DEBUG result = [Dune, Emma]
// INFO  < findTitles:28 (elapsed 1ms)
```

### Entry Message

The entry message is logged before the method is invoked. It consists of the entry prefix, the method name, the inline
parameters in parentheses and the line number, separated from the method name by a colon. The parameters are separated
by a comma without a space. A method without parameters, or whose parameters are all excluded or logged separately, has
no parentheses.

### Separately Logged Parameters

A parameter annotated with `@ParamLog(inline = false)` is not part of the entry message. It is logged as a message of
its own, directly after the entry message, as shown for `limit` above. This keeps the entry message short for parameters
with long values. The [Parameters](parameters.md#inline-and-separate-parameters) page covers this in detail.

### Result Message

The result message is logged after the method has returned and before the exit message. It is not logged for `void`
methods and not logged if the method throws an exception. A `null` result is formatted as an empty value, so the message
reads `result =` in that case, as surrounding spaces of a message are trimmed.

### Exit Message

The exit message is logged when the method has returned or thrown an exception. It consists of the exit prefix, the
method name and the line number, optionally followed by the elapsed time in parentheses. If the method throws an
exception, the message ends with an arrow, the name of the exception class and the exception message in parentheses.

The exception class is shown without its package. A nested exception class includes its enclosing classes, for example
`OrderService.OutOfStockException`. If the exception has no message, the parentheses are omitted. The exception itself
is passed on to the caller unchanged; method logging only reports it.

## Log Levels

Each message has a log level. Method logging knows three levels, defined by the enum [`Level`][jd-level]: `TRACE`,
`DEBUG` and `INFO`. The fourth constant, `DEFAULT`, is not a level. It tells method logging to take the level from the
class-level or global configuration.

Three level settings exist. The `entryExitLevel` applies to the entry and exit messages, the `parameterLevel` applies to
the parameters, and the `resultLevel` applies to the result message. Without further configuration, the entry/exit level
is `INFO` and the other two are `DEBUG`.

The levels interact in the following way:

1. If the logger does not enable the entry/exit level, nothing at all is logged for the invocation. Parameters and the
   result are not formatted either, regardless of their levels.
2. If the logger does not enable the parameter level, the entry message is logged without the inline parameters and the
   separately logged parameters are omitted. The inline parameters are part of the entry message and therefore written
   at the entry/exit level, but they are only included if the parameter level is enabled as well.
3. If the logger does not enable the result level, the result message is omitted.

The first rule makes method logging cheap for disabled loggers: the only work done is the check of the level. The second
rule allows a production configuration to log the method calls at INFO level without exposing the parameter values,
which are only shown when DEBUG is enabled for the class.

The levels are set per method with the attributes of `@MethodLogging`:

```java title="AuditService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

import static de.sayayi.lib.methodlogging.Level.DEBUG;
import static de.sayayi.lib.methodlogging.Level.INFO;
import static de.sayayi.lib.methodlogging.Level.TRACE;

@Service
public class AuditService
{
  @MethodLogging(entryExitLevel = DEBUG, parameterLevel = TRACE,
                 resultLevel = INFO)
  public int countOrders(String customer) {
    return 3;
  }
}
// with DEBUG enabled:  > countOrders:16, result = 3, < countOrders:16
// with TRACE enabled:  > countOrders(customer=Ada):16, ...
// with INFO only:      nothing is logged
```

The example also shows a consequence of the first rule: the result level `INFO` is higher than the entry/exit level
`DEBUG`, but the result is still only logged if `DEBUG` is enabled. A result level above the entry/exit level only
changes the level the result message is written at; it never makes the result appear without the entry and exit
messages.

Class-level defaults and global defaults for the levels are described on the [Configuration](configuration.md) page.

/// note | Mapping to the logging framework
The levels are mapped to the levels of the same name of the logging framework. Java Util Logging has no TRACE and DEBUG
levels, so `TRACE`, `DEBUG` and `INFO` are mapped to `FINEST`, `FINE` and `INFO` there. Method logging never logs at
WARN or ERROR level.
///

## Line Numbers

The line number in the entry and exit messages helps to find the method in the source code. It is taken from the class
file and is the line of the first statement in the method body, not the line of the method declaration. For `placeOrder`
above, it is the line of the `return` statement.

The line number is shown by default. It is hidden with `lineNumber = HIDE`:

```java title="Hiding the line number"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;

@Service
public class ShippingService
{
  @MethodLogging(lineNumber = HIDE)
  public String ship(long orderId) {
    return "DHL-" + orderId;
  }
}
// INFO  > ship(orderId=42)
// DEBUG result = DHL-42
// INFO  < ship
```

If the class file contains no line number information, or cannot be read, the line number is omitted. The
[`Visibility`][jd-visibility] enum used for this attribute has the constants `SHOW`, `HIDE` and `DEFAULT`, where
`DEFAULT` again takes the setting from the class-level or global configuration.

## Elapsed Time

With `elapsedTime = SHOW`, the exit message includes the time the invocation took, as shown for `findTitles` above. The
elapsed time is hidden by default. It is formatted with the largest units that apply, omitting parts that are not
significant:

| Duration                  | Logged as |
|---------------------------|-----------|
| 0.4 milliseconds          | `0ms`     |
| 850 milliseconds          | `850ms`   |
| 2 seconds                 | `2s`      |
| 2 seconds, 15 ms          | `2s15ms`  |
| 1 minute, 5 seconds       | `1m5s`    |
| 1 minute, 0 seconds, 5 ms | `1m`      |
| 1 hour, 29 seconds        | `1h`      |
| 1 hour, 30 seconds        | `1h1m`    |

Durations below one second are shown in milliseconds, including `0ms`. Durations below one minute show the seconds,
followed by the milliseconds unless these are zero. Durations from one minute show minutes and seconds, omitting zero
seconds. Durations from one hour show hours and minutes, rounded to the nearest minute.

The time is measured with a monotonic clock, so adjustments of the system clock do not affect it. The measurement starts
before the entry message is logged and ends before the exit message is logged. It therefore includes the formatting of
the parameters and the result, which is usually negligible compared to the method itself.

/// note | Asynchronous results
The elapsed time ends when the method returns. For a method returning a `CompletableFuture`, a Reactor `Mono` or another
asynchronous result, it does not include the time until the result is completed.
///

## Which Methods Are Logged

Whether an annotated method is intercepted depends on the advice mode. In the default proxy mode, only calls through the
Spring proxy are logged. A call from one method of a bean to another method of the same bean bypasses the proxy and is
not logged. Private and static methods cannot be intercepted, nor can final methods of beans proxied by subclassing
(CGLIB). The AspectJ mode logs calls within the same bean as well as private and final methods, but requires weaving.
Static methods are not logged in either mode. The [Proxy and AspectJ Mode](advice-modes.md) page compares both modes.

In proxy mode, `@MethodLogging` is also found on the method of an interface or superclass that the bean method
implements or overrides. The annotation can therefore be placed on a service interface, so all implementations are
logged:

```java title="Inventory.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;

public interface Inventory
{
  @MethodLogging
  int reserve(String isbn, int quantity);
}
// every implementation of reserve is logged in proxy mode
```

```java title="WarehouseInventory.java"
package com.example.bookstore;

import org.springframework.stereotype.Component;

@Component
public class WarehouseInventory implements Inventory
{
  @Override
  public int reserve(String isbn, int quantity) {
    return quantity;
  }
}
// INFO  > reserve(isbn=0441013597,quantity=2):10
// DEBUG result = 2
// INFO  < reserve:10
```

The line number, the logger field and the parameter names are taken from the implementing class, as this is the code
that is executed.

[jd-level]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/Level.html
[jd-visibility]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/Visibility.html
