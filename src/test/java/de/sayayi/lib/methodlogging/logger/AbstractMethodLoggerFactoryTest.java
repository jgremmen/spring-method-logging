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

import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;

import static de.sayayi.lib.methodlogging.MethodLogger.NO_OP;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Abstract method logger factory")
class AbstractMethodLoggerFactoryTest
{
  private static final MethodLoggerFactory INIT_FACTORY = JULLoggerFactory.withFallback();
  private static final Field INIT_LOG_FIELD = getInitLogField();


  @Test
  @DisplayName("Loggers for classes without logger field are cached per class")
  void classLoggerCached()
  {
    final var factory = new RecordingLoggerFactory();

    final var logger1 = factory.from(null, new A(), A.class);
    final var logger2 = factory.from(null, new A(), A.class);
    final var logger3 = factory.from(null, new B(), B.class);

    assertSame(logger1, logger2);
    assertNotSame(logger1, logger3);
    assertEquals(List.of(A.class, B.class), factory.classes);
  }


  @Test
  @DisplayName("Loggers from static final fields are cached per field")
  void fieldLoggerCached() throws Exception
  {
    final var factory = new RecordingLoggerFactory();
    final var logField = WithLoggers.class.getDeclaredField("log");
    final var auditLogField = WithLoggers.class.getDeclaredField("auditLog");
    final var bean = new WithLoggers();

    final var logger1 = factory.from(logField, bean, WithLoggers.class);
    final var logger2 = factory.from(WithLoggers.class.getDeclaredField("log"), bean, WithLoggers.class);
    final var logger3 = factory.from(auditLogField, bean, WithLoggers.class);

    assertSame(logger1, logger2);
    assertNotSame(logger1, logger3);
    assertEquals(List.of("log", "auditLog"), factory.fields);
  }


  @Test
  @DisplayName("Loggers from instance fields are reused while the field holds the same logger")
  void instanceFieldLoggerReused() throws Exception
  {
    final var factory = new RecordingLoggerFactory();
    final var logField = WithInstanceLogger.class.getDeclaredField("log");
    final var bean1 = new WithInstanceLogger();
    final var bean2 = new WithInstanceLogger();

    final var logger1 = factory.from(logField, bean1, WithInstanceLogger.class);
    final var logger2 = factory.from(logField, bean1, WithInstanceLogger.class);
    final var logger3 = factory.from(logField, bean2, WithInstanceLogger.class);

    assertSame(logger1, logger2);
    assertSame(logger1, logger3);
    assertEquals(List.of("log"), factory.fields);
  }


  @Test
  @DisplayName("Loggers from instance fields are created again when the field is reassigned")
  void instanceFieldLoggerReassigned() throws Exception
  {
    final var factory = new RecordingLoggerFactory();
    final var logField = WithInstanceLogger.class.getDeclaredField("log");
    final var bean = new WithInstanceLogger();

    final var logger1 = factory.from(logField, bean, WithInstanceLogger.class);
    bean.log = java.util.logging.Logger.getLogger("other");
    final var logger2 = factory.from(logField, bean, WithInstanceLogger.class);
    final var logger3 = factory.from(logField, bean, WithInstanceLogger.class);

    assertNotSame(logger1, logger2);
    assertSame(logger2, logger3);
    assertEquals(List.of("log", "log"), factory.fields);
  }


  @Test
  @DisplayName("Loggers from instance fields holding different loggers are cached per target object")
  void instanceFieldLoggerPerTargetObject() throws Exception
  {
    final var factory = new RecordingLoggerFactory();
    final var logField = WithInstanceLogger.class.getDeclaredField("log");
    final var bean1 = new WithInstanceLogger();
    final var bean2 = new WithInstanceLogger();

    bean2.log = java.util.logging.Logger.getLogger("other");

    final var logger1 = factory.from(logField, bean1, WithInstanceLogger.class);
    final var logger2 = factory.from(logField, bean2, WithInstanceLogger.class);
    final var logger3 = factory.from(logField, bean1, WithInstanceLogger.class);
    final var logger4 = factory.from(logField, bean2, WithInstanceLogger.class);

    assertNotSame(logger1, logger2);
    assertSame(logger1, logger3);
    assertSame(logger2, logger4);
    assertEquals(List.of("log", "log"), factory.fields);
  }


  @Test
  @DisplayName("Cached loggers from instance fields do not keep the target object reachable")
  void instanceFieldLoggerReleasesTarget() throws Exception
  {
    final var factory = new RecordingLoggerFactory();
    final var logField = WithInstanceLogger.class.getDeclaredField("log");
    var bean = new WithInstanceLogger();
    final var beanReference = new WeakReference<>(bean);

    factory.from(logField, bean, WithInstanceLogger.class);
    //noinspection UnusedAssignment
    bean = null;

    for(var n = 0; n < 50 && beanReference.get() != null; n++)
    {
      System.gc();
      //noinspection BusyWait
      Thread.sleep(20);
    }

    assertNull(beanReference.get(), "target object has not been garbage collected");
  }


