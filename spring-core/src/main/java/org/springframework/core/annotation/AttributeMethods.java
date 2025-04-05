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

package org.springframework.core.annotation;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;


/**
 * <pre>
 * 提供一种快速方法给定注解类型{@link Annotation}的合法的属性方法[按一定顺序，不一定是其定义顺序]的工具
 * <b>注意：若注解的某个属性方法methodA返回值是Class&lt;T&gt类型（T表示任意类型），只有当methodA方法被调用时（直接调用或反射调用该方法），才会触发T类型的加载</b>
 *  </pre>
 */
final class AttributeMethods {

	static final AttributeMethods NONE = new AttributeMethods(null, new Method[0]);

	//全局，缓存解析过的注解
	static final Map<Class<? extends Annotation>, AttributeMethods> cache = new ConcurrentReferenceHashMap<>();

	private static final Comparator<Method> methodComparator = (m1, m2) -> {
		if (m1 != null && m2 != null) {
			return m1.getName().compareTo(m2.getName());
		}
		return (m1 != null ? -1 : 1);
	};


	// 注解类型
	@Nullable
	private final Class<? extends Annotation> annotationType;

	// 注解的所有合法属性方法（按定义顺序，数组元素值非null）
	private final Method[] attributeMethods;

	//记录每个位置的属性方法是否需要抛异常？位置元素为true[返回类型是Class或Class数组]表示需要
	private final boolean[] canThrowTypeNotPresentException;

	// 是否至少有一个属性方法定义了默认值？true - 有一个属性方法有默认值
	private final boolean hasDefaultValueMethod;

	// 是否至少有一个属性方法的返回类型是Annotation或Annotation数组？ true - 有属性方法含Class类型
	private final boolean hasNestedAnnotation;


	private AttributeMethods(@Nullable Class<? extends Annotation> annotationType, Method[] attributeMethods) {
		this.annotationType = annotationType;
		this.attributeMethods = attributeMethods;
		this.canThrowTypeNotPresentException = new boolean[attributeMethods.length];
		boolean foundDefaultValueMethod = false;
		boolean foundNestedAnnotation = false;
		for (int i = 0; i < attributeMethods.length; i++) {
			Method method = this.attributeMethods[i];
			Class<?> type = method.getReturnType();
			if (!foundDefaultValueMethod && (method.getDefaultValue() != null)) {
				//发现有属性方法有默认返回值
				foundDefaultValueMethod = true;
			}
			if (!foundNestedAnnotation && (type.isAnnotation() || (type.isArray() && type.getComponentType().isAnnotation()))) {
				//发现有属性方法返回类型是注解或注解数组
				foundNestedAnnotation = true;
			}
			ReflectionUtils.makeAccessible(method);
			//注意：注解中属性返回类型是Class<T>时（T为任意类型），当T不存在时，不会影响加载此属性方法
			// 属性方法返回类型是Class或Class数组，记录此方法可以抛异常
			this.canThrowTypeNotPresentException[i] = (type == Class.class || type == Class[].class || type.isEnum());
		}
		this.hasDefaultValueMethod = foundDefaultValueMethod;
		this.hasNestedAnnotation = foundNestedAnnotation;
	}


