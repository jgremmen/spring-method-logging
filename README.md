## Spring Method Logging Extension

[![License](https://img.shields.io/github/license/jgremmen/spring-method-logging)](https://github.com/jgremmen/spring-method-logging/blob/master/LICENSE)

A lightweight extension for the Spring Framework that adds automatic method entry, exit, parameter and result logging
to Spring-managed beans. All it takes is a couple of annotations — no boilerplate logging code required.

### Features

- **Method entry/exit logging** - automatically logs when a method is entered and exited
- **Parameter logging** - logs method parameter names and values, either inline or on separate lines
- **Result logging** - logs the return value of a method
- **Elapsed time** - optionally logs how long a method took to execute (until it returns; for async or reactive
  results this excludes the time until the result is completed)
- **Line numbers** - optionally includes the source line number in log messages
- **Exception logging** - logs exception type and message when a method exits with an error
- **Multiple logging frameworks** - supports SLF4J, Log4j2, Logback, JUL, JBoss Logging, Tinylog
  and JCL (Java Commons Logging) out of the box
- **Customizable** - log levels, message formats, parameter visibility and more can be configured
  globally, per class or per method

### Maven coordinates

```xml
<dependency>
  <groupId>de.sayayi.lib</groupId>
  <artifactId>spring-method-logging</artifactId>
  <version>0.5.0</version>
</dependency>
```

### Requirements

- Java 21 or later
- Spring Framework 5.3, 6 or 7

Parameter names are logged as they are discovered by Spring. Compile your classes with `-parameters`, as Spring
Framework 6.1 and later no longer read parameter names from the debug information. Otherwise, synthesized names
(`arg0`, `arg1`, …) are logged, unless a name is given with `@ParamLog(name = …)`. Without parameter names, a name
excluded with `@MethodLogging(exclude = …)` which matches no parameter fails the method analysis, so the parameter is
not logged unnoticed.

### Getting started

Enable the method logging functionality by adding `@EnableMethodLogging` to a Spring configuration class:

```java
@Configuration
@EnableMethodLogging
public class LetsDoSomeLoggingConfiguration {
}
```

Any bean method annotated with `@MethodLogging` will now have its entry and exit logged automatically:

```java
@Component
public class MyBean
{
  @MethodLogging(lineNumber = HIDE)
  public String test(String name) {
    return name;
  }
}
```

Invoking the `test` method will produce the following log output:

```
> test(name=Hello World)
result = Hello World
< test
```

### Class-level defaults

Use `@MethodLoggingConfig` on a class to define defaults for all `@MethodLogging`-annotated methods in that class.
Individual methods can still override any of these settings. Besides the settings of `@MethodLogging` (log levels,
visibility of line number, elapsed time, parameters and result, message formats and logger field name),
`@MethodLoggingConfig` defines the prefixes of the method entry and exit messages.

### Parameter control

The `@ParamLog` annotation can be placed on individual method parameters to control how they appear in the log output.
It allows you to set a custom format, change the display name or log the parameter on a separate line instead of inline.

Formats use the message syntax of the [message-format](https://central.sonatype.com/artifact/de.sayayi.lib/message-format)
library, which is also used to format the log messages. The parameter value is available as `%{value}`, which is the
default format.

Parameters can be excluded from logging, e.g. if their value has no meaningful string representation, produces far
too much output (like large collections or binary data) or contains sensitive information such as passwords or tokens.
Use `@ParamLog(exclude = true)` on the parameter, or exclude parameters by name with the `exclude` attribute of
`@MethodLogging`. `@ParamLog(exclude = true)` is preferred, as it does not depend on parameter names being available,
which is particularly important for sensitive parameters. Parameters can also be excluded by type with
`MethodLoggingConfigurer.excludeMethodParameter(ResolvableType)`.

### Using a logger instance

The basic configuration uses the JCL logger (also used internally by the Spring Framework), which in turn delegates to 
another logging framework such as Log4j or SLF4J. Method logging can instead be configured to use a logger field 
provided by the bean that contains the methods to be logged.

A typical scenario for Log4j2 looks like this:

```java
public class MyBean 
{
  // log4j logger
  private static final Logger logger = LogManager.getLogger(MyBean.class);

  @MethodLogging
  public void test() 
  {
    logger.info("I'm busy testing...");
    ...
    logger.info("not done yet");
    ...
    logger.info("I'm done");
  }
}
```

To use the `logger` field, configure the method logger factory and the field name by implementing
`MethodLoggingConfigurer`:

```java
@Configuration
@EnableMethodLogging
public class LetsDoSomeLoggingConfiguration implements MethodLoggingConfigurer 
{
   public MethodLoggerFactory methodLoggerFactory() {
     return Log4j2LoggerFactory.withFallback();
   }

   public String defaultLoggerFieldName() {
     return "logger";
   }
}
```

This way, the method entry/exit messages and your application log messages all go through the same logger, keeping the
output consistent.

### Java modules

On the module path, method logging reads logger fields by reflection. Packages containing beans with a logger field
must therefore be opened to `de.sayayi.lib.methodlogging`, in addition to the Spring modules:

```java
module com.example.app
{
  requires de.sayayi.lib.methodlogging;

  opens com.example.service to spring.aop, spring.beans, spring.context, spring.core, de.sayayi.lib.methodlogging;
}
```

If such a package is not opened, creating the bean fails with an `IllegalStateException` that names the package.

With Spring Framework 7, logging uses the `org.apache.commons.logging` module, which Spring requires on the module path
as well. As it is not required by the (automatic) Spring modules, it must be added to the module graph, for example with
`requires org.apache.commons.logging` in the application module or `--add-modules org.apache.commons.logging`.

### License

This project is licensed under the [Apache License 2.0](LICENSE).
