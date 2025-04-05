/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.core.type;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * 定义类和类上注解[通过asm技术从字节码二进制流中解析得到、或反射]的相关访问方法
 */
public interface AnnotationMetadata extends ClassMetadata, AnnotatedTypeMetadata {


	/**
	 * 获取类上的所有注解的全限定类名（类上的直接注解）
	 * @return 注解全限定类名
	 */
	default Set<String> getAnnotationTypes() {
		return getAnnotations().stream()
				.filter(MergedAnnotation::isDirectlyPresent)
				.map(annotation -> annotation.getType().getName())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}


	/**
	 * 获取类上指定注解（annotationName）的所有元注解（注解的注解）
	 * @param annotationName 注解名
	 * @return 注解的元注解名
	 */
	default Set<String> getMetaAnnotationTypes(String annotationName) {
		MergedAnnotation<?> annotation = getAnnotations().get(annotationName, MergedAnnotation::isDirectlyPresent);
		if (!annotation.isPresent()) {
			return Collections.emptySet();
		}
		return MergedAnnotations.from(annotation.getType(), SearchStrategy.INHERITED_ANNOTATIONS).stream()
				.map(mergedAnnotation -> mergedAnnotation.getType().getName())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}


	/**
	 * 当前类是否有直接标注注解（annotationName）？
	 * @param annotationName 注解名
	 * @return true - 当前类上含annotationName注解
	 */
	default boolean hasAnnotation(String annotationName) {
		return getAnnotations().isDirectlyPresent(annotationName);
	}


	/**
	 * 当前类上的注解是否含有指定元注解（metaAnnotationName）？
	 * @param metaAnnotationName 元注解名称
	 * @return true - 类上的注解含有指定元注解
	 */
	default boolean hasMetaAnnotation(String metaAnnotationName) {
		return getAnnotations().get(metaAnnotationName,
				MergedAnnotation::isMetaPresent).isPresent();
	}


	/**
	 * 类的方法上是否含有注解（annotationName）？
	 * @param annotationName 注解名称
	 * @return true - 方法上含指定注解
	 */
	default boolean hasAnnotatedMethods(String annotationName) {
		return !getAnnotatedMethods(annotationName).isEmpty();
	}


	/**
	 * 获取类中含指定注解（annotationName）的所有方法
	 * @param annotationName 注解名
	 * @return 含指定注解名的所有方法
	 */
	Set<MethodMetadata> getAnnotatedMethods(String annotationName);

	/**
	 * 获取类的所有方法
	 * @return 方法
	 */
	Set<MethodMetadata> getDeclaredMethods();


	/**
	 * 使用标准反射为给定类创建新的AnnotationMetadata实例的工厂方法。
	 * @param type 类名
	 * @return 类名对应的AnnotationMetadata实例
	 */
	static AnnotationMetadata introspect(Class<?> type) {
		return StandardAnnotationMetadata.from(type);
	}

}
