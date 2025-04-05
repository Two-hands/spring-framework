/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.core.type.classreading;

import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * 工厂方法模式：定义了一个创建对象的接口，但由子类决定实例化哪一个类
 * 用于创建MetadataReader实例（不同类型的MetadataReader实例由不同的工厂子类负责）的工厂接口
 */
public interface MetadataReaderFactory {

	/**
	 * 返回新建MetadataReader实例解析读取className
	 */
	MetadataReader getMetadataReader(String className) throws IOException;

	/**
	 * 返回新建MetadataReader实例从resource获取并解析
	 */
	MetadataReader getMetadataReader(Resource resource) throws IOException;

}
