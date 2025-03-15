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

import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * 持有AopProxyFactory，用于用于创建AopProxy对象，进而创建代理对象
 * AopProxy可分为JDK和CGLIB两大方向，里面包含了Advised的配置，根据配置中的目标源、接口、Advisors等构建代理对象方法的增强逻辑...
 *
 * <ul>
 * 在AopProxy中构建代理对象的执行逻辑：
 *     <li>JDK的AopProxy会构建InvocationHandle#invoke方法：将Advisors转为Interceptor，尝试构建MethodInvocation执行链完成整个功能增强和目标对象方法的调用</li>
 *     <li>CGLIB的AopProxy会构建Callback对象，将Advisors转为Interceptor，尝试构建MethodInvocation执行链完成整个功能增强和目标对象方法的调用</li>
 * </ul>
 *
 * 代理工厂的基类：
 *    持有AopProxyFactory用于创建AopProxy实例（自身作为AopProxyFactory创建代理对象的入参[AdvisedSupport - Advised配置]）
 *    并且提供一个机会在第一个AopProxy实例创建前修改Advised配置（模式：观察者模式）
 *
 *    AopProxyFactory通常是DefaultAopProxyFactory类型，提供JDK和CGLIB方式创建代理对象
 */
@SuppressWarnings("serial")
public class ProxyCreatorSupport extends AdvisedSupport {

	/**
	 * 根据Advised配置生成AopProxy对象（JDK、CGLIB）
	 */
	private AopProxyFactory aopProxyFactory;

	private final List<AdvisedSupportListener> listeners = new ArrayList<>();

	/** Set to true when the first AOP proxy has been created. */
	private boolean active = false;


	/**
	 * Create a new ProxyCreatorSupport instance.
	 */
	public ProxyCreatorSupport() {
		this.aopProxyFactory = DefaultAopProxyFactory.INSTANCE;
	}

	/**
	 * Create a new ProxyCreatorSupport instance.
	 * @param aopProxyFactory the AopProxyFactory to use
	 */
	public ProxyCreatorSupport(AopProxyFactory aopProxyFactory) {
		Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
		this.aopProxyFactory = aopProxyFactory;
	}


	/**
	 * Customize the AopProxyFactory, allowing different strategies
	 * to be dropped in without changing the core framework.
	 * <p>Default is {@link DefaultAopProxyFactory}, using dynamic JDK
	 * proxies or CGLIB proxies based on the requirements.
	 */
	public void setAopProxyFactory(AopProxyFactory aopProxyFactory) {
		Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
		this.aopProxyFactory = aopProxyFactory;
	}

	/**
	 * Return the AopProxyFactory that this ProxyConfig uses.
	 */
	public AopProxyFactory getAopProxyFactory() {
		return this.aopProxyFactory;
	}

	/**
	 * Add the given AdvisedSupportListener to this proxy configuration.
	 * @param listener the listener to register
	 */
	public void addListener(AdvisedSupportListener listener) {
		Assert.notNull(listener, "AdvisedSupportListener must not be null");
		this.listeners.add(listener);
	}

	/**
	 * Remove the given AdvisedSupportListener from this proxy configuration.
	 * @param listener the listener to remove
	 */
	public void removeListener(AdvisedSupportListener listener) {
		Assert.notNull(listener, "AdvisedSupportListener must not be null");
		this.listeners.remove(listener);
	}



	/**
	 * 子类可以通过调用此方法获取一个新的AopProxy对象，创建时传入AdvisedSupport - 当前对象就是此类型
	 * @return AopProxy对象
	 */
	protected final synchronized AopProxy createAopProxy() {
		if (!this.active) {
			activate();
		}
		return getAopProxyFactory().createAopProxy(this);
	}

	/**
	 * Activate this proxy configuration.
	 * @see AdvisedSupportListener#activated
	 */
	private void activate() {
		this.active = true;
		for (AdvisedSupportListener listener : this.listeners) {
			listener.activated(this);
		}
	}

	/**
	 * Propagate advice change event to all AdvisedSupportListeners.
	 * @see AdvisedSupportListener#adviceChanged
	 */
	@Override
	protected void adviceChanged() {
		super.adviceChanged();
		synchronized (this) {
			if (this.active) {
				for (AdvisedSupportListener listener : this.listeners) {
					listener.adviceChanged(this);
				}
			}
		}
	}

	/**
	 * Subclasses can call this to check whether any AOP proxies have been created yet.
	 */
	protected final synchronized boolean isActive() {
		return this.active;
	}

}
