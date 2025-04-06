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

package org.springframework.core.env;

/**
 * 应用运行时的环境信息，应用运行时环境包含2个主要方面：配置文件（profiles）和属性（properties）
 * 属性信息可以通过父接口{@link PropertyResolver}定义的方法进行访问
 */

/**
 * Interface representing the environment in which the current application is running.
 * Models two key aspects of the application environment: <em>profiles</em> and
 * <em>properties</em>. Methods related to property access are exposed via the
 * {@link PropertyResolver} superinterface.
 *
 * <p>A <em>profile</em> is a named, logical group of bean definitions to be registered
 * with the container only if the given profile is <em>active</em>. Beans may be assigned
 * to a profile whether defined in XML or via annotations; see the spring-beans 3.1 schema
 * or the {@link org.springframework.context.annotation.Profile @Profile} annotation for
 * syntax details. The role of the {@code Environment} object with relation to profiles is
 * in determining which profiles (if any) are currently {@linkplain #getActiveProfiles
 * active}, and which profiles (if any) should be {@linkplain #getDefaultProfiles active
 * by default}.
 *
 * <p><em>Properties</em> play an important role in almost all applications, and may
 * originate from a variety of sources: properties files, JVM system properties, system
 * environment variables, JNDI, servlet context parameters, ad-hoc Properties objects,
 * Maps, and so on. The role of the {@code Environment} object with relation to properties
 * is to provide the user with a convenient service interface for configuring property
 * sources and resolving properties from them.
 *
 * <p>Beans managed within an {@code ApplicationContext} may register to be {@link
 * org.springframework.context.EnvironmentAware EnvironmentAware} or {@code @Inject} the
 * {@code Environment} in order to query profile state or resolve properties directly.
 *
 * <p>In most cases, however, application-level beans should not need to interact with the
 * {@code Environment} directly but instead may request to have {@code ${...}} property
 * values replaced by a property placeholder configurer such as
 * {@link org.springframework.context.support.PropertySourcesPlaceholderConfigurer
 * PropertySourcesPlaceholderConfigurer}, which itself is {@code EnvironmentAware} and
 * registered by default when using {@code <context:property-placeholder/>}.
 *
 * <p>Configuration of the {@code Environment} object must be done through the
 * {@code ConfigurableEnvironment} interface, returned from all
 * {@code AbstractApplicationContext} subclass {@code getEnvironment()} methods. See
 * {@link ConfigurableEnvironment} Javadoc for usage examples demonstrating manipulation
 * of property sources prior to application context {@code refresh()}.
 *
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.1
 * @see PropertyResolver
 * @see EnvironmentCapable
 * @see ConfigurableEnvironment
 * @see AbstractEnvironment
 * @see StandardEnvironment
 * @see org.springframework.context.EnvironmentAware
 * @see org.springframework.context.ConfigurableApplicationContext#getEnvironment
 * @see org.springframework.context.ConfigurableApplicationContext#setEnvironment
 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
 */
public interface Environment extends PropertyResolver {

	/**
	 * 返回显示激活的配置文件集，配置文件可以通过{@linkplain AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 * 	"spring.profiles.active"}或{@link ConfigurableEnvironment#setActiveProfiles(String...)}进行配置
	 *  若没有明确指定需要激活的配置文件，默认配置文件将被激活
	 */
	String[] getActiveProfiles();


	/**
	 * 返回默认激活的配置文件集（当没有明确指定激活配置文件集时）
	 */
	String[] getDefaultProfiles();


	/**
	 * <pre>
	 * 通过表达式判断是否激活了指定的配置文件集，如：
	 *  1、{@code "p1 & p2"} ：判断p1和p2配置文件是否都激活？
	 *  2、{@code "(p1 & p2) | p3"}：判断是否p1和p2配置文件都激活，或p3配置文件激活
	 *
	 * 表达式语句支持可以见{@link Profiles#of(String...)}
	 * 多个表达式只要一个满足就返回true
	 * </pre>
	 */
	default boolean matchesProfiles(String... profileExpressions) {
		return acceptsProfiles(Profiles.of(profileExpressions));
	}


	/**
	 * <pre>
	 * 判定环境中是否激活了指定的配置文件集？ true - 激活
	 * 如：{@code env.acceptsProfiles("p1", "!p2")}返回{@code true}表示配置文件p1为激活状态，p2为未激活状态
	 * </pre>
	 */
	@Deprecated
	boolean acceptsProfiles(String... profiles);


	/**
	 * 判定环境中是否激活了指定的配置文件集？ true - 激活
	 * @param profiles 配置文件集
	 * @return true - 配置文件集均为激活状态
	 */
	boolean acceptsProfiles(Profiles profiles);

}
