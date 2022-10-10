package de.sayayi.lib.methodlogging.internal;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;

import static lombok.AccessLevel.PROTECTED;


@RequiredArgsConstructor(access = PROTECTED)
@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public final class BeanFactoryMethodLoggingAdvisor extends AbstractBeanFactoryPointcutAdvisor
{
  private final AnnotationMethodLoggingSource annotationMethodLoggingSource;


  @Autowired
  public void setAdvice(MethodLoggingInterceptor methodLoggingInterceptor) {
    super.setAdvice(methodLoggingInterceptor);
  }


  @Override
  public @NotNull Pointcut getPointcut()
  {
    return new StaticMethodMatcherPointcut() {
      @Override
      public boolean matches(@NotNull Method method, @NotNull Class<?> targetClass)
      {
        return annotationMethodLoggingSource != null &&
               annotationMethodLoggingSource.getMethodLoggingDefinition(method, targetClass).isPresent();
      }
    };
  }
}