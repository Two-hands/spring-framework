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

package org.springframework.aop.framework;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.SmartClassLoader;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for {@link BeanPostProcessor} implementations that apply a
 * Spring AOP {@link Advisor} to specific beans.
 *
 * @author Juergen Hoeller
 * @since 3.2
 *
 * <br/>
 * 主要把某个Advisor应用到特定(某些)的beans【生成代理对象，仅含一个Advisor】
 * Advisor从哪里来，如何创建由子类决定
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisingBeanPostProcessor extends ProxyProcessorSupport
		implements SmartInstantiationAwareBeanPostProcessor {

	@Nullable
	protected Advisor advisor;

	protected boolean beforeExistingAdvisors = false;

	private final Map<Class<?>, Boolean> eligibleBeans = new ConcurrentHashMap<>(256);


	/**
	 * Set whether this post-processor's advisor is supposed to apply before
	 * existing advisors when encountering a pre-advised object.
	 * <p>Default is "false", applying the advisor after existing advisors, i.e.
	 * as close as possible to the target method. Switch this to "true" in order
	 * for this post-processor's advisor to wrap existing advisors as well.
	 * <p>Note: Check the concrete post-processor's javadoc whether it possibly
	 * changes this flag by default, depending on the nature of its advisor.
	 */
	public void setBeforeExistingAdvisors(boolean beforeExistingAdvisors) {
		this.beforeExistingAdvisors = beforeExistingAdvisors;
	}


	/**
	 * 判断当前给定beanClass是否需要代理？需要代理则返回代理对象类型，否则返回原类型
	 */
	@Override
	public Class<?> determineBeanType(Class<?> beanClass, String beanName) {
		if (this.advisor != null && isEligible(beanClass)) {
			//advisor可以应用到当前beanClass，需要代理，创建ProxyFactory生成代理对象类型
			ProxyFactory proxyFactory = new ProxyFactory();
			proxyFactory.copyFrom(this);
			proxyFactory.setTargetClass(beanClass);

			if (!proxyFactory.isProxyTargetClass()) {
				//未指定ProxyFactory.proxyTargetClass（默认为false），尝试根据目标对象判断：
				// 1、含有合理的接口，使用JDK代理（代理对象实现这些接口）
				// 2、没有合理的接口，使用CGLIB代理（代理对象直接继承目标对象）
				evaluateProxyInterfaces(beanClass, proxyFactory);
			}
			//添加advisor
			proxyFactory.addAdvisor(this.advisor);
			//扩展自定义advised
			customizeProxyFactory(proxyFactory);

			ClassLoader classLoader = getProxyClassLoader();
			if (classLoader instanceof SmartClassLoader smartClassLoader &&
					classLoader != beanClass.getClassLoader()) {
				classLoader = smartClassLoader.getOriginalClassLoader();
			}
			//设置加载器，获取代理对象类型
			return proxyFactory.getProxyClass(classLoader);
		}

		return beanClass;
	}

	/**
	 * bean实例化后，如果bean是Advised类型，尝试向bean（Advised#addAdvisor）添加这个advisor，
	 * 如果需要代理bean，则创建代理对象并返回
	 * @param bean   目标对象
	 * @param beanName   bean名称
	 * @return  原目标对象或代理对象
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (this.advisor == null || bean instanceof AopInfrastructureBean) {
			//没有advisor、或bean是AopInfrastructureBean类型，忽略...
			return bean;
		}

		if (bean instanceof Advised advised) {
			//代理对象会基础Advised接口（见AopProxyUtils#completeProxiedInterfaces）
			//当前bean已经是代理对象，若advisor可以应用到目标对象且advised.frozen=false，则将advisor添加到代理对象中...
			if (!advised.isFrozen() && isEligible(AopUtils.getTargetClass(bean))) {
				//是否需要放在advisor链的最前面？
				if (this.beforeExistingAdvisors) {
					advised.addAdvisor(0, this.advisor);
				}
				else if (advised.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE &&
						advised.getAdvisorCount() > 0) {
					//没有发现目标对象，将当前advisor放入倒入第二个位置（最后一个向后移动）
					//TODO 最后一个位置的advisor有特别作用？？？
					advised.addAdvisor(advised.getAdvisorCount() - 1, this.advisor);
					return bean;
				}
				else {
					//直接添加到最后
					advised.addAdvisor(this.advisor);
				}
				return bean;
			}
		}


		if (isEligible(bean, beanName)) {
			//bean是非代理对象，创建代理对象
			ProxyFactory proxyFactory = prepareProxyFactory(bean, beanName);
			if (!proxyFactory.isProxyTargetClass()) {
				evaluateProxyInterfaces(bean.getClass(), proxyFactory);
			}
			proxyFactory.addAdvisor(this.advisor);
			customizeProxyFactory(proxyFactory);

			// Use original ClassLoader if bean class not locally loaded in overriding class loader
			ClassLoader classLoader = getProxyClassLoader();
			if (classLoader instanceof SmartClassLoader smartClassLoader &&
					classLoader != bean.getClass().getClassLoader()) {
				classLoader = smartClassLoader.getOriginalClassLoader();
			}
			return proxyFactory.getProxy(classLoader);
		}

		//不需要代理
		return bean;
	}

	/**
	 * Check whether the given bean is eligible for advising with this
	 * post-processor's {@link Advisor}.
	 * <p>Delegates to {@link #isEligible(Class)} for target class checking.
	 * Can be overridden e.g. to specifically exclude certain beans by name.
	 * <p>Note: Only called for regular bean instances but not for existing
	 * proxy instances which implement {@link Advised} and allow for adding
	 * the local {@link Advisor} to the existing proxy's {@link Advisor} chain.
	 * For the latter, {@link #isEligible(Class)} is being called directly,
	 * with the actual target class behind the existing proxy (as determined
	 * by {@link AopUtils#getTargetClass(Object)}).
	 * @param bean the bean instance
	 * @param beanName the name of the bean
	 * @see #isEligible(Class)
	 */
	protected boolean isEligible(Object bean, String beanName) {
		return isEligible(bean.getClass());
	}

	/**
	 * Check whether the given class is eligible for advising with this
	 * post-processor's {@link Advisor}.
	 * <p>Implements caching of {@code canApply} results per bean target class.
	 * @param targetClass the class to check against
	 * @see AopUtils#canApply(Advisor, Class)
	 */
	protected boolean isEligible(Class<?> targetClass) {
		Boolean eligible = this.eligibleBeans.get(targetClass);
		if (eligible != null) {
			return eligible;
		}
		if (this.advisor == null) {
			return false;
		}

		//检测targetClass是否满足advisor增强条件？如果满足 - 将advice应用到targetClass
		eligible = AopUtils.canApply(this.advisor, targetClass);
		this.eligibleBeans.put(targetClass, eligible);
		return eligible;
	}

	/**
	 * Prepare a {@link ProxyFactory} for the given bean.
	 * <p>Subclasses may customize the handling of the target instance and in
	 * particular the exposure of the target class. The default introspection
	 * of interfaces for non-target-class proxies and the configured advisor
	 * will be applied afterwards; {@link #customizeProxyFactory} allows for
	 * late customizations of those parts right before proxy creation.
	 * @param bean the bean instance to create a proxy for
	 * @param beanName the corresponding bean name
	 * @return the ProxyFactory, initialized with this processor's
	 * {@link ProxyConfig} settings and the specified bean
	 * @since 4.2.3
	 * @see #customizeProxyFactory
	 */
	protected ProxyFactory prepareProxyFactory(Object bean, String beanName) {
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.copyFrom(this);
		proxyFactory.setTarget(bean);
		return proxyFactory;
	}

	/**
	 * Subclasses may choose to implement this: for example,
	 * to change the interfaces exposed.
	 * <p>The default implementation is empty.
	 * @param proxyFactory the ProxyFactory that is already configured with
	 * target, advisor and interfaces and will be used to create the proxy
	 * immediately after this method returns
	 * @since 4.2.3
	 * @see #prepareProxyFactory
	 */
	protected void customizeProxyFactory(ProxyFactory proxyFactory) {
	}

}
