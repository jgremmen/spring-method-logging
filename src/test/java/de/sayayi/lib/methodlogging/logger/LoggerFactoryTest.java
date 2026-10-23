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
import java.util.logging.Logger;

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




  @SuppressWarnings("unused")
  static final class TypedFields
  {
    private static final Logger jul = Logger.getLogger("logger-factory-test");
    private static final String string = "not a logger";
  }
}
