# Thread Safety

Logged methods are invoked by many threads at once, for example by the request threads of a web application. This page
describes which parts of method logging are shared between threads and which requirements this places on custom
implementations.

## Shared by All Threads

Method logging keeps one configuration per application context. The analyzed settings of each method, the message
support and the method logger factory are created once and shared by all threads that invoke logged methods. The
analyzed settings are immutable. An invocation itself keeps its state, such as the start time for the elapsed time, in
local variables, so concurrent invocations of the same method do not interfere.

A method can be analyzed by several threads at the same time when it is invoked concurrently for the first time. The
analysis runs without holding a lock; all threads produce equivalent results, and the first cached result is used.

## Built-In Components

All classes of the library that can be shared are thread-safe. This includes the `AutoDetectLoggerFactory`, every
framework-specific factory, `AbstractMethodLoggerFactory` with its caches, the method loggers created by these
factories, `MethodLogger.NO_OP` and the `MethodLoggingAspect`. A single factory instance can serve any number of threads
and, if desired, several application contexts.

The `MethodLoggingAspect` of the AspectJ mode is a singleton per class loader. Attaching and detaching the interceptor
is atomic, so an application context that starts or closes while other threads execute woven methods does not disturb
them. Each execution uses either the old or the new configuration.

## Custom Implementations

The following interfaces are called concurrently and require thread-safe implementations:

| Type                               | Called                                                   | Requirement                                      |
|------------------------------------|----------------------------------------------------------|--------------------------------------------------|
| `MethodLoggerFactory`              | `from` on every invocation, by any thread                | thread-safe; caches must be concurrent           |
| `MethodLogger`                     | by any thread; returned instances may be cached and shared | thread-safe                                    |
| `MessageSupport` from the configurer | for every formatted message, by any thread             | thread-safe; not modified after it is returned   |
| `MethodLoggingConfigurer`          | `excludeMethodParameter` during analysis, possibly concurrently | thread-safe and free of side effects       |

The remaining methods of `MethodLoggingConfigurer`, `messageSupport()`, `methodLoggerFactory()` and the five default
methods, are called once by a single thread when the configuration is created.

`AbstractMethodLoggerFactory` may call the `createMethodLogger` methods of a subclass concurrently for the same field or
class when a method logger is requested for the first time. The method loggers created in such a race must be
equivalent, as only one of them is cached. Creating the method logger without side effects, as in the [custom factory
example](../developer-guide/custom-loggers.md#step-2-write-the-factory), meets this requirement.

## Configurer Creation

The configurer bean is created lazily, as described on the
[Configuration](../user-guide/configuration.md#when-the-configurer-is-created) page. While one thread creates it, no
other thread waits for it: logged methods invoked by any thread during that time are executed without logging. This
avoids deadlocks with threads that hold locks of the bean factory while they invoke logged methods.

## Logger Fields

A logger field that is not `static final` is read on every invocation, by the thread that invokes the method. A field
that is reassigned at runtime is therefore seen with the visibility guarantees of the Java memory model; a `volatile` or
`final` field ensures that every thread sees the current logger. Logger fields are usually `static final`, in which case
the question does not arise.
