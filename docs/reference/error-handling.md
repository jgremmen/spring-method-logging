# Error Handling

Method logging distinguishes two kinds of errors. Configuration errors are mistakes in the annotations or in the
configurer, which are detected when a method is analyzed. Failures while logging occur during an invocation, for example
when a logging backend fails. The guiding principle is that configuration errors are reported as early as possible,
while failures of the logging itself never change the outcome of the logged method.

## Configuration Errors

The following errors are detected when the method logging configuration is created or when a method is analyzed. All of
them are reported as an `IllegalStateException` with a message that names the cause:

| Error                                                                 | Example message                                                                 |
|-----------------------------------------------------------------------|---------------------------------------------------------------------------------|
| a configurer default method returns `null` or `DEFAULT`               | `MethodLoggingConfigurer#defaultEntryExitLevel() must not return DEFAULT`       |
| an excluded parameter name matches no parameter                       | `Excluded parameter 'pasword' matches no parameter of public void ...login(...)` |
| a custom message format cannot be parsed                              | `Invalid resultFormat '%{result' for public java.lang.String ...`               |
| a strict factory finds no logger field                                | `Class class ...OrderService or one of its superclasses must provide a logger field` |
| the logger field type cannot hold a logger of the factory's framework | `Logger field 'log' of class ...WrongField must be of type org.slf4j.Logger`    |
| the logger field cannot be made accessible on the module path         | `Logger field 'log' of class ... is not accessible; package ... must be opened ...` |
| a custom `MethodLoggerFactory.validate` rejects the logger field      | defined by the factory                                                          |

The rules for excluded names are described on the [Parameters](../user-guide/parameters.md#excluding-by-name) page, the
formats on the [Message Formats](../user-guide/message-formats.md) page and the logger field checks on the [Logger
Fields and Frameworks](../user-guide/loggers.md) page.

Errors of the configurer bean itself are passed on as they are. If the application context contains two configurers and
none is marked with `@Primary`, Spring's `NoUniqueBeanDefinitionException` is raised. An exception thrown while the
configurer bean is created is passed on in the same way. A runtime exception thrown by one of the configurer methods,
such as `messageSupport()`, is passed on unchanged as well. Any other error is wrapped in an `IllegalStateException`
with the message `Method logging configurer failed`, or `Method logging configurer is not available` if obtaining the
configurer bean fails.

### When Configuration Errors Are Reported

Where the error surfaces depends on when the method is analyzed:

| Situation                                                            | Effect of a configuration error                                        |
|----------------------------------------------------------------------|------------------------------------------------------------------------|
| invalid configurer default or failing configurer bean, any mode      | the application context fails to start                                |
| method of a singleton bean in proxy mode                             | the application context fails to start                                |
| method of a prototype bean created during startup, proxy mode        | the application context fails to start                                |
| method of a bean created after startup, proxy mode                   | warning on the first invocation, method runs without logging           |
| any method in AspectJ mode                                           | warning on the first invocation, method runs without logging           |

When the application context fails to start, `refresh()` throws the `IllegalStateException` described above, and the
beans created so far are destroyed. The startup failure points directly to the method and the setting to correct.

When a warning is reported instead, the exception is logged at WARN level once per method and bean class, with the
message `Method logging analysis failed for method 'name'` and the exception as cause. The result of the analysis is
cached, so the analysis is not repeated: the method keeps running without logging until the application is restarted
with a corrected configuration. One case is not cached: a logged method invoked while the singleton beans are still
being created, for example from an initialization method of another bean, may find the configurer bean not yet
available. This is reported on every such invocation, in both modes, because the configurer may become available a
moment later.

/// tip | Test the configuration of lazy beans
Methods of lazy beans and of beans in AspectJ mode are only analyzed when they are used. A test that obtains and invokes
these beans detects configuration errors before production does.
///

## Failures While Logging

Once a method has been analyzed successfully, logging it can still fail. A method logger factory can throw an exception,
a logging backend can fail, or a parameter value cannot be formatted. Such failures are not passed on to the caller. The
result and the exceptions of the logged method are passed on unchanged in all cases.

A value that cannot be formatted, typically because its `toString()` method throws an exception, is logged as `<format
failed: ExceptionName>`, where the exception name is that of the most specific cause, without package. The remaining
messages are logged normally:

```java title="BrokenToString.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class BrokenToString
{
  @MethodLogging
  public Object describe() {
    return new Object() {
      @Override
      public String toString() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
// INFO  > describe:11
// DEBUG result = <format failed: UnsupportedOperationException>
// INFO  < describe:11
```

All other failures are reported through Commons Logging, with the logger name
`de.sayayi.lib.methodlogging.internal.MethodLoggingInterceptor`. The report names the phase that failed and the method:

| Phase           | Failure                                                     | Consequence for the invocation                 |
|-----------------|-------------------------------------------------------------|------------------------------------------------|
| `logger lookup` | the method logger factory throws an exception               | the method is invoked without logging          |
| `entry`         | logging the entry message or the separate parameters fails  | the method is invoked, result and exit are still logged |
| `result`        | logging the result message fails                            | the exit is still logged                       |
| `exit`          | logging the exit message fails                              | none                                           |

The first failure of each phase for a method is reported at WARN level, with the message `Method logging (entry) failed
for method 'name'; subsequent failures are reported at DEBUG level`. Later failures of the same phase for that method
are reported at DEBUG level, so a permanently failing backend does not flood the log.

This also applies to a `StackOverflowError`, for example from objects whose `toString()` methods refer to each other,
and to linkage errors from a logging framework that is not visible to the library. Only errors from which the virtual
machine cannot recover, such as an `OutOfMemoryError`, are passed on to the caller.

/// warning | Formatting is not free of side effects
Method logging catches exceptions raised while formatting, but it cannot undo what the formatting did. Evaluated
suppliers, consumed iterables and an `OutOfMemoryError` from formatting a huge collection do affect the logged method.
The [Parameters](../user-guide/parameters.md#side-effects-of-formatting) page explains how to avoid this.
///

## Reacting to Errors

A configuration error that stops the application context is corrected in the code: the misspelled name, the invalid
format or the configurer default named in the message. A warning about a failed analysis is treated the same way, since
the method remains unlogged until the application is restarted.

Failures while logging point to a problem of the logging setup or of a value's `toString()` method. The category
`de.sayayi.lib.methodlogging.internal.MethodLoggingInterceptor` is kept at least at WARN level in the logging
configuration, so these reports are visible. Setting it to DEBUG shows every repeated failure while a problem is
investigated.

A custom [method logger](../developer-guide/custom-loggers.md) cannot use exceptions to signal anything to the logged
method, as they are caught. It reports problems through its own logging or returns `MethodLogger.NO_OP` from the factory
to disable logging.
