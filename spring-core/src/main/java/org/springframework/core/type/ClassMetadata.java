/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core.type;

import org.springframework.lang.Nullable;


/**
 * 定义类[通过asm技术从字节码二进制流中解析得到、或反射]的相关信息
 */
public interface ClassMetadata {

	/**
	 * 返回全限定类名
	 * @return 全限定类型
	 */
	String getClassName();


	/**
	 * 是否为接口？
	 * @return true - 接口类
	 */
	boolean isInterface();


	/**
	 * 是否为注解类型？
	 * @return true - 注解类
	 */
	boolean isAnnotation();


	/**
	 * 是否为抽象类？
	 * @return true - 抽象类
	 */
	boolean isAbstract();


	/**
	 * 是否为具体类（非抽象、接口）
	 * @return true - 具体实现类
	 */
	default boolean isConcrete() {
		return !(isInterface() || isAbstract());
	}


	/**
	 * 类是否被final修饰？
	 * @return true - 类被final修饰
	 */
	boolean isFinal();


	/**
	 * 是否为顶级类或静态内部类
	 * @return true - 不是非静态内部类
	 */
	boolean isIndependent();

	/**
	 * 当前类是否有外部类（即当前类是内部类）？
	 * @return true - 当前类有外部类
	 */
	default boolean hasEnclosingClass() {
		return (getEnclosingClassName() != null);
	}

	/**
	 * Return the name of the enclosing class of the underlying class,
	 * or {@code null} if the underlying class is a top-level class.
	 */
	/**
	 * 若当前类是内部类，返回其外部类名称
	 * @return 外部类名称
	 */
	@Nullable
	String getEnclosingClassName();

	/**
	 * 类是否有父类？
	 * @return true - 有（只有Object没有父类）
	 */
	default boolean hasSuperClass() {
		return (getSuperClassName() != null);
	}

	/**
	 * 类的父类名称
	 * @return 直接继承的父类名称
	 */
	@Nullable
	String getSuperClassName();

	/**
	 * 类的接口
	 * @return  直接实现的接口
	 */
	String[] getInterfaceNames();

	/**
	 * 获取类的内部类名称
	 * @return 类的直接定义的所有内部类名称
	 */
	String[] getMemberClassNames();

}
