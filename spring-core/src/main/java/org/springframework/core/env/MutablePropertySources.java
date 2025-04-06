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

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * PropertySources接口的默认实现。允许操作拥有的属性源
 * 可以定义属性源的优先级（通过addFirst和addLast等方法）
 */
public class MutablePropertySources implements PropertySources {

	//属性源集合
	private final List<PropertySource<?>> propertySourceList = new CopyOnWriteArrayList<>();



	public MutablePropertySources() {
	}


	public MutablePropertySources(PropertySources propertySources) {
		this();
		for (PropertySource<?> propertySource : propertySources) {
			addLast(propertySource);
		}
	}


	@Override
	public Iterator<PropertySource<?>> iterator() {
		return this.propertySourceList.iterator();
	}

	@Override
	public Spliterator<PropertySource<?>> spliterator() {
		return Spliterators.spliterator(this.propertySourceList, 0);
	}

	@Override
	public Stream<PropertySource<?>> stream() {
		return this.propertySourceList.stream();
	}

	@Override
	public boolean contains(String name) {
		for (PropertySource<?> propertySource : this.propertySourceList) {
			if (propertySource.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	@Override
	@Nullable
	public PropertySource<?> get(String name) {
		for (PropertySource<?> propertySource : this.propertySourceList) {
			if (propertySource.getName().equals(name)) {
				return propertySource;
			}
		}
		return null;
	}


	/**
	 * 【添加】propertySource到属性源集合中，并且当前属性源拥有[最高优先级]
	 * @param propertySource 新增的高优先级属性源
	 */
	public void addFirst(PropertySource<?> propertySource) {
		synchronized (this.propertySourceList) {
			//存在：先移除，再添加
			removeIfPresent(propertySource);
			this.propertySourceList.add(0, propertySource);
		}
	}


	/**
	 * 【添加】propertySource到属性源集合中，并且当前属性源拥有[最低优先级]
	 * @param propertySource 新增的低优先级属性源
	 */
	public void addLast(PropertySource<?> propertySource) {
		synchronized (this.propertySourceList) {
			removeIfPresent(propertySource);
			this.propertySourceList.add(propertySource);
		}
	}

	/**
	 * 【添加】新的属性源，优先级高于relativePropertySourceName对应的属性源
	 * @param relativePropertySourceName 已经含有的属性源的名称
	 * @param propertySource 新增的属性源
	 */
	public void addBefore(String relativePropertySourceName, PropertySource<?> propertySource) {
		//relativePropertySourceName不能与propertySource.name相等
		assertLegalRelativeAddition(relativePropertySourceName, propertySource);
		synchronized (this.propertySourceList) {
			removeIfPresent(propertySource);
			//找到指定name对应的属性源的位置
			int index = assertPresentAndGetIndex(relativePropertySourceName);
			//将新属性源放置在指定位置，原有位置及其后面的属性源向后顺移
			addAtIndex(index, propertySource);
		}
	}

	/**
	 * 【添加】新的属性源，优先级低于relativePropertySourceName对应的属性源
	 * @param relativePropertySourceName 已经含有的属性源的名称
	 * @param propertySource 新增的属性源
	 */
	public void addAfter(String relativePropertySourceName, PropertySource<?> propertySource) {
		assertLegalRelativeAddition(relativePropertySourceName, propertySource);
		synchronized (this.propertySourceList) {
			removeIfPresent(propertySource);
			//找到指定name对应的属性源的位置
			int index = assertPresentAndGetIndex(relativePropertySourceName);
			//将新属性源放置在指定位置后面，原index + 1位置及其后面的属性源向后顺移
			addAtIndex(index + 1, propertySource);
		}
	}


	/**
	 * 返回propertySource属性源所在的位置，返回-1表示不存在
	 */
	public int precedenceOf(PropertySource<?> propertySource) {
		return this.propertySourceList.indexOf(propertySource);
	}


	/**
	 * 【移除】name[属性源名称，唯一标识]对应的属性源
	 * @param name 属性源名称
	 * @return 移除的属性源，为null时表示不存在
	 */
	@Nullable
	public PropertySource<?> remove(String name) {
		synchronized (this.propertySourceList) {
			int index = this.propertySourceList.indexOf(PropertySource.named(name));
			return (index != -1 ? this.propertySourceList.remove(index) : null);
		}
	}


	/**
	 * 【替换】name名称的旧属性源，使用新属性源propertySource代替，name不存在时报错
	 * @param name 已存在的属性源名称
	 * @param propertySource 新的属性源
	 */
	public void replace(String name, PropertySource<?> propertySource) {
		synchronized (this.propertySourceList) {
			int index = assertPresentAndGetIndex(name);
			this.propertySourceList.set(index, propertySource);
		}
	}


	public int size() {
		return this.propertySourceList.size();
	}

	@Override
	public String toString() {
		return this.propertySourceList.toString();
	}



	protected void assertLegalRelativeAddition(String relativePropertySourceName, PropertySource<?> propertySource) {
		String newPropertySourceName = propertySource.getName();
		if (relativePropertySourceName.equals(newPropertySourceName)) {
			throw new IllegalArgumentException(
					"PropertySource named '" + newPropertySourceName + "' cannot be added relative to itself");
		}
	}


	protected void removeIfPresent(PropertySource<?> propertySource) {
		this.propertySourceList.remove(propertySource);
	}


	private void addAtIndex(int index, PropertySource<?> propertySource) {
		removeIfPresent(propertySource);
		this.propertySourceList.add(index, propertySource);
	}


	private int assertPresentAndGetIndex(String name) {
		int index = this.propertySourceList.indexOf(PropertySource.named(name));
		if (index == -1) {
			throw new IllegalArgumentException("PropertySource named '" + name + "' does not exist");
		}
		return index;
	}

}
