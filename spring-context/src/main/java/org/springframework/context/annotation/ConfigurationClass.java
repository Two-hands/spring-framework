/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.context.annotation;

import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.*;

/**
 * Represents a user-defined {@link Configuration @Configuration} class.
 * <p>Includes a set of {@link Bean} methods, including all such methods
 * defined in the ancestry of the class, in a 'flattened-out' manner.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 * @see BeanMethod
 * @see ConfigurationClassParser
 */
final class ConfigurationClass {

	//当前"配置类"的AnnotationMetadata（一般为SimpleAnnotationMetadata或StandardAnnotationMetadata）
	private final AnnotationMetadata metadata;

	//当前"配置类"的资源信息，表示资源的文件加载位置
	private final Resource resource;

	//当前"配置类"的bean名称
	@Nullable
	private String beanName;


	//记录当前"配置类"被其他哪些配置类导入的:
	// 1、若当前"配置类"为"配置类A"的内部类，则importedBy包含"配置类A"
	// 2、当前"配置类"是"配置类A"通过@Import导入，则importedBy包含"配置类A"
	private final Set<ConfigurationClass> importedBy = new LinkedHashSet<>(1);


	//当前"配置类"和接口中含@Bean注解的方法（含MethodMetadata和当前"配置类"）
	private final Set<BeanMethod> beanMethods = new LinkedHashSet<>();


	//当前"配置类"含@ImportResource注解定义的扫描信息（用于后面扫描文件中定义的bean信息，注入BeanFactory）
	private final Map<String, Class<? extends BeanDefinitionReader>> importedResources =
			new LinkedHashMap<>();

	//当前"配置类"上@Import注解指定的ImportBeanDefinitionRegistrar实例：
	private final Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> importBeanDefinitionRegistrars =
			new LinkedHashMap<>();

	final Set<String> skippedBeanMethods = new HashSet<>();


	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param metadataReader reader used to parse the underlying {@link Class}
	 * @param beanName must not be {@code null}
	 */
	ConfigurationClass(MetadataReader metadataReader, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = metadataReader.getAnnotationMetadata();
		this.resource = metadataReader.getResource();
		this.beanName = beanName;
	}

	/**
	 * Create a new {@link ConfigurationClass} representing a class that was imported
	 * using the {@link Import} annotation or automatically processed as a nested
	 * configuration class (if importedBy is not {@code null}).
	 * @param metadataReader reader used to parse the underlying {@link Class}
	 * @param importedBy the configuration class importing this one
	 * @since 3.1.1
	 */
	ConfigurationClass(MetadataReader metadataReader, ConfigurationClass importedBy) {
		this.metadata = metadataReader.getAnnotationMetadata();
		this.resource = metadataReader.getResource();
		this.importedBy.add(importedBy);
	}

	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param clazz the underlying {@link Class} to represent
	 * @param beanName name of the {@code @Configuration} class bean
	 */
	ConfigurationClass(Class<?> clazz, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = AnnotationMetadata.introspect(clazz);
		this.resource = new DescriptiveResource(clazz.getName());
		this.beanName = beanName;
	}

	/**
	 * Create a new {@link ConfigurationClass} representing a class that was imported
	 * using the {@link Import} annotation or automatically processed as a nested
	 * configuration class (if imported is {@code true}).
	 * @param clazz the underlying {@link Class} to represent
	 * @param importedBy the configuration class importing this one
	 * @since 3.1.1
	 */
	ConfigurationClass(Class<?> clazz, ConfigurationClass importedBy) {
		this.metadata = AnnotationMetadata.introspect(clazz);
		this.resource = new DescriptiveResource(clazz.getName());
		this.importedBy.add(importedBy);
	}

	/**
	 * Create a new {@link ConfigurationClass} with the given name.
	 * @param metadata the metadata for the underlying class to represent
	 * @param beanName name of the {@code @Configuration} class bean
	 */
	ConfigurationClass(AnnotationMetadata metadata, String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		this.metadata = metadata;
		this.resource = new DescriptiveResource(metadata.getClassName());
		this.beanName = beanName;
	}


	AnnotationMetadata getMetadata() {
		return this.metadata;
	}

	Resource getResource() {
		return this.resource;
	}

	String getSimpleName() {
		return ClassUtils.getShortName(getMetadata().getClassName());
	}

	void setBeanName(@Nullable String beanName) {
		this.beanName = beanName;
	}

