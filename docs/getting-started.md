# Getting Started

This page adds Spring Method Logging to a project and logs a first method. It continues the bookstore example introduced
on the [home page](index.md).

## Dependencies

The library is published to Maven Central under the coordinates `de.sayayi.lib:spring-method-logging`.

=== "Gradle (Groovy)"

    ```groovy title="build.gradle"
    // adds Spring Method Logging and its required dependencies
    dependencies {
      implementation 'de.sayayi.lib:spring-method-logging:0.6.0'
    }
    ```

=== "Gradle (Kotlin)"

    ```kotlin title="build.gradle.kts"
    // adds Spring Method Logging and its required dependencies
    dependencies {
        implementation("de.sayayi.lib:spring-method-logging:0.6.0")
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <!-- adds Spring Method Logging and its required dependencies -->
    <dependency>
      <groupId>de.sayayi.lib</groupId>
      <artifactId>spring-method-logging</artifactId>
      <version>0.6.0</version>
    </dependency>
    ```

The library requires Java 21 or later. It depends on `spring-context` and on `message-format`, the library used for
formatting the log messages. Both are compile dependencies and are added to the project transitively. The supported
versions are:

| Dependency                           | Supported versions                     |
|--------------------------------------|----------------------------------------|
| `org.springframework:spring-context` | 5.3 or later, before 8.0 (5.3, 6 and 7) |
| `de.sayayi.lib:message-format`       | 0.23.x                                 |

The logging frameworks and the AspectJ runtime are optional dependencies. They are not added transitively, as an
application uses only one or two of them. The application declares the one it uses itself:

