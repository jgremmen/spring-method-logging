# Troubleshooting

This page lists problems that commonly occur when method logging is introduced, together with their causes and
solutions. Each section starts with the observed symptom.

## Nothing Is Logged

**The method is called from within the same bean.** In proxy mode, only calls through the Spring proxy are logged. A
call from one method of a bean to another method of the same bean, a call of a private or static method, and a call of a
final method of a CGLIB proxy bypass the interceptor. The [Proxy and AspectJ Mode](../user-guide/advice-modes.md) page
describes the alternatives.

**The object is not a Spring bean.** In proxy mode, an object created with `new` is not proxied. Only beans obtained
from the application context, or injected by it, are logged.

**The entry/exit level is not enabled.** Entry and exit messages are logged at INFO level by default. If the logger of
the class is set to WARN, nothing is logged, including the parameters and the result. The level is configured for the
logger name of the class in the configuration of the logging framework.

**The logger field holds `null`.** A logger field containing `null` disables logging for the method, with all factories
except the Tinylog factory. Apart from the Tinylog factory, a framework-specific factory also disables logging if the
field holds a logger of another framework.

**Commons Logging forwards to a framework without a backend.** A class without logger field is logged through Commons
Logging. Spring's Commons Logging bridge prefers the Log4j2 API if it is on the class path, even if the application logs
with SLF4J and Logback. If `log4j-api` is present without a Log4j2 implementation, typically as a transitive dependency,
the messages are lost. Adding a logger field to the class, or adding the bridge `log4j-to-slf4j`, solves the problem.

**The AspectJ aspect is not woven.** In AspectJ mode, nothing is logged if the class was not woven at compile time or if
the JVM was started without the weaver agent. The application context must also have started, as the aspect logs only
once it has been configured.

**The method was analyzed with an error.** A configuration error found after startup is reported once as a warning, and
the method is executed without logging from then on. The log contains a message
`Method logging analysis failed for method 'class#name'`, as described on the
[Error Handling](error-handling.md#when-configuration-errors-are-reported) page.

## Parameters Are Missing in the Entry Message

The entry message shows the inline parameters only if the parameter level is enabled, which is DEBUG by default. With
the logger at INFO level, the entry message reads `> placeOrder:11` instead of `> placeOrder(order=...):11`. Either
DEBUG is enabled for the class, or the parameter level is set to INFO with `parameterLevel = INFO`. The interaction of
the levels is explained on the [Logging Methods](../user-guide/logging-methods.md#log-levels) page.

## Parameters Are Named arg0, arg1

The classes are compiled without parameter names. With Spring Framework 6.1 and later, the compiler option `-parameters`
is required; the [Getting Started](../getting-started.md#compiling-with-parameter-names) page shows the build
configuration. A name can also be given explicitly with `@ParamLog(name = ...)`.

## NoClassDefFoundError for a Logging Framework with Gradle

The application uses a logging framework, such as SLF4J or Log4j2, without declaring it as a dependency. The Gradle
module metadata of method logging puts the optional logging frameworks on the compile classpath of the application, but
not on its runtime classpath, so the build succeeds and the missing class is only noticed at runtime. The framework is
declared as an `implementation` dependency of the application, as listed on the
[Getting Started](../getting-started.md#dependencies) page.

## The Application Context Fails with "Excluded parameter ... matches no parameter"

A name listed in `@MethodLogging(exclude = ...)` matches no parameter. The name is misspelled, the parameter was
renamed, or parameter names are not available, in which case the message says so. The name is corrected, or the
parameter is excluded with `@ParamLog(exclude = true)`, which does not depend on names.

## A Warning "contains an unsupported logger of type ..."

The logger field holds an object that the `AutoDetectLoggerFactory` does not recognize, such as a custom logging facade.
The warning is logged once per logger field under the logger name
`de.sayayi.lib.methodlogging.logger.AutoDetectLoggerFactory`. The method is logged through a Commons Logging logger
instead. A [custom method logger
factory](../developer-guide/custom-loggers.md) connects the facade. If the field merely has the default name `log` but
is not meant to be used for method logging, another logger field name is configured.

The same warning with "an inaccessible logger" means that the logger belongs to a framework that is loaded by a class
loader the library cannot see.

## The Log Shows a Location Inside the Library

Patterns such as `%C`, `%M` and `%L` print a class and line of the library instead of the logged method. Only the
method loggers for Log4j2, Logback and Java Util Logging pass the location of the logged method to the framework. With
the `AutoDetectLoggerFactory`, a class needs a logger field of one of these frameworks, as classes without logger field
are logged through Commons Logging, which does not support locations. A framework-specific factory created with
`withFallback()` uses a logger of its framework for such classes instead. The
[Source Locations](../user-guide/loggers.md#source-locations) section has the complete list.

## Numbers and Dates Look Different on Another Server

The default message support formats numbers and dates according to the default locale of the JVM. A server with a German
locale logs `1.234,5` where a server with a US locale logs `1,234.5`. A message support with a fixed locale, as shown on
the [Message Formats](../user-guide/message-formats.md#custom-message-support) page, produces consistent output.

## A Supplier Is Called Twice or a Stream Is Empty

The default message support evaluates suppliers and iterates iterables when it formats them. A supplier parameter is
therefore called once by method logging and once by the method, and a parameter that can be iterated only once is
already consumed when the method receives it. The parameter is excluded, or a message support without these formatters
is used, as described on the [Parameters](../user-guide/parameters.md#side-effects-of-formatting) page.

## IllegalStateException "... is not accessible; package ... must be opened"

On the module path, the package of the class with the logger field is not opened to `de.sayayi.lib.methodlogging`. The
`opens` directive of the application module is extended, as shown on the [Java
Modules](../user-guide/java-modules.md#opening-packages) page.

## Commons Logging Is Missing on the Module Path with Spring Framework 7

With Spring Framework 7 on the module path, the module `org.apache.commons.logging` is not resolved automatically. It is
added with `requires org.apache.commons.logging` or `--add-modules org.apache.commons.logging`, see [Java
Modules](../user-guide/java-modules.md#spring-framework-7).

## A Logged Method Called by the Configurer Is Not Logged

Methods invoked while the configurer bean is being created are not logged, as explained on the
[Configuration](../user-guide/configuration.md#when-the-configurer-is-created) page. Once the configurer exists, the
same methods are logged normally.