	@Nullable
	String getBeanName() {
		return this.beanName;
	}

	/**
	 * Return whether this configuration class was registered via @{@link Import} or
	 * automatically registered due to being nested within another configuration class.
	 * @since 3.1.1
	 * @see #getImportedBy()
	 */
	boolean isImported() {
		return !this.importedBy.isEmpty();
	}

	/**
	 * Merge the imported-by declarations from the given configuration class into this one.
	 * @since 4.0.5
	 */
	void mergeImportedBy(ConfigurationClass otherConfigClass) {
		this.importedBy.addAll(otherConfigClass.importedBy);
	}

	/**
	 * Return the configuration classes that imported this class,
	 * or an empty Set if this configuration was not imported.
	 * @since 4.0.5
	 * @see #isImported()
	 */
	Set<ConfigurationClass> getImportedBy() {
		return this.importedBy;
	}

	void addBeanMethod(BeanMethod method) {
		this.beanMethods.add(method);
	}

	Set<BeanMethod> getBeanMethods() {
		return this.beanMethods;
	}

	void addImportedResource(String importedResource, Class<? extends BeanDefinitionReader> readerClass) {
		this.importedResources.put(importedResource, readerClass);
	}

	Map<String, Class<? extends BeanDefinitionReader>> getImportedResources() {
		return this.importedResources;
	}

	void addImportBeanDefinitionRegistrar(ImportBeanDefinitionRegistrar registrar, AnnotationMetadata importingClassMetadata) {
		this.importBeanDefinitionRegistrars.put(registrar, importingClassMetadata);
	}

	Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> getImportBeanDefinitionRegistrars() {
		return this.importBeanDefinitionRegistrars;
	}

	void validate(ProblemReporter problemReporter) {
		Map<String, Object> attributes = this.metadata.getAnnotationAttributes(Configuration.class.getName());

		// A configuration class may not be final (CGLIB limitation) unless it declares proxyBeanMethods=false
		//当proxyBeanMethods=false时配置类如果有final修饰，无法代理
		if (attributes != null && (Boolean) attributes.get("proxyBeanMethods")) {
			if (this.metadata.isFinal()) {
				problemReporter.error(new FinalConfigurationProblem());
			}
			for (BeanMethod beanMethod : this.beanMethods) {
				beanMethod.validate(problemReporter);
			}
		}

		// A configuration class may not contain overloaded bean methods unless it declares enforceUniqueMethods=false
		//配置类默认不能包含重载bean方法,除非它声明enforceUniqueMethods=false
		if (attributes != null && (Boolean) attributes.get("enforceUniqueMethods")) {
			Map<String, MethodMetadata> beanMethodsByName = new LinkedHashMap<>();
			for (BeanMethod beanMethod : this.beanMethods) {
				MethodMetadata current = beanMethod.getMetadata();
				MethodMetadata existing = beanMethodsByName.put(current.getMethodName(), current);
				if (existing != null && existing.getDeclaringClassName().equals(current.getDeclaringClassName())) {
					problemReporter.error(new BeanMethodOverloadingProblem(existing.getMethodName()));
				}
			}
		}
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof ConfigurationClass that &&
				getMetadata().getClassName().equals(that.getMetadata().getClassName())));
	}

	@Override
	public int hashCode() {
		return getMetadata().getClassName().hashCode();
	}

	@Override
	public String toString() {
		return "ConfigurationClass: beanName '" + this.beanName + "', " + this.resource;
	}


	/**
	 * Configuration classes must be non-final to accommodate CGLIB subclassing.
	 */
	private class FinalConfigurationProblem extends Problem {

		FinalConfigurationProblem() {
			super(String.format("@Configuration class '%s' may not be final. Remove the final modifier to continue.",
					getSimpleName()), new Location(getResource(), getMetadata()));
		}
	}


	/**
	 * Configuration classes are not allowed to contain overloaded bean methods
	 * by default (as of 6.0).
	 */
	private class BeanMethodOverloadingProblem extends Problem {

		BeanMethodOverloadingProblem(String methodName) {
			super(String.format("@Configuration class '%s' contains overloaded @Bean methods with name '%s'. Use " +
							"unique method names for separate bean definitions (with individual conditions etc) " +
							"or switch '@Configuration.enforceUniqueMethods' to 'false'.",
					getSimpleName(), methodName), new Location(getResource(), getMetadata()));
		}
	}

}
