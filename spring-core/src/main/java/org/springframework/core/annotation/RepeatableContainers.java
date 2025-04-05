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

package org.springframework.core.annotation;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Strategy used to determine annotations that act as containers for other
 * annotations. The {@link #standardRepeatables()} method provides a default
 * strategy that respects Java's {@link Repeatable @Repeatable} support and
 * should be suitable for most situations.
 *
 * <p>The {@link #of} method can be used to register relationships for
 * annotations that do not wish to use {@link Repeatable @Repeatable}.
 *
 * <p>To completely disable repeatable support use {@link #none()}.
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 */
public abstract class RepeatableContainers {

	static final Map<Class<? extends Annotation>, Object> cache = new ConcurrentReferenceHashMap<>();

	@Nullable
	private final RepeatableContainers parent;


	private RepeatableContainers(@Nullable RepeatableContainers parent) {
		this.parent = parent;
	}


	/**
	 * Add an additional explicit relationship between a container and
	 * repeatable annotation.
	 * <p>WARNING: the arguments supplied to this method are in the reverse order
	 * of those supplied to {@link #of(Class, Class)}.
	 * @param container the container annotation type
	 * @param repeatable the repeatable annotation type
	 * @return a new {@link RepeatableContainers} instance
	 */
	public RepeatableContainers and(Class<? extends Annotation> container,
			Class<? extends Annotation> repeatable) {

		return new ExplicitRepeatableContainer(this, repeatable, container);
	}

	@Nullable
	Annotation[] findRepeatedAnnotations(Annotation annotation) {
		if (this.parent == null) {
			return null;
		}
		return this.parent.findRepeatedAnnotations(annotation);
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (other == this) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(this.parent, ((RepeatableContainers) other).parent);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(this.parent);
	}


	/**
	 * Create a {@link RepeatableContainers} instance that searches using Java's
	 * {@link Repeatable @Repeatable} annotation.
	 * @return a {@link RepeatableContainers} instance
	 */

	/**
	 * 返回StandardRepeatableContainers实例，用于搜索java中{@link Repeatable @Repeatable}注解
	 * @return
	 */
	public static RepeatableContainers standardRepeatables() {
		return StandardRepeatableContainers.INSTANCE;
	}

	/**
	 * Create a {@link RepeatableContainers} instance that uses predefined
	 * repeatable and container types.
	 * <p>WARNING: the arguments supplied to this method are in the reverse order
	 * of those supplied to {@link #and(Class, Class)}.
	 * @param repeatable the repeatable annotation type
	 * @param container the container annotation type or {@code null}. If specified,
	 * this annotation must declare a {@code value} attribute returning an array
	 * of repeatable annotations. If not specified, the container will be
	 * deduced by inspecting the {@code @Repeatable} annotation on
	 * {@code repeatable}.
	 * @return a {@link RepeatableContainers} instance
	 * @throws IllegalArgumentException if the supplied container type is
	 * {@code null} and the annotation type is not a repeatable annotation
	 * @throws AnnotationConfigurationException if the supplied container type
	 * is not a properly configured container for a repeatable annotation
	 */
	public static RepeatableContainers of(
			Class<? extends Annotation> repeatable, @Nullable Class<? extends Annotation> container) {

		return new ExplicitRepeatableContainer(null, repeatable, container);
	}

	/**
	 * Create a {@link RepeatableContainers} instance that does not support any
	 * repeatable annotations.
	 * @return a {@link RepeatableContainers} instance
	 */
	public static RepeatableContainers none() {
		return NoRepeatableContainers.INSTANCE;
	}



	/**
	 * RepeatableContainers的标准实现，用于搜索java提供的{@link Repeatable @Repeatable}注解
	 */
	private static class StandardRepeatableContainers extends RepeatableContainers {

		private static final Object NONE = new Object();

		//单例模式
		private static final StandardRepeatableContainers INSTANCE = new StandardRepeatableContainers();

		StandardRepeatableContainers() {
			super(null);
		}

		@Override
		@Nullable
		Annotation[] findRepeatedAnnotations(Annotation annotation) {
			// 获取注解中value()属性方法（返回类型为注解数组，且注解含@Repeatable）
			Method method = getRepeatedAnnotationsMethod(annotation.annotationType());
			if (method != null) {
				return (Annotation[]) AnnotationUtils.invokeAnnotationMethod(method, annotation);
			}
			return super.findRepeatedAnnotations(annotation);
		}

		@Nullable
		private static Method getRepeatedAnnotationsMethod(Class<? extends Annotation> annotationType) {
			Object result = cache.computeIfAbsent(annotationType,
					StandardRepeatableContainers::computeRepeatedAnnotationsMethod);
			return (result != NONE ? (Method) result : null);
		}

		/**
		 * 尝试找到给定注解类型的属性方法是value()，并且其返回类型是数组（元素类型是数组且含@Repeatable），若找到则返回这个Method
		 * @param annotationType 注解类型
		 * @return NONE 或 具体的Method
		 */
		private static Object computeRepeatedAnnotationsMethod(Class<? extends Annotation> annotationType) {
			// 获取注解类型定义的所有合法的属性方法
			AttributeMethods methods = AttributeMethods.forAnnotationType(annotationType);
			//拿到注解中定义的value()方法
			Method method = methods.get(MergedAnnotation.VALUE);
			if (method != null) {
				//返回类型是Annotation数组，并且数组的元素含java.lang.annotation.Repeatable注解
				/*
				如：
				@interface A{
				    B[] value();
				}

				@Repeatable
				@interface B{
				    String name();
				}

				@A的value方法返回类型是@B的数组，并且@B含有@Repeatable注解
				 */
				Class<?> returnType = method.getReturnType();
				if (returnType.isArray()) {
					Class<?> componentType = returnType.getComponentType();
					if (Annotation.class.isAssignableFrom(componentType) &&
							componentType.isAnnotationPresent(Repeatable.class)) {
						return method;
					}
				}
			}
			return NONE;
		}
	}


	/**
	 * A single explicit mapping.
	 */
	private static class ExplicitRepeatableContainer extends RepeatableContainers {

		private final Class<? extends Annotation> repeatable;

		private final Class<? extends Annotation> container;

		private final Method valueMethod;

		ExplicitRepeatableContainer(@Nullable RepeatableContainers parent,
				Class<? extends Annotation> repeatable, @Nullable Class<? extends Annotation> container) {

			super(parent);
			Assert.notNull(repeatable, "Repeatable must not be null");
			if (container == null) {
				container = deduceContainer(repeatable);
			}
			Method valueMethod = AttributeMethods.forAnnotationType(container).get(MergedAnnotation.VALUE);
			try {
				if (valueMethod == null) {
					throw new NoSuchMethodException("No value method found");
				}
				Class<?> returnType = valueMethod.getReturnType();
				if (!returnType.isArray() || returnType.getComponentType() != repeatable) {
					throw new AnnotationConfigurationException(
							"Container type [%s] must declare a 'value' attribute for an array of type [%s]"
								.formatted(container.getName(), repeatable.getName()));
				}
			}
			catch (AnnotationConfigurationException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new AnnotationConfigurationException(
						"Invalid declaration of container type [%s] for repeatable annotation [%s]"
							.formatted(container.getName(), repeatable.getName()), ex);
			}
			this.repeatable = repeatable;
			this.container = container;
			this.valueMethod = valueMethod;
		}

		private Class<? extends Annotation> deduceContainer(Class<? extends Annotation> repeatable) {
			Repeatable annotation = repeatable.getAnnotation(Repeatable.class);
			Assert.notNull(annotation, () -> "Annotation type must be a repeatable annotation: " +
						"failed to resolve container type for " + repeatable.getName());
			return annotation.value();
		}

		@Override
		@Nullable
		Annotation[] findRepeatedAnnotations(Annotation annotation) {
			if (this.container.isAssignableFrom(annotation.annotationType())) {
				return (Annotation[]) AnnotationUtils.invokeAnnotationMethod(this.valueMethod, annotation);
			}
			return super.findRepeatedAnnotations(annotation);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (!super.equals(other)) {
				return false;
			}
			ExplicitRepeatableContainer otherErc = (ExplicitRepeatableContainer) other;
			return (this.container.equals(otherErc.container) && this.repeatable.equals(otherErc.repeatable));
		}

		@Override
		public int hashCode() {
			int hashCode = super.hashCode();
			hashCode = 31 * hashCode + this.container.hashCode();
			hashCode = 31 * hashCode + this.repeatable.hashCode();
			return hashCode;
		}
	}


	/**
	 * No repeatable containers.
	 */
	private static class NoRepeatableContainers extends RepeatableContainers {

		private static final NoRepeatableContainers INSTANCE = new NoRepeatableContainers();

		NoRepeatableContainers() {
			super(null);
		}
	}

}
