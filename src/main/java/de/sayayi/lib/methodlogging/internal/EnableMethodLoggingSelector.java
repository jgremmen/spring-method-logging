/*
 * Copyright 2022 Jeroen Gremmen
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

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public final class EnableMethodLoggingSelector implements ImportSelector
{
  @Override
  public @NotNull String[] selectImports(@NotNull AnnotationMetadata importingClassMetadata)
  {
    return new String[] {
      AutoProxyRegistrar.class.getName(),
      MethodLoggingConfiguration.class.getName()
    };
  }
}
