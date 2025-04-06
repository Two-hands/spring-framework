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

package org.springframework.core.env;

import java.util.Map;

/**
 * <pre>
 * 可以自由激活配置文件集、合并多个Environment数据
 * 可以操作环境中的属性源：环境中的属性（PropertySource）可以自由的被移除、排序、替换，向环境中添加新属性，如：
 *   1、向环境中添加一个最高优先级的PropertySource：
 *        ConfigurableEnvironment environment = new StandardEnvironment();
 *        MutablePropertySources propertySources = environment.getPropertySources();
 *        Map&lt;String, Object&gt; myMap = new HashMap&lt;&gt;();
 *        myMap.put("xyz", "myValue");
 *        propertySources.addFirst(new MapPropertySource("MY_MAP", myMap));
 *
 *   2、从环境中移除systemProperties属性源：
 *        MutablePropertySources propertySources = environment.getPropertySources();
 *        propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
 *
 *   3、替换环境中的StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME：
 *        MutablePropertySources propertySources = environment.getPropertySources();
 *        MockPropertySource mockEnvVars = new MockPropertySource().withProperty("xyz", "myValue");
 *        propertySources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, mockEnvVars);
 *
 *  注意：在AbstractApplicationContext#refresh()调用前，确保Environment中所有PropertySource都以准备就绪，
 *  确保容器启动时能够正常使用Environment的属性值（如：容器中的占位符处理）
 *  注意：若在{@code ApplicationContext}中使用{@link Environment}，要确保环境的任何操作都
 */
public interface ConfigurableEnvironment extends Environment, ConfigurablePropertyResolver {

	/**
	 * 设置环境{@code Environment}中应该激活的配置文件集（可以多个）
	 */
	void setActiveProfiles(String... profiles);


	/**
	 * 设置环境{@code Environment}中应该激活的配置文件
	 */
	void addActiveProfile(String profile);


	/**
	 * 设置环境{@code Environment}中应该激活的默认配置文件（可以多个）
	 * <b>前提是没有明确调用{@link #setActiveProfiles}、{@link #addActiveProfile}进行配置文件激活</b>
	 */
	void setDefaultProfiles(String... profiles);

	/**
	 * 返回环境中的所有属性源（封装到{@link PropertySources}中），允许操作PropertySources来搜索环境中的属性值
	 * PropertySources提供的方法{@link MutablePropertySources#addFirst addFirst}、
	 * {@link MutablePropertySources#addLast addLast}、
	 * {@link MutablePropertySources#addBefore addBefore}和
	 * {@link MutablePropertySources#addAfter addAfter}用于用户自定义控制配置源的顺序，以达到自定义控制搜索数据源的优先级
	 */
	MutablePropertySources getPropertySources();


	/**
	 * <pre>
	 * {@link System#getProperties()}的返回值
	 * 返回环境中的SystemProperties属性（JVM定义的相关的属性，如：-Dxx）
	 * </pre>
	 */
	Map<String, Object> getSystemProperties();

	/**
	 * <pre>
	 * {@link System#getenv()}的返回值
	 * 返回环境中SystemEnvironment属性（操作系统定义的相关属性，如：路径配置、系统信息）
	 * </pre>
	 */
	Map<String, Object> getSystemEnvironment();

	/**
	 * <pre>
	 * 将parent的环境信息（配置文件和属性）添加到当前子环境中来
	 * 若parent环境与当前子环境中都包含同名的PropertySource，移除前者中的PropertySource（允许子环境覆盖父环境）,配置文件（激活、默认）也会去重
	 * <b>当前方法只是将父环境中的信息"拷贝"过来，若后面父环境内容发生变化，子环境不可见</b>
	 * </pre>
	 */
	void merge(ConfigurableEnvironment parent);

}
