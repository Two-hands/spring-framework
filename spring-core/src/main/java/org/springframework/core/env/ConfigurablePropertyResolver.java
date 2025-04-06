/*
 * Copyright 2002-2016 the original author or authors.
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

import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.lang.Nullable;


/**
 * 提供[解析Environment属性值]的配置化入口，通过自定义{@link org.springframework.core.convert.ConversionService}来转换属性值类型
 */
public interface ConfigurablePropertyResolver extends PropertyResolver {

	/**
	 * Return the {@link ConfigurableConversionService} used when performing type
	 * conversions on properties.
	 * <p>The configurable nature of the returned conversion service allows for
	 * the convenient addition and removal of individual {@code Converter} instances:
	 * <pre class="code">
	 * ConfigurableConversionService cs = env.getConversionService();
	 * cs.addConverter(new FooConverter());
	 * </pre>
	 * @see PropertyResolver#getProperty(String, Class)
	 * @see org.springframework.core.convert.converter.ConverterRegistry#addConverter
	 */
	ConfigurableConversionService getConversionService();

	/**
	 * Set the {@link ConfigurableConversionService} to be used when performing type
	 * conversions on properties.
	 * <p><strong>Note:</strong> as an alternative to fully replacing the
	 * {@code ConversionService}, consider adding or removing individual
	 * {@code Converter} instances by drilling into {@link #getConversionService()}
	 * and calling methods such as {@code #addConverter}.
	 * @see PropertyResolver#getProperty(String, Class)
	 * @see #getConversionService()
	 * @see org.springframework.core.convert.converter.ConverterRegistry#addConverter
	 */
	void setConversionService(ConfigurableConversionService conversionService);

	/**
	 * 定义占位符必须以特定标识开头，一般是"${"
	 */
	void setPlaceholderPrefix(String placeholderPrefix);


	/**
	 * 定义占位符必须以特定标识结尾，一般是"}"
	 */
	void setPlaceholderSuffix(String placeholderSuffix);


	/**
	 * 定义占位符中的k-v分隔符，当指定后，若k对应的属性值不存在，则使用v作为默认值，一般是":"
	 */
	void setValueSeparator(@Nullable String valueSeparator);

	/**
	 * 当内嵌占位符解析失败时（即：占位符对应的值中还含有占位符，需要再次解析），是否要报错？
	 * @param ignoreUnresolvableNestedPlaceholders true - 忽略解析失败的情况，false - 报错
	 */
	void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders);


	/**
	 * 定义环境中必须存在的属性值，这些必须的属性值将在{@link #validateRequiredProperties()}中校验
	 */
	void setRequiredProperties(String... requiredProperties);


	/**
	 * 校验必须存在的属性值是否存在，若不存在则抛异常
	 */
	void validateRequiredProperties() throws MissingRequiredPropertiesException;

}
