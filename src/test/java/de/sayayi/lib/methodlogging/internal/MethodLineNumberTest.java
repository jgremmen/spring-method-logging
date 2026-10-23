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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Jeroen Gremmen
 * @since 0.5.1
 */
@DisplayName("Method line number")
class MethodLineNumberTest
{
  private static final String BEAN_RESOURCE = Bean.class.getName().replace('.', '/') + ".class";


  @Test
  @DisplayName("Line number is taken from the class file")
  void lineNumber() throws Exception
  {
    final var methodDef =
        new AnnotationMethodLoggingSource(new MethodLoggingConfigurer() {}, new DefaultResourceLoader())
            .getMethodDefinition(Bean.class.getMethod("logged"), Bean.class);

    assertTrue(requireNonNull(methodDef).line > 0);

    // the source location passed to method loggers
    assertEquals(Bean.class.getName(), methodDef.location.getClassName());
    assertEquals("logged", methodDef.location.getMethodName());
    assertEquals("MethodLineNumberTest.java", methodDef.location.getFileName());
    assertEquals(methodDef.line, methodDef.location.getLineNumber());
  }


  @Test
  @DisplayName("Class file not readable by ASM results in no line number")
  void unsupportedClassFileVersion() throws Exception
  {
    final var beanClass = new UnsupportedClassFileVersionLoader().loadClass(Bean.class.getName());
    final var methodDef = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {},
        new DefaultResourceLoader())
        .getMethodDefinition(beanClass.getMethod("logged"), beanClass);

    assertEquals(-1, requireNonNull(methodDef).line);
  }




  public static class Bean
  {
    @MethodLogging
    public void logged() {
    }
  }




  /**
   * Defines {@link Bean} from its original class file, but provides the class file with an unsupported major version as
   * resource.
   */
  private static final class UnsupportedClassFileVersionLoader extends ClassLoader
  {
    private final byte[] classFile;


    private UnsupportedClassFileVersionLoader()
    {
      super(MethodLineNumberTest.class.getClassLoader());

      try(var classFileStream = requireNonNull(getParent().getResourceAsStream(BEAN_RESOURCE))) {
        classFile = classFileStream.readAllBytes();
      } catch(IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }


    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
      if (!Bean.class.getName().equals(name))
        return super.loadClass(name, resolve);

      synchronized(getClassLoadingLock(name))
      {
        var clazz = findLoadedClass(name);
        if (clazz == null)
          clazz = defineClass(name, classFile, 0, classFile.length);

        return clazz;
      }
    }


    @Override
    public InputStream getResourceAsStream(@NotNull String name)
    {
      if (!BEAN_RESOURCE.equals(name))
        return super.getResourceAsStream(name);

      final var unsupportedClassFile = classFile.clone();

      // major version 32767; ASM rejects class files newer than it supports with an IllegalArgumentException
      unsupportedClassFile[6] = (byte)0x7f;
      unsupportedClassFile[7] = (byte)0xff;

      return new ByteArrayInputStream(unsupportedClassFile);
    }
  }
}
