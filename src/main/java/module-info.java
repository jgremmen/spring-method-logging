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
 * Spring Method Logging provides annotation-driven method entry/exit logging for Spring applications.
 * <p>
 * Annotate methods with {@code @MethodLogging} to automatically log method entry, exit, parameter values, return
 * values and elapsed time - without writing any logging boilerplate. The library integrates with Spring AOP and
 * supports multiple logging frameworks (SLF4J, Logback, Log4j2, JUL, JBoss Logging, Tinylog, and JCL).
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>
 *     {@code de.sayayi.lib.methodlogging} - Core interfaces:
 *     {@link de.sayayi.lib.methodlogging.MethodLogger MethodLogger} for delegating log output,
 *     {@link de.sayayi.lib.methodlogging.MethodLoggerFactory MethodLoggerFactory} for creating logger instances and
 *     {@link de.sayayi.lib.methodlogging.MethodLoggingConfigurer MethodLoggingConfigurer} for customizing global
 *     defaults such as log levels, message formatting and parameter exclusion.
 *   </li>
 *   <li>
 *     {@code de.sayayi.lib.methodlogging.annotation} - Annotations to enable and configure method logging:
 *     {@link de.sayayi.lib.methodlogging.annotation.EnableMethodLogging @EnableMethodLogging} activates the feature,
 *     {@link de.sayayi.lib.methodlogging.annotation.MethodLogging @MethodLogging} marks methods for logging,
 *     {@link de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig @MethodLoggingConfig} sets class-level
 *     defaults and {@link de.sayayi.lib.methodlogging.annotation.ParamLog @ParamLog} controls per-parameter log
 *     formatting.
 *   </li>
 *   <li>
 *     {@code de.sayayi.lib.methodlogging.logger} - Built-in {@link de.sayayi.lib.methodlogging.MethodLoggerFactory}
 *     implementations for each supported logging framework.
 *   </li>
 * </ul>
 *
 * @author Jeroen Gremmen
 * @since 0.5.0
 */
module de.sayayi.lib.methodlogging
{
  requires de.sayayi.lib.message;
  requires spring.aop;
  requires spring.beans;
  requires spring.context;
  requires spring.core;

  requires static java.logging;
  requires static ch.qos.logback.classic;
  requires static ch.qos.logback.core;
  requires static org.apache.logging.log4j;
  requires static org.jboss.logging;
  requires static org.jetbrains.annotations;
  requires static org.slf4j;
  requires static org.tinylog.api;
  requires static spring.jcl;

  exports de.sayayi.lib.methodlogging;
  exports de.sayayi.lib.methodlogging.annotation;
  exports de.sayayi.lib.methodlogging.logger;
}
