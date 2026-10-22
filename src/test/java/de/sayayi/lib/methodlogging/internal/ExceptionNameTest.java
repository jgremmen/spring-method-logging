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
package de.sayayi.lib.methodlogging.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.sayayi.lib.methodlogging.internal.MethodLoggingInterceptor.exceptionName;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Jeroen Gremmen
 * @since 0.5.1
 */
@DisplayName("Exception name")
class ExceptionNameTest
{
  @Test
  @DisplayName("Top level class")
  void topLevel() {
    assertEquals("IllegalStateException", exceptionName(IllegalStateException.class));
  }


  @Test
  @DisplayName("Nested class includes enclosing class")
  void nested() {
    assertEquals("ExceptionNameTest.NestedException", exceptionName(NestedException.class));
  }


  @Test
  @DisplayName("Anonymous class uses binary name")
  void anonymous()
  {
    final var exception = new RuntimeException() {};

    assertEquals("ExceptionNameTest$1", exceptionName(exception.getClass()));
  }


  @Test
  @DisplayName("Local class uses binary name")
  void local()
  {
    class LocalException extends RuntimeException {}

    assertEquals("ExceptionNameTest$1LocalException", exceptionName(LocalException.class));
  }




  static final class NestedException extends RuntimeException {
  }
}
