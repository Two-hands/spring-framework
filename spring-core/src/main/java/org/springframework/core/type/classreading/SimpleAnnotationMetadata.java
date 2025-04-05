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

package org.springframework.core.type.classreading;

import org.springframework.asm.Opcodes;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <pre>
 * 基于asm技术从字节码二进制流中解析出的类的信息，其包含：
 *   1、类的基本信息，如：类名、方法标志、直接父类、直接接口、内部类
 *   2、类的方法（MethodMetadata）
 *   3、类上的注解（MergedAnnotations）
 *
 * <b> 快速获取指定类的字节码中的重要数据，无需经过类加载器加载 </b>
 * </pre>
 */
final class SimpleAnnotationMetadata implements AnnotationMetadata {

	//类名
	private final String className;

	//类的访问标志
	private final int access;

	//当前内部类的外部类名称
	@Nullable
	private final String enclosingClassName;

	//类的直接父类
	@Nullable
	private final String superClassName;

	//当前类是静态内部类？true - 是
	private final boolean independentInnerClass;


	//类的直接实现接口
	private final Set<String> interfaceNames;


	//类中的内部类
	private final Set<String> memberClassNames;

	//类中的所有方法
	private final Set<MethodMetadata> declaredMethods;

	//类上的注解
	private final MergedAnnotations annotations;


	//类上的注解的全限定类名（懒初始化）
	@Nullable
	private Set<String> annotationTypes;


	SimpleAnnotationMetadata(String className, int access, @Nullable String enclosingClassName,
			@Nullable String superClassName, boolean independentInnerClass, Set<String> interfaceNames,
			Set<String> memberClassNames, Set<MethodMetadata> declaredMethods, MergedAnnotations annotations) {

		this.className = className;
		this.access = access;
		this.enclosingClassName = enclosingClassName;
		this.superClassName = superClassName;
		this.independentInnerClass = independentInnerClass;
		this.interfaceNames = interfaceNames;
		this.memberClassNames = memberClassNames;
		this.declaredMethods = declaredMethods;
		this.annotations = annotations;
	}

	@Override
	public String getClassName() {
		return this.className;
	}

	@Override
	public boolean isInterface() {
		return (this.access & Opcodes.ACC_INTERFACE) != 0;
	}

	@Override
	public boolean isAnnotation() {
		return (this.access & Opcodes.ACC_ANNOTATION) != 0;
	}

	@Override
	public boolean isAbstract() {
		return (this.access & Opcodes.ACC_ABSTRACT) != 0;
	}

	@Override
	public boolean isFinal() {
		return (this.access & Opcodes.ACC_FINAL) != 0;
	}

	@Override
	public boolean isIndependent() {
		return (this.enclosingClassName == null || this.independentInnerClass);
	}

	@Override
	@Nullable
	public String getEnclosingClassName() {
		return this.enclosingClassName;
	}

	@Override
	@Nullable
	public String getSuperClassName() {
		return this.superClassName;
	}

	@Override
	public String[] getInterfaceNames() {
		return StringUtils.toStringArray(this.interfaceNames);
	}

	@Override
	public String[] getMemberClassNames() {
		return StringUtils.toStringArray(this.memberClassNames);
	}

	@Override
	public MergedAnnotations getAnnotations() {
		return this.annotations;
	}

	@Override
	public Set<String> getAnnotationTypes() {
		Set<String> annotationTypes = this.annotationTypes;
		if (annotationTypes == null) {
			annotationTypes = Collections.unmodifiableSet(
					AnnotationMetadata.super.getAnnotationTypes());
			this.annotationTypes = annotationTypes;
		}
		return annotationTypes;
	}

	@Override
	public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
		Set<MethodMetadata> result = new LinkedHashSet<>(4);
		for (MethodMetadata annotatedMethod : this.declaredMethods) {
			if (annotatedMethod.isAnnotated(annotationName)) {
				result.add(annotatedMethod);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	@Override
	public Set<MethodMetadata> getDeclaredMethods() {
		return Collections.unmodifiableSet(this.declaredMethods);
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof SimpleAnnotationMetadata that && this.className.equals(that.className)));
	}

	@Override
	public int hashCode() {
		return this.className.hashCode();
	}

	@Override
	public String toString() {
		return this.className;
	}

}
