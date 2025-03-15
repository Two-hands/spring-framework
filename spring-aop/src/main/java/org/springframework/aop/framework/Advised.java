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

package org.springframework.aop.framework;

import org.aopalliance.aop.Advice;

import org.springframework.aop.Advisor;
import org.springframework.aop.TargetClassAware;
import org.springframework.aop.TargetSource;

/**
 *
 * <pre>
 *    实现此接口的类将拥有"操作和管理"AOP工厂代理配置。
 *    如果向Advised中添加Advice，最终会转换为Advisor（如：DefaultIntroductionAdvisor、DefaultPointcutAdvisor...）进行保。
 *
 * 说明：
 * Advice（通知）：表示在特定的连接点（join point）上采取的操作
 * Advisor（通知者）：Advisor充当Advice和Pointcut的适配器（Advisor持有Advice‌）
 * Advised（配置）：提供了操作和管理Advice和Advisor的能力
 * </pre>
 */
public interface Advised extends TargetClassAware {

	/**
	 * Advised配置是否被冻结？
	 * @return true - 冻结，此时无法再对Advice做改变
	 */
	boolean isFrozen();


	/**
	 * 直接代理目标类，而不是接口？
	 * @return true - 直接代理目标类
	 */
	boolean isProxyTargetClass();


	/**
	 * 获取被AOP代理（增强）的所有接口
	 * @return 返回已经被代理增强的所有接口
	 */
	Class<?>[] getProxiedInterfaces();


	/**
	 * 检测给定接口是否被代理（增强）
	 * @param intf  被检测的接口
	 * @return true - intf已经被代理（增强）
	 */
	boolean isInterfaceProxied(Class<?> intf);


	/**
	 * 设置被代理目标源（可能是目标对象或目标接口），当且仅当frozen=false时才能进行设置
	 * @param targetSource 被代理目标对象
	 */
	void setTargetSource(TargetSource targetSource);


	/**
	 * 获取被代理目标源
	 */
	TargetSource getTargetSource();

	/**
	 * 设置AOP框架是否应该将[代理对象]通过AopContext中的ThreadLocal进行暴露
	 *    如：可以在目标对象方法中获取"目标对象的代理对象"
	 * @param exposeProxy 暴露代理对象？ true - 可以根据AopContext#currentProxy方法在被代理对象自身方法中获取其代理对象引用
	 */
	void setExposeProxy(boolean exposeProxy);

	/**
	 * AOP框架是否应该将[代理对象]通过AopContext中的ThreadLocal进行暴露？
	 * @return true - 可以根据AopContext#currentProxy方法在被代理对象自身方法中获取其代理对象引用
	 */
	boolean isExposeProxy();

	/**
	 * Set whether this proxy configuration is pre-filtered so that it only
	 * contains applicable advisors (matching this proxy's target class).
	 * <p>Default is "false". Set this to "true" if the advisors have been
	 * pre-filtered already, meaning that the ClassFilter check can be skipped
	 * when building the actual advisor chain for proxy invocations.
	 * @see org.springframework.aop.ClassFilter
	 */
	void setPreFiltered(boolean preFiltered);

	/**
	 * Return whether this proxy configuration is pre-filtered so that it only
	 * contains applicable advisors (matching this proxy's target class).
	 */
	boolean isPreFiltered();

	/**
	 * 返回代理对象中所有起增强作用的advisors
	 * @return 代理对象中起增强作用的所有advisors
	 */
	Advisor[] getAdvisors();

	/**
	 * 返回代理对象中起增强作用的advisors的数量
	 * @return 起作用的advisors数量
	 */
	default int getAdvisorCount() {
		return getAdvisors().length;
	}


	/**
	 * 向advisors链末尾新增一个advisor
	 */
	void addAdvisor(Advisor advisor) throws AopConfigException;

	/**
	 * 向advisors链的指定位置添加一个advisor
	 * @param pos 放置的位置
	 * @param advisor 新增的advisor
	 */
	void addAdvisor(int pos, Advisor advisor) throws AopConfigException;

	/**
	 * 从advisors链中移除指定的advisor
	 * @param advisor 要被移除的advisor
	 * @return 是否移除成功？true - 移除成功 false - 未找到
	 */
	boolean removeAdvisor(Advisor advisor);


	/**
	 * 从advisors链中移除指定位置的advisor
	 * @param index 要被移除的advisor的位置
	 */
	void removeAdvisor(int index) throws AopConfigException;


	/**
	 * 返回指定的advisor在advisors链中的位置
	 * @param advisor 要被定位的advisor
	 * @return 指定advisor的位置，-1表示未找到
	 */
	int indexOf(Advisor advisor);

	/**
	 * 使用Advisor b 替换掉advisors链中的 Advisor a，注意：当Advisor是IntroductionAdvisor时，代理功能需要按照新的Advisor而取消旧的Advisor
	 * @param a 旧Advisor
	 * @param b 新Advisor
	 * @return true - 替换成功，false - 旧Advisor未找到，无法替换
	 */
	boolean replaceAdvisor(Advisor a, Advisor b) throws AopConfigException;

	/**
	 * 向advisors链末尾添加一个新的advice（需要被包装为DefaultPointcutAdvisor - 其pointcut默认都起作用）
	 * @param advice 新增的advice
	 */
	void addAdvice(Advice advice) throws AopConfigException;


	/**
	 * 向advisors链中的指定位置添加一个advice（需要被包装为DefaultPointcutAdvisor）
	 * @param pos 添加的位置
	 * @param advice 新增的advice
	 */
	void addAdvice(int pos, Advice advice) throws AopConfigException;

	/**
	 * 从advisors链中移除指定的advice（因为Advisor中含advice，可以通过比对找到）
	 * @param advice 含此advice的advisor将从advisors链中移除
	 * @return true - 移除成功，false - 未找到
	 */
	boolean removeAdvice(Advice advice);


	/**
	 * 返回指定advice（因为Advisor中含advice，可以通过比对找到）在advisors链中的位置
	 * @param advice 指定的advice
	 * @return 含此advice的advisor在advisors链中的位置，-1为未找到
	 */
	int indexOf(Advice advice);

	/**
	 * As {@code toString()} will normally be delegated to the target,
	 * this returns the equivalent for the AOP proxy.
	 * @return a string description of the proxy configuration
	 */
	String toProxyConfigString();

}
