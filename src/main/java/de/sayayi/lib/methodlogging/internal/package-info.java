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
 * Internal implementation of method logging: the Spring configuration, the analysis of logged methods and the method
 * interceptor. This package is not exported and is not part of the API.
 * <p>
 * {@code @EnableMethodLogging} imports the {@link MethodLoggingConfigurationSelector}, which selects the
 * {@link ProxyMethodLoggingConfiguration} or the {@link AspectJMethodLoggingConfiguration}, depending on the advice
 * mode. Both extend {@link MethodLoggingConfiguration}, which provides the beans shared by both modes.
 * <p>
 * The {@link AnnotationMethodLoggingSource} analyzes the methods annotated with {@code @MethodLogging} and resolves
 * their settings into method definitions ({@link MethodDef}) and parameter definitions ({@link ParameterDef}). The
 * {@link MethodLoggingInterceptor} uses these definitions to log the invocations of the methods. For AspectJ based
 * method logging, the {@link AspectJMethodInterceptor} holds the interceptor used by the aspect.
 * <p>
 * The {@link AnnotationMethodLoggingSource} analyzes the parameters with the {@link ParameterAnalyzer} and reads the
 * source locations of the methods with {@link ClassLocation}. The {@link DefaultMessageSupport} creates the default
 * message support, using the parameter formatters of the package {@code de.sayayi.lib.methodlogging.format}.
 *
 * @see AnnotationMethodLoggingSource
 * @see MethodLoggingInterceptor
 *
 * @since 0.1.0
 */
package de.sayayi.lib.methodlogging.internal;
