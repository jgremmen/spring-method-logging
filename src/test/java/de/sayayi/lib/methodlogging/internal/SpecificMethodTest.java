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

import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.DefaultResourceLoader;

import static de.sayayi.lib.methodlogging.Level.DEBUG;
import static de.sayayi.lib.methodlogging.Level.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Specific method resolution")
class SpecificMethodTest
{
  @ParameterizedTest(name = "resolve specific method: {0}")
  @CsvSource({
      // proxies invoke the most specific method, so a superclass method stands for the overriding method
      "true,  Sub,  INFO,  subName",
      // AspectJ passes the executed method, e.g. a superclass method invoked by super.work(...)
      "false, Base, DEBUG, baseName"
  })
  @DisplayName("Superclass method invoked on subclass instance")
  void superclassMethod(boolean resolveSpecificMethod, String locationClass, String entryExitLevel,
                        String parameterName) throws Exception
  {
    final var methodDef = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {}, new DefaultResourceLoader(), resolveSpecificMethod)
        .getMethodDefinition(Base.class.getMethod("work", String.class, String.class), Sub.class);

    assertNotNull(methodDef);
    assertEquals(SpecificMethodTest.class.getName() + '$' + locationClass, methodDef.location.getClassName());
    assertEquals(entryExitLevel, methodDef.entryExitLevel.name());

    // the overriding method is no declaration of the superclass method; the secret is excluded by Base, whether the
    // base or the overriding method is executed
    assertEquals(parameterName, methodDef.inlineParameters.getFirst().name());
    assertEquals(1, methodDef.inlineParameters.size());
  }




  public static class Base
  {
    @MethodLogging(entryExitLevel = DEBUG)
    @SuppressWarnings("unused")
    public void work(@ParamLog(name = "baseName") String name, @ParamLog(exclude = true) String secret) {
    }
  }




  public static class Sub extends Base
  {
    @Override
    @MethodLogging(entryExitLevel = INFO)
    public void work(@ParamLog(name = "subName") String name, @ParamLog(name = "pwd") String secret) {
      super.work(name, secret);
    }
  }
}
