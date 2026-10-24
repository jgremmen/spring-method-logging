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
 * AspectJ support for method logging, used with
 * {@link de.sayayi.lib.methodlogging.annotation.EnableMethodLogging#mode() @EnableMethodLogging(mode = ASPECTJ)}.
 * <p>
 * The {@link de.sayayi.lib.methodlogging.aspectj.MethodLoggingAspect MethodLoggingAspect} is applied to methods
 * annotated with {@link de.sayayi.lib.methodlogging.annotation.MethodLogging @MethodLogging} by load-time weaving (this
 * library provides a {@code META-INF/aop.xml} declaring the aspect) or by compile-time weaving. Contrary to proxy based
 * method logging, calls within the same class are logged as well. The aspect is configured by the Spring application
 * context enabling method logging.
 * <p>
 * This package requires the AspectJ runtime, which is an optional dependency of this library.
 *
 * @see de.sayayi.lib.methodlogging.aspectj.MethodLoggingAspect
 */
package de.sayayi.lib.methodlogging.aspectj;
