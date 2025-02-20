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

package org.springframework.aop.framework.autoproxy;

import org.springframework.aop.framework.AbstractAdvisingBeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;

/**
 * Extension of {@link AbstractAutoProxyCreator} which implements {@link BeanFactoryAware},
 * adds exposure of the original target class for each proxied bean
 * ({@link AutoProxyUtils#ORIGINAL_TARGET_CLASS_ATTRIBUTE}),
 * and participates in an externally enforced target-class mode for any given bean
 * ({@link AutoProxyUtils#PRESERVE_TARGET_CLASS_ATTRIBUTE}).
 * This post-processor is therefore aligned with {@link AbstractAutoProxyCreator}.
 *
 * @author Juergen Hoeller
 * @since 4.2.3
 * @see AutoProxyUtils#shouldProxyTargetClass
 * @see AutoProxyUtils#determineTargetClass
 *
 * <br/>
 * 提供BeanFactory成员变量，用于子类扩展根据从BeanFactory中直接或间接获取Advisor，或者创建的Advisor与BeanFactory有关联
 */
@SuppressWarnings("serial")
public abstract class AbstractBeanFactoryAwareAdvisingPostProcessor extends AbstractAdvisingBeanPostProcessor
		implements BeanFactoryAware {

	@Nullable
	private ConfigurableListableBeanFactory beanFactory;


	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = (beanFactory instanceof ConfigurableListableBeanFactory clbf ? clbf : null);
	}

	@Override
	protected ProxyFactory prepareProxyFactory(Object bean, String beanName) {
		if (this.beanFactory != null) {
			//BeanDefinition中添加attribute=AutoProxyUtils.ORIGINAL_TARGET_CLASS_ATTRIBUTE
			AutoProxyUtils.exposeTargetClass(this.beanFactory, beanName, bean.getClass());
		}

		//如果proxyFactory.isProxyTargetClass=true，根据beanName获取BeanDefinition判断其attribute中
		//AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE=true？如果是，则设置直接代理目标类
		ProxyFactory proxyFactory = super.prepareProxyFactory(bean, beanName);
		if (!proxyFactory.isProxyTargetClass() && this.beanFactory != null &&
				AutoProxyUtils.shouldProxyTargetClass(this.beanFactory, beanName)) {
			proxyFactory.setProxyTargetClass(true);
		}
		return proxyFactory;
	}

	@Override
	protected boolean isEligible(Object bean, String beanName) {
		return (!AutoProxyUtils.isOriginalInstance(beanName, bean.getClass()) &&
				super.isEligible(bean, beanName));
	}

}
