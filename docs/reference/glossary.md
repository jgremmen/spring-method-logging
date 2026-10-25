# Glossary

This glossary defines the terms used throughout the manual. Where a term corresponds to a type or attribute of the API,
the name is given in parentheses.

**Advice mode** (`@EnableMethodLogging(mode)`) is the technique used to intercept logged methods, either the proxy mode
or the AspectJ mode. See [Proxy and AspectJ Mode](../user-guide/advice-modes.md).

**AspectJ mode** (`AdviceMode.ASPECTJ`) is the advice mode that weaves the `MethodLoggingAspect` into the classes, at
compile time or at load time.

**Compile-time weaving** is the weaving of the `MethodLoggingAspect` into the classes by the AspectJ compiler `ajc`,
with the method logging JAR on its aspect path. See [AspectJ Mode](../user-guide/advice-modes.md#aspectj-mode).

A **composed annotation** is an annotation that is meta-annotated with `@MethodLogging` or `@ParamLog` and presets some
of their attributes. See [Composed Annotations](../developer-guide/composed-annotations.md).

The **configurer** (`MethodLoggingConfigurer`) is the bean that provides the global defaults, the message support and
the method logger factory. See [Global Configuration](../user-guide/configuration.md#global-configuration).

The **entry message** is the message logged before a method is invoked. It contains the entry prefix, the method name,
the inline parameters and the line number.

The **elapsed time** (`elapsedTime`) is the duration of an invocation, shown at the end of the exit message if it is
enabled. It is hidden by default.

The **entry prefix** (`methodEntryPrefix`) is the text at the start of the entry message, `> ` by default.

The **entry/exit level** (`entryExitLevel`) is the log level of the entry and exit messages. If it is not enabled,
nothing is logged for an invocation.

The **exit message** is the message logged after a method has returned or thrown an exception. It contains the exit
prefix, the method name, the line number, the elapsed time and the exception, if any.

The **exit prefix** (`methodExitPrefix`) is the text at the start of the exit message, `< ` by default.

The **fallback logger** is the logger used for a class without logger field. It is a Commons Logging logger with the
`AutoDetectLoggerFactory`, or a logger of the respective framework with a factory created by `withFallback()`.

An **inline parameter** is a parameter logged as part of the entry message, which is the default (`@ParamLog(inline =
true)`).

A **location-aware method logger** passes the source location of the logged method to the logging framework, so location
patterns show the logged method. See [Source Locations](../user-guide/loggers.md#source-locations).

**Load-time weaving** is the weaving of the `MethodLoggingAspect` into the classes while the JVM loads them, performed
by the AspectJ weaver running as a Java agent. See [AspectJ Mode](../user-guide/advice-modes.md#aspectj-mode).

A **logged method** is a method annotated with `@MethodLogging`, directly or through a composed annotation.

The **logger field** (`loggerFieldName`) is a field of the bean class or one of its superclasses that holds the logger
used for method logging. Its name is `log` by default.

A **message format** is a text with embedded parameters, such as `result = %{result}`, from which the message support
produces a message. See [Message Formats](../user-guide/message-formats.md).

The **message support** (`MessageSupport`) is the component of the message-format library that parses and formats all
messages of method logging. See [Message Formats](../user-guide/message-formats.md).

The **method analysis** is the one-time evaluation of a logged method. It resolves the settings, parameter names,
formats and logger field of the method and detects configuration errors.

A **method logger** (`MethodLogger`) is the adapter that writes the messages of method logging to a logging framework.

The **method logger factory** (`MethodLoggerFactory`) provides the method logger for each invocation. See [Logger Fields
and Frameworks](../user-guide/loggers.md).

The **parameter level** (`parameterLevel`) is the log level of the separately logged parameters. The inline parameters
are only included in the entry message if this level is enabled as well.

The **parameter value format** (`@ParamLog(format)`) is the message format applied to the value of a single parameter,
with the variable `value`.

A **post formatter** is a message format construct written `%(name,'message',...)`, which transforms the result of a
nested message. The post formatter `clip`, for example, limits its length.

**Proxy mode** (`AdviceMode.PROXY`) is the default advice mode, in which Spring wraps beans with logged methods in
proxies.

The **result level** (`resultLevel`) is the log level of the result message.

The **result message** contains the return value of a method and is logged before the exit message.

A **separately logged parameter** is a parameter logged in a message of its own after the entry message
(`@ParamLog(inline = false)`).

The **source location** of a logged method consists of its class, its name, its source file and the line of its first
statement. It is passed to location-aware method loggers.

A **strict factory** is a framework-specific method logger factory created with `strict()`, which requires a logger
field.

**Weaving** is the insertion of advice into the compiled code of a class, which the AspectJ mode uses instead of
proxies. It takes place as compile-time weaving or as load-time weaving.
