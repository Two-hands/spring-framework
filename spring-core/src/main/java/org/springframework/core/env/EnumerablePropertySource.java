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

import org.springframework.util.ObjectUtils;

/**
 * 能够枚举底层属性源对象的所有k-v，提供{@link #getPropertyNames()}方法获取所有k集合（不用访问底层属性源对象），比
 * {@link #containsProperty(String)}方法判断k-v是否存在更高效
 */
public abstract class EnumerablePropertySource<T> extends PropertySource<T> {


	public EnumerablePropertySource(String name, T source) {
		super(name, source);
	}

	/**
	 * 底层是Object对象实例
	 * @param name 属性源名称
	 */
	protected EnumerablePropertySource(String name) {
		super(name);
	}


	/**
	 * 判断当前PropertySource是否含指定key属性（这里通过枚举底层属性源的所有的keys进行判断）
	 */
	@Override
	public boolean containsProperty(String name) {
		return ObjectUtils.containsElement(getPropertyNames(), name);
	}


	/**
	 * 枚举底层属性源的所有keys，可以缓存起来提高效率（如果底层属性集合不会被修改的话）
	 */
	public abstract String[] getPropertyNames();

}
