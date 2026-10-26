# Java Modules

The library is a named module with the name `de.sayayi.lib.methodlogging`. This page describes what an application
running on the module path has to declare. Applications on the class path are not affected.

## Reading the Module

The application module requires the method logging module:

```java title="module-info.java"
module com.example.bookstore
{
  requires de.sayayi.lib.methodlogging;
}
// the application can use the annotations and the configurer
```

The method logging module requires the modules `spring.context`, `spring.core` and `de.sayayi.lib.message` transitively.
Their types appear in the public API, such as `ResolvableType` in `MethodLoggingConfigurer` and `MessageSupport` from
the message-format library, so the application module reads them without declaring them.

The module exports the packages `de.sayayi.lib.methodlogging`, `de.sayayi.lib.methodlogging.annotation`,
`de.sayayi.lib.methodlogging.aspectj`, `de.sayayi.lib.methodlogging.format` and `de.sayayi.lib.methodlogging.logger`.
The package `de.sayayi.lib.methodlogging.internal` is not exported and is not part of the API.

The logging frameworks and the AspectJ runtime are optional dependencies, declared with `requires static`. An
application using one of them requires its module itself, for example `requires org.slf4j;` for SLF4J logger fields.

## Opening Packages

Spring creates beans and proxies by reflection, and method logging reads logger fields by reflection. On the module
path, both require the package of the bean to be opened. A package containing beans with logger fields is opened to the
method logging module in addition to the Spring modules:

```java title="module-info.java"
module com.example.bookstore
{
  requires de.sayayi.lib.methodlogging;
  requires org.slf4j;

  opens com.example.bookstore to spring.core, spring.beans,
      spring.context, spring.aop, de.sayayi.lib.methodlogging;
}
// Spring and method logging may access the beans and logger fields
```

A package whose beans have no logger fields only has to be opened to the Spring modules. If a logger field is found in a
package that is not opened to method logging, the method analysis fails with an `IllegalStateException`. Its message
names the field, the class and the package that must be opened: "Logger field 'log' of class
com.example.bookstore.OrderService is not accessible; package com.example.bookstore must be opened to module
de.sayayi.lib.methodlogging".

The consequences of a failed analysis are described on the [Error
Handling](../reference/error-handling.md#configuration-errors) page. For singleton beans in proxy mode, the application
context does not start.

## Spring Framework 7

Spring Framework 5.3 and 6 provide Apache Commons Logging through the automatic module `spring.jcl`, which the Spring
modules read. Spring Framework 7 uses the named module `org.apache.commons.logging` instead. Method logging needs it for
its fallback logger and its failure reports, but the automatic Spring modules do not require it, so it is not resolved
automatically. It is added to the module graph either by the application module or on the command line:

```java title="module-info.java"
module com.example.bookstore
{
  requires de.sayayi.lib.methodlogging;
  requires org.apache.commons.logging;

  opens com.example.bookstore to spring.core, spring.beans,
      spring.context, spring.aop, de.sayayi.lib.methodlogging;
}
// adds Commons Logging to the module graph with Spring Framework 7
```

```shell title="Adding the module on the command line"
# resolves Commons Logging without changing module-info.java
java --add-modules org.apache.commons.logging \
     --module-path mods \
     -m com.example.bookstore/com.example.bookstore.ShopApplication
```
