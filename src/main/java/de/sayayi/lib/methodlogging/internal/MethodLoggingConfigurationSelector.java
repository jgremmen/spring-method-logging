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

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;


/**
 * Selects the method logging configuration to import, depending on {@link EnableMethodLogging#mode()}.
 *
 * @author Jeroen Gremmen
 * @since 0.5.1
 */
public final class MethodLoggingConfigurationSelector extends AdviceModeImportSelector<EnableMethodLogging>
{
  @Override
  protected @NotNull String[] selectImports(@NotNull AdviceMode adviceMode)
  {
    return switch(adviceMode) {
      case PROXY -> new String[] { AutoProxyRegistrar.class.getName(), ProxyMethodLoggingConfiguration.class.getName() };
      case ASPECTJ -> new String[] { AspectJMethodLoggingConfiguration.class.getName() };
    };
  }
}
