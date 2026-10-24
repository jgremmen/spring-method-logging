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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * Tests that the logger factories do not keep the class loader of this library reachable, if the logger or target
 * classes are loaded by a class loader outliving the library (e.g. the bootstrap class loader for JUL loggers or a
 * container class loader).
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Class loader leaks")
class ClassLoaderLeakTest
{
  @Test
  @DisplayName("Auto detection does not keep the library class loader reachable")
  void autoDetectReleasesClassLoader() throws Exception
  {
    final var libraryClassLoader = useAutoDetectLoggerFactoryInIsolation();

    for(var n = 0; n < 50 && libraryClassLoader.get() != null; n++)
    {
      System.gc();
      //noinspection BusyWait
      Thread.sleep(20);
    }

    assertNull(libraryClassLoader.get(), "library class loader has not been garbage collected");
  }


  /**
   * Loads the library classes with an isolated class loader and obtains method loggers from an auto-detecting logger
   * factory for a static final JUL logger field (bootstrap logger class) and for a class without logger field. Both the
   * logger field and the class are loaded by the class loader of this test, which outlives the isolated class loader.
   *
   * @return  weak reference to the isolated class loader, never {@code null}
   */
  private static @NotNull WeakReference<ClassLoader> useAutoDetectLoggerFactoryInIsolation() throws Exception
  {
    final var libraryLocation =
        AutoDetectLoggerFactory.class.getProtectionDomain().getCodeSource().getLocation();

    try(var libraryClassLoader = new LibraryClassLoader(libraryLocation)) {
      final var factoryClass = libraryClassLoader.loadClass(AutoDetectLoggerFactory.class.getName());
      final var factory = factoryClass.getConstructor().newInstance();
      final var from = factoryClass.getMethod("from", Field.class, Object.class, Class.class);
      final var bean = new JulLoggerField();

      from.invoke(factory, JulLoggerField.class.getDeclaredField("log"), bean, JulLoggerField.class);
      from.invoke(factory, null, bean, JulLoggerField.class);

      return new WeakReference<>(libraryClassLoader);
    }
  }




  /**
   * Class loader loading the library classes itself, instead of delegating to its parent first.
   */
  private static final class LibraryClassLoader extends URLClassLoader
  {
    private LibraryClassLoader(@NotNull URL libraryLocation) {
      super(new URL[] { libraryLocation }, ClassLoaderLeakTest.class.getClassLoader());
    }


    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
      if (name.startsWith("de.sayayi.lib.methodlogging."))
      {
        synchronized(getClassLoadingLock(name))
        {
          var type = findLoadedClass(name);
          if (type == null)
          {
            try {
              type = findClass(name);
            } catch(ClassNotFoundException ex) {
              // test classes are not part of the library location
              return super.loadClass(name, resolve);
            }
          }

          if (resolve)
            resolveClass(type);

          return type;
        }
      }

      return super.loadClass(name, resolve);
    }
  }




  @SuppressWarnings("unused")
  static final class JulLoggerField
  {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger("leak");
  }
}