  @Test
  @DisplayName("Loggers from an inherited instance field are cached per target class")
  void instanceFieldLoggerPerTargetClass() throws Exception
  {
    final var factory = new RecordingLoggerFactory();
    final var logField = LoggingBase.class.getDeclaredField("log");
    final var subA = new SubA();
    final var subB = new SubB();

    final var loggerA1 = factory.from(logField, subA, SubA.class);
    final var loggerB1 = factory.from(logField, subB, SubB.class);
    final var loggerA2 = factory.from(logField, subA, SubA.class);
    final var loggerB2 = factory.from(logField, subB, SubB.class);

    assertNotSame(loggerA1, loggerB1);
    assertSame(loggerA1, loggerA2);
    assertSame(loggerB1, loggerB2);
    assertEquals(List.of("log", "log"), factory.fields);
  }


  @Test
  @DisplayName("Concurrently requested static field loggers resolve to the same logger")
  void fieldLoggerConcurrent() throws Exception
  {
    final var threads = 16;
    final var factory = new RecordingLoggerFactory();
    final var logField = WithLoggers.class.getDeclaredField("log");
    final var bean = new WithLoggers();
    final var barrier = new CyclicBarrier(threads);
    final Callable<MethodLogger> task = () -> {
      barrier.await();
      return factory.from(logField, bean, WithLoggers.class);
    };

    try(var executor = newFixedThreadPool(threads)) {
      final var futures = new ArrayList<Future<MethodLogger>>();

      for(var n = 0; n < threads; n++)
        futures.add(executor.submit(task));

      final var first = futures.getFirst().get();

      for(var future: futures)
        assertSame(first, future.get());

      assertSame(first, factory.from(logField, bean, WithLoggers.class));
    }
  }


  @Test
  @DisplayName("No-op logger of a static final field read during class initialization is not cached")
  void fieldLoggerDuringClassInitialization()
  {
    final var logger = InitializingLoggers.INSTANCE.logger;

    assertSame(NO_OP, logger);
    assertNotSame(NO_OP, INIT_FACTORY.from(INIT_LOG_FIELD, InitializingLoggers.INSTANCE, InitializingLoggers.class));
  }


  @Test
  @DisplayName("Loggers for classes without logger field are cached per factory")
  void classLoggerCachedPerFactory()
  {
    assertNotSame(
        new JCLLoggerFactory(true).from(null, new A(), A.class),
        new JCLLoggerFactory(true).from(null, new A(), A.class));
  }



  private static @NotNull Field getInitLogField()
  {
    try {
      final var field = InitializingLoggers.class.getDeclaredField("log");
      field.setAccessible(true);
      return field;
    } catch(NoSuchFieldException ex) {
      throw new IllegalStateException(ex);
    }
  }




  private static final class RecordingLoggerFactory extends AbstractMethodLoggerFactory
  {
    private final List<Class<?>> classes = new ArrayList<>();
    private final List<String> fields = new CopyOnWriteArrayList<>();


    private RecordingLoggerFactory() {
      super(true);
    }


    @Override
    protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz)
    {
      classes.add(clazz);
      return new JCLLoggerFactory(true).createMethodLogger(clazz);
    }


    @Override
    protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj)
    {
      fields.add(loggerField.getName());
      return new JCLLoggerFactory(true).createMethodLogger(loggerField.getDeclaringClass());
    }
  }




  static final class A {
  }




  static final class B {
  }


  @SuppressWarnings("unused")
  static final class WithLoggers
  {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger("log");
    private static final java.util.logging.Logger auditLog = java.util.logging.Logger.getLogger("audit");
  }



  static final class WithInstanceLogger
  {
    java.util.logging.Logger log = java.util.logging.Logger.getLogger("log");
  }




  abstract static class LoggingBase
  {
    final java.util.logging.Logger log = java.util.logging.Logger.getLogger(getClass().getName());
  }




  static final class SubA extends LoggingBase {
  }




  static final class SubB extends LoggingBase {
  }




  @SuppressWarnings("unused")
  static final class InitializingLoggers
  {
    // created before the logger field is initialized
    static final InitializingLoggers INSTANCE = new InitializingLoggers();

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger("init");

    private final MethodLogger logger;


    private InitializingLoggers() {
      logger = INIT_FACTORY.from(INIT_LOG_FIELD, this, InitializingLoggers.class);
    }
  }
}
