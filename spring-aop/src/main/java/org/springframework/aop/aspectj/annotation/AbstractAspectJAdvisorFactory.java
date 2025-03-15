/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.aspectj.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.PerClauseKind;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Abstract base class for factories that can create Spring AOP Advisors
 * given AspectJ classes from classes honoring the AspectJ 5 annotation syntax.
 *
 * <p>This class handles annotation parsing and validation functionality.
 * It does not actually generate Spring AOP Advisors, which is deferred to subclasses.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 2.0
 *
 * <br/>
 * 定义切面类（或其父类）必须有@Aspect注解，同时定义了获取和解析方法上的AspectJ注解属性的基础功能
 */
public abstract class AbstractAspectJAdvisorFactory implements AspectJAdvisorFactory {

	/*
	注解的正常执行顺序：
      @Around -> @Before -> method -> @Around -> @After -> @AfterReturning

    注解的正常执行顺序：
      @Around -> @Before -> method -> @Around -> @After -> @AfterThrowing
	 */
	private static final Class<?>[] ASPECTJ_ANNOTATION_CLASSES = new Class<?>[] {
			Pointcut.class, Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class};


	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/*
	AspectJ注解（按解析顺序排列）：@Pointcut、@Around、@Before、@After、@AfterReturning、@AfterThrowing
	获取AspectJ注解的argNames属性值作为匹配方法的参数名
	 */
	protected final ParameterNameDiscoverer parameterNameDiscoverer = new AspectJAnnotationParameterNameDiscoverer();


	@Override
	public boolean isAspect(Class<?> clazz) {
		return (AnnotationUtils.findAnnotation(clazz, Aspect.class) != null);
	}

	@Override
	public void validate(Class<?> aspectClass) throws AopConfigException {
		AjType<?> ajType = AjTypeSystem.getAjType(aspectClass);
		if (!ajType.isAspect()) {
			throw new NotAnAtAspectException(aspectClass);
		}
		if (ajType.getPerClause().getKind() == PerClauseKind.PERCFLOW) {
			throw new AopConfigException(aspectClass.getName() + " uses percflow instantiation model: " +
					"This is not supported in Spring AOP.");
		}
		if (ajType.getPerClause().getKind() == PerClauseKind.PERCFLOWBELOW) {
			throw new AopConfigException(aspectClass.getName() + " uses percflowbelow instantiation model: " +
					"This is not supported in Spring AOP.");
		}
	}


	/**
	 * 从方法中获取[@Around、@Before、@After、@AfterReturning、@AfterReturning]注解，将其解析并封装为AspectJAnnotation对象
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	protected static AspectJAnnotation findAspectJAnnotationOnMethod(Method method) {
		for (Class<?> annotationType : ASPECTJ_ANNOTATION_CLASSES) {
			AspectJAnnotation annotation = findAnnotation(method, (Class<Annotation>) annotationType);
			if (annotation != null) {
				return annotation;
			}
		}
		return null;
	}

	@Nullable
	private static AspectJAnnotation findAnnotation(Method method, Class<? extends Annotation> annotationType) {
		Annotation annotation = AnnotationUtils.findAnnotation(method, annotationType);
		if (annotation != null) {
			return new AspectJAnnotation(annotation);
		}
		else {
			return null;
		}
	}


	/**
	 * Enum for AspectJ annotation types.
	 * @see AspectJAnnotation#getAnnotationType()
	 */
	protected enum AspectJAnnotationType {

		AtPointcut, AtAround, AtBefore, AtAfter, AtAfterReturning, AtAfterThrowing
	}


	/**
	 * Class modeling an AspectJ annotation, exposing its type enumeration and
	 * pointcut String.
	 *
	 * <br/>
	 * 存储AspectJ注解的解析结果，含：原注解对象，AspectJ注解类型，切入点表达式、方法参数名称定义
	 */
	protected static class AspectJAnnotation {

		private static final String[] EXPRESSION_ATTRIBUTES = {"pointcut", "value"};

		private static final Map<Class<?>, AspectJAnnotationType> annotationTypeMap = Map.of(
				Pointcut.class, AspectJAnnotationType.AtPointcut, //
				Around.class, AspectJAnnotationType.AtAround, //
				Before.class, AspectJAnnotationType.AtBefore, //
				After.class, AspectJAnnotationType.AtAfter, //
				AfterReturning.class, AspectJAnnotationType.AtAfterReturning, //
				AfterThrowing.class, AspectJAnnotationType.AtAfterThrowing //
			);

		private final Annotation annotation;

		private final AspectJAnnotationType annotationType;

		private final String pointcutExpression;

		private final String argumentNames;

		public AspectJAnnotation(Annotation annotation) {
			this.annotation = annotation;
			this.annotationType = determineAnnotationType(annotation);
			try {
				this.pointcutExpression = resolvePointcutExpression(annotation);
				Object argNames = AnnotationUtils.getValue(annotation, "argNames");
				this.argumentNames = (argNames instanceof String names ? names : "");
			}
			catch (Exception ex) {
				throw new IllegalArgumentException(annotation + " is not a valid AspectJ annotation", ex);
			}
		}

		private AspectJAnnotationType determineAnnotationType(Annotation annotation) {
			AspectJAnnotationType type = annotationTypeMap.get(annotation.annotationType());
			if (type != null) {
				return type;
			}
			throw new IllegalStateException("Unknown annotation type: " + annotation);
		}

		private String resolvePointcutExpression(Annotation annotation) {
			for (String attributeName : EXPRESSION_ATTRIBUTES) {
				Object val = AnnotationUtils.getValue(annotation, attributeName);
				if (val instanceof String str && !str.isEmpty()) {
					return str;
				}
			}
			throw new IllegalStateException("Failed to resolve pointcut expression in: " + annotation);
		}

		public AspectJAnnotationType getAnnotationType() {
			return this.annotationType;
		}

		public Annotation getAnnotation() {
			return this.annotation;
		}

		public String getPointcutExpression() {
			return this.pointcutExpression;
		}

		public String getArgumentNames() {
			return this.argumentNames;
		}

		@Override
		public String toString() {
			return this.annotation.toString();
		}
	}


	/**
	 * ParameterNameDiscoverer implementation that analyzes the arg names
	 * specified at the AspectJ annotation level.
	 *
	 * <br/>
	 * 获取方法上AspectJ注解的argNames属性值作为匹配方法的参数名
	 */
	private static class AspectJAnnotationParameterNameDiscoverer implements ParameterNameDiscoverer {

		private static final String[] EMPTY_ARRAY = new String[0];

		@Override
		@Nullable
		public String[] getParameterNames(Method method) {
			if (method.getParameterCount() == 0) {
				return EMPTY_ARRAY;
			}
			AspectJAnnotation annotation = findAspectJAnnotationOnMethod(method);
			if (annotation == null) {
				return null;
			}
			StringTokenizer nameTokens = new StringTokenizer(annotation.getArgumentNames(), ",");
			int numTokens = nameTokens.countTokens();
			if (numTokens > 0) {
				String[] names = new String[numTokens];
				for (int i = 0; i < names.length; i++) {
					names[i] = nameTokens.nextToken();
				}
				return names;
			}
			else {
				return null;
			}
		}

		@Override
		@Nullable
		public String[] getParameterNames(Constructor<?> ctor) {
			throw new UnsupportedOperationException("Spring AOP cannot handle constructor advice");
		}
	}

}
