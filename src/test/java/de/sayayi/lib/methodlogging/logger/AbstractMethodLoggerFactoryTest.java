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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.5.1
 */
@DisplayName("Abstract method logger factory")
class AbstractMethodLoggerFactoryTest
{
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
  @DisplayName("Loggers for classes without logger field are cached per factory")
  void classLoggerCachedPerFactory()
  {
    assertNotSame(
        new JCLLoggerFactory(true).from(null, new A(), A.class),
        new JCLLoggerFactory(true).from(null, new A(), A.class));
  }




  private static final class RecordingLoggerFactory extends AbstractMethodLoggerFactory
  {
    private final List<Class<?>> classes = new ArrayList<>();


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
    protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj) {
      throw new UnsupportedOperationException();
    }
  }




  static final class A {
  }




  static final class B {
  }
}
