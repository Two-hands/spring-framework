/*
 * Copyright 2002-2009 the original author or authors.
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
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;


/**
 * 一个简单的外观接口，定义了访问类的元信息[通过asm解析字节码二进制流得到的数据]
 */
public interface MetadataReader {


	/**
	 * 获取类文件关联的resource
	 * @return  className关联的具体resource（如：ClasspathResource）
	 */
	Resource getResource();

	/**
	 * 获取类元数据
	 * @return 类元数据
	 */
	ClassMetadata getClassMetadata();

	/**
	 * 获取类及其注解的元数据
	 * @return 类及其注解的元数据
	 */
	AnnotationMetadata getAnnotationMetadata();

}
