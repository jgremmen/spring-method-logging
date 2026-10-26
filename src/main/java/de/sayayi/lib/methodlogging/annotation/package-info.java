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

/**
 * Annotations enabling and configuring method logging.
 * <p>
 * {@link EnableMethodLogging @EnableMethodLogging} enables method logging for a Spring application context. Methods
 * annotated with {@link MethodLogging @MethodLogging} are logged; the settings are resolved from the method annotation,
 * the {@link MethodLoggingConfig @MethodLoggingConfig} annotation of the class and the
 * {@link de.sayayi.lib.methodlogging.MethodLoggingConfigurer MethodLoggingConfigurer}, in that order.
 * {@link ParamLog @ParamLog} controls the logging of individual parameters.
 *
 * @since 0.1.0
 */
package de.sayayi.lib.methodlogging.annotation;
