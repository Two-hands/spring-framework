/*
 * Copyright 2002-2021 the original author or authors.
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

/**
 * 定义方法[通过asm技术从字节码二进制流中解析得到、或反射]的方法的相关信息
 */
public interface MethodMetadata extends AnnotatedTypeMetadata {

	/**
	 * 获取方法名称
	 * @return 方法名
	 */
	String getMethodName();


	/**
	 * 获取方法所在的类名称
	 * @return 方法所在的类名称
	 */
	String getDeclaringClassName();


	/**
	 * 获取方法返回类型的名称
	 * @return 返回类型名称
	 */
	String getReturnTypeName();


	/**
	 * 是否为抽象方法？
	 * @return  true - 抽象方法
	 */
	boolean isAbstract();

	/**
	 * 是否为静态方法？
	 * @return true - 静态方法
	 */
	boolean isStatic();

	/**
	 * 方法是否有final
	 * @return  true - 方法含final
	 */
	boolean isFinal();

	/**
	 * Determine whether the underlying method is overridable,
	 * i.e. not marked as static, final, or private.
	 */
	/**
	 * 方法是否可重写（方法不含static、final、private关键字）？
	 * @return true - 方法可重写
	 */
	boolean isOverridable();

}
