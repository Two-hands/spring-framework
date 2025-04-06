/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.core.env;

import org.springframework.lang.Nullable;


/**
 * 解析属性
 */
public interface PropertyResolver {

	/**
	 * 是否含key对应的属性？true - 含有其对应的属性值
	 */
	boolean containsProperty(String key);

	/**
	 * 根据key获取其对应的属性值
	 */
	@Nullable
	String getProperty(String key);


	/**
	 * 获取key对应的属性值，若属性值不存在则返回默认值defaultValue
	 */
	String getProperty(String key, String defaultValue);


	/**
	 * 获取key对应的属性值（属性值的类型为targetType）
	 */
	@Nullable
	<T> T getProperty(String key, Class<T> targetType);


	/**
	 * 获取key对应的属性值（属性值的类型为targetType），若不存在返回默认值defaultValue
	 */
	<T> T getProperty(String key, Class<T> targetType, T defaultValue);


	/**
	 * 获取key对应的属性值，该值不存在时抛异常
	 */
	String getRequiredProperty(String key) throws IllegalStateException;

	/**
	 * 获取key对应的属性值（属性值的类型为targetType），该值不存在时抛异常
	 */
	<T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException;

	/**
	 * <pre>
	 * 解析给定的文本内容中的占位符（${}包裹的内容），以占位符作为key获取其属性值并进行替换，不可解
	 * 析的占位符（key没有对应属性值）将原封不动的返回
	 *    如：text为xx${name}，当name的属性值为Michael,返回值为xxMichael，当没有对应属性值时，返回xx${name}
	 * </pre>
	 */
	String resolvePlaceholders(String text);

	/**
	 * <pre>
	 * 解析给定的文本内容中的占位符（${}包裹的内容），以占位符作为key获取其属性值并进行替换，不可解
	 * 析的占位符（key没有对应属性值）将抛异常
	 *    如：text为xx${name}，当name的属性值为Michael,返回值为xxMichael，当没有对应属性值时，会抛IllegalArgumentException异常
	 * </pre>
	 */
	String resolveRequiredPlaceholders(String text) throws IllegalArgumentException;

}
