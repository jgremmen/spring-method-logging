package de.sayayi.lib.methodlogging.annotation;

import de.sayayi.lib.methodlogging.internal.EnableMethodLoggingSelector;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.springframework.context.annotation.AdviceMode.PROXY;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;


/**
 * Enables Spring's annotation-driven method logging capability. To be used together
 * with @{@link org.springframework.context.annotation.Configuration Configuration}
 * classes as follows:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableMethodLogging
 * public class AppConfig implements MethodLoggingConfigurer
 * {
 *   &#064;Override
 *   public MessageContext messageContext()
 *   {
 *     return new MessageContext(new DefaultFormatterService(),
 *         new MessageFactory(new LRUMessagePartNormalizer(64)));
 *   }
 *
 *   &#064;Override
 *   public MethodLoggerFactoryDelegate methodLoggerFactory()
 *   {
 *     // this application only uses log4j2
 *     return Log4J2MethodLogger.FIELD_FACTORY;
 *   }
 * }</pre>
 *
 * {@code @EnableMethodLogging} is responsible for registering the necessary Spring
 * components that power annotation-driven method logging, such as the
 * proxy- or AspectJ-based advice that weaves the interceptor into the call stack when
 * {@link de.sayayi.lib.methodlogging.annotation.MethodLogging @MethodLogging} methods are invoked.
 *
 * <p>The {@link #mode} attribute controls how advice is applied: If the mode is
 * {@link AdviceMode#PROXY} (the default), then the other attributes control the behavior
 * of the proxying. Please note that proxy mode allows for interception of calls through
 * the proxy only; local calls within the same class cannot get intercepted that way.
 *
 * <p>Note that if the {@linkplain #mode} is set to {@link AdviceMode#ASPECTJ}, then the
 * value of the {@link #proxyTargetClass} attribute will be ignored. Note also that in
 * this case the {@code spring-aspects} module JAR must be present on the classpath, with
 * compile-time weaving or load-time weaving applying the aspect to the affected classes.
 * There is no proxy involved in such a scenario; local calls will be intercepted as well.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see de.sayayi.lib.methodlogging.MethodLoggingConfigurer
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
@Import(EnableMethodLoggingSelector.class)
public @interface EnableMethodLogging
{
  /**
   * Indicate whether subclass-based (CGLIB) proxies are to be created as opposed
   * to standard Java interface-based proxies. The default is {@code false}. <strong>
   * Applicable only if {@link #mode()} is set to {@link AdviceMode#PROXY}</strong>.
   * <p>Note that setting this attribute to {@code true} will affect <em>all</em>
   * Spring-managed beans requiring proxying, not just those marked with {@code @MethodLogging}.
   * For example, other beans marked with Spring's {@code @Transactional} annotation will
   * be upgraded to subclass proxying at the same time. This approach has no negative
   * impact in practice unless one is explicitly expecting one type of proxy vs another,
   * e.g. in tests.
   */
  boolean proxyTargetClass() default false;


  /**
   * Indicate how method logging advice should be applied.
   * <p><b>The default is {@link AdviceMode#PROXY}.</b>
   * Please note that proxy mode allows for interception of calls through the proxy
   * only. Local calls within the same class cannot get intercepted that way;
   * a method logging annotation on such a method within a local call will be ignored
   * since Spring's interceptor does not even kick in for such a runtime scenario.
   * For a more advanced mode of interception, consider switching this to
   * {@link AdviceMode#ASPECTJ}.
   */
  AdviceMode mode() default PROXY;


  /**
   * Indicate the ordering of the execution of the method logging advisor
   * when multiple advices are applied at a specific joinpoint.
   * <p>The default is {@link Ordered#LOWEST_PRECEDENCE}.
   */
  int order() default LOWEST_PRECEDENCE;
}