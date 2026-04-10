## Spring Method Logging Extension

[![License](https://img.shields.io/github/license/jgremmen/spring-method-logging)](https://github.com/jgremmen/spring-method-logging/blob/master/LICENSE)

A lightweight extension for the Spring Framework that adds automatic method entry, exit, parameter and result logging
to Spring-managed beans. All it takes is a couple of annotations — no boilerplate logging code required.

### Features

- **Method entry/exit logging** - automatically logs when a method is entered and exited
- **Parameter logging** - logs method parameter names and values, either inline or on separate lines
- **Result logging** - logs the return value of a method
- **Elapsed time** - optionally logs how long a method took to execute
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
Individual methods can still override any of these settings.

### Parameter control

The `@ParamLog` annotation can be placed on individual method parameters to control how they appear in the log output.
It allows you to set a custom format, change the display name or log the parameter on a separate line instead of inline.

Parameters can also be excluded from logging by name using the `exclude` attribute on `@MethodLogging`.

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
     return new Log4j2LoggerFactory(true);
   }

   public String defaultLoggerFieldName() {
     return "logger";
   }
}
```

This way, the method entry/exit messages and your application log messages all go through the same logger, keeping the
output consistent.

### License

This project is licensed under the [Apache License 2.0](LICENSE).
