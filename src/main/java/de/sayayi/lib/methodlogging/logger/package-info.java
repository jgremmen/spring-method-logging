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
 * Method logger factories for the supported logging frameworks.
 * <p>
 * {@link AutoDetectLoggerFactory} (the default) selects the logging framework by the type of the logger held by the
 * logger field, including the JDK platform logger ({@link System.Logger}), and falls back to a JCL logger. The other
 * factories ({@link JBossLoggerFactory}, {@link JCLLoggerFactory}, {@link JULLoggerFactory},
 * {@link Log4j2LoggerFactory}, {@link LogbackLoggerFactory}, {@link Slf4jLoggerFactory} and
 * {@link TinylogLoggerFactory}) support a single logging framework each and require it to be present at runtime; they
 * are created with {@code withFallback()}, using a logger for the target class if no logger field is available, or
 * {@code strict()}, requiring a logger field. Custom factories can extend {@link AbstractMethodLoggerFactory}, which
 * caches the created method loggers.
 * <p>
 * The {@link de.sayayi.lib.methodlogging.MethodLogger MethodLogger} implementations bridging to the logging frameworks
 * are internal to this package.
 *
 * @since 0.1.0
 */
package de.sayayi.lib.methodlogging.logger;