	/**
	 * Determine if values from the given annotation can be safely accessed without
	 * causing any {@link TypeNotPresentException TypeNotPresentExceptions}.
	 * <p>This method is designed to cover Google App Engine's late arrival of such
	 * exceptions for {@code Class} values (instead of the more typical early
	 * {@code Class.getAnnotations() failure} on a regular JVM).
	 * @param annotation the annotation to check
	 * @return {@code true} if all values are present
	 * @see #validate(Annotation)
	 */
	boolean canLoad(Annotation annotation) {
		assertAnnotation(annotation);
		for (int i = 0; i < size(); i++) {
			if (canThrowTypeNotPresentException(i)) {
				try {
					AnnotationUtils.invokeAnnotationMethod(get(i), annotation);
				}
				catch (IllegalStateException ex) {
					// Plain invocation failure to expose -> leave up to attribute retrieval
					// (if any) where such invocation failure will be logged eventually.
				}
				catch (Throwable ex) {
					// TypeNotPresentException etc. -> annotation type not actually loadable.
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Check if values from the given annotation can be safely accessed without causing
	 * any {@link TypeNotPresentException TypeNotPresentExceptions}.
	 * <p>This method is designed to cover Google App Engine's late arrival of such
	 * exceptions for {@code Class} values (instead of the more typical early
	 * {@code Class.getAnnotations() failure} on a regular JVM).
	 * @param annotation the annotation to validate
	 * @throws IllegalStateException if a declared {@code Class} attribute could not be read
	 * @see #canLoad(Annotation)
	 */
	void validate(Annotation annotation) {
		assertAnnotation(annotation);
		for (int i = 0; i < size(); i++) {
			if (canThrowTypeNotPresentException(i)) {
				try {
					AnnotationUtils.invokeAnnotationMethod(get(i), annotation);
				}
				catch (IllegalStateException ex) {
					throw ex;
				}
				catch (Throwable ex) {
					throw new IllegalStateException("Could not obtain annotation attribute value for " +
							get(i).getName() + " declared on " + annotation.annotationType(), ex);
				}
			}
		}
	}

	private void assertAnnotation(Annotation annotation) {
		Assert.notNull(annotation, "Annotation must not be null");
		if (this.annotationType != null) {
			Assert.isInstanceOf(this.annotationType, annotation);
		}
	}

	/**
	 * Get the attribute with the specified name or {@code null} if no
	 * matching attribute exists.
	 * @param name the attribute name to find
	 * @return the attribute method or {@code null}
	 */
	@Nullable
	Method get(String name) {
		int index = indexOf(name);
		return (index != -1 ? this.attributeMethods[index] : null);
	}

	/**
	 * Get the attribute at the specified index.
	 * @param index the index of the attribute to return
	 * @return the attribute method
	 * @throws IndexOutOfBoundsException if the index is out of range
	 * ({@code index < 0 || index >= size()})
	 */
	Method get(int index) {
		return this.attributeMethods[index];
	}

	/**
	 * Determine if the attribute at the specified index could throw a
	 * {@link TypeNotPresentException} when accessed.
	 * @param index the index of the attribute to check
	 * @return {@code true} if the attribute can throw a
	 * {@link TypeNotPresentException}
	 */
	boolean canThrowTypeNotPresentException(int index) {
		return this.canThrowTypeNotPresentException[index];
	}

	/**
	 * Get the index of the attribute with the specified name, or {@code -1}
	 * if there is no attribute with the name.
	 * @param name the name to find
	 * @return the index of the attribute, or {@code -1}
	 */
	int indexOf(String name) {
		for (int i = 0; i < this.attributeMethods.length; i++) {
			if (this.attributeMethods[i].getName().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Get the index of the specified attribute, or {@code -1} if the
	 * attribute is not in this collection.
	 * @param attribute the attribute to find
	 * @return the index of the attribute, or {@code -1}
	 */
	int indexOf(Method attribute) {
		for (int i = 0; i < this.attributeMethods.length; i++) {
			if (this.attributeMethods[i].equals(attribute)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Get the number of attributes in this collection.
	 * @return the number of attributes
	 */
	int size() {
		return this.attributeMethods.length;
	}

	/**
	 * Determine if at least one of the attribute methods has a default value.
	 * @return {@code true} if there is at least one attribute method with a default value
	 */
	boolean hasDefaultValueMethod() {
		return this.hasDefaultValueMethod;
	}

	/**
	 * Determine if at least one of the attribute methods is a nested annotation.
	 * @return {@code true} if there is at least one attribute method with a nested
	 * annotation type
	 */
	boolean hasNestedAnnotation() {
		return this.hasNestedAnnotation;
	}



	/**
	 * 获取给定注解类型的所有属性方法
	 * @param annotationType 注解类型
	 * @return 注解的属性方法
	 */
	static AttributeMethods forAnnotationType(@Nullable Class<? extends Annotation> annotationType) {
		if (annotationType == null) {
			return NONE;
		}
		//解析过后进行缓存
		return cache.computeIfAbsent(annotationType, AttributeMethods::compute);
	}

	/**
	 * 解析给定注解类型，获取其所有的属性方法
	 * @param annotationType 需要解析属性方法的注解类型
	 * @return 属性方法集合（包含注解类型及其合法的属性方法）
	 */
	private static AttributeMethods compute(Class<? extends Annotation> annotationType) {
		//通过返回获取[注解]声明的所有属性方法（Class#getDeclaredMethods）
		Method[] methods = annotationType.getDeclaredMethods();
		int size = methods.length;
		// 跳过methods中的有参的方法 和 返回类型是void的方法
		for (int i = 0; i < methods.length; i++) {
			if (!isAttributeMethod(methods[i])) {
				methods[i] = null;
				size--;
			}
		}
		if (size == 0) {
			return NONE;
		}

		//方法排序（按方法名字典顺序排序，null值向后移）
		Arrays.sort(methods, methodComparator);
		//重新分配数组，去除null元素
		Method[] attributeMethods = Arrays.copyOf(methods, size);
		//将[注解]类型与其合法的属性方法一起封装成AttributeMethods对象
		return new AttributeMethods(annotationType, attributeMethods);
	}

	/**
	 * 判断给定方法是否为注解的属性方法？ - 判断条件：无参方法且有返回类型[不为void]
	 * @param method 需要判断的方法
	 * @return true - 为注解属性方法
	 */
	private static boolean isAttributeMethod(Method method) {
		return (method.getParameterCount() == 0 && method.getReturnType() != void.class);
	}

	/**
	 * Create a description for the given attribute method suitable to use in
	 * exception messages and logs.
	 * @param attribute the attribute to describe
	 * @return a description of the attribute
	 */
	static String describe(@Nullable Method attribute) {
		if (attribute == null) {
			return "(none)";
		}
		return describe(attribute.getDeclaringClass(), attribute.getName());
	}

	/**
	 * Create a description for the given attribute method suitable to use in
	 * exception messages and logs.
	 * @param annotationType the annotation type
	 * @param attributeName the attribute name
	 * @return a description of the attribute
	 */
	static String describe(@Nullable Class<?> annotationType, @Nullable String attributeName) {
		if (attributeName == null) {
			return "(none)";
		}
		String in = (annotationType != null ? " in annotation [" + annotationType.getName() + "]" : "");
		return "attribute '" + attributeName + "'" + in;
	}

}
