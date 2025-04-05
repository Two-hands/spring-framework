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

package org.springframework.core.type.classreading;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * MetadataReaderFactory接口的简单实现，可根据全限定类名加载字节码文件二进制流，
 * 创建SimpleMetadataReader对象，通过asm框架解析二进制流获取类的基本信息
 *
 */
public class SimpleMetadataReaderFactory implements MetadataReaderFactory {

	private final ResourceLoader resourceLoader;


	/**
	 * Create a new SimpleMetadataReaderFactory for the default class loader.
	 */
	public SimpleMetadataReaderFactory() {
		this.resourceLoader = new DefaultResourceLoader();
	}

	/**
	 * Create a new SimpleMetadataReaderFactory for the given resource loader.
	 * @param resourceLoader the Spring ResourceLoader to use
	 * (also determines the ClassLoader to use)
	 */
	public SimpleMetadataReaderFactory(@Nullable ResourceLoader resourceLoader) {
		this.resourceLoader = (resourceLoader != null ? resourceLoader : new DefaultResourceLoader());
	}

	/**
	 * Create a new SimpleMetadataReaderFactory for the given class loader.
	 * @param classLoader the ClassLoader to use
	 */
	public SimpleMetadataReaderFactory(@Nullable ClassLoader classLoader) {
		this.resourceLoader =
				(classLoader != null ? new DefaultResourceLoader(classLoader) : new DefaultResourceLoader());
	}


	/**
	 * Return the ResourceLoader that this MetadataReaderFactory has been
	 * constructed with.
	 */
	public final ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}


	@Override
	public MetadataReader getMetadataReader(String className) throws IOException {
		try {
			// 构建资源的路径：拼接 classpath: + 全限定类名（将.替换为/） + .class
			// 如：className="org.springframework.Main" -> classpath:org/springframework/Main.class
			String resourcePath = ResourceLoader.CLASSPATH_URL_PREFIX +
					ClassUtils.convertClassNameToResourcePath(className) + ClassUtils.CLASS_FILE_SUFFIX;
			Resource resource = this.resourceLoader.getResource(resourcePath);
			//根据类资源创建SimpleMetadataReader
			return getMetadataReader(resource);
		}
		catch (FileNotFoundException ex) {
			//如果资源获取失败：可能是内部类，而内部类与外部内之间可能用英文点（.）分割，需要将其替换为$再进行重新拼接加载
			//如：如：className="org.springframework.Main.InnerClass" -> classpath:org/springframework/Main$InnerClass.class
			int lastDotIndex = className.lastIndexOf('.');
			if (lastDotIndex != -1) {
				String innerClassName =
						className.substring(0, lastDotIndex) + '$' + className.substring(lastDotIndex + 1);
				String innerClassResourcePath = ResourceLoader.CLASSPATH_URL_PREFIX +
						ClassUtils.convertClassNameToResourcePath(innerClassName) + ClassUtils.CLASS_FILE_SUFFIX;
				Resource innerClassResource = this.resourceLoader.getResource(innerClassResourcePath);
				if (innerClassResource.exists()) {
					return getMetadataReader(innerClassResource);
				}
			}
			throw ex;
		}
	}

	@Override
	public MetadataReader getMetadataReader(Resource resource) throws IOException {
		//根据字节码流解析出AnnotationMetadata
		return new SimpleMetadataReader(resource, this.resourceLoader.getClassLoader());
	}

}
