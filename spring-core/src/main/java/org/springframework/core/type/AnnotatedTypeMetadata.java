/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.core.annotation.*;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;

import java.lang.annotation.Annotation;
import java.util.Map;


/**
 * 定义对特定目标（类或方法）上面注解的访问方法
 */
public interface AnnotatedTypeMetadata {

	/**
	 * 获取目标（类或方法）上的所有注解
	 * @return 直接标注的注解
	 */
	MergedAnnotations getAnnotations();


	/**
	 * 目标（类或方法）上是否含有注解（annotationName）
	 * @param annotationName 注解名称
	 * @return true - 目标（类或方法）上含指定注解名
	 */
	default boolean isAnnotated(String annotationName) {
		return getAnnotations().isPresent(annotationName);
	}


	/**
	 *  目标（类或方法）上注解（annotationName）的所有属性（若属性值被重写，以重写的属性为准[忽略重写前的属性值]）
	 * @param annotationName 注解名称
	 * @return 目标上指定注解的所有属性
	 */
	@Nullable
	default Map<String, Object> getAnnotationAttributes(String annotationName) {
		return getAnnotationAttributes(annotationName, false);
	}

	/**
	 * 目标（类或方法）上注解（annotationName）的所有属性（若属性值被重写，以重写的属性为准[忽略重写前的属性值]）
	 * @param annotationName 注解名称
	 * @param classValuesAsString  属性值是Class类型是否转为字符串？ true - 转为字符串（避免过早加载Class并初始化）
	 * @return 目标上指定注解的所有属性
	 */
	@Nullable
	default Map<String, Object> getAnnotationAttributes(String annotationName,
			boolean classValuesAsString) {

		MergedAnnotation<Annotation> annotation = getAnnotations().get(annotationName,
				null, MergedAnnotationSelectors.firstDirectlyDeclared());
		if (!annotation.isPresent()) {
			return null;
		}
		return annotation.asAnnotationAttributes(Adapt.values(classValuesAsString, true));
	}


	/**
	 * 目标（类或方法）上注解（annotationName）的所有属性（若属性值被重写，以原有属性为准[忽略重写后的属性值]）
	 * @param annotationName 注解名称
	 * @return 目标上指定注解的所有属性
	 */
	@Nullable
	default MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName) {
		return getAllAnnotationAttributes(annotationName, false);
	}

	/**
	 * 目标（类或方法）上注解（annotationName）的所有属性（若属性值被重写，以原有属性为准[忽略重写后的属性值]）
	 * @param annotationName 注解名称
	 * @param classValuesAsString 属性值是Class类型是否转为字符串？ true - 转为字符串（避免过早加载Class并初始化）
	 * @return 目标上指定注解的所有属性
	 */
	@Nullable
	default MultiValueMap<String, Object> getAllAnnotationAttributes(
			String annotationName, boolean classValuesAsString) {

		Adapt[] adaptations = Adapt.values(classValuesAsString, true);
		return getAnnotations().stream(annotationName)
				.filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
				.map(MergedAnnotation::withNonMergedAttributes)
				.collect(MergedAnnotationCollectors.toMultiValueMap(map ->
						map.isEmpty() ? null : map, adaptations));
	}
}
