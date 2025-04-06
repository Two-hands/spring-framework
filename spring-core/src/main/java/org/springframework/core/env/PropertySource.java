/*
 * Copyright 2002-2023 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * <pre>
 * 提供k-v属性的基类（v可以是任意类型，如{@link java.util.Map}，{@link java.util.Properties}）;
 *
 * {@code PropertySource}不会单独使用，而是通过{@link PropertySources}聚合多个PropertySource，
 * 并通过{@link PropertyResolver}实现基于优先级的搜索功能（根据k搜索解析获取v）;
 *
 * {@code PropertySource}是通过其name唯一标识确定的，不是通过其属性值内容决定，可以通过@Configuration配置
 * 类上的@PropertySource向Environmen环境中添加PropertySource
 * </pre>
 */
public abstract class PropertySource<T> {

	protected final Log logger = LogFactory.getLog(getClass());

	//唯一标识一个PropertySource
	protected final String name;

	//属性集合
	protected final T source;


	/**
	 * Create a new {@code PropertySource} with the given name and source object.
	 * @param name the associated name
	 * @param source the source object
	 */
	public PropertySource(String name, T source) {
		Assert.hasText(name, "Property source name must contain at least one character");
		Assert.notNull(source, "Property source must not be null");
		this.name = name;
		this.source = source;
	}

	/**
	 * Create a new {@code PropertySource} with the given name and with a new
	 * {@code Object} instance as the underlying source.
	 * <p>Often useful in testing scenarios when creating anonymous implementations
	 * that never query an actual source but rather return hard-coded values.
	 */
	@SuppressWarnings("unchecked")
	public PropertySource(String name) {
		this(name, (T) new Object());
	}


	/**
	 * 获取PropertySource的标识
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * 获取PropertySource底层的属性集合
	 */
	public T getSource() {
		return this.source;
	}


	/**
	 * PropertySource中是否包含key对应的属性值？
	 * @param name  属性名称key
	 * @return true - 含有属性值
	 */
	public boolean containsProperty(String name) {
		return (getProperty(name) != null);
	}

	/**
	 * 根据key从属性源中获取匹配的属性值
	 * @param name 属性名称key
	 * @return 属性值
	 */
	@Nullable
	public abstract Object getProperty(String name);



	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof PropertySource<?> that &&
				ObjectUtils.nullSafeEquals(getName(), that.getName())));
	}


	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(getName());
	}



	@Override
	public String toString() {
		if (logger.isDebugEnabled()) {
			return getClass().getSimpleName() + "@" + System.identityHashCode(this) +
					" {name='" + getName() + "', properties=" + getSource() + "}";
		}
		else {
			return getClass().getSimpleName() + " {name='" + getName() + "'}";
		}
	}


	/**
	 * 返回ComparisonPropertySource实例，仅用于比较，
	 * 比如：{@link MutablePropertySources#addBefore}与{@link MutablePropertySources#addAfter}
	 */
	public static PropertySource<?> named(String name) {
		return new ComparisonPropertySource(name);
	}


	/**
	 * 用作占位符，用于在应用程序上下文创建时暂时替代无法立即初始化的实际属性源，如：基于ServletContext的属性源必须要等到
	 * ServletContext可用后才可以添加（在AbstractApplicationContext#refresh后替换）
	 * 见：AbstractApplicationContext#initPropertySources()
	 */
	public static class StubPropertySource extends PropertySource<Object> {

		public StubPropertySource(String name) {
			super(name);
		}

		/**
		 * Always returns {@code null}.
		 */
		@Override
		@Nullable
		public String getProperty(String name) {
			return null;
		}
	}


	/**
	 * 用于比较目的
	 */
	static class ComparisonPropertySource extends StubPropertySource {

		private static final String USAGE_ERROR =
				"ComparisonPropertySource instances are for use with collection comparison only";

		public ComparisonPropertySource(String name) {
			super(name);
		}

		@Override
		public Object getSource() {
			throw new UnsupportedOperationException(USAGE_ERROR);
		}

		@Override
		public boolean containsProperty(String name) {
			throw new UnsupportedOperationException(USAGE_ERROR);
		}

		@Override
		@Nullable
		public String getProperty(String name) {
			throw new UnsupportedOperationException(USAGE_ERROR);
		}
	}

}
