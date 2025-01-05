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

package org.springframework.context.annotation;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BeanNameGenerator} implementation for bean classes annotated with the
 * {@link org.springframework.stereotype.Component @Component} annotation or
 * with another annotation that is itself annotated with {@code @Component} as a
 * meta-annotation. For example, Spring's stereotype annotations (such as
 * {@link org.springframework.stereotype.Repository @Repository}) are
 * themselves annotated with {@code @Component}.
 *
 * <p>Also supports Jakarta EE's {@link jakarta.annotation.ManagedBean} and
 * JSR-330's {@link jakarta.inject.Named} annotations, if available. Note that
 * Spring component annotations always override such standard annotations.
 *
 * <p>If the annotation's value doesn't indicate a bean name, an appropriate
 * name will be built based on the short name of the class (with the first
 * letter lower-cased), unless the first two letters are uppercase. For example:
 *
 * <pre class="code">com.xyz.FooServiceImpl -&gt; fooServiceImpl</pre>
 * <pre class="code">com.xyz.URLFooServiceImpl -&gt; URLFooServiceImpl</pre>
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.5
 * @see org.springframework.stereotype.Component#value()
 * @see org.springframework.stereotype.Repository#value()
 * @see org.springframework.stereotype.Service#value()
 * @see org.springframework.stereotype.Controller#value()
 * @see jakarta.inject.Named#value()
 * @see FullyQualifiedAnnotationBeanNameGenerator
 *
 *
 * <br/>
 * BeanNameGenerator的实现类，用于根据@Component注解或其他注解（该注解含有@Comopnent注解，如：Srping的原生注解@Repository，它含有@Component注解）
 * 此外，还支持如@ManagedBean和@Named（注意：若这2个注解与Spring原生注解【如：@Component、@Repository、@Service】一起使用，前者不生效）；
 * 如果注解没有指明名称（如：@Component#value()），则默认使用类名称（首字母小写，但前两个字母都大写则不变小写），如：
 *    1、com.xyz.FooServiceImpl  -> fooServiceImpl  （去掉包路径，类名首字母小写，驼峰）
 *    2、com.xyz.URLFooServiceImpl -> URLFooServiceImpl （去掉包路径，前两个字母均大写，类名首字母不用小写）
 *
 */
public class AnnotationBeanNameGenerator implements BeanNameGenerator {

	/**
	 * A convenient constant for a default {@code AnnotationBeanNameGenerator} instance,
	 * as used for component scanning purposes.
	 * @since 5.2
	 */
	public static final AnnotationBeanNameGenerator INSTANCE = new AnnotationBeanNameGenerator();

	/**
	 * 指明根据哪个注解获取名称
	 */
	private static final String COMPONENT_ANNOTATION_CLASSNAME = "org.springframework.stereotype.Component";

	private final Map<String, Set<String>> metaAnnotationTypesCache = new ConcurrentHashMap<>();


	@Override
	public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {

		//先通过注解获取beanName
		if (definition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
			String beanName = determineBeanNameFromAnnotation(annotatedBeanDefinition);
			if (StringUtils.hasText(beanName)) {
				// Explicit bean name found.
				return beanName;
			}
		}
		// Fallback: generate a unique default bean name.
		//注解中无法获取beanName或获取结果为空串，则为bean构建一个默认的beanName
		return buildDefaultBeanName(definition, registry);
	}

