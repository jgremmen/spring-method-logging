/*
 * Copyright 2026 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.lib.methodlogging.logger;

import ch.qos.logback.classic.LoggerContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static de.sayayi.lib.methodlogging.Level.INFO;
import static de.sayayi.lib.methodlogging.MethodLogger.NO_OP;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Logger factories")
class LoggerFactoryTest
{
  @Test
  @DisplayName("Auto detection uses the type of the logger instance")
  void autoDetectInstanceType() throws Exception
  {
    final var factory = new AutoDetectLoggerFactory();
    final var bean = new ObjectFields();

    assertInstanceOf(JULLogger.class,
        factory.from(field(ObjectFields.class, "jul"), bean, ObjectFields.class));
    assertInstanceOf(SystemLogger.class,
        factory.from(field(ObjectFields.class, "system"), bean, ObjectFields.class));
    assertInstanceOf(LogbackLogger.class,
        factory.from(field(ObjectFields.class, "logback"), bean, ObjectFields.class));
    assertSame(NO_OP, factory.from(field(ObjectFields.class, "none"), bean, ObjectFields.class));
  }


  @Test
  @DisplayName("Auto detection falls back to a JCL logger for unsupported logger types")
  void autoDetectUnsupported() throws Exception
  {
    final var factory = new AutoDetectLoggerFactory();
    final var field = field(ObjectFields.class, "unsupported");
    final var logger = factory.from(field, new ObjectFields(), ObjectFields.class);

    assertInstanceOf(JCLLogger.class, logger);
    assertSame(logger, factory.from(field, new ObjectFields(), ObjectFields.class));
    assertSame(logger, factory.from(null, new ObjectFields(), ObjectFields.class));
  }


  @Test
  @DisplayName("Fallback loggers for an inherited static logger field are selected by target class")
  void autoDetectUnsupportedInherited() throws Exception
  {
    final var factory = new AutoDetectLoggerFactory();
    final var field = field(UnsupportedStaticLoggerBase.class, "log");
    final var loggerA = factory.from(field, new UnsupportedStaticLoggerA(), UnsupportedStaticLoggerA.class);
    final var loggerB = factory.from(field, new UnsupportedStaticLoggerB(), UnsupportedStaticLoggerB.class);

    assertInstanceOf(JCLLogger.class, loggerA);
    assertNotSame(loggerA, loggerB);
    assertSame(loggerA, factory.from(null, new UnsupportedStaticLoggerA(), UnsupportedStaticLoggerA.class));
    assertSame(loggerB, factory.from(null, new UnsupportedStaticLoggerB(), UnsupportedStaticLoggerB.class));
    assertSame(loggerA, factory.from(field, new UnsupportedStaticLoggerA(), UnsupportedStaticLoggerA.class));
  }


  @Test
  @DisplayName("Auto detection falls back to a JCL logger for loggers not accessible to the library")
  void autoDetectInaccessible() throws Exception
  {
    final var slf4jLocation = org.slf4j.Logger.class.getProtectionDomain().getCodeSource().getLocation();

    try(var slf4jClassLoader = new Slf4jClassLoader(slf4jLocation)) {
      final var loggerType = slf4jClassLoader.loadClass(org.slf4j.Logger.class.getName());
      final var bean = new InaccessibleLoggerField(Proxy.newProxyInstance(slf4jClassLoader,
          new Class<?>[] { loggerType }, (proxy, method, args) -> switch(method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "inaccessible logger";
            default -> null;
          }));
      final var factory = new AutoDetectLoggerFactory();
      final var field = field(InaccessibleLoggerField.class, "log");
      final var logger = factory.from(field, bean, InaccessibleLoggerField.class);

      assertNotSame(org.slf4j.Logger.class, loggerType);
      assertInstanceOf(JCLLogger.class, logger);
      assertSame(logger, factory.from(null, bean, InaccessibleLoggerField.class));
    }
  }


  @Test
  @DisplayName("Framework specific loggers do not log objects of another type")
  void wrongLoggerType() throws Exception
  {
    final var factory = JULLoggerFactory.withFallback();
    final var bean = new ObjectFields();

    assertSame(NO_OP, factory.from(field(ObjectFields.class, "unsupported"), bean, ObjectFields.class));
    assertSame(NO_OP, factory.from(field(ObjectFields.class, "system"), bean, ObjectFields.class));
    assertSame(NO_OP, JULLogger.of(null));
    assertSame(NO_OP, Slf4jLogger.of("logger"));
  }


  @Test
  @DisplayName("java.util.logging logger factory uses the logger field")
  void julLoggerField() throws Exception
  {
    assertInstanceOf(JULLogger.class,
        JULLoggerFactory.strict().from(field(TypedFields.class, "jul"), new TypedFields(), TypedFields.class));
  }


  @Test
  @DisplayName("java.util.logging logger factory without logger field")
  void julWithoutLoggerField()
  {
    // the bug only showed if no logger with the class name existed yet; so capture the records on an ancestor
    final var loggerName = NoLoggerField.class.getName();
    assertNull(LogManager.getLogManager().getLogger(loggerName));

    final var ancestor = Logger.getLogger(NoLoggerField.class.getPackageName());
    final var messages = new ArrayList<String>();
    final var handler = new Handler() {
      @Override public void publish(LogRecord record) {
        if (loggerName.equals(record.getLoggerName()))
          messages.add(record.getLevel() + "|" + record.getMessage());
      }
      @Override public void flush() {}
      @Override public void close() {}
    };

    ancestor.addHandler(handler);
    try {
      final var methodLogger = JULLoggerFactory.withFallback().from(null, new NoLoggerField(), NoLoggerField.class);

      assertTrue(methodLogger.isLogEnabled(INFO));
      methodLogger.log(INFO, "> test");
    } finally {
      ancestor.removeHandler(handler);
    }

    assertEquals(List.of("INFO|> test"), messages);
  }


  @Test
  @DisplayName("Strict factory requires a logger field")
  void strictRequiresField()
  {
    final var strict = JULLoggerFactory.strict();

    assertEquals("Class " + ObjectFields.class + " or one of its superclasses must provide a logger field",
        assertThrowsExactly(IllegalStateException.class, () -> strict.validate(null, ObjectFields.class))
            .getMessage());
    assertDoesNotThrow(() -> JULLoggerFactory.withFallback().validate(null, ObjectFields.class));
  }


  @Test
  @DisplayName("Logger field type must be able to hold a logger of the factory")
  void loggerFieldType() throws Exception
  {
    final var factory = JULLoggerFactory.withFallback();

    assertDoesNotThrow(() -> factory.validate(field(ObjectFields.class, "jul"), ObjectFields.class));
    assertDoesNotThrow(() -> factory.validate(field(TypedFields.class, "jul"), TypedFields.class));

    final var stringField = field(TypedFields.class, "string");
    assertEquals("Logger field 'string' of " + TypedFields.class + " must be of type java.util.logging.Logger",
        assertThrowsExactly(IllegalStateException.class, () -> factory.validate(stringField, TypedFields.class))
            .getMessage());

    // the auto detecting factory accepts any logger field
    assertDoesNotThrow(() -> new AutoDetectLoggerFactory().validate(stringField, TypedFields.class));
  }




  /**
   * Returns the logger field, made accessible as by the method logging analysis.
   */
  private static @NotNull Field field(@NotNull Class<?> type, @NotNull String name) throws NoSuchFieldException
  {
    final var field = type.getDeclaredField(name);
    field.setAccessible(true);

    return field;
  }




  @SuppressWarnings({ "unused", "FieldMayBeFinal" })
  static final class ObjectFields
  {
    private Object jul = Logger.getLogger("logger-factory-test");
    private Object system = System.getLogger("logger-factory-test");
    private Object logback = new LoggerContext().getLogger("logger-factory-test");
    private Object unsupported = "not a logger";
    private Object none = null;
  }




  @SuppressWarnings({ "unused", "ClassCanBeRecord", "FieldCanBeLocal" })
  static final class InaccessibleLoggerField
  {
    private final Object log;


    InaccessibleLoggerField(@NotNull Object log) {
      this.log = log;
    }
  }




  /**
   * Class loader loading the SLF4J API itself, instead of delegating to its parent first.
   */
  private static final class Slf4jClassLoader extends URLClassLoader
  {
    private Slf4jClassLoader(@NotNull URL slf4jLocation) {
      super(new URL[] { slf4jLocation }, LoggerFactoryTest.class.getClassLoader());
    }


    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
      if (name.startsWith("org.slf4j."))
      {
        synchronized(getClassLoadingLock(name))
        {
          var type = findLoadedClass(name);
          if (type == null)
            type = findClass(name);

          if (resolve)
            resolveClass(type);

          return type;
        }
      }

      return super.loadClass(name, resolve);
    }
  }




  @SuppressWarnings("unused")
  static class UnsupportedStaticLoggerBase {
    private static final Object log = "not a logger";
  }




  static final class UnsupportedStaticLoggerA extends UnsupportedStaticLoggerBase {
  }




  static final class UnsupportedStaticLoggerB extends UnsupportedStaticLoggerBase {
  }




  static final class NoLoggerField {
  }




  @SuppressWarnings("unused")
  static final class TypedFields
  {
    private static final Logger jul = Logger.getLogger("logger-factory-test");
    private static final String string = "not a logger";
  }
}
