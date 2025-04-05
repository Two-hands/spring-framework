/*
 * Copyright 2002-2022 the original author or authors.
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

import org.springframework.asm.ClassReader;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * <pre>
 * 用于读取访问元数据：通过SimpleAnnotationMetadataReadingVisitor获取类元数据部分视图，然后返回视图信息
 * 通过ASM框架读取class元数比直接通过类加载器加载class相比优势在于：
 *   1、在某些情况下，只需要class部分元数据，使用asm更高效（无需将类完全加载，加载涉及到动态链接，父类、接口等关联信息的联动加载）
 *   2、某个类的内部类可能涉及到或依赖到的其他类可能并无法加载（类无法找到），asm可避免此类问题（类加载器加载class类无法避免这个问题）
 * </pre>
 */
final class SimpleMetadataReader implements MetadataReader {

	private static final int PARSING_OPTIONS = ClassReader.SKIP_DEBUG
			| ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES;

	private final Resource resource;

	/**
	 * 根据字节码文件解析出的结果
	 */
	private final AnnotationMetadata annotationMetadata;

	/**
	 * 通过读取字节码二进制流数据，记录重要数据的偏移量，并使用visitor结合偏移量信息获取Class类的元数据
	 * Class类的元数据包含但不限于：类上的注解信息、类名称信息、类访问信息、超类信息、接口信息、成员类信息、方法信息...
	 * @param resource Class类资源（用于获取Class的二进制流数据）
	 * @param classLoader 类加载器
	 * @throws IOException io异常
	 */
	SimpleMetadataReader(Resource resource, @Nullable ClassLoader classLoader) throws IOException {

		SimpleAnnotationMetadataReadingVisitor visitor = new SimpleAnnotationMetadataReadingVisitor(classLoader);
		//解析Class二进制流数据，从字节码二进制流中获取类、方法、注解的基本信息
		//PARSING_OPTIONS - 忽略解析字节码中的DEBUG、CODE、FRAME信息
		getClassReader(resource).accept(visitor, PARSING_OPTIONS);
		this.resource = resource;
		this.annotationMetadata = visitor.getMetadata();
	}

	private static ClassReader getClassReader(Resource resource) throws IOException {
		try (InputStream is = resource.getInputStream()) {
			try {
				return new ClassReader(is);
			}
			catch (IllegalArgumentException ex) {
				throw new IOException("ASM ClassReader failed to parse class file - " +
						"probably due to a new Java class file version that isn't supported yet: " + resource, ex);
			}
		}
	}


	@Override
	public Resource getResource() {
		return this.resource;
	}

	@Override
	public ClassMetadata getClassMetadata() {
		return this.annotationMetadata;
	}

	@Override
	public AnnotationMetadata getAnnotationMetadata() {
		return this.annotationMetadata;
	}

}