	/**
	 * Derive a bean name from one of the annotations on the class.
	 * @param annotatedDef the annotation-aware bean definition
	 * @return the bean name, or {@code null} if none is found
	 */
	@Nullable
	protected String determineBeanNameFromAnnotation(AnnotatedBeanDefinition annotatedDef) {
		AnnotationMetadata amd = annotatedDef.getMetadata();
		//获取Class（annotatedDef包含的Class）上直接标注的所有注解【这些注解有@Retention标注并且为RetentionPolicy.RUNTIME】
		Set<String> types = amd.getAnnotationTypes();
		String beanName = null;
		//遍历直接标注的所有注解
		for (String type : types) {
			//获取每个注解的所有属性（不包含注解上的元注解的属性）
			AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(amd, type);
			if (attributes != null) {
				//解析注解的所有元注解（级联方式获取元注解的元注解），并将解析结果缓存起来：以key（注解） -> value（注解的元注解）
				//如：@Configuration -> [@Component、@Indexed]
				Set<String> metaTypes = this.metaAnnotationTypesCache.computeIfAbsent(type, key -> {
					Set<String> result = amd.getMetaAnnotationTypes(key);
					return (result.isEmpty() ? Collections.emptySet() : result);
				});
				/*
				   判断注解和元注解是否含有指定注解，若有，则获取该注解的value值作为beanName（不能是空串）
				   type：Class类上的其中一个注解（A）
				   metaTypes 注解（A）上的所有元注解
				   attributes：注解（A）的所有属性
				 */
				if (isStereotypeWithNameValue(type, metaTypes, attributes)) {
					Object value = attributes.get("value");
					if (value instanceof String strVal && !strVal.isEmpty()) {
						if (beanName != null && !strVal.equals(beanName)) {
							throw new IllegalStateException("Stereotype annotations suggest inconsistent " +
									"component names: '" + beanName + "' versus '" + strVal + "'");
						}
						beanName = strVal;
					}
				}
			}
		}
		return beanName;
	}

	/**
	 * Check whether the given annotation is a stereotype that is allowed
	 * to suggest a component name through its annotation {@code value()}.
	 * @param annotationType the name of the annotation class to check
	 * @param metaAnnotationTypes the names of meta-annotations on the given annotation
	 * @param attributes the map of attributes for the given annotation
	 * @return whether the annotation qualifies as a stereotype with component name
	 *
	 * <br/>
	 * 是否可以根据注解属性值作为beanName?
	 *   1、优先判断注解是否为@Component
	 *   2、步骤1不满足判断注解的元注解中是否包含@Component
	 *   3、步骤1、2不满足则判断注解是否为@ManagedBean
	 *   4、步骤1、2、3不满足则判断注解是否为@Named
	 * 若上述条件都不满足，则无法通过注解信息作为beanName
	 * 若上述条件满足一个，且注解含有value属性值，则把该值作为beanName
	 */
	protected boolean isStereotypeWithNameValue(String annotationType,
			Set<String> metaAnnotationTypes, @Nullable Map<String, Object> attributes) {

		boolean isStereotype = annotationType.equals(COMPONENT_ANNOTATION_CLASSNAME) ||
				metaAnnotationTypes.contains(COMPONENT_ANNOTATION_CLASSNAME) ||
				annotationType.equals("jakarta.annotation.ManagedBean") ||
				annotationType.equals("jakarta.inject.Named");

		return (isStereotype && attributes != null && attributes.containsKey("value"));
	}

	/**
	 * Derive a default bean name from the given bean definition.
	 * <p>The default implementation delegates to {@link #buildDefaultBeanName(BeanDefinition)}.
	 * @param definition the bean definition to build a bean name for
	 * @param registry the registry that the given bean definition is being registered with
	 * @return the default bean name (never {@code null})
	 */
	protected String buildDefaultBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
		return buildDefaultBeanName(definition);
	}

	/**
	 * Derive a default bean name from the given bean definition.
	 * <p>The default implementation simply builds a decapitalized version
	 * of the short class name: e.g. "mypackage.MyJdbcDao" &rarr; "myJdbcDao".
	 * <p>Note that inner classes will thus have names of the form
	 * "outerClassName.InnerClassName", which because of the period in the
	 * name may be an issue if you are autowiring by name.
	 * @param definition the bean definition to build a bean name for
	 * @return the default bean name (never {@code null})
	 */
	protected String buildDefaultBeanName(BeanDefinition definition) {
		String beanClassName = definition.getBeanClassName();
		Assert.state(beanClassName != null, "No bean class name set");
		String shortClassName = ClassUtils.getShortName(beanClassName);
		return StringUtils.uncapitalizeAsProperty(shortClassName);
	}

}
