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

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.*;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	/**
	 * Singleton instance of this class.
	 * @since 6.0.10
	 */
	public static final DefaultAdvisorChainFactory INSTANCE = new DefaultAdvisorChainFactory();


	/**
	 * 根据给定的目标类型、目标方法从Advised获取可应用的Advisors，并将其转换为Interceptor数组返回
	 */
	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {


		//Advisor适配中心（模式：适配+策略）
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		Advisor[] advisors = config.getAdvisors();
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		Boolean hasIntroductions = null;

		//遍历Advised中所有Advisors，通过条件匹配组织好Advisor中的Advice（直接或间接转换为MethodInterceptor）集合返回
		for (Advisor advisor : advisors) {
			if (advisor instanceof PointcutAdvisor pointcutAdvisor) {
				//PointcutAdvisor类型：
				//  1、当前Advisor可以应用到目标类
				//  2、在目标类匹配成功后，继续判断当前Advisor是否可以应用到具体指定方法
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					// MethodMatcher - 方法匹配器，用于确定给定方法是否与目标方法匹配...
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;
					if (mm instanceof IntroductionAwareMethodMatcher iamm) {
						if (hasIntroductions == null) {
							//advisors中是否至少有一个advisor是IntroductionAdvisor类型，并且这个advisor能够应用到当前类上
							// 若上述满足 hasIntroductions = true,否则为false
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						match = iamm.matches(method, actualClass, hasIntroductions);
					}
					else {
						match = mm.matches(method, actualClass);
					}


					if (match) {
						//当前Advisor可以应用到当前类（actualClass）的具体指定方法（method），将Advisor转换为MethodInterceptor
						//   Advisor中含Advice（其大多数情况就是MethodInterceptor，只有少数情况会是
						//      1、AfterReturningAdvice
						//      2、MethodBeforeAdvice
						//      3、SimpleBeforeAdvice
						//      4、ThrowsAdvice
						//以上4中情况下会用适配器模式将其转换为MethodInterceptor
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
						if (mm.isRuntime()) {
							// 在运行时进行目标方法的匹配：创建InterceptorAndDynamicMethodMatcher对象包装与MethodMatcher
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}

			}
			else if (advisor instanceof IntroductionAdvisor ia) {
				//advisor是IntroductionAdvisor类型：
				// 只要类匹配即可
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				//其他类型：
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor ia && ia.getClassFilter().matches(actualClass)) {
				return true;
			}
		}
		return false;
	}

}
