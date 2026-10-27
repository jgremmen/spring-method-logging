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
package de.sayayi.lib.methodlogging;


/**
 * Controls the visibility of an optional feature in method logging. Each feature can be explicitly shown or hidden, or
 * left at {@link #DEFAULT} to inherit the setting from the class-level
 * {@link de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig MethodLoggingConfig} or the global configurer
 * defaults. A feature without a configurer default takes the default of its {@code MethodLoggingConfig} attribute.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
public enum Visibility
{
  /** Inherit the visibility from the class-level or global configuration. */
  DEFAULT,

  /** Explicitly enable the feature. */
  SHOW,

  /** Explicitly disable the feature. */
  HIDE
}