| Optional dependency                  | Supported versions          | Used for                                                |
|--------------------------------------|-----------------------------|---------------------------------------------------------|
| `org.slf4j:slf4j-api`                | 1.7.0 or later, before 2.1  | SLF4J logger fields                                     |
| `ch.qos.logback:logback-classic`     | 1.3, 1.4 and 1.5            | Logback logger fields                                   |
| `org.apache.logging.log4j:log4j-api` | 2.13.0 or later, before 3   | Log4j2 logger fields                                    |
| `org.jboss.logging:jboss-logging`    | 3.3.2 or later, before 4    | JBoss Logging logger fields                             |
| `org.tinylog:tinylog-api`            | 2.0 to 2.8                  | Tinylog loggers                                         |
| `org.aspectj:aspectjrt`              | 1.9.21.1 or later, before 2 | [AspectJ mode](user-guide/advice-modes.md#aspectj-mode) |

Spring Framework 7 requires Log4j API 2.15.0 or later when Log4j2 is used.

Java Util Logging and the JDK platform logger are part of the JDK. Apache Commons Logging is provided by Spring itself,
through `spring-jcl` in Spring Framework 5.3 and 6 and through `commons-logging` in Spring Framework 7.

### Compiling with Parameter Names

The entry message contains the names of the parameters. Spring discovers these names from the class files, which contain
them only if the classes are compiled with the `-parameters` option of `javac`. Spring Framework 5.3 and 6.0 also read
the names from the debug information, but Spring Framework 6.1 and later no longer do. Without parameter names, the
parameters are logged with the synthesized names `arg0`, `arg1` and so on.

=== "Gradle (Groovy)"

    ```groovy title="build.gradle"
    // compiles all classes with parameter names
    tasks.withType(JavaCompile).configureEach {
      options.compilerArgs << '-parameters'
    }
    ```

=== "Gradle (Kotlin)"

    ```kotlin title="build.gradle.kts"
    // compiles all classes with parameter names
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <!-- compiles all classes with parameter names -->
    <properties>
      <maven.compiler.parameters>true</maven.compiler.parameters>
    </properties>
    ```

A parameter can also be named explicitly with `@ParamLog(name = ...)`, as described on the
[Parameters](user-guide/parameters.md#parameter-names) page.

### Module Path

Applications running on the module path read the module `de.sayayi.lib.methodlogging`:

```java title="module-info.java"
module com.example.bookstore
{
  requires de.sayayi.lib.methodlogging;

  opens com.example.bookstore to spring.core, spring.beans,
      spring.context, spring.aop, de.sayayi.lib.methodlogging;
}
// the application module reads method logging and opens its beans
```

The `opens` directive allows Spring to create the beans and proxies and allows method logging to read logger fields. The
[Java Modules](user-guide/java-modules.md) page explains which packages have to be opened and what changes with Spring
Framework 7.

## A First Logged Method

Three steps are needed to log a method: method logging is enabled in the application context, the method is annotated
with `@MethodLogging`, and the method is invoked on the Spring bean.

### Step 1: Enable Method Logging

`@EnableMethodLogging` is placed on a `@Configuration` class. It registers the infrastructure that intercepts the
annotated methods. The annotation is needed once per application context.

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@EnableMethodLogging
public class ShopConfiguration {
}
// enables method logging for all beans of the application context
```

In a Spring Boot application, the annotation is placed on the application class or on any other configuration class.

### Step 2: Annotate the Method

The service of the bookstore places orders. Its `placeOrder` method is annotated with `@MethodLogging`:

```java title="OrderService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class OrderService
{
  @MethodLogging
  public long placeOrder(Order order) {
    return order.id();
  }
}
// placeOrder is logged whenever it is invoked through the Spring bean
```

The `Order` record is the one shown on the [home page](index.md#the-problem).

### Step 3: Invoke the Method

The method is logged when it is invoked on the bean obtained from the application context:

```java title="ShopApplication.java"
package com.example.bookstore;

import org.springframework.context.annotation.*;

public class ShopApplication
{
  public static void main(String[] args)
  {
    try (var context =
             new AnnotationConfigApplicationContext(
                 ShopConfiguration.class)) {
      var orderService = context.getBean(OrderService.class);
      orderService.placeOrder(new Order(42, "0441013597", 2));
    }
  }
}
// INFO  > placeOrder(order=Order[id=42, isbn=0441013597, quantity=2]):11
// DEBUG result = 42
// INFO  < placeOrder:11
```

The first message is the entry message. It starts with the entry prefix `> `, followed by the method name, the
parameters in parentheses and the source line of the method. The second message contains the return value. The third
message is the exit message, which starts with the exit prefix `< `.

The levels differ: the entry and exit messages are logged at INFO level, the parameters and the result at DEBUG level.
If the logger only enables INFO, the parameters and the result are omitted:

```java title="Output with DEBUG disabled"
orderService.placeOrder(new Order(42, "0441013597", 2));
// INFO  > placeOrder:11
// INFO  < placeOrder:11
```

## Where the Messages Go

`OrderService` has no logger field, so the messages are written to an Apache Commons Logging logger named after the
class, `com.example.bookstore.OrderService`. Spring forwards Commons Logging to Log4j2, SLF4J or Java Util Logging,
depending on which of them is present. The levels of the messages are therefore configured for the category
`com.example.bookstore.OrderService` in the configuration of that logging framework.

If the class has a logger field named `log`, method logging uses that logger instead:

```java title="OrderService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderService
{
  private static final Logger log =
      LoggerFactory.getLogger(OrderService.class);

  @MethodLogging
  public long placeOrder(Order order)
  {
    log.info("storing order {}", order.id());
    return order.id();
  }
}
// INFO  > placeOrder(order=Order[id=42, isbn=0441013597, quantity=2]):17
// INFO  storing order 42
// DEBUG result = 42
// INFO  < placeOrder:17
```

The messages of method logging and the regular log statement of the method now share one logger, so they appear together
and are controlled by the same level configuration. The [Logger Fields and Frameworks](user-guide/loggers.md) page
describes the lookup in detail.

## Next Steps

The [Logging Methods](user-guide/logging-methods.md) page explains each logged message and the options controlling it.
The [Parameters](user-guide/parameters.md) page shows how to exclude sensitive parameters, which is recommended before
method logging is used in production.

/// danger | Sensitive values are logged by default
All parameters and results are logged unless they are excluded. A method receiving a password, a token or personal data
writes that value to the log as soon as it is annotated. The [Parameters](user-guide/parameters.md#excluding-parameters)
page shows how to exclude such values.
///
