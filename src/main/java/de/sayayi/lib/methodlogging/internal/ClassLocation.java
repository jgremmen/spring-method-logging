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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Map.copyOf;
import static org.springframework.asm.ClassReader.SKIP_FRAMES;
import static org.springframework.asm.SpringAsmInfo.ASM_VERSION;


/**
 * Source file and line of the first statement of each method of a class, as read from its class file.
 *
 * @param sourceFile   source file name or {@code null} if not available
 * @param lineNumbers  line of the first statement, by method name and descriptor, not {@code null}
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
record ClassLocation(String sourceFile, @NotNull @Unmodifiable Map<String,Integer> lineNumbers)
{
  /**
   * Reads the source file and the line numbers of the methods of {@code type} from its class file. If the class file
   * is not available or cannot be read, the information read so far is returned.
   *
   * @param type  class to read the class file for, not {@code null}
   *
   * @return  class location, never {@code null}
   */
  @Contract(pure = true)
  static @NotNull ClassLocation read(@NotNull Class<?> type)
  {
    final var classInputStream = type
        .getResourceAsStream('/' + type.getName().replace('.', '/') + ".class");
    if (classInputStream == null)
      return new ClassLocation(null, Map.of());

    final var sourceFile = new AtomicReference<String>();
    final var lineNumbers = new HashMap<String,Integer>();

    try(classInputStream) {
      new ClassReader(classInputStream).accept(
          new ClassVisitor(ASM_VERSION) {
            @Override
            public void visitSource(String source, String debug) {
              sourceFile.set(source);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] ex)
            {
              final var methodDescriptor = name.concat(descriptor);

              return new MethodVisitor(ASM_VERSION) {
                @Override
                public void visitLineNumber(int line, Label start) {
                  lineNumbers.putIfAbsent(methodDescriptor, line);
                }
              };
            }
          },
          SKIP_FRAMES);
    } catch(IOException | RuntimeException ignored) {
      // class file not readable by ASM (e.g. unsupported class file version)
    }

    return new ClassLocation(sourceFile.get(), copyOf(lineNumbers));
  }
}
