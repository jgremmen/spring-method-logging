# Proxy and AspectJ Mode

Method logging has to intercept the call of an annotated method. Spring offers two techniques for this, called advice
modes: proxies, which wrap a bean in an object that intercepts calls from outside, and AspectJ weaving, which inserts
the interception into the compiled code of the class. The mode is selected with the `mode` attribute of
`@EnableMethodLogging`. This page explains both modes and their differences.

## Proxy Mode

The proxy mode is the default. When Spring creates a bean that has at least one method annotated with `@MethodLogging`,
it returns a proxy instead of the bean itself. Other beans receive the proxy when the bean is injected, and each call
through the proxy passes the method logging interceptor before it reaches the bean.

Spring creates an interface-based JDK proxy if the bean implements interfaces, and a subclass-based CGLIB proxy
otherwise. With `@EnableMethodLogging(proxyTargetClass = true)`, all proxies are created by subclassing, as described on
the [Configuration](configuration.md#enabling-method-logging) page. No additional dependencies or build steps are
required.

### Self-Invocation

A proxy only sees calls that go through it. When a method of a bean calls another method of the same bean, the call goes
directly to the bean and is not logged:

```java title="CartService.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.springframework.stereotype.Service;

@Service
public class CartService
{
  @MethodLogging
  public int checkout(String customer) {
    return countItems(customer);
  }

  @MethodLogging
  public int countItems(String customer) {
    return 3;
  }
}
// checkout("Ada") in proxy mode logs:
// INFO  > checkout(customer=Ada):11
// DEBUG result = 3
// INFO  < checkout:11
// countItems is executed, but not logged
```

The same applies to private and static methods, which a proxy cannot intercept at all, and to final methods of beans
proxied by subclassing. An annotation on such a method has no effect in proxy mode.

/// tip | Move the inner method to another bean
If the inner call must be logged in proxy mode, the inner method is moved to a separate bean, which is injected into the
first one. Alternatively, the AspectJ mode logs self-invocations without any change to the code.
///

### Analysis at Startup

In proxy mode, the methods of all singleton beans are analyzed after all singleton beans have been created. A
configuration error, such as an excluded parameter name that matches no parameter, therefore stops the application
context from starting. This includes prototype beans that are created while the singletons are created, for example
because they are injected into a singleton. Beans created after startup, such as lazy beans or prototype beans obtained
later, are analyzed when their proxy is created. An error found then is reported as a warning on the first invocation of
the method, which is executed without logging. The [Error Handling](../reference/error-handling.md) page describes both
cases.

### Composed and Inherited Annotations

The proxy mode finds `@MethodLogging` on the executed method, on the interface and superclass methods it implements or
overrides, and as a meta-annotation of a [composed annotation](../developer-guide/composed-annotations.md).

## AspectJ Mode

AspectJ is a language extension for Java that can insert code, called advice, into existing classes. The insertion is
called weaving and takes place either when the classes are compiled (compile-time weaving) or when they are loaded by
the JVM (load-time weaving). Method logging provides the aspect [`MethodLoggingAspect`][jd-aspect], which applies the
method logging interceptor to the execution of every method annotated with `@MethodLogging`.

With weaving, no proxy is involved. The logging code is part of the class itself, which has the following consequences:

- Calls within the same class are logged, including calls of private methods.
- Methods of objects not created by Spring are logged as well, once an application context has configured the aspect.
- `@MethodLogging` must be placed on the executed method itself. Annotations on interface or superclass methods are not
  considered, because AspectJ does not inherit method annotations, and composed `@MethodLogging` annotations are not
  supported. `@ParamLog` annotations, including composed ones, are still taken from interface and superclass methods.
- Static methods are not logged.

The attributes `proxyTargetClass` and `order` of `@EnableMethodLogging` are ignored in this mode.

### Step 1: Add the AspectJ Runtime

The AspectJ runtime is an optional dependency of the library and has to be added. For load-time weaving, the weaver
`org.aspectj:aspectjweaver` contains the runtime; for compile-time weaving, `org.aspectj:aspectjrt` is sufficient.

=== "Gradle (Groovy)"

    ```groovy title="build.gradle"
    // AspectJ runtime for woven classes
    dependencies {
      implementation 'org.aspectj:aspectjrt:1.9.22'
    }
    ```

=== "Gradle (Kotlin)"

    ```kotlin title="build.gradle.kts"
    // AspectJ runtime for woven classes
    dependencies {
        implementation("org.aspectj:aspectjrt:1.9.22")
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <!-- AspectJ runtime for woven classes -->
    <dependency>
      <groupId>org.aspectj</groupId>
      <artifactId>aspectjrt</artifactId>
      <version>1.9.22</version>
    </dependency>
    ```

Versions from 1.9.21.1 up to, but excluding, 2.0 are supported.

### Step 2: Select the AspectJ Mode

```java title="ShopConfiguration.java"
package com.example.bookstore;

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@EnableMethodLogging(mode = AdviceMode.ASPECTJ)
public class ShopConfiguration {
}
// configures the woven MethodLoggingAspect instead of creating proxies
```

### Step 3: Weave the Classes

The aspect has to be woven into the classes with logged methods, either at compile time or at load time.

For **load-time weaving**, the JVM is started with the AspectJ weaver as Java agent. The library contains a
`META-INF/aop.xml` file that declares the aspect, so no further configuration is necessary:

```shell title="Starting the application with load-time weaving"
# weaves MethodLoggingAspect into classes as they are loaded
java -javaagent:aspectjweaver-1.9.22.jar \
     -jar bookstore.jar
```

Without restrictions, the weaver examines every class that is loaded. An `aop.xml` of the application can limit the
classes to be woven with a `weaver` element, as documented by AspectJ.

For **compile-time weaving**, the classes are compiled with the AspectJ compiler `ajc`, with the method logging JAR on
its aspect path. The build plugins for AspectJ provide a setting for this, such as the `aspectLibraries` of the AspectJ
Maven plugin. On the command line, the call looks like this:

```shell title="Compiling with the AspectJ compiler"
# compiles and weaves the application classes
ajc -21 -parameters \
    -aspectpath spring-method-logging-0.6.0.jar \
    -classpath "$CLASSPATH" \
    -d target/classes \
    $(find src/main/java -name '*.java')
```

The `-parameters` option keeps the parameter names, as described on the [Getting
Started](../getting-started.md#compiling-with-parameter-names) page.

With the classes woven, the self-invocation example from above logs both methods:

```java title="Self-invocation in AspectJ mode"
cartService.checkout("Ada");
// INFO  > checkout(customer=Ada):11
// INFO  > countItems(customer=Ada):16
// DEBUG result = 3
// INFO  < countItems:16
// DEBUG result = 3
// INFO  < checkout:11
```

### Lifecycle of the Aspect

The aspect is a singleton per class loader. It starts logging when an application context with
`@EnableMethodLogging(mode = AdviceMode.ASPECTJ)` configures it during startup. Until then, annotated methods are
executed without logging. When the application context is closed, the aspect stops logging again.

If several application contexts in the same class loader enable the AspectJ mode, the context created last configures
the aspect, and its configuration applies to all woven classes. When that context is closed, logging stops; the aspect
is not handed back to a context created earlier. Applications with several contexts, such as test suites that create a
context per test class, therefore see the configuration of the most recently created context.

### Analysis on First Invocation

In AspectJ mode, a method is analyzed when it is executed for the first time. The configurer is still created at
startup, so an invalid configurer default stops the application context from starting. An error in the configuration of
a method, however, is reported as a warning on the first execution, and the method is executed without logging. The
[Error Handling](../reference/error-handling.md#configuration-errors) page lists these errors.

## Choosing a Mode

| Aspect                                   | Proxy mode                     | AspectJ mode                            |
|------------------------------------------|--------------------------------|-----------------------------------------|
| additional setup                         | none                           | AspectJ runtime and weaving             |
| calls within the same class              | not logged                     | logged                                  |
| private methods                          | not logged                     | logged                                  |
| final methods                            | not logged with CGLIB proxies  | logged                                  |
| static methods                           | not logged                     | not logged                              |
| objects not created by Spring            | not logged                     | logged                                  |
| `@MethodLogging` on interface or superclass | supported                   | not supported                           |
| composed `@MethodLogging` annotations    | supported                      | not supported                           |
| method configuration errors              | fail the startup (singletons)  | reported as warning on first execution  |
| ordering relative to other advice        | `order` attribute              | determined by AspectJ                   |

The proxy mode suits most applications. The AspectJ mode is the choice when internal calls or objects outside the
application context have to be logged and the build or runtime already uses AspectJ.

[jd-aspect]: https://javadoc.io/doc/de.sayayi.lib/spring-method-logging/0.6.0/de.sayayi.lib.methodlogging/de/sayayi/lib/methodlogging/aspectj/MethodLoggingAspect.html
