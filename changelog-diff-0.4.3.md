# Version [0.4.3](https://github.com/jgremmen/spring-method-logging/releases/tag/0.4.3) (2024-05-23)

## New Features

### Logback Support

Support for the Logback logging framework has been added. The new `LogbackLoggerFactory` and its
backing `LogbackLogger` implementation allow method logging through Logback's native
`ch.qos.logback.classic.Logger`.

The `AutoDetectLoggerFactory` now also recognizes `ch.qos.logback.classic.Logger` fields and
automatically selects the Logback logger adapter.

To use Logback explicitly, configure the method logger factory:

```java
@Configuration
@EnableMethodLogging
public class AppConfig implements MethodLoggingConfigurer {
  @Override
  public MethodLoggerFactory methodLoggerFactory() {
    return new LogbackLoggerFactory(true);
  }
}
```

The required dependency is `ch.qos.logback:logback-classic` with a version range of `[1.3.0,1.4.0)`.


### Improved Parameter Name Discovery

The internal parameter name discoverer has been changed from `LocalVariableTableParameterNameDiscoverer`
to `DefaultParameterNameDiscoverer`. This provides better parameter name resolution, in particular for
classes compiled with the `-parameters` flag, as the standard reflection-based discoverer is consulted
first before falling back to debug information.


## Bug Fixes

- Fixed `NullPointerException` when logging interface proxy beans where the interface used for access
  is a JDK standard library interface.
- Fixed `NullPointerException` in method line number resolution when the class bytecode is not
  accessible.
