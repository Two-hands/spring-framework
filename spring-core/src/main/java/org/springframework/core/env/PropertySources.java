/*
 * Copyright 2002-2018 the original author or authors.
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

import java.util.stream.Stream;
import java.util.stream.StreamSupport;


/**
 * 持有一个或多个PropertySource
 */
public interface PropertySources extends Iterable<PropertySource<?>> {

	/**
	 * Return a sequential {@link Stream} containing the property sources.
	 * @since 5.1
	 */
	default Stream<PropertySource<?>> stream() {
		return StreamSupport.stream(spliterator(), false);
	}


	/**
	 * 是否含name[属性源名称，唯一标识]的属性源？
	 * @param name 属性源标识名称
	 * @return true - 包含name的属性源
	 */
	boolean contains(String name);

	/**
	 * 通过name[属性源名称，唯一标识]获取属性源
	 * @param name 属性源标识名称
	 * @return 属性源
	 */
	@Nullable
	PropertySource<?> get(String name);

}
