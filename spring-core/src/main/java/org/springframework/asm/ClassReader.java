// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
package org.springframework.asm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <pre>
 * 一个解析器，用于使ClassVisitor访问Java虚拟机规范（JVMS）中定义的ClassFile结构。
 * 此类解析ClassFile内容，并为遇到的每个字段、方法和字节码指令调用给定ClassVisitor的适当访问方法。
 * 参考文档：<a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html">字节码文件结构</a>
 *
 *
 * ClassFile结构：
 *    ClassFile {
 *       u4             magic  - 魔数值，固定为0xCAFEBABE;
 *       u2             minor_version  - 次版本号;
 *       u2             major_version  - 主版本号，如：jdk1.8对应的版本号为52;
 *       u2             constant_pool_count  - 常量池容量;
 *       cp_info        constant_pool[constant_pool_count-1]  - 常量池数组[索引0位置必须为空，表示null，常量池每个元素都是cp_info结构];
 *       u2             access_flags  - 类的访问标志，如：ACC_PUBLIC、ACC_FINAL、ACC_INTERFACE、ACC_ABSTRACT、ACC_ANNOTATION、ACC_ENUM....;
 *       u2             this_class  - 类（或接口）的全限定类名，值为常量池的索引，指向表示该类的CONSTANT_Class_info结构;
 *       u2             super_class  - 父类的全限定类名，值为0（Object没有父类）或常量池的索引，指向表示该类的CONSTANT_Class_info结构;
 *       u2             interfaces_count  - 接口数量;
 *       u2             interfaces[interfaces_count]  - 接口全限定类名数组，按源代码定义的接口顺序排序[其中每一个值都是常量池索引，指向表示该类的CONSTANT_Class_info结构];
 *       u2             fields_count  - 字段个数(类变量和实例变量，仅包含当前类的，不包含其父类或接口中的变量);
 *       field_info     fields[fields_count]  - 字段数组[其中每一个值都是常量池索引，指向表示该类的field_info结构];
 *       u2             methods_count  - 方法个数（实例方法、类方法，进包含当前类的，不包含其父类或接口中的方法）;
 *       method_info    methods[methods_count]  - 方法数组[其中每一个值都是常量池索引，指向表示该类的method_info结构];
 *       u2             attributes_count  - 属性个数;
 *       attribute_info attributes[attributes_count]  - 属性数组[其中每一个值都是常量池索引，指向表示该类的attribute_info结构];
 *    }
 *
 * </pre>
 */
public class ClassReader {

	/**
	 * A flag to skip the Code attributes. If this flag is set the Code attributes are neither parsed
	 * nor visited.
	 */
	//是否要跳过代码属性，true - 不会解析也不会访问代码属性
	public static final int SKIP_CODE = 1;

	/**
	 * A flag to skip the SourceFile, SourceDebugExtension, LocalVariableTable,
	 * LocalVariableTypeTable, LineNumberTable and MethodParameters attributes. If this flag is set
	 * these attributes are neither parsed nor visited (i.e. {@link ClassVisitor#visitSource}, {@link
	 * MethodVisitor#visitLocalVariable}, {@link MethodVisitor#visitLineNumber} and {@link
	 * MethodVisitor#visitParameter} are not called).
	 */
	public static final int SKIP_DEBUG = 2;

	/**
	 * A flag to skip the StackMap and StackMapTable attributes. If this flag is set these attributes
	 * are neither parsed nor visited (i.e. {@link MethodVisitor#visitFrame} is not called). This flag
	 * is useful when the {@link ClassWriter#COMPUTE_FRAMES} option is used: it avoids visiting frames
	 * that will be ignored and recomputed from scratch.
	 */
	public static final int SKIP_FRAMES = 4;

	/**
	 * A flag to expand the stack map frames. By default stack map frames are visited in their
	 * original format (i.e. "expanded" for classes whose version is less than V1_6, and "compressed"
	 * for the other classes). If this flag is set, stack map frames are always visited in expanded
	 * format (this option adds a decompression/compression step in ClassReader and ClassWriter which
	 * degrades performance quite a lot).
	 */
	public static final int EXPAND_FRAMES = 8;

	/**
	 * A flag to expand the ASM specific instructions into an equivalent sequence of standard bytecode
	 * instructions. When resolving a forward jump it may happen that the signed 2 bytes offset
	 * reserved for it is not sufficient to store the bytecode offset. In this case the jump
	 * instruction is replaced with a temporary ASM specific instruction using an unsigned 2 bytes
	 * offset (see {@link Label#resolve}). This internal flag is used to re-read classes containing
	 * such instructions, in order to replace them with standard instructions. In addition, when this
	 * flag is used, goto_w and jsr_w are <i>not</i> converted into goto and jsr, to make sure that
	 * infinite loops where a goto_w is replaced with a goto in ClassReader and converted back to a
	 * goto_w in ClassWriter cannot occur.
	 */
	static final int EXPAND_ASM_INSNS = 256;

	/**
	 * The maximum size of array to allocate.
	 */
	private static final int MAX_BUFFER_SIZE = 1024 * 1024;

	/**
	 * The size of the temporary byte array used to read class input streams chunk by chunk.
	 */
	private static final int INPUT_STREAM_DATA_CHUNK_SIZE = 4096;


	@Deprecated
	public final byte[] b;


	// access_flags所在位置偏移量
	public final int header;


	//二进制字节码文件数组
	final byte[] classFileBuffer;


	//记录常量池每个cp_info的起始偏移量（不含tag）
	private final int[] cpInfoOffsets;

	//常量池每个字面量值
	private final String[] constantUtf8Values;

	/**
	 * The ConstantDynamic objects corresponding to the CONSTANT_Dynamic constant pool items. This
	 * cache avoids multiple parsing of a given CONSTANT_Dynamic constant pool item.
	 */
	private final ConstantDynamic[] constantDynamicValues;


	//attribute_info（BootstrapMethods类型）中每个bootstrap_methods位置偏移量
	private final int[] bootstrapMethodOffsets;


	//CONSTANT_Utf8_info类型的常量值最大容量
	private final int maxStringLength;


	/**
	 * 创建一个新ClassReader，用于读取解析字节码文件的二进制流
	 * @param classFile 字节码文件二进制流数组
	 */
	public ClassReader(final byte[] classFile) {
		this(classFile, 0, classFile.length);
	}

	/**
	 * 创建一个新ClassReader，用于读取解析字节码文件的二进制流
	 * @param classFileBuffer  字节码文件二进制流数组
	 * @param classFileOffset  二进制数组流开始读取的位置
	 * @param classFileLength ?
	 */
	public ClassReader(
			final byte[] classFileBuffer,
			final int classFileOffset,
			final int classFileLength) {
		this(classFileBuffer, classFileOffset, /* checkClassVersion = */ true);
	}


	/**
	 * 创建一个新ClassReader，用于读取解析字节码文件的二进制流，解析出必要的初始信息
	 * @param classFileBuffer  字节码文件二进制流数组
	 * @param classFileOffset  二进制数组流开始读取的位置
	 * @param checkClassVersion 是否要校验字节码编译的版本，true - 校验
	 */
	ClassReader(
			final byte[] classFileBuffer, final int classFileOffset, final boolean checkClassVersion) {
		this.classFileBuffer = classFileBuffer;
		this.b = classFileBuffer;


		//验证字节码编译的版本
		if (checkClassVersion && readShort(classFileOffset + 6) > Opcodes.V21) {
			throw new IllegalArgumentException(
					"Unsupported class file major version " + readShort(classFileOffset + 6));
		}

		//常量池容量（偏移量8，占2个字节）
		int constantPoolCount = readUnsignedShort(classFileOffset + 8);
		//记录常量池中每个cp_info的偏移量（不含tag）
		cpInfoOffsets = new int[constantPoolCount];
		//常量池中的字面量（utf-8编码）
		constantUtf8Values = new String[constantPoolCount];


		// 当前cp_info的位置（从1开始，位置0的值保留）
		int currentCpInfoIndex = 1;

		// 当前读取的常量池内容的偏移量（从偏移量10开始：4+2+2+2）
		int currentCpInfoOffset = classFileOffset + 10;
		//记录常量池中长度最长的常量值索引位置
		int currentMaxStringLength = 0;

		//与动态方法调用或lambda有关（指向CONSTANT_MethodHandle_info、CONSTANT_MethodType_info）
		boolean hasBootstrapMethods = false;
		boolean hasConstantDynamic = false;

		//遍历常量池，解析每一个cp_info
		while (currentCpInfoIndex < constantPoolCount) {
			cpInfoOffsets[currentCpInfoIndex++] = currentCpInfoOffset + 1;
			//cp_info结构所占字节数，包含tag
			int cpInfoSize;
			//读取tag（占一个字节）：
			switch (classFileBuffer[currentCpInfoOffset]) {
				//占5个字节的cp_info类型：CONSTANT_Fieldref_info、CONSTANT_Methodref_info、
				//                     CONSTANT_InterfaceMethodref_info、CONSTANT_Integer_info、
				// 					   CONSTANT_Float_info、CONSTANT_NameAndType_info
				case Symbol.CONSTANT_FIELDREF_TAG:
				case Symbol.CONSTANT_METHODREF_TAG:
				case Symbol.CONSTANT_INTERFACE_METHODREF_TAG:
				case Symbol.CONSTANT_INTEGER_TAG:
				case Symbol.CONSTANT_FLOAT_TAG:
				case Symbol.CONSTANT_NAME_AND_TYPE_TAG:
					cpInfoSize = 5;
					break;
				//占5个字节的cp_info类型：CONSTANT_Dynamic_info [与动态调用有关，严格来讲，没有此类型]
				case Symbol.CONSTANT_DYNAMIC_TAG:
					cpInfoSize = 5;
					hasBootstrapMethods = true;
					hasConstantDynamic = true;
					break;
				//占5个字节的cp_info类型：CONSTANT_InvokeDynamic_info [与动态调用有关]
				case Symbol.CONSTANT_INVOKE_DYNAMIC_TAG:
					cpInfoSize = 5;
					hasBootstrapMethods = true;
					break;
				//占9个字节的cp_info类型：CONSTANT_Long_info、CONSTANT_Double_info
				case Symbol.CONSTANT_LONG_TAG:
				case Symbol.CONSTANT_DOUBLE_TAG:
					cpInfoSize = 9;
					currentCpInfoIndex++;
					break;
				//占n个字节的cp_info类型：CONSTANT_Utf8_info
				case Symbol.CONSTANT_UTF8_TAG:
					//3 = tag(1bytes) + length(2bytes)
					cpInfoSize = 3 + readUnsignedShort(currentCpInfoOffset + 1);
					if (cpInfoSize > currentMaxStringLength) {
						//记录最长字面量长度
						currentMaxStringLength = cpInfoSize;
					}
					break;
				//占4个字节的cp_info类型：CONSTANT_MethodHandle_info
				case Symbol.CONSTANT_METHOD_HANDLE_TAG:
					cpInfoSize = 4;
					break;
				//占3个字节的cp_info类型：CONSTANT_Class_info、CONSTANT_String_info、
				//                     CONSTANT_MethodType_info、CONSTANT_Package_info、CONSTANT_Module_info
				case Symbol.CONSTANT_CLASS_TAG:
				case Symbol.CONSTANT_STRING_TAG:
				case Symbol.CONSTANT_METHOD_TYPE_TAG:
				case Symbol.CONSTANT_PACKAGE_TAG:
				case Symbol.CONSTANT_MODULE_TAG:
					cpInfoSize = 3;
					break;
				default:
					throw new IllegalArgumentException();
			}
			currentCpInfoOffset += cpInfoSize;
		}

		//最长字面量的长度值
		maxStringLength = currentMaxStringLength;
		//类、接口的访问标志，占2个字节（紧跟着常量池后）
		header = currentCpInfoOffset;

		// Allocate the cache of ConstantDynamic values, if there is at least one.
		constantDynamicValues = hasConstantDynamic ? new ConstantDynamic[constantPoolCount] : null;

		// Read the BootstrapMethods attribute, if any (only get the offset of each method).
		bootstrapMethodOffsets =
				hasBootstrapMethods ? readBootstrapMethodsAttribute(currentMaxStringLength) : null;
	}

	/**
	 * 创建一个新ClassReader，用于读取解析字节码文件的二进制流
	 * @param inputStream 字节码文件输入流
	 */
	public ClassReader(final InputStream inputStream) throws IOException {
		this(readStream(inputStream, false));
	}

	/**
	 * 创建一个新ClassReader，用于读取解析指定class文件的二进制流
	 * @param className class文件
	 */
	public ClassReader(final String className) throws IOException {
		this(
				readStream(
						ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class"), true));
	}


	/**
	 * 从输入流中获取字节码二进制流，转为二进制数组
	 * @param inputStream class文件二进制流
	 * @param close 读取完后是否自动关闭流
	 * @return 字节码二进制流数组
	 */
	@SuppressWarnings("PMD.UseTryWithResources")
	private static byte[] readStream(final InputStream inputStream, final boolean close)
			throws IOException {
		if (inputStream == null) {
			throw new IOException("Class not found");
		}
		int bufferSize = computeBufferSize(inputStream);
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			byte[] data = new byte[bufferSize];
			int bytesRead;
			int readCount = 0;
			while ((bytesRead = inputStream.read(data, 0, bufferSize)) != -1) {
				outputStream.write(data, 0, bytesRead);
				readCount++;
			}
			outputStream.flush();
			if (readCount == 1) {
				// SPRING PATCH: some misbehaving InputStreams return -1 but still write to buffer (gh-27429)
				// return data;
				// END OF PATCH
			}
			return outputStream.toByteArray();
		} finally {
			if (close) {
				inputStream.close();
			}
		}
	}

	/**
	 * 计算读取输入流时使用的缓冲区大小
	 * @param inputStream 输入流
	 * @return 缓冲区大小
	 */
	private static int computeBufferSize(final InputStream inputStream) throws IOException {
		//有些流可能返回0
		int expectedLength = inputStream.available();

		//输入流中的数据长度不足256，缓冲区默认大小为4KB
		if (expectedLength < 256) {
			return INPUT_STREAM_DATA_CHUNK_SIZE;
		}

		//尽可能最大保证缓冲区的的大小
		return Math.min(expectedLength, MAX_BUFFER_SIZE);
	}



	/**
	 * 返回类或接口的访问标志
	 * @return 访问标志
	 */
	public int getAccess() {
		//access_flags：占2个字节
		return readUnsignedShort(header);
	}

	/**
	 * 获取类的全限定类名
	 * @return 类名
	 */
	public String getClassName() {
		//class name：占2个字节
		return readClass(header + 2, new char[maxStringLength]);
	}

	/**
	 * 获取父类的全限定类名
	 * @return 父类名
	 */
	public String getSuperName() {
		// super_class：占2个字节
		return readClass(header + 4, new char[maxStringLength]);
	}


	/**
	 * 获取接口（可能实现多个接口）
	 * @return 接口数组
	 */
	public String[] getInterfaces() {
		int currentOffset = header + 6;
		// interfaces_count：占2个字节
		int interfacesCount = readUnsignedShort(currentOffset);
		String[] interfaces = new String[interfacesCount];
		//获取所有interface名称
		if (interfacesCount > 0) {
			char[] charBuffer = new char[maxStringLength];
			for (int i = 0; i < interfacesCount; ++i) {
				currentOffset += 2;
				interfaces[i] = readClass(currentOffset, charBuffer);
			}
		}
		return interfaces;
	}

	// -----------------------------------------------------------------------------------------------
	// Public methods
	// -----------------------------------------------------------------------------------------------


	/**
	 * 提供该入口让visitor访问
	 * @param classVisitor visitor实例
	 * @param parsingOptions 解析选项：{@link #SKIP_CODE} - 跳过代码部分,{@link #SKIP_DEBUG} - 跳过DEBUG信息部分, {@link #SKIP_FRAMES} - 跳过StackMap and StackMapTable属性 or {@link #EXPAND_FRAMES}
	 */
	public void accept(final ClassVisitor classVisitor, final int parsingOptions) {
		accept(classVisitor, new Attribute[0], parsingOptions);
	}

	/**
	 * Makes the given visitor visit the JVMS ClassFile structure passed to the constructor of this
	 * {@link ClassReader}.
	 *
	 * @param classVisitor        the visitor that must visit this class.
	 * @param attributePrototypes prototypes of the attributes that must be parsed during the visit of
	 *                            the class. Any attribute whose type is not equal to the type of one the prototypes will not
	 *                            be parsed: its byte array value will be passed unchanged to the ClassWriter. <i>This may
	 *                            corrupt it if this value contains references to the constant pool, or has syntactic or
	 *                            semantic links with a class element that has been transformed by a class adapter between
	 *                            the reader and the writer</i>.
	 * @param parsingOptions      the options to use to parse this class. One or more of {@link
	 *                            #SKIP_CODE}, {@link #SKIP_DEBUG}, {@link #SKIP_FRAMES} or {@link #EXPAND_FRAMES}.
	 */
	public void accept(
			final ClassVisitor classVisitor,
			final Attribute[] attributePrototypes,
			final int parsingOptions) {
		Context context = new Context();
		context.attributePrototypes = attributePrototypes;
		context.parsingOptions = parsingOptions;
		context.charBuffer = new char[maxStringLength];

		// Read the access_flags, this_class, super_class, interface_count and interfaces fields.
		char[] charBuffer = context.charBuffer;
		int currentOffset = header;
		//获取访问标志
		int accessFlags = readUnsignedShort(currentOffset);
		//获取类名
		String thisClass = readClass(currentOffset + 2, charBuffer);
		//获取直接父类名
		String superClass = readClass(currentOffset + 4, charBuffer);
		//获取实现的所有直接接口
		String[] interfaces = new String[readUnsignedShort(currentOffset + 6)];
		currentOffset += 8;
		for (int i = 0; i < interfaces.length; ++i) {
			interfaces[i] = readClass(currentOffset, charBuffer);
			currentOffset += 2;
		}

		// Read the class attributes (the variables are ordered as in Section 4.7 of the JVMS).
		// Attribute offsets exclude the attribute_name_index and attribute_length fields.

		//内部类属性的起始偏移量，没有为0
		int innerClassesOffset = 0;
		//匿名内部类属性的起始偏移量，没有为0
		int enclosingMethodOffset = 0;
		//类上的泛型签名信息，不存在为null
		String signature = null;
		//字节码文件对应的源文件名称(不含包信息)，不存在为null
		String sourceFile = null;
		// - The string corresponding to the SourceDebugExtension attribute, or null.
		String sourceDebugExtension = null;
		//运行时可见注解的起始偏移量，没有为0
		int runtimeVisibleAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleAnnotations attribute, or 0.
		int runtimeInvisibleAnnotationsOffset = 0;
		// - The offset of the RuntimeVisibleTypeAnnotations attribute, or 0.
		int runtimeVisibleTypeAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleTypeAnnotations attribute, or 0.
		int runtimeInvisibleTypeAnnotationsOffset = 0;
		// - The offset of the Module attribute, or 0.
		int moduleOffset = 0;
		// - The offset of the ModulePackages attribute, or 0.
		int modulePackagesOffset = 0;
		// - The string corresponding to the ModuleMainClass attribute, or null.
		String moduleMainClass = null;
		// - The string corresponding to the NestHost attribute, or null.
		String nestHostClass = null;
		// - The offset of the NestMembers attribute, or 0.
		int nestMembersOffset = 0;
		// - The offset of the PermittedSubclasses attribute, or 0
		int permittedSubclassesOffset = 0;
		// - The offset of the Record attribute, or 0.
		int recordOffset = 0;
		// - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
		//   This list in the <i>reverse order</i> or their order in the ClassFile structure.
		Attribute attributes = null;

		//获取类的attribute_info数组的开始偏移量
		int currentAttributeOffset = getFirstAttributeOffset();
		for (int i = readUnsignedShort(currentAttributeOffset - 2); i > 0; --i) {

			/*
			attribute_info {
    				u2             attribute_name_index;   // 常量池索引值，指向CONSTANT_Utf8_info (属性名称，如：Code、ConstantValue)
    				u4             attribute_length;       // info数组长度
    				u1             info[attribute_length]; // 不同属性的具体结构值
}
			 */

			//获取属性名称(2bytes)（获取属性类型，如：Code、ConstantValue...）
			String attributeName = readUTF8(currentAttributeOffset, charBuffer);
			//获取属性内容长度（4bytes）
			int attributeLength = readInt(currentAttributeOffset + 2);
			currentAttributeOffset += 6;
			//根据attributeName得到不同类型的attribute_info的结构，解析不同类的attribute_info
			if (Constants.SOURCE_FILE.equals(attributeName)) {
				//解析SourceFile属性，获取获取字节码文件对应的源文件名称（不含包信息）
				sourceFile = readUTF8(currentAttributeOffset, charBuffer);
			} else if (Constants.INNER_CLASSES.equals(attributeName)) {
				//记录InnerClasses属性的偏移量，解析内部类
				innerClassesOffset = currentAttributeOffset;
			} else if (Constants.ENCLOSING_METHOD.equals(attributeName)) {
				//记录EnclosingMethod属性的偏移量，解析匿名内部类
				enclosingMethodOffset = currentAttributeOffset;
			} else if (Constants.NEST_HOST.equals(attributeName)) {
				nestHostClass = readClass(currentAttributeOffset, charBuffer);
			} else if (Constants.NEST_MEMBERS.equals(attributeName)) {
				nestMembersOffset = currentAttributeOffset;
			} else if (Constants.PERMITTED_SUBCLASSES.equals(attributeName)) {
				permittedSubclassesOffset = currentAttributeOffset;
			} else if (Constants.SIGNATURE.equals(attributeName)) {
				//解析Signature属性，获取类上的泛型签名信息
				signature = readUTF8(currentAttributeOffset, charBuffer);
			} else if (Constants.RUNTIME_VISIBLE_ANNOTATIONS.equals(attributeName)) {
				//记录RuntimeVisibleAnnotations属性的偏移量，解析运行时可见注解
				runtimeVisibleAnnotationsOffset = currentAttributeOffset;
			} else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				runtimeVisibleTypeAnnotationsOffset = currentAttributeOffset;
			} else if (Constants.DEPRECATED.equals(attributeName)) {
				accessFlags |= Opcodes.ACC_DEPRECATED;
			} else if (Constants.SYNTHETIC.equals(attributeName)) {
				accessFlags |= Opcodes.ACC_SYNTHETIC;
			} else if (Constants.SOURCE_DEBUG_EXTENSION.equals(attributeName)) {
				if (attributeLength > classFileBuffer.length - currentAttributeOffset) {
					throw new IllegalArgumentException();
				}
				sourceDebugExtension =
						readUtf(currentAttributeOffset, attributeLength, new char[attributeLength]);
			} else if (Constants.RUNTIME_INVISIBLE_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleAnnotationsOffset = currentAttributeOffset;
			} else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleTypeAnnotationsOffset = currentAttributeOffset;
			} else if (Constants.RECORD.equals(attributeName)) {
				recordOffset = currentAttributeOffset;
				accessFlags |= Opcodes.ACC_RECORD;
			} else if (Constants.MODULE.equals(attributeName)) {
				moduleOffset = currentAttributeOffset;
			} else if (Constants.MODULE_MAIN_CLASS.equals(attributeName)) {
				moduleMainClass = readClass(currentAttributeOffset, charBuffer);
			} else if (Constants.MODULE_PACKAGES.equals(attributeName)) {
				modulePackagesOffset = currentAttributeOffset;
			} else if (!Constants.BOOTSTRAP_METHODS.equals(attributeName)) {
				// The BootstrapMethods attribute is read in the constructor.
				Attribute attribute =
						readAttribute(
								attributePrototypes,
								attributeName,
								currentAttributeOffset,
								attributeLength,
								charBuffer,
								-1,
								null);
				attribute.nextAttribute = attributes;
				attributes = attribute;
			}
			currentAttributeOffset += attributeLength;
		}


		/*
		读取类的基本信息：
		   1、version - 版本号
		   2、accessFlags - 访问标志，如：public、abstract、interface...
		   3、thisClass - 目标类的全限定类名
		   4、signature - 签名
		   5、superClass  目标类的父类的全限定类名
		   6、目标类的所有接口
		 */
		classVisitor.visit(
				readInt(cpInfoOffsets[1] - 7), accessFlags, thisClass, signature, superClass, interfaces);

		// Visit the SourceFile and SourceDebugExtenstion attributes.
		if ((parsingOptions & SKIP_DEBUG) == 0
				&& (sourceFile != null || sourceDebugExtension != null)) {
			classVisitor.visitSource(sourceFile, sourceDebugExtension);
		}

		// Visit the Module, ModulePackages and ModuleMainClass attributes.
		if (moduleOffset != 0) {
			readModuleAttributes(
					classVisitor, context, moduleOffset, modulePackagesOffset, moduleMainClass);
		}

		// Visit the NestHost attribute.
		if (nestHostClass != null) {
			classVisitor.visitNestHost(nestHostClass);
		}

		// 解析匿名内部类
		if (enclosingMethodOffset != 0) {
			String className = readClass(enclosingMethodOffset, charBuffer);
			int methodIndex = readUnsignedShort(enclosingMethodOffset + 2);
			String name = methodIndex == 0 ? null : readUTF8(cpInfoOffsets[methodIndex], charBuffer);
			String type = methodIndex == 0 ? null : readUTF8(cpInfoOffsets[methodIndex] + 2, charBuffer);
			classVisitor.visitOuterClass(className, name, type);
		}

		// Visit the RuntimeVisibleAnnotations attribute.
		//读取类上运行时（RetentionPolicy.RUNTIME）的注解
		if (runtimeVisibleAnnotationsOffset != 0) {
			// 注解个数
			int numAnnotations = readUnsignedShort(runtimeVisibleAnnotationsOffset);
			int currentAnnotationOffset = runtimeVisibleAnnotationsOffset + 2;

			//遍历解析每一个注解
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				// 获取注解的字段描述符（如：Lorg/springframework/stereotype/Component;）
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				// 解析注解的每个元素名称与元素值
				currentAnnotationOffset =
						readElementValues(
								//创建annotationDescriptor注解的AnnotationVisitor实例
								classVisitor.visitAnnotation(annotationDescriptor, /* visible = */ true),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeInvisibleAnnotations attribute.
		if (runtimeInvisibleAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeInvisibleAnnotationsOffset);
			int currentAnnotationOffset = runtimeInvisibleAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								classVisitor.visitAnnotation(annotationDescriptor, /* visible = */ false),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeVisibleTypeAnnotations attribute.
		if (runtimeVisibleTypeAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeVisibleTypeAnnotationsOffset);
			int currentAnnotationOffset = runtimeVisibleTypeAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the target_type, target_info and target_path fields.
				currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset);
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								classVisitor.visitTypeAnnotation(
										context.currentTypeAnnotationTarget,
										context.currentTypeAnnotationTargetPath,
										annotationDescriptor,
										/* visible = */ true),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeInvisibleTypeAnnotations attribute.
		if (runtimeInvisibleTypeAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeInvisibleTypeAnnotationsOffset);
			int currentAnnotationOffset = runtimeInvisibleTypeAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the target_type, target_info and target_path fields.
				currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset);
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								classVisitor.visitTypeAnnotation(
										context.currentTypeAnnotationTarget,
										context.currentTypeAnnotationTargetPath,
										annotationDescriptor,
										/* visible = */ false),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the non standard attributes.
		while (attributes != null) {
			// Copy and reset the nextAttribute field so that it can also be used in ClassWriter.
			Attribute nextAttribute = attributes.nextAttribute;
			attributes.nextAttribute = null;
			classVisitor.visitAttribute(attributes);
			attributes = nextAttribute;
		}

		// Visit the NestedMembers attribute.
		if (nestMembersOffset != 0) {
			int numberOfNestMembers = readUnsignedShort(nestMembersOffset);
			int currentNestMemberOffset = nestMembersOffset + 2;
			while (numberOfNestMembers-- > 0) {
				classVisitor.visitNestMember(readClass(currentNestMemberOffset, charBuffer));
				currentNestMemberOffset += 2;
			}
		}

		// Visit the PermittedSubclasses attribute.
		if (permittedSubclassesOffset != 0) {
			int numberOfPermittedSubclasses = readUnsignedShort(permittedSubclassesOffset);
			int currentPermittedSubclassesOffset = permittedSubclassesOffset + 2;
			while (numberOfPermittedSubclasses-- > 0) {
				classVisitor.visitPermittedSubclass(
						readClass(currentPermittedSubclassesOffset, charBuffer));
				currentPermittedSubclassesOffset += 2;
			}
		}

		// 内部类解析
		if (innerClassesOffset != 0) {
			/*
			InnerClasses_attribute {
   				u2 attribute_name_index;
    			u4 attribute_length;
    			u2 number_of_classes; // 内部类个数
    				{   u2 inner_class_info_index; // 常量池索引值，指向 CONSTANT_Class_info（内部类全限定类名）
        				u2 outer_class_info_index; // 常量池索引值，指向 CONSTANT_Class_info（外部类的全限定类名）
        				u2 inner_name_index; // 常量池索引值，指向CONSTANT_Utf8_info（内部类名称）
        				u2 inner_class_access_flags;  // 内部类的访问标志
    				} classes[number_of_classes];
			}
			 */
			//获取内部类的个数(2bytes)
			int numberOfClasses = readUnsignedShort(innerClassesOffset);
			int currentClassesOffset = innerClassesOffset + 2;
			while (numberOfClasses-- > 0) {
				//当内部类全限定类名有值，但外部类的全限定类名为null，表示当前内部类是匿名内部类
				classVisitor.visitInnerClass(
						//内部类全限定类名
						readClass(currentClassesOffset, charBuffer),
						//外部类的全限定类名
						readClass(currentClassesOffset + 2, charBuffer),
						//内部类名称
						readUTF8(currentClassesOffset + 4, charBuffer),
						//内部类的访问标志
						readUnsignedShort(currentClassesOffset + 6));
				//每个内部类结构占8个字节
				currentClassesOffset += 8;
			}
		}

		// Visit Record components.
		if (recordOffset != 0) {
			int recordComponentsCount = readUnsignedShort(recordOffset);
			recordOffset += 2;
			while (recordComponentsCount-- > 0) {
				recordOffset = readRecordComponent(classVisitor, context, recordOffset);
			}
		}

		// Visit the fields and methods.
		int fieldsCount = readUnsignedShort(currentOffset);
		currentOffset += 2;

		//读取字段，以及字段上的注解
		while (fieldsCount-- > 0) {
			currentOffset = readField(classVisitor, context, currentOffset);
		}
		int methodsCount = readUnsignedShort(currentOffset);
		currentOffset += 2;

		//读取方法，以及方法上的注解
		while (methodsCount-- > 0) {
			currentOffset = readMethod(classVisitor, context, currentOffset);
		}

		// Visit the end of the class.
		classVisitor.visitEnd();
	}

	// ----------------------------------------------------------------------------------------------
	// Methods to parse modules, fields and methods
	// ----------------------------------------------------------------------------------------------

	/**
	 * Reads the Module, ModulePackages and ModuleMainClass attributes and visit them.
	 *
	 * @param classVisitor         the current class visitor
	 * @param context              information about the class being parsed.
	 * @param moduleOffset         the offset of the Module attribute (excluding the attribute_info's
	 *                             attribute_name_index and attribute_length fields).
	 * @param modulePackagesOffset the offset of the ModulePackages attribute (excluding the
	 *                             attribute_info's attribute_name_index and attribute_length fields), or 0.
	 * @param moduleMainClass      the string corresponding to the ModuleMainClass attribute, or {@literal
	 *                             null}.
	 */
	private void readModuleAttributes(
			final ClassVisitor classVisitor,
			final Context context,
			final int moduleOffset,
			final int modulePackagesOffset,
			final String moduleMainClass) {
		char[] buffer = context.charBuffer;

		// Read the module_name_index, module_flags and module_version_index fields and visit them.
		int currentOffset = moduleOffset;
		String moduleName = readModule(currentOffset, buffer);
		int moduleFlags = readUnsignedShort(currentOffset + 2);
		String moduleVersion = readUTF8(currentOffset + 4, buffer);
		currentOffset += 6;
		ModuleVisitor moduleVisitor = classVisitor.visitModule(moduleName, moduleFlags, moduleVersion);
		if (moduleVisitor == null) {
			return;
		}

		// Visit the ModuleMainClass attribute.
		if (moduleMainClass != null) {
			moduleVisitor.visitMainClass(moduleMainClass);
		}

		// Visit the ModulePackages attribute.
		if (modulePackagesOffset != 0) {
			int packageCount = readUnsignedShort(modulePackagesOffset);
			int currentPackageOffset = modulePackagesOffset + 2;
			while (packageCount-- > 0) {
				moduleVisitor.visitPackage(readPackage(currentPackageOffset, buffer));
				currentPackageOffset += 2;
			}
		}

		// Read the 'requires_count' and 'requires' fields.
		int requiresCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (requiresCount-- > 0) {
			// Read the requires_index, requires_flags and requires_version fields and visit them.
			String requires = readModule(currentOffset, buffer);
			int requiresFlags = readUnsignedShort(currentOffset + 2);
			String requiresVersion = readUTF8(currentOffset + 4, buffer);
			currentOffset += 6;
			moduleVisitor.visitRequire(requires, requiresFlags, requiresVersion);
		}

		// Read the 'exports_count' and 'exports' fields.
		int exportsCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (exportsCount-- > 0) {
			// Read the exports_index, exports_flags, exports_to_count and exports_to_index fields
			// and visit them.
			String exports = readPackage(currentOffset, buffer);
			int exportsFlags = readUnsignedShort(currentOffset + 2);
			int exportsToCount = readUnsignedShort(currentOffset + 4);
			currentOffset += 6;
			String[] exportsTo = null;
			if (exportsToCount != 0) {
				exportsTo = new String[exportsToCount];
				for (int i = 0; i < exportsToCount; ++i) {
					exportsTo[i] = readModule(currentOffset, buffer);
					currentOffset += 2;
				}
			}
			moduleVisitor.visitExport(exports, exportsFlags, exportsTo);
		}

		// Reads the 'opens_count' and 'opens' fields.
		int opensCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (opensCount-- > 0) {
			// Read the opens_index, opens_flags, opens_to_count and opens_to_index fields and visit them.
			String opens = readPackage(currentOffset, buffer);
			int opensFlags = readUnsignedShort(currentOffset + 2);
			int opensToCount = readUnsignedShort(currentOffset + 4);
			currentOffset += 6;
			String[] opensTo = null;
			if (opensToCount != 0) {
				opensTo = new String[opensToCount];
				for (int i = 0; i < opensToCount; ++i) {
					opensTo[i] = readModule(currentOffset, buffer);
					currentOffset += 2;
				}
			}
			moduleVisitor.visitOpen(opens, opensFlags, opensTo);
		}

		// Read the 'uses_count' and 'uses' fields.
		int usesCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (usesCount-- > 0) {
			moduleVisitor.visitUse(readClass(currentOffset, buffer));
			currentOffset += 2;
		}

		// Read the 'provides_count' and 'provides' fields.
		int providesCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (providesCount-- > 0) {
			// Read the provides_index, provides_with_count and provides_with_index fields and visit them.
			String provides = readClass(currentOffset, buffer);
			int providesWithCount = readUnsignedShort(currentOffset + 2);
			currentOffset += 4;
			String[] providesWith = new String[providesWithCount];
			for (int i = 0; i < providesWithCount; ++i) {
				providesWith[i] = readClass(currentOffset, buffer);
				currentOffset += 2;
			}
			moduleVisitor.visitProvide(provides, providesWith);
		}

		// Visit the end of the module attributes.
		moduleVisitor.visitEnd();
	}

	/**
	 * Reads a record component and visit it.
	 *
	 * @param classVisitor          the current class visitor
	 * @param context               information about the class being parsed.
	 * @param recordComponentOffset the offset of the current record component.
	 * @return the offset of the first byte following the record component.
	 */
	private int readRecordComponent(
			final ClassVisitor classVisitor, final Context context, final int recordComponentOffset) {
		char[] charBuffer = context.charBuffer;

		int currentOffset = recordComponentOffset;
		String name = readUTF8(currentOffset, charBuffer);
		String descriptor = readUTF8(currentOffset + 2, charBuffer);
		currentOffset += 4;

		// Read the record component attributes (the variables are ordered as in Section 4.7 of the
		// JVMS).

		// Attribute offsets exclude the attribute_name_index and attribute_length fields.
		// - The string corresponding to the Signature attribute, or null.
		String signature = null;
		// - The offset of the RuntimeVisibleAnnotations attribute, or 0.
		int runtimeVisibleAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleAnnotations attribute, or 0.
		int runtimeInvisibleAnnotationsOffset = 0;
		// - The offset of the RuntimeVisibleTypeAnnotations attribute, or 0.
		int runtimeVisibleTypeAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleTypeAnnotations attribute, or 0.
		int runtimeInvisibleTypeAnnotationsOffset = 0;
		// - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
		//   This list in the <i>reverse order</i> or their order in the ClassFile structure.
		Attribute attributes = null;

		int attributesCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (attributesCount-- > 0) {
			// Read the attribute_info's attribute_name and attribute_length fields.
			String attributeName = readUTF8(currentOffset, charBuffer);
			int attributeLength = readInt(currentOffset + 2);
			currentOffset += 6;
			// The tests are sorted in decreasing frequency order (based on frequencies observed on
			// typical classes).
			if (Constants.SIGNATURE.equals(attributeName)) {
				signature = readUTF8(currentOffset, charBuffer);
			} else if (Constants.RUNTIME_VISIBLE_ANNOTATIONS.equals(attributeName)) {
				runtimeVisibleAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				runtimeVisibleTypeAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_INVISIBLE_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleTypeAnnotationsOffset = currentOffset;
			} else {
				Attribute attribute =
						readAttribute(
								context.attributePrototypes,
								attributeName,
								currentOffset,
								attributeLength,
								charBuffer,
								-1,
								null);
				attribute.nextAttribute = attributes;
				attributes = attribute;
			}
			currentOffset += attributeLength;
		}

		RecordComponentVisitor recordComponentVisitor =
				classVisitor.visitRecordComponent(name, descriptor, signature);
		if (recordComponentVisitor == null) {
			return currentOffset;
		}

		// Visit the RuntimeVisibleAnnotations attribute.
		if (runtimeVisibleAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeVisibleAnnotationsOffset);
			int currentAnnotationOffset = runtimeVisibleAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								recordComponentVisitor.visitAnnotation(annotationDescriptor, /* visible = */ true),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeInvisibleAnnotations attribute.
		if (runtimeInvisibleAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeInvisibleAnnotationsOffset);
			int currentAnnotationOffset = runtimeInvisibleAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								recordComponentVisitor.visitAnnotation(annotationDescriptor, /* visible = */ false),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeVisibleTypeAnnotations attribute.
		if (runtimeVisibleTypeAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeVisibleTypeAnnotationsOffset);
			int currentAnnotationOffset = runtimeVisibleTypeAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the target_type, target_info and target_path fields.
				currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset);
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								recordComponentVisitor.visitTypeAnnotation(
										context.currentTypeAnnotationTarget,
										context.currentTypeAnnotationTargetPath,
										annotationDescriptor,
										/* visible = */ true),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeInvisibleTypeAnnotations attribute.
		if (runtimeInvisibleTypeAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeInvisibleTypeAnnotationsOffset);
			int currentAnnotationOffset = runtimeInvisibleTypeAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the target_type, target_info and target_path fields.
				currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset);
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								recordComponentVisitor.visitTypeAnnotation(
										context.currentTypeAnnotationTarget,
										context.currentTypeAnnotationTargetPath,
										annotationDescriptor,
										/* visible = */ false),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the non standard attributes.
		while (attributes != null) {
			// Copy and reset the nextAttribute field so that it can also be used in FieldWriter.
			Attribute nextAttribute = attributes.nextAttribute;
			attributes.nextAttribute = null;
			recordComponentVisitor.visitAttribute(attributes);
			attributes = nextAttribute;
		}

		// Visit the end of the field.
		recordComponentVisitor.visitEnd();
		return currentOffset;
	}

	/**
	 * Reads a JVMS field_info structure and makes the given visitor visit it.
	 *
	 * @param classVisitor    the visitor that must visit the field.
	 * @param context         information about the class being parsed.
	 * @param fieldInfoOffset the start offset of the field_info structure.
	 * @return the offset of the first byte following the field_info structure.
	 */
	private int readField(
			final ClassVisitor classVisitor, final Context context, final int fieldInfoOffset) {
		char[] charBuffer = context.charBuffer;

		// Read the access_flags, name_index and descriptor_index fields.
		int currentOffset = fieldInfoOffset;
		int accessFlags = readUnsignedShort(currentOffset);
		String name = readUTF8(currentOffset + 2, charBuffer);
		String descriptor = readUTF8(currentOffset + 4, charBuffer);
		currentOffset += 6;

		// Read the field attributes (the variables are ordered as in Section 4.7 of the JVMS).
		// Attribute offsets exclude the attribute_name_index and attribute_length fields.
		// - The value corresponding to the ConstantValue attribute, or null.
		Object constantValue = null;
		// - The string corresponding to the Signature attribute, or null.
		String signature = null;
		// - The offset of the RuntimeVisibleAnnotations attribute, or 0.
		int runtimeVisibleAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleAnnotations attribute, or 0.
		int runtimeInvisibleAnnotationsOffset = 0;
		// - The offset of the RuntimeVisibleTypeAnnotations attribute, or 0.
		int runtimeVisibleTypeAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleTypeAnnotations attribute, or 0.
		int runtimeInvisibleTypeAnnotationsOffset = 0;
		// - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
		//   This list in the <i>reverse order</i> or their order in the ClassFile structure.
		Attribute attributes = null;

		int attributesCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (attributesCount-- > 0) {
			// Read the attribute_info's attribute_name and attribute_length fields.
			String attributeName = readUTF8(currentOffset, charBuffer);
			int attributeLength = readInt(currentOffset + 2);
			currentOffset += 6;
			// The tests are sorted in decreasing frequency order (based on frequencies observed on
			// typical classes).
			if (Constants.CONSTANT_VALUE.equals(attributeName)) {
				int constantvalueIndex = readUnsignedShort(currentOffset);
				constantValue = constantvalueIndex == 0 ? null : readConst(constantvalueIndex, charBuffer);
			} else if (Constants.SIGNATURE.equals(attributeName)) {
				signature = readUTF8(currentOffset, charBuffer);
			} else if (Constants.DEPRECATED.equals(attributeName)) {
				accessFlags |= Opcodes.ACC_DEPRECATED;
			} else if (Constants.SYNTHETIC.equals(attributeName)) {
				accessFlags |= Opcodes.ACC_SYNTHETIC;
			} else if (Constants.RUNTIME_VISIBLE_ANNOTATIONS.equals(attributeName)) {
				runtimeVisibleAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				runtimeVisibleTypeAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_INVISIBLE_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleTypeAnnotationsOffset = currentOffset;
			} else {
				Attribute attribute =
						readAttribute(
								context.attributePrototypes,
								attributeName,
								currentOffset,
								attributeLength,
								charBuffer,
								-1,
								null);
				attribute.nextAttribute = attributes;
				attributes = attribute;
			}
			currentOffset += attributeLength;
		}

		// Visit the field declaration.
		FieldVisitor fieldVisitor =
				classVisitor.visitField(accessFlags, name, descriptor, signature, constantValue);
		if (fieldVisitor == null) {
			return currentOffset;
		}

		// Visit the RuntimeVisibleAnnotations attribute.
		if (runtimeVisibleAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeVisibleAnnotationsOffset);
			int currentAnnotationOffset = runtimeVisibleAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								fieldVisitor.visitAnnotation(annotationDescriptor, /* visible = */ true),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeInvisibleAnnotations attribute.
		if (runtimeInvisibleAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeInvisibleAnnotationsOffset);
			int currentAnnotationOffset = runtimeInvisibleAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								fieldVisitor.visitAnnotation(annotationDescriptor, /* visible = */ false),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeVisibleTypeAnnotations attribute.
		if (runtimeVisibleTypeAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeVisibleTypeAnnotationsOffset);
			int currentAnnotationOffset = runtimeVisibleTypeAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the target_type, target_info and target_path fields.
				currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset);
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								fieldVisitor.visitTypeAnnotation(
										context.currentTypeAnnotationTarget,
										context.currentTypeAnnotationTargetPath,
										annotationDescriptor,
										/* visible = */ true),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeInvisibleTypeAnnotations attribute.
		if (runtimeInvisibleTypeAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeInvisibleTypeAnnotationsOffset);
			int currentAnnotationOffset = runtimeInvisibleTypeAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the target_type, target_info and target_path fields.
				currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset);
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								fieldVisitor.visitTypeAnnotation(
										context.currentTypeAnnotationTarget,
										context.currentTypeAnnotationTargetPath,
										annotationDescriptor,
										/* visible = */ false),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the non standard attributes.
		while (attributes != null) {
			// Copy and reset the nextAttribute field so that it can also be used in FieldWriter.
			Attribute nextAttribute = attributes.nextAttribute;
			attributes.nextAttribute = null;
			fieldVisitor.visitAttribute(attributes);
			attributes = nextAttribute;
		}

		// Visit the end of the field.
		fieldVisitor.visitEnd();
		return currentOffset;
	}

	/**
	 * Reads a JVMS method_info structure and makes the given visitor visit it.
	 *
	 * @param classVisitor     the visitor that must visit the method.
	 * @param context          information about the class being parsed.
	 * @param methodInfoOffset the start offset of the method_info structure.
	 * @return the offset of the first byte following the method_info structure.
	 */
	private int readMethod(
			final ClassVisitor classVisitor, final Context context, final int methodInfoOffset) {
		char[] charBuffer = context.charBuffer;

		// Read the access_flags, name_index and descriptor_index fields.
		int currentOffset = methodInfoOffset;
		context.currentMethodAccessFlags = readUnsignedShort(currentOffset);
		context.currentMethodName = readUTF8(currentOffset + 2, charBuffer);
		context.currentMethodDescriptor = readUTF8(currentOffset + 4, charBuffer);
		currentOffset += 6;

		// Read the method attributes (the variables are ordered as in Section 4.7 of the JVMS).
		// Attribute offsets exclude the attribute_name_index and attribute_length fields.
		// - The offset of the Code attribute, or 0.
		int codeOffset = 0;
		// - The offset of the Exceptions attribute, or 0.
		int exceptionsOffset = 0;
		// - The strings corresponding to the Exceptions attribute, or null.
		String[] exceptions = null;
		// - Whether the method has a Synthetic attribute.
		boolean synthetic = false;
		// - The constant pool index contained in the Signature attribute, or 0.
		int signatureIndex = 0;
		// - The offset of the RuntimeVisibleAnnotations attribute, or 0.
		int runtimeVisibleAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleAnnotations attribute, or 0.
		int runtimeInvisibleAnnotationsOffset = 0;
		// - The offset of the RuntimeVisibleParameterAnnotations attribute, or 0.
		int runtimeVisibleParameterAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleParameterAnnotations attribute, or 0.
		int runtimeInvisibleParameterAnnotationsOffset = 0;
		// - The offset of the RuntimeVisibleTypeAnnotations attribute, or 0.
		int runtimeVisibleTypeAnnotationsOffset = 0;
		// - The offset of the RuntimeInvisibleTypeAnnotations attribute, or 0.
		int runtimeInvisibleTypeAnnotationsOffset = 0;
		// - The offset of the AnnotationDefault attribute, or 0.
		int annotationDefaultOffset = 0;
		// - The offset of the MethodParameters attribute, or 0.
		int methodParametersOffset = 0;
		// - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
		//   This list in the <i>reverse order</i> or their order in the ClassFile structure.
		Attribute attributes = null;

		int attributesCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (attributesCount-- > 0) {
			// Read the attribute_info's attribute_name and attribute_length fields.
			String attributeName = readUTF8(currentOffset, charBuffer);
			int attributeLength = readInt(currentOffset + 2);
			currentOffset += 6;
			// The tests are sorted in decreasing frequency order (based on frequencies observed on
			// typical classes).
			if (Constants.CODE.equals(attributeName)) {
				if ((context.parsingOptions & SKIP_CODE) == 0) {
					codeOffset = currentOffset;
				}
			} else if (Constants.EXCEPTIONS.equals(attributeName)) {
				exceptionsOffset = currentOffset;
				exceptions = new String[readUnsignedShort(exceptionsOffset)];
				int currentExceptionOffset = exceptionsOffset + 2;
				for (int i = 0; i < exceptions.length; ++i) {
					exceptions[i] = readClass(currentExceptionOffset, charBuffer);
					currentExceptionOffset += 2;
				}
			} else if (Constants.SIGNATURE.equals(attributeName)) {
				signatureIndex = readUnsignedShort(currentOffset);
			} else if (Constants.DEPRECATED.equals(attributeName)) {
				context.currentMethodAccessFlags |= Opcodes.ACC_DEPRECATED;
			} else if (Constants.RUNTIME_VISIBLE_ANNOTATIONS.equals(attributeName)) {
				runtimeVisibleAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				runtimeVisibleTypeAnnotationsOffset = currentOffset;
			} else if (Constants.ANNOTATION_DEFAULT.equals(attributeName)) {
				annotationDefaultOffset = currentOffset;
			} else if (Constants.SYNTHETIC.equals(attributeName)) {
				synthetic = true;
				context.currentMethodAccessFlags |= Opcodes.ACC_SYNTHETIC;
			} else if (Constants.RUNTIME_INVISIBLE_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleTypeAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS.equals(attributeName)) {
				runtimeVisibleParameterAnnotationsOffset = currentOffset;
			} else if (Constants.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS.equals(attributeName)) {
				runtimeInvisibleParameterAnnotationsOffset = currentOffset;
			} else if (Constants.METHOD_PARAMETERS.equals(attributeName)) {
				methodParametersOffset = currentOffset;
			} else {
				Attribute attribute =
						readAttribute(
								context.attributePrototypes,
								attributeName,
								currentOffset,
								attributeLength,
								charBuffer,
								-1,
								null);
				attribute.nextAttribute = attributes;
				attributes = attribute;
			}
			currentOffset += attributeLength;
		}

		// Visit the method declaration.
		MethodVisitor methodVisitor =
				classVisitor.visitMethod(
						context.currentMethodAccessFlags,
						context.currentMethodName,
						context.currentMethodDescriptor,
						signatureIndex == 0 ? null : readUtf(signatureIndex, charBuffer),
						exceptions);
		if (methodVisitor == null) {
			return currentOffset;
		}

		// If the returned MethodVisitor is in fact a MethodWriter, it means there is no method
		// adapter between the reader and the writer. In this case, it might be possible to copy
		// the method attributes directly into the writer. If so, return early without visiting
		// the content of these attributes.
		if (methodVisitor instanceof MethodWriter) {
			MethodWriter methodWriter = (MethodWriter) methodVisitor;
			if (methodWriter.canCopyMethodAttributes(
					this,
					synthetic,
					(context.currentMethodAccessFlags & Opcodes.ACC_DEPRECATED) != 0,
					readUnsignedShort(methodInfoOffset + 4),
					signatureIndex,
					exceptionsOffset)) {
				methodWriter.setMethodAttributesSource(methodInfoOffset, currentOffset - methodInfoOffset);
				return currentOffset;
			}
		}

		// Visit the MethodParameters attribute.
		if (methodParametersOffset != 0 && (context.parsingOptions & SKIP_DEBUG) == 0) {
			int parametersCount = readByte(methodParametersOffset);
			int currentParameterOffset = methodParametersOffset + 1;
			while (parametersCount-- > 0) {
				// Read the name_index and access_flags fields and visit them.
				methodVisitor.visitParameter(
						readUTF8(currentParameterOffset, charBuffer),
						readUnsignedShort(currentParameterOffset + 2));
				currentParameterOffset += 4;
			}
		}

		// Visit the AnnotationDefault attribute.
		if (annotationDefaultOffset != 0) {
			AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotationDefault();
			readElementValue(annotationVisitor, annotationDefaultOffset, null, charBuffer);
			if (annotationVisitor != null) {
				annotationVisitor.visitEnd();
			}
		}

		// Visit the RuntimeVisibleAnnotations attribute.
		if (runtimeVisibleAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeVisibleAnnotationsOffset);
			int currentAnnotationOffset = runtimeVisibleAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								methodVisitor.visitAnnotation(annotationDescriptor, /* visible = */ true),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeInvisibleAnnotations attribute.
		if (runtimeInvisibleAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeInvisibleAnnotationsOffset);
			int currentAnnotationOffset = runtimeInvisibleAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								methodVisitor.visitAnnotation(annotationDescriptor, /* visible = */ false),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeVisibleTypeAnnotations attribute.
		if (runtimeVisibleTypeAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeVisibleTypeAnnotationsOffset);
			int currentAnnotationOffset = runtimeVisibleTypeAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the target_type, target_info and target_path fields.
				currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset);
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								methodVisitor.visitTypeAnnotation(
										context.currentTypeAnnotationTarget,
										context.currentTypeAnnotationTargetPath,
										annotationDescriptor,
										/* visible = */ true),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeInvisibleTypeAnnotations attribute.
		if (runtimeInvisibleTypeAnnotationsOffset != 0) {
			int numAnnotations = readUnsignedShort(runtimeInvisibleTypeAnnotationsOffset);
			int currentAnnotationOffset = runtimeInvisibleTypeAnnotationsOffset + 2;
			while (numAnnotations-- > 0) {
				// Parse the target_type, target_info and target_path fields.
				currentAnnotationOffset = readTypeAnnotationTarget(context, currentAnnotationOffset);
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
				currentAnnotationOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentAnnotationOffset =
						readElementValues(
								methodVisitor.visitTypeAnnotation(
										context.currentTypeAnnotationTarget,
										context.currentTypeAnnotationTargetPath,
										annotationDescriptor,
										/* visible = */ false),
								currentAnnotationOffset,
								/* named = */ true,
								charBuffer);
			}
		}

		// Visit the RuntimeVisibleParameterAnnotations attribute.
		if (runtimeVisibleParameterAnnotationsOffset != 0) {
			readParameterAnnotations(
					methodVisitor, context, runtimeVisibleParameterAnnotationsOffset, /* visible = */ true);
		}

		// Visit the RuntimeInvisibleParameterAnnotations attribute.
		if (runtimeInvisibleParameterAnnotationsOffset != 0) {
			readParameterAnnotations(
					methodVisitor,
					context,
					runtimeInvisibleParameterAnnotationsOffset,
					/* visible = */ false);
		}

		// Visit the non standard attributes.
		while (attributes != null) {
			// Copy and reset the nextAttribute field so that it can also be used in MethodWriter.
			Attribute nextAttribute = attributes.nextAttribute;
			attributes.nextAttribute = null;
			methodVisitor.visitAttribute(attributes);
			attributes = nextAttribute;
		}

		// Visit the Code attribute.
		if (codeOffset != 0) {
			methodVisitor.visitCode();
			readCode(methodVisitor, context, codeOffset);
		}

		// Visit the end of the method.
		methodVisitor.visitEnd();
		return currentOffset;
	}

	// ----------------------------------------------------------------------------------------------
	// Methods to parse a Code attribute
	// ----------------------------------------------------------------------------------------------

	/**
	 * Reads a JVMS 'Code' attribute and makes the given visitor visit it.
	 *
	 * @param methodVisitor the visitor that must visit the Code attribute.
	 * @param context       information about the class being parsed.
	 * @param codeOffset    the start offset in {@link #classFileBuffer} of the Code attribute, excluding
	 *                      its attribute_name_index and attribute_length fields.
	 */
	private void readCode(
			final MethodVisitor methodVisitor, final Context context, final int codeOffset) {
		int currentOffset = codeOffset;

		// Read the max_stack, max_locals and code_length fields.
		final byte[] classBuffer = classFileBuffer;
		final char[] charBuffer = context.charBuffer;
		final int maxStack = readUnsignedShort(currentOffset);
		final int maxLocals = readUnsignedShort(currentOffset + 2);
		final int codeLength = readInt(currentOffset + 4);
		currentOffset += 8;
		if (codeLength > classFileBuffer.length - currentOffset) {
			throw new IllegalArgumentException();
		}

		// Read the bytecode 'code' array to create a label for each referenced instruction.
		final int bytecodeStartOffset = currentOffset;
		final int bytecodeEndOffset = currentOffset + codeLength;
		final Label[] labels = context.currentMethodLabels = new Label[codeLength + 1];
		while (currentOffset < bytecodeEndOffset) {
			final int bytecodeOffset = currentOffset - bytecodeStartOffset;
			final int opcode = classBuffer[currentOffset] & 0xFF;
			switch (opcode) {
				case Opcodes.NOP:
				case Opcodes.ACONST_NULL:
				case Opcodes.ICONST_M1:
				case Opcodes.ICONST_0:
				case Opcodes.ICONST_1:
				case Opcodes.ICONST_2:
				case Opcodes.ICONST_3:
				case Opcodes.ICONST_4:
				case Opcodes.ICONST_5:
				case Opcodes.LCONST_0:
				case Opcodes.LCONST_1:
				case Opcodes.FCONST_0:
				case Opcodes.FCONST_1:
				case Opcodes.FCONST_2:
				case Opcodes.DCONST_0:
				case Opcodes.DCONST_1:
				case Opcodes.IALOAD:
				case Opcodes.LALOAD:
				case Opcodes.FALOAD:
				case Opcodes.DALOAD:
				case Opcodes.AALOAD:
				case Opcodes.BALOAD:
				case Opcodes.CALOAD:
				case Opcodes.SALOAD:
				case Opcodes.IASTORE:
				case Opcodes.LASTORE:
				case Opcodes.FASTORE:
				case Opcodes.DASTORE:
				case Opcodes.AASTORE:
				case Opcodes.BASTORE:
				case Opcodes.CASTORE:
				case Opcodes.SASTORE:
				case Opcodes.POP:
				case Opcodes.POP2:
				case Opcodes.DUP:
				case Opcodes.DUP_X1:
				case Opcodes.DUP_X2:
				case Opcodes.DUP2:
				case Opcodes.DUP2_X1:
				case Opcodes.DUP2_X2:
				case Opcodes.SWAP:
				case Opcodes.IADD:
				case Opcodes.LADD:
				case Opcodes.FADD:
				case Opcodes.DADD:
				case Opcodes.ISUB:
				case Opcodes.LSUB:
				case Opcodes.FSUB:
				case Opcodes.DSUB:
				case Opcodes.IMUL:
				case Opcodes.LMUL:
				case Opcodes.FMUL:
				case Opcodes.DMUL:
				case Opcodes.IDIV:
				case Opcodes.LDIV:
				case Opcodes.FDIV:
				case Opcodes.DDIV:
				case Opcodes.IREM:
				case Opcodes.LREM:
				case Opcodes.FREM:
				case Opcodes.DREM:
				case Opcodes.INEG:
				case Opcodes.LNEG:
				case Opcodes.FNEG:
				case Opcodes.DNEG:
				case Opcodes.ISHL:
				case Opcodes.LSHL:
				case Opcodes.ISHR:
				case Opcodes.LSHR:
				case Opcodes.IUSHR:
				case Opcodes.LUSHR:
				case Opcodes.IAND:
				case Opcodes.LAND:
				case Opcodes.IOR:
				case Opcodes.LOR:
				case Opcodes.IXOR:
				case Opcodes.LXOR:
				case Opcodes.I2L:
				case Opcodes.I2F:
				case Opcodes.I2D:
				case Opcodes.L2I:
				case Opcodes.L2F:
				case Opcodes.L2D:
				case Opcodes.F2I:
				case Opcodes.F2L:
				case Opcodes.F2D:
				case Opcodes.D2I:
				case Opcodes.D2L:
				case Opcodes.D2F:
				case Opcodes.I2B:
				case Opcodes.I2C:
				case Opcodes.I2S:
				case Opcodes.LCMP:
				case Opcodes.FCMPL:
				case Opcodes.FCMPG:
				case Opcodes.DCMPL:
				case Opcodes.DCMPG:
				case Opcodes.IRETURN:
				case Opcodes.LRETURN:
				case Opcodes.FRETURN:
				case Opcodes.DRETURN:
				case Opcodes.ARETURN:
				case Opcodes.RETURN:
				case Opcodes.ARRAYLENGTH:
				case Opcodes.ATHROW:
				case Opcodes.MONITORENTER:
				case Opcodes.MONITOREXIT:
				case Constants.ILOAD_0:
				case Constants.ILOAD_1:
				case Constants.ILOAD_2:
				case Constants.ILOAD_3:
				case Constants.LLOAD_0:
				case Constants.LLOAD_1:
				case Constants.LLOAD_2:
				case Constants.LLOAD_3:
				case Constants.FLOAD_0:
				case Constants.FLOAD_1:
				case Constants.FLOAD_2:
				case Constants.FLOAD_3:
				case Constants.DLOAD_0:
				case Constants.DLOAD_1:
				case Constants.DLOAD_2:
				case Constants.DLOAD_3:
				case Constants.ALOAD_0:
				case Constants.ALOAD_1:
				case Constants.ALOAD_2:
				case Constants.ALOAD_3:
				case Constants.ISTORE_0:
				case Constants.ISTORE_1:
				case Constants.ISTORE_2:
				case Constants.ISTORE_3:
				case Constants.LSTORE_0:
				case Constants.LSTORE_1:
				case Constants.LSTORE_2:
				case Constants.LSTORE_3:
				case Constants.FSTORE_0:
				case Constants.FSTORE_1:
				case Constants.FSTORE_2:
				case Constants.FSTORE_3:
				case Constants.DSTORE_0:
				case Constants.DSTORE_1:
				case Constants.DSTORE_2:
				case Constants.DSTORE_3:
				case Constants.ASTORE_0:
				case Constants.ASTORE_1:
				case Constants.ASTORE_2:
				case Constants.ASTORE_3:
					currentOffset += 1;
					break;
				case Opcodes.IFEQ:
				case Opcodes.IFNE:
				case Opcodes.IFLT:
				case Opcodes.IFGE:
				case Opcodes.IFGT:
				case Opcodes.IFLE:
				case Opcodes.IF_ICMPEQ:
				case Opcodes.IF_ICMPNE:
				case Opcodes.IF_ICMPLT:
				case Opcodes.IF_ICMPGE:
				case Opcodes.IF_ICMPGT:
				case Opcodes.IF_ICMPLE:
				case Opcodes.IF_ACMPEQ:
				case Opcodes.IF_ACMPNE:
				case Opcodes.GOTO:
				case Opcodes.JSR:
				case Opcodes.IFNULL:
				case Opcodes.IFNONNULL:
					createLabel(bytecodeOffset + readShort(currentOffset + 1), labels);
					currentOffset += 3;
					break;
				case Constants.ASM_IFEQ:
				case Constants.ASM_IFNE:
				case Constants.ASM_IFLT:
				case Constants.ASM_IFGE:
				case Constants.ASM_IFGT:
				case Constants.ASM_IFLE:
				case Constants.ASM_IF_ICMPEQ:
				case Constants.ASM_IF_ICMPNE:
				case Constants.ASM_IF_ICMPLT:
				case Constants.ASM_IF_ICMPGE:
				case Constants.ASM_IF_ICMPGT:
				case Constants.ASM_IF_ICMPLE:
				case Constants.ASM_IF_ACMPEQ:
				case Constants.ASM_IF_ACMPNE:
				case Constants.ASM_GOTO:
				case Constants.ASM_JSR:
				case Constants.ASM_IFNULL:
				case Constants.ASM_IFNONNULL:
					createLabel(bytecodeOffset + readUnsignedShort(currentOffset + 1), labels);
					currentOffset += 3;
					break;
				case Constants.GOTO_W:
				case Constants.JSR_W:
				case Constants.ASM_GOTO_W:
					createLabel(bytecodeOffset + readInt(currentOffset + 1), labels);
					currentOffset += 5;
					break;
				case Constants.WIDE:
					switch (classBuffer[currentOffset + 1] & 0xFF) {
						case Opcodes.ILOAD:
						case Opcodes.FLOAD:
						case Opcodes.ALOAD:
						case Opcodes.LLOAD:
						case Opcodes.DLOAD:
						case Opcodes.ISTORE:
						case Opcodes.FSTORE:
						case Opcodes.ASTORE:
						case Opcodes.LSTORE:
						case Opcodes.DSTORE:
						case Opcodes.RET:
							currentOffset += 4;
							break;
						case Opcodes.IINC:
							currentOffset += 6;
							break;
						default:
							throw new IllegalArgumentException();
					}
					break;
				case Opcodes.TABLESWITCH:
					// Skip 0 to 3 padding bytes.
					currentOffset += 4 - (bytecodeOffset & 3);
					// Read the default label and the number of table entries.
					createLabel(bytecodeOffset + readInt(currentOffset), labels);
					int numTableEntries = readInt(currentOffset + 8) - readInt(currentOffset + 4) + 1;
					currentOffset += 12;
					// Read the table labels.
					while (numTableEntries-- > 0) {
						createLabel(bytecodeOffset + readInt(currentOffset), labels);
						currentOffset += 4;
					}
					break;
				case Opcodes.LOOKUPSWITCH:
					// Skip 0 to 3 padding bytes.
					currentOffset += 4 - (bytecodeOffset & 3);
					// Read the default label and the number of switch cases.
					createLabel(bytecodeOffset + readInt(currentOffset), labels);
					int numSwitchCases = readInt(currentOffset + 4);
					currentOffset += 8;
					// Read the switch labels.
					while (numSwitchCases-- > 0) {
						createLabel(bytecodeOffset + readInt(currentOffset + 4), labels);
						currentOffset += 8;
					}
					break;
				case Opcodes.ILOAD:
				case Opcodes.LLOAD:
				case Opcodes.FLOAD:
				case Opcodes.DLOAD:
				case Opcodes.ALOAD:
				case Opcodes.ISTORE:
				case Opcodes.LSTORE:
				case Opcodes.FSTORE:
				case Opcodes.DSTORE:
				case Opcodes.ASTORE:
				case Opcodes.RET:
				case Opcodes.BIPUSH:
				case Opcodes.NEWARRAY:
				case Opcodes.LDC:
					currentOffset += 2;
					break;
				case Opcodes.SIPUSH:
				case Constants.LDC_W:
				case Constants.LDC2_W:
				case Opcodes.GETSTATIC:
				case Opcodes.PUTSTATIC:
				case Opcodes.GETFIELD:
				case Opcodes.PUTFIELD:
				case Opcodes.INVOKEVIRTUAL:
				case Opcodes.INVOKESPECIAL:
				case Opcodes.INVOKESTATIC:
				case Opcodes.NEW:
				case Opcodes.ANEWARRAY:
				case Opcodes.CHECKCAST:
				case Opcodes.INSTANCEOF:
				case Opcodes.IINC:
					currentOffset += 3;
					break;
				case Opcodes.INVOKEINTERFACE:
				case Opcodes.INVOKEDYNAMIC:
					currentOffset += 5;
					break;
				case Opcodes.MULTIANEWARRAY:
					currentOffset += 4;
					break;
				default:
					throw new IllegalArgumentException();
			}
		}

		// Read the 'exception_table_length' and 'exception_table' field to create a label for each
		// referenced instruction, and to make methodVisitor visit the corresponding try catch blocks.
		int exceptionTableLength = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (exceptionTableLength-- > 0) {
			Label start = createLabel(readUnsignedShort(currentOffset), labels);
			Label end = createLabel(readUnsignedShort(currentOffset + 2), labels);
			Label handler = createLabel(readUnsignedShort(currentOffset + 4), labels);
			String catchType = readUTF8(cpInfoOffsets[readUnsignedShort(currentOffset + 6)], charBuffer);
			currentOffset += 8;
			methodVisitor.visitTryCatchBlock(start, end, handler, catchType);
		}

		// Read the Code attributes to create a label for each referenced instruction (the variables
		// are ordered as in Section 4.7 of the JVMS). Attribute offsets exclude the
		// attribute_name_index and attribute_length fields.
		// - The offset of the current 'stack_map_frame' in the StackMap[Table] attribute, or 0.
		// Initially, this is the offset of the first 'stack_map_frame' entry. Then this offset is
		// updated after each stack_map_frame is read.
		int stackMapFrameOffset = 0;
		// - The end offset of the StackMap[Table] attribute, or 0.
		int stackMapTableEndOffset = 0;
		// - Whether the stack map frames are compressed (i.e. in a StackMapTable) or not.
		boolean compressedFrames = true;
		// - The offset of the LocalVariableTable attribute, or 0.
		int localVariableTableOffset = 0;
		// - The offset of the LocalVariableTypeTable attribute, or 0.
		int localVariableTypeTableOffset = 0;
		// - The offset of each 'type_annotation' entry in the RuntimeVisibleTypeAnnotations
		// attribute, or null.
		int[] visibleTypeAnnotationOffsets = null;
		// - The offset of each 'type_annotation' entry in the RuntimeInvisibleTypeAnnotations
		// attribute, or null.
		int[] invisibleTypeAnnotationOffsets = null;
		// - The non standard attributes (linked with their {@link Attribute#nextAttribute} field).
		//   This list in the <i>reverse order</i> or their order in the ClassFile structure.
		Attribute attributes = null;

		int attributesCount = readUnsignedShort(currentOffset);
		currentOffset += 2;
		while (attributesCount-- > 0) {
			// Read the attribute_info's attribute_name and attribute_length fields.
			String attributeName = readUTF8(currentOffset, charBuffer);
			int attributeLength = readInt(currentOffset + 2);
			currentOffset += 6;
			if (Constants.LOCAL_VARIABLE_TABLE.equals(attributeName)) {
				if ((context.parsingOptions & SKIP_DEBUG) == 0) {
					localVariableTableOffset = currentOffset;
					// Parse the attribute to find the corresponding (debug only) labels.
					int currentLocalVariableTableOffset = currentOffset;
					int localVariableTableLength = readUnsignedShort(currentLocalVariableTableOffset);
					currentLocalVariableTableOffset += 2;
					while (localVariableTableLength-- > 0) {
						int startPc = readUnsignedShort(currentLocalVariableTableOffset);
						createDebugLabel(startPc, labels);
						int length = readUnsignedShort(currentLocalVariableTableOffset + 2);
						createDebugLabel(startPc + length, labels);
						// Skip the name_index, descriptor_index and index fields (2 bytes each).
						currentLocalVariableTableOffset += 10;
					}
				}
			} else if (Constants.LOCAL_VARIABLE_TYPE_TABLE.equals(attributeName)) {
				localVariableTypeTableOffset = currentOffset;
				// Here we do not extract the labels corresponding to the attribute content. We assume they
				// are the same or a subset of those of the LocalVariableTable attribute.
			} else if (Constants.LINE_NUMBER_TABLE.equals(attributeName)) {
				if ((context.parsingOptions & SKIP_DEBUG) == 0) {
					// Parse the attribute to find the corresponding (debug only) labels.
					int currentLineNumberTableOffset = currentOffset;
					int lineNumberTableLength = readUnsignedShort(currentLineNumberTableOffset);
					currentLineNumberTableOffset += 2;
					while (lineNumberTableLength-- > 0) {
						int startPc = readUnsignedShort(currentLineNumberTableOffset);
						int lineNumber = readUnsignedShort(currentLineNumberTableOffset + 2);
						currentLineNumberTableOffset += 4;
						createDebugLabel(startPc, labels);
						labels[startPc].addLineNumber(lineNumber);
					}
				}
			} else if (Constants.RUNTIME_VISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				visibleTypeAnnotationOffsets =
						readTypeAnnotations(methodVisitor, context, currentOffset, /* visible = */ true);
				// Here we do not extract the labels corresponding to the attribute content. This would
				// require a full parsing of the attribute, which would need to be repeated when parsing
				// the bytecode instructions (see below). Instead, the content of the attribute is read one
				// type annotation at a time (i.e. after a type annotation has been visited, the next type
				// annotation is read), and the labels it contains are also extracted one annotation at a
				// time. This assumes that type annotations are ordered by increasing bytecode offset.
			} else if (Constants.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.equals(attributeName)) {
				invisibleTypeAnnotationOffsets =
						readTypeAnnotations(methodVisitor, context, currentOffset, /* visible = */ false);
				// Same comment as above for the RuntimeVisibleTypeAnnotations attribute.
			} else if (Constants.STACK_MAP_TABLE.equals(attributeName)) {
				if ((context.parsingOptions & SKIP_FRAMES) == 0) {
					stackMapFrameOffset = currentOffset + 2;
					stackMapTableEndOffset = currentOffset + attributeLength;
				}
				// Here we do not extract the labels corresponding to the attribute content. This would
				// require a full parsing of the attribute, which would need to be repeated when parsing
				// the bytecode instructions (see below). Instead, the content of the attribute is read one
				// frame at a time (i.e. after a frame has been visited, the next frame is read), and the
				// labels it contains are also extracted one frame at a time. Thanks to the ordering of
				// frames, having only a "one frame lookahead" is not a problem, i.e. it is not possible to
				// see an offset smaller than the offset of the current instruction and for which no Label
				// exist. Except for UNINITIALIZED type offsets. We solve this by parsing the stack map
				// table without a full decoding (see below).
			} else if ("StackMap".equals(attributeName)) {
				if ((context.parsingOptions & SKIP_FRAMES) == 0) {
					stackMapFrameOffset = currentOffset + 2;
					stackMapTableEndOffset = currentOffset + attributeLength;
					compressedFrames = false;
				}
				// IMPORTANT! Here we assume that the frames are ordered, as in the StackMapTable attribute,
				// although this is not guaranteed by the attribute format. This allows an incremental
				// extraction of the labels corresponding to this attribute (see the comment above for the
				// StackMapTable attribute).
			} else {
				Attribute attribute =
						readAttribute(
								context.attributePrototypes,
								attributeName,
								currentOffset,
								attributeLength,
								charBuffer,
								codeOffset,
								labels);
				attribute.nextAttribute = attributes;
				attributes = attribute;
			}
			currentOffset += attributeLength;
		}

		// Initialize the context fields related to stack map frames, and generate the first
		// (implicit) stack map frame, if needed.
		final boolean expandFrames = (context.parsingOptions & EXPAND_FRAMES) != 0;
		if (stackMapFrameOffset != 0) {
			// The bytecode offset of the first explicit frame is not offset_delta + 1 but only
			// offset_delta. Setting the implicit frame offset to -1 allows us to use of the
			// "offset_delta + 1" rule in all cases.
			context.currentFrameOffset = -1;
			context.currentFrameType = 0;
			context.currentFrameLocalCount = 0;
			context.currentFrameLocalCountDelta = 0;
			context.currentFrameLocalTypes = new Object[maxLocals];
			context.currentFrameStackCount = 0;
			context.currentFrameStackTypes = new Object[maxStack];
			if (expandFrames) {
				computeImplicitFrame(context);
			}
			// Find the labels for UNINITIALIZED frame types. Instead of decoding each element of the
			// stack map table, we look for 3 consecutive bytes that "look like" an UNINITIALIZED type
			// (tag ITEM_Uninitialized, offset within bytecode bounds, NEW instruction at this offset).
			// We may find false positives (i.e. not real UNINITIALIZED types), but this should be rare,
			// and the only consequence will be the creation of an unneeded label. This is better than
			// creating a label for each NEW instruction, and faster than fully decoding the whole stack
			// map table.
			for (int offset = stackMapFrameOffset; offset < stackMapTableEndOffset - 2; ++offset) {
				if (classBuffer[offset] == Frame.ITEM_UNINITIALIZED) {
					int potentialBytecodeOffset = readUnsignedShort(offset + 1);
					if (potentialBytecodeOffset >= 0
							&& potentialBytecodeOffset < codeLength
							&& (classBuffer[bytecodeStartOffset + potentialBytecodeOffset] & 0xFF)
							== Opcodes.NEW) {
						createLabel(potentialBytecodeOffset, labels);
					}
				}
			}
		}
		if (expandFrames && (context.parsingOptions & EXPAND_ASM_INSNS) != 0) {
			// Expanding the ASM specific instructions can introduce F_INSERT frames, even if the method
			// does not currently have any frame. These inserted frames must be computed by simulating the
			// effect of the bytecode instructions, one by one, starting from the implicit first frame.
			// For this, MethodWriter needs to know maxLocals before the first instruction is visited. To
			// ensure this, we visit the implicit first frame here (passing only maxLocals - the rest is
			// computed in MethodWriter).
			methodVisitor.visitFrame(Opcodes.F_NEW, maxLocals, null, 0, null);
		}

		// Visit the bytecode instructions. First, introduce state variables for the incremental parsing
		// of the type annotations.

		// Index of the next runtime visible type annotation to read (in the
		// visibleTypeAnnotationOffsets array).
		int currentVisibleTypeAnnotationIndex = 0;
		// The bytecode offset of the next runtime visible type annotation to read, or -1.
		int currentVisibleTypeAnnotationBytecodeOffset =
				getTypeAnnotationBytecodeOffset(visibleTypeAnnotationOffsets, 0);
		// Index of the next runtime invisible type annotation to read (in the
		// invisibleTypeAnnotationOffsets array).
		int currentInvisibleTypeAnnotationIndex = 0;
		// The bytecode offset of the next runtime invisible type annotation to read, or -1.
		int currentInvisibleTypeAnnotationBytecodeOffset =
				getTypeAnnotationBytecodeOffset(invisibleTypeAnnotationOffsets, 0);

		// Whether a F_INSERT stack map frame must be inserted before the current instruction.
		boolean insertFrame = false;

		// The delta to subtract from a goto_w or jsr_w opcode to get the corresponding goto or jsr
		// opcode, or 0 if goto_w and jsr_w must be left unchanged (i.e. when expanding ASM specific
		// instructions).
		final int wideJumpOpcodeDelta =
				(context.parsingOptions & EXPAND_ASM_INSNS) == 0 ? Constants.WIDE_JUMP_OPCODE_DELTA : 0;

		currentOffset = bytecodeStartOffset;
		while (currentOffset < bytecodeEndOffset) {
			final int currentBytecodeOffset = currentOffset - bytecodeStartOffset;

			// Visit the label and the line number(s) for this bytecode offset, if any.
			Label currentLabel = labels[currentBytecodeOffset];
			if (currentLabel != null) {
				currentLabel.accept(methodVisitor, (context.parsingOptions & SKIP_DEBUG) == 0);
			}

			// Visit the stack map frame for this bytecode offset, if any.
			while (stackMapFrameOffset != 0
					&& (context.currentFrameOffset == currentBytecodeOffset
					|| context.currentFrameOffset == -1)) {
				// If there is a stack map frame for this offset, make methodVisitor visit it, and read the
				// next stack map frame if there is one.
				if (context.currentFrameOffset != -1) {
					if (!compressedFrames || expandFrames) {
						methodVisitor.visitFrame(
								Opcodes.F_NEW,
								context.currentFrameLocalCount,
								context.currentFrameLocalTypes,
								context.currentFrameStackCount,
								context.currentFrameStackTypes);
					} else {
						methodVisitor.visitFrame(
								context.currentFrameType,
								context.currentFrameLocalCountDelta,
								context.currentFrameLocalTypes,
								context.currentFrameStackCount,
								context.currentFrameStackTypes);
					}
					// Since there is already a stack map frame for this bytecode offset, there is no need to
					// insert a new one.
					insertFrame = false;
				}
				if (stackMapFrameOffset < stackMapTableEndOffset) {
					stackMapFrameOffset =
							readStackMapFrame(stackMapFrameOffset, compressedFrames, expandFrames, context);
				} else {
					stackMapFrameOffset = 0;
				}
			}

			// Insert a stack map frame for this bytecode offset, if requested by setting insertFrame to
			// true during the previous iteration. The actual frame content is computed in MethodWriter.
			if (insertFrame) {
				if ((context.parsingOptions & EXPAND_FRAMES) != 0) {
					methodVisitor.visitFrame(Constants.F_INSERT, 0, null, 0, null);
				}
				insertFrame = false;
			}

			// Visit the instruction at this bytecode offset.
			int opcode = classBuffer[currentOffset] & 0xFF;
			switch (opcode) {
				case Opcodes.NOP:
				case Opcodes.ACONST_NULL:
				case Opcodes.ICONST_M1:
				case Opcodes.ICONST_0:
				case Opcodes.ICONST_1:
				case Opcodes.ICONST_2:
				case Opcodes.ICONST_3:
				case Opcodes.ICONST_4:
				case Opcodes.ICONST_5:
				case Opcodes.LCONST_0:
				case Opcodes.LCONST_1:
				case Opcodes.FCONST_0:
				case Opcodes.FCONST_1:
				case Opcodes.FCONST_2:
				case Opcodes.DCONST_0:
				case Opcodes.DCONST_1:
				case Opcodes.IALOAD:
				case Opcodes.LALOAD:
				case Opcodes.FALOAD:
				case Opcodes.DALOAD:
				case Opcodes.AALOAD:
				case Opcodes.BALOAD:
				case Opcodes.CALOAD:
				case Opcodes.SALOAD:
				case Opcodes.IASTORE:
				case Opcodes.LASTORE:
				case Opcodes.FASTORE:
				case Opcodes.DASTORE:
				case Opcodes.AASTORE:
				case Opcodes.BASTORE:
				case Opcodes.CASTORE:
				case Opcodes.SASTORE:
				case Opcodes.POP:
				case Opcodes.POP2:
				case Opcodes.DUP:
				case Opcodes.DUP_X1:
				case Opcodes.DUP_X2:
				case Opcodes.DUP2:
				case Opcodes.DUP2_X1:
				case Opcodes.DUP2_X2:
				case Opcodes.SWAP:
				case Opcodes.IADD:
				case Opcodes.LADD:
				case Opcodes.FADD:
				case Opcodes.DADD:
				case Opcodes.ISUB:
				case Opcodes.LSUB:
				case Opcodes.FSUB:
				case Opcodes.DSUB:
				case Opcodes.IMUL:
				case Opcodes.LMUL:
				case Opcodes.FMUL:
				case Opcodes.DMUL:
				case Opcodes.IDIV:
				case Opcodes.LDIV:
				case Opcodes.FDIV:
				case Opcodes.DDIV:
				case Opcodes.IREM:
				case Opcodes.LREM:
				case Opcodes.FREM:
				case Opcodes.DREM:
				case Opcodes.INEG:
				case Opcodes.LNEG:
				case Opcodes.FNEG:
				case Opcodes.DNEG:
				case Opcodes.ISHL:
				case Opcodes.LSHL:
				case Opcodes.ISHR:
				case Opcodes.LSHR:
				case Opcodes.IUSHR:
				case Opcodes.LUSHR:
				case Opcodes.IAND:
				case Opcodes.LAND:
				case Opcodes.IOR:
				case Opcodes.LOR:
				case Opcodes.IXOR:
				case Opcodes.LXOR:
				case Opcodes.I2L:
				case Opcodes.I2F:
				case Opcodes.I2D:
				case Opcodes.L2I:
				case Opcodes.L2F:
				case Opcodes.L2D:
				case Opcodes.F2I:
				case Opcodes.F2L:
				case Opcodes.F2D:
				case Opcodes.D2I:
				case Opcodes.D2L:
				case Opcodes.D2F:
				case Opcodes.I2B:
				case Opcodes.I2C:
				case Opcodes.I2S:
				case Opcodes.LCMP:
				case Opcodes.FCMPL:
				case Opcodes.FCMPG:
				case Opcodes.DCMPL:
				case Opcodes.DCMPG:
				case Opcodes.IRETURN:
				case Opcodes.LRETURN:
				case Opcodes.FRETURN:
				case Opcodes.DRETURN:
				case Opcodes.ARETURN:
				case Opcodes.RETURN:
				case Opcodes.ARRAYLENGTH:
				case Opcodes.ATHROW:
				case Opcodes.MONITORENTER:
				case Opcodes.MONITOREXIT:
					methodVisitor.visitInsn(opcode);
					currentOffset += 1;
					break;
				case Constants.ILOAD_0:
				case Constants.ILOAD_1:
				case Constants.ILOAD_2:
				case Constants.ILOAD_3:
				case Constants.LLOAD_0:
				case Constants.LLOAD_1:
				case Constants.LLOAD_2:
				case Constants.LLOAD_3:
				case Constants.FLOAD_0:
				case Constants.FLOAD_1:
				case Constants.FLOAD_2:
				case Constants.FLOAD_3:
				case Constants.DLOAD_0:
				case Constants.DLOAD_1:
				case Constants.DLOAD_2:
				case Constants.DLOAD_3:
				case Constants.ALOAD_0:
				case Constants.ALOAD_1:
				case Constants.ALOAD_2:
				case Constants.ALOAD_3:
					opcode -= Constants.ILOAD_0;
					methodVisitor.visitVarInsn(Opcodes.ILOAD + (opcode >> 2), opcode & 0x3);
					currentOffset += 1;
					break;
				case Constants.ISTORE_0:
				case Constants.ISTORE_1:
				case Constants.ISTORE_2:
				case Constants.ISTORE_3:
				case Constants.LSTORE_0:
				case Constants.LSTORE_1:
				case Constants.LSTORE_2:
				case Constants.LSTORE_3:
				case Constants.FSTORE_0:
				case Constants.FSTORE_1:
				case Constants.FSTORE_2:
				case Constants.FSTORE_3:
				case Constants.DSTORE_0:
				case Constants.DSTORE_1:
				case Constants.DSTORE_2:
				case Constants.DSTORE_3:
				case Constants.ASTORE_0:
				case Constants.ASTORE_1:
				case Constants.ASTORE_2:
				case Constants.ASTORE_3:
					opcode -= Constants.ISTORE_0;
					methodVisitor.visitVarInsn(Opcodes.ISTORE + (opcode >> 2), opcode & 0x3);
					currentOffset += 1;
					break;
				case Opcodes.IFEQ:
				case Opcodes.IFNE:
				case Opcodes.IFLT:
				case Opcodes.IFGE:
				case Opcodes.IFGT:
				case Opcodes.IFLE:
				case Opcodes.IF_ICMPEQ:
				case Opcodes.IF_ICMPNE:
				case Opcodes.IF_ICMPLT:
				case Opcodes.IF_ICMPGE:
				case Opcodes.IF_ICMPGT:
				case Opcodes.IF_ICMPLE:
				case Opcodes.IF_ACMPEQ:
				case Opcodes.IF_ACMPNE:
				case Opcodes.GOTO:
				case Opcodes.JSR:
				case Opcodes.IFNULL:
				case Opcodes.IFNONNULL:
					methodVisitor.visitJumpInsn(
							opcode, labels[currentBytecodeOffset + readShort(currentOffset + 1)]);
					currentOffset += 3;
					break;
				case Constants.GOTO_W:
				case Constants.JSR_W:
					methodVisitor.visitJumpInsn(
							opcode - wideJumpOpcodeDelta,
							labels[currentBytecodeOffset + readInt(currentOffset + 1)]);
					currentOffset += 5;
					break;
				case Constants.ASM_IFEQ:
				case Constants.ASM_IFNE:
				case Constants.ASM_IFLT:
				case Constants.ASM_IFGE:
				case Constants.ASM_IFGT:
				case Constants.ASM_IFLE:
				case Constants.ASM_IF_ICMPEQ:
				case Constants.ASM_IF_ICMPNE:
				case Constants.ASM_IF_ICMPLT:
				case Constants.ASM_IF_ICMPGE:
				case Constants.ASM_IF_ICMPGT:
				case Constants.ASM_IF_ICMPLE:
				case Constants.ASM_IF_ACMPEQ:
				case Constants.ASM_IF_ACMPNE:
				case Constants.ASM_GOTO:
				case Constants.ASM_JSR:
				case Constants.ASM_IFNULL:
				case Constants.ASM_IFNONNULL: {
					// A forward jump with an offset > 32767. In this case we automatically replace ASM_GOTO
					// with GOTO_W, ASM_JSR with JSR_W and ASM_IFxxx <l> with IFNOTxxx <L> GOTO_W <l> L:...,
					// where IFNOTxxx is the "opposite" opcode of ASMS_IFxxx (e.g. IFNE for ASM_IFEQ) and
					// where <L> designates the instruction just after the GOTO_W.
					// First, change the ASM specific opcodes ASM_IFEQ ... ASM_JSR, ASM_IFNULL and
					// ASM_IFNONNULL to IFEQ ... JSR, IFNULL and IFNONNULL.
					opcode =
							opcode < Constants.ASM_IFNULL
									? opcode - Constants.ASM_OPCODE_DELTA
									: opcode - Constants.ASM_IFNULL_OPCODE_DELTA;
					Label target = labels[currentBytecodeOffset + readUnsignedShort(currentOffset + 1)];
					if (opcode == Opcodes.GOTO || opcode == Opcodes.JSR) {
						// Replace GOTO with GOTO_W and JSR with JSR_W.
						methodVisitor.visitJumpInsn(opcode + Constants.WIDE_JUMP_OPCODE_DELTA, target);
					} else {
						// Compute the "opposite" of opcode. This can be done by flipping the least
						// significant bit for IFNULL and IFNONNULL, and similarly for IFEQ ... IF_ACMPEQ
						// (with a pre and post offset by 1).
						opcode = opcode < Opcodes.GOTO ? ((opcode + 1) ^ 1) - 1 : opcode ^ 1;
						Label endif = createLabel(currentBytecodeOffset + 3, labels);
						methodVisitor.visitJumpInsn(opcode, endif);
						methodVisitor.visitJumpInsn(Constants.GOTO_W, target);
						// endif designates the instruction just after GOTO_W, and is visited as part of the
						// next instruction. Since it is a jump target, we need to insert a frame here.
						insertFrame = true;
					}
					currentOffset += 3;
					break;
				}
				case Constants.ASM_GOTO_W:
					// Replace ASM_GOTO_W with GOTO_W.
					methodVisitor.visitJumpInsn(
							Constants.GOTO_W, labels[currentBytecodeOffset + readInt(currentOffset + 1)]);
					// The instruction just after is a jump target (because ASM_GOTO_W is used in patterns
					// IFNOTxxx <L> ASM_GOTO_W <l> L:..., see MethodWriter), so we need to insert a frame
					// here.
					insertFrame = true;
					currentOffset += 5;
					break;
				case Constants.WIDE:
					opcode = classBuffer[currentOffset + 1] & 0xFF;
					if (opcode == Opcodes.IINC) {
						methodVisitor.visitIincInsn(
								readUnsignedShort(currentOffset + 2), readShort(currentOffset + 4));
						currentOffset += 6;
					} else {
						methodVisitor.visitVarInsn(opcode, readUnsignedShort(currentOffset + 2));
						currentOffset += 4;
					}
					break;
				case Opcodes.TABLESWITCH: {
					// Skip 0 to 3 padding bytes.
					currentOffset += 4 - (currentBytecodeOffset & 3);
					// Read the instruction.
					Label defaultLabel = labels[currentBytecodeOffset + readInt(currentOffset)];
					int low = readInt(currentOffset + 4);
					int high = readInt(currentOffset + 8);
					currentOffset += 12;
					Label[] table = new Label[high - low + 1];
					for (int i = 0; i < table.length; ++i) {
						table[i] = labels[currentBytecodeOffset + readInt(currentOffset)];
						currentOffset += 4;
					}
					methodVisitor.visitTableSwitchInsn(low, high, defaultLabel, table);
					break;
				}
				case Opcodes.LOOKUPSWITCH: {
					// Skip 0 to 3 padding bytes.
					currentOffset += 4 - (currentBytecodeOffset & 3);
					// Read the instruction.
					Label defaultLabel = labels[currentBytecodeOffset + readInt(currentOffset)];
					int numPairs = readInt(currentOffset + 4);
					currentOffset += 8;
					int[] keys = new int[numPairs];
					Label[] values = new Label[numPairs];
					for (int i = 0; i < numPairs; ++i) {
						keys[i] = readInt(currentOffset);
						values[i] = labels[currentBytecodeOffset + readInt(currentOffset + 4)];
						currentOffset += 8;
					}
					methodVisitor.visitLookupSwitchInsn(defaultLabel, keys, values);
					break;
				}
				case Opcodes.ILOAD:
				case Opcodes.LLOAD:
				case Opcodes.FLOAD:
				case Opcodes.DLOAD:
				case Opcodes.ALOAD:
				case Opcodes.ISTORE:
				case Opcodes.LSTORE:
				case Opcodes.FSTORE:
				case Opcodes.DSTORE:
				case Opcodes.ASTORE:
				case Opcodes.RET:
					methodVisitor.visitVarInsn(opcode, classBuffer[currentOffset + 1] & 0xFF);
					currentOffset += 2;
					break;
				case Opcodes.BIPUSH:
				case Opcodes.NEWARRAY:
					methodVisitor.visitIntInsn(opcode, classBuffer[currentOffset + 1]);
					currentOffset += 2;
					break;
				case Opcodes.SIPUSH:
					methodVisitor.visitIntInsn(opcode, readShort(currentOffset + 1));
					currentOffset += 3;
					break;
				case Opcodes.LDC:
					methodVisitor.visitLdcInsn(readConst(classBuffer[currentOffset + 1] & 0xFF, charBuffer));
					currentOffset += 2;
					break;
				case Constants.LDC_W:
				case Constants.LDC2_W:
					methodVisitor.visitLdcInsn(readConst(readUnsignedShort(currentOffset + 1), charBuffer));
					currentOffset += 3;
					break;
				case Opcodes.GETSTATIC:
				case Opcodes.PUTSTATIC:
				case Opcodes.GETFIELD:
				case Opcodes.PUTFIELD:
				case Opcodes.INVOKEVIRTUAL:
				case Opcodes.INVOKESPECIAL:
				case Opcodes.INVOKESTATIC:
				case Opcodes.INVOKEINTERFACE: {
					int cpInfoOffset = cpInfoOffsets[readUnsignedShort(currentOffset + 1)];
					int nameAndTypeCpInfoOffset = cpInfoOffsets[readUnsignedShort(cpInfoOffset + 2)];
					String owner = readClass(cpInfoOffset, charBuffer);
					String name = readUTF8(nameAndTypeCpInfoOffset, charBuffer);
					String descriptor = readUTF8(nameAndTypeCpInfoOffset + 2, charBuffer);
					if (opcode < Opcodes.INVOKEVIRTUAL) {
						methodVisitor.visitFieldInsn(opcode, owner, name, descriptor);
					} else {
						boolean isInterface =
								classBuffer[cpInfoOffset - 1] == Symbol.CONSTANT_INTERFACE_METHODREF_TAG;
						methodVisitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
					}
					if (opcode == Opcodes.INVOKEINTERFACE) {
						currentOffset += 5;
					} else {
						currentOffset += 3;
					}
					break;
				}
				case Opcodes.INVOKEDYNAMIC: {
					int cpInfoOffset = cpInfoOffsets[readUnsignedShort(currentOffset + 1)];
					int nameAndTypeCpInfoOffset = cpInfoOffsets[readUnsignedShort(cpInfoOffset + 2)];
					String name = readUTF8(nameAndTypeCpInfoOffset, charBuffer);
					String descriptor = readUTF8(nameAndTypeCpInfoOffset + 2, charBuffer);
					int bootstrapMethodOffset = bootstrapMethodOffsets[readUnsignedShort(cpInfoOffset)];
					Handle handle =
							(Handle) readConst(readUnsignedShort(bootstrapMethodOffset), charBuffer);
					Object[] bootstrapMethodArguments =
							new Object[readUnsignedShort(bootstrapMethodOffset + 2)];
					bootstrapMethodOffset += 4;
					for (int i = 0; i < bootstrapMethodArguments.length; i++) {
						bootstrapMethodArguments[i] =
								readConst(readUnsignedShort(bootstrapMethodOffset), charBuffer);
						bootstrapMethodOffset += 2;
					}
					methodVisitor.visitInvokeDynamicInsn(
							name, descriptor, handle, bootstrapMethodArguments);
					currentOffset += 5;
					break;
				}
				case Opcodes.NEW:
				case Opcodes.ANEWARRAY:
				case Opcodes.CHECKCAST:
				case Opcodes.INSTANCEOF:
					methodVisitor.visitTypeInsn(opcode, readClass(currentOffset + 1, charBuffer));
					currentOffset += 3;
					break;
				case Opcodes.IINC:
					methodVisitor.visitIincInsn(
							classBuffer[currentOffset + 1] & 0xFF, classBuffer[currentOffset + 2]);
					currentOffset += 3;
					break;
				case Opcodes.MULTIANEWARRAY:
					methodVisitor.visitMultiANewArrayInsn(
							readClass(currentOffset + 1, charBuffer), classBuffer[currentOffset + 3] & 0xFF);
					currentOffset += 4;
					break;
				default:
					throw new AssertionError();
			}

			// Visit the runtime visible instruction annotations, if any.
			while (visibleTypeAnnotationOffsets != null
					&& currentVisibleTypeAnnotationIndex < visibleTypeAnnotationOffsets.length
					&& currentVisibleTypeAnnotationBytecodeOffset <= currentBytecodeOffset) {
				if (currentVisibleTypeAnnotationBytecodeOffset == currentBytecodeOffset) {
					// Parse the target_type, target_info and target_path fields.
					int currentAnnotationOffset =
							readTypeAnnotationTarget(
									context, visibleTypeAnnotationOffsets[currentVisibleTypeAnnotationIndex]);
					// Parse the type_index field.
					String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
					currentAnnotationOffset += 2;
					// Parse num_element_value_pairs and element_value_pairs and visit these values.
					readElementValues(
							methodVisitor.visitInsnAnnotation(
									context.currentTypeAnnotationTarget,
									context.currentTypeAnnotationTargetPath,
									annotationDescriptor,
									/* visible = */ true),
							currentAnnotationOffset,
							/* named = */ true,
							charBuffer);
				}
				currentVisibleTypeAnnotationBytecodeOffset =
						getTypeAnnotationBytecodeOffset(
								visibleTypeAnnotationOffsets, ++currentVisibleTypeAnnotationIndex);
			}

			// Visit the runtime invisible instruction annotations, if any.
			while (invisibleTypeAnnotationOffsets != null
					&& currentInvisibleTypeAnnotationIndex < invisibleTypeAnnotationOffsets.length
					&& currentInvisibleTypeAnnotationBytecodeOffset <= currentBytecodeOffset) {
				if (currentInvisibleTypeAnnotationBytecodeOffset == currentBytecodeOffset) {
					// Parse the target_type, target_info and target_path fields.
					int currentAnnotationOffset =
							readTypeAnnotationTarget(
									context, invisibleTypeAnnotationOffsets[currentInvisibleTypeAnnotationIndex]);
					// Parse the type_index field.
					String annotationDescriptor = readUTF8(currentAnnotationOffset, charBuffer);
					currentAnnotationOffset += 2;
					// Parse num_element_value_pairs and element_value_pairs and visit these values.
					readElementValues(
							methodVisitor.visitInsnAnnotation(
									context.currentTypeAnnotationTarget,
									context.currentTypeAnnotationTargetPath,
									annotationDescriptor,
									/* visible = */ false),
							currentAnnotationOffset,
							/* named = */ true,
							charBuffer);
				}
				currentInvisibleTypeAnnotationBytecodeOffset =
						getTypeAnnotationBytecodeOffset(
								invisibleTypeAnnotationOffsets, ++currentInvisibleTypeAnnotationIndex);
			}
		}
		if (labels[codeLength] != null) {
			methodVisitor.visitLabel(labels[codeLength]);
		}

		// Visit LocalVariableTable and LocalVariableTypeTable attributes.
		if (localVariableTableOffset != 0 && (context.parsingOptions & SKIP_DEBUG) == 0) {
			// The (start_pc, index, signature_index) fields of each entry of the LocalVariableTypeTable.
			int[] typeTable = null;
			if (localVariableTypeTableOffset != 0) {
				typeTable = new int[readUnsignedShort(localVariableTypeTableOffset) * 3];
				currentOffset = localVariableTypeTableOffset + 2;
				int typeTableIndex = typeTable.length;
				while (typeTableIndex > 0) {
					// Store the offset of 'signature_index', and the value of 'index' and 'start_pc'.
					typeTable[--typeTableIndex] = currentOffset + 6;
					typeTable[--typeTableIndex] = readUnsignedShort(currentOffset + 8);
					typeTable[--typeTableIndex] = readUnsignedShort(currentOffset);
					currentOffset += 10;
				}
			}
			int localVariableTableLength = readUnsignedShort(localVariableTableOffset);
			currentOffset = localVariableTableOffset + 2;
			while (localVariableTableLength-- > 0) {
				int startPc = readUnsignedShort(currentOffset);
				int length = readUnsignedShort(currentOffset + 2);
				String name = readUTF8(currentOffset + 4, charBuffer);
				String descriptor = readUTF8(currentOffset + 6, charBuffer);
				int index = readUnsignedShort(currentOffset + 8);
				currentOffset += 10;
				String signature = null;
				if (typeTable != null) {
					for (int i = 0; i < typeTable.length; i += 3) {
						if (typeTable[i] == startPc && typeTable[i + 1] == index) {
							signature = readUTF8(typeTable[i + 2], charBuffer);
							break;
						}
					}
				}
				methodVisitor.visitLocalVariable(
						name, descriptor, signature, labels[startPc], labels[startPc + length], index);
			}
		}

		// Visit the local variable type annotations of the RuntimeVisibleTypeAnnotations attribute.
		if (visibleTypeAnnotationOffsets != null) {
			for (int typeAnnotationOffset : visibleTypeAnnotationOffsets) {
				int targetType = readByte(typeAnnotationOffset);
				if (targetType == TypeReference.LOCAL_VARIABLE
						|| targetType == TypeReference.RESOURCE_VARIABLE) {
					// Parse the target_type, target_info and target_path fields.
					currentOffset = readTypeAnnotationTarget(context, typeAnnotationOffset);
					// Parse the type_index field.
					String annotationDescriptor = readUTF8(currentOffset, charBuffer);
					currentOffset += 2;
					// Parse num_element_value_pairs and element_value_pairs and visit these values.
					readElementValues(
							methodVisitor.visitLocalVariableAnnotation(
									context.currentTypeAnnotationTarget,
									context.currentTypeAnnotationTargetPath,
									context.currentLocalVariableAnnotationRangeStarts,
									context.currentLocalVariableAnnotationRangeEnds,
									context.currentLocalVariableAnnotationRangeIndices,
									annotationDescriptor,
									/* visible = */ true),
							currentOffset,
							/* named = */ true,
							charBuffer);
				}
			}
		}

		// Visit the local variable type annotations of the RuntimeInvisibleTypeAnnotations attribute.
		if (invisibleTypeAnnotationOffsets != null) {
			for (int typeAnnotationOffset : invisibleTypeAnnotationOffsets) {
				int targetType = readByte(typeAnnotationOffset);
				if (targetType == TypeReference.LOCAL_VARIABLE
						|| targetType == TypeReference.RESOURCE_VARIABLE) {
					// Parse the target_type, target_info and target_path fields.
					currentOffset = readTypeAnnotationTarget(context, typeAnnotationOffset);
					// Parse the type_index field.
					String annotationDescriptor = readUTF8(currentOffset, charBuffer);
					currentOffset += 2;
					// Parse num_element_value_pairs and element_value_pairs and visit these values.
					readElementValues(
							methodVisitor.visitLocalVariableAnnotation(
									context.currentTypeAnnotationTarget,
									context.currentTypeAnnotationTargetPath,
									context.currentLocalVariableAnnotationRangeStarts,
									context.currentLocalVariableAnnotationRangeEnds,
									context.currentLocalVariableAnnotationRangeIndices,
									annotationDescriptor,
									/* visible = */ false),
							currentOffset,
							/* named = */ true,
							charBuffer);
				}
			}
		}

		// Visit the non standard attributes.
		while (attributes != null) {
			// Copy and reset the nextAttribute field so that it can also be used in MethodWriter.
			Attribute nextAttribute = attributes.nextAttribute;
			attributes.nextAttribute = null;
			methodVisitor.visitAttribute(attributes);
			attributes = nextAttribute;
		}

		// Visit the max stack and max locals values.
		methodVisitor.visitMaxs(maxStack, maxLocals);
	}

	/**
	 * Returns the label corresponding to the given bytecode offset. The default implementation of
	 * this method creates a label for the given offset if it has not been already created.
	 *
	 * @param bytecodeOffset a bytecode offset in a method.
	 * @param labels         the already created labels, indexed by their offset. If a label already exists
	 *                       for bytecodeOffset this method must not create a new one. Otherwise it must store the new
	 *                       label in this array.
	 * @return a non null Label, which must be equal to labels[bytecodeOffset].
	 */
	protected Label readLabel(final int bytecodeOffset, final Label[] labels) {
		// SPRING PATCH: leniently handle offset mismatch
		if (bytecodeOffset >= labels.length) {
			return new Label();
		}
		// END OF PATCH
		if (labels[bytecodeOffset] == null) {
			labels[bytecodeOffset] = new Label();
		}
		return labels[bytecodeOffset];
	}

	/**
	 * Creates a label without the {@link Label#FLAG_DEBUG_ONLY} flag set, for the given bytecode
	 * offset. The label is created with a call to {@link #readLabel} and its {@link
	 * Label#FLAG_DEBUG_ONLY} flag is cleared.
	 *
	 * @param bytecodeOffset a bytecode offset in a method.
	 * @param labels         the already created labels, indexed by their offset.
	 * @return a Label without the {@link Label#FLAG_DEBUG_ONLY} flag set.
	 */
	private Label createLabel(final int bytecodeOffset, final Label[] labels) {
		Label label = readLabel(bytecodeOffset, labels);
		label.flags &= ~Label.FLAG_DEBUG_ONLY;
		return label;
	}

	/**
	 * Creates a label with the {@link Label#FLAG_DEBUG_ONLY} flag set, if there is no already
	 * existing label for the given bytecode offset (otherwise does nothing). The label is created
	 * with a call to {@link #readLabel}.
	 *
	 * @param bytecodeOffset a bytecode offset in a method.
	 * @param labels         the already created labels, indexed by their offset.
	 */
	private void createDebugLabel(final int bytecodeOffset, final Label[] labels) {
		if (labels[bytecodeOffset] == null) {
			readLabel(bytecodeOffset, labels).flags |= Label.FLAG_DEBUG_ONLY;
		}
	}

	// ----------------------------------------------------------------------------------------------
	// Methods to parse annotations, type annotations and parameter annotations
	// ----------------------------------------------------------------------------------------------

	/**
	 * Parses a Runtime[In]VisibleTypeAnnotations attribute to find the offset of each type_annotation
	 * entry it contains, to find the corresponding labels, and to visit the try catch block
	 * annotations.
	 *
	 * @param methodVisitor                the method visitor to be used to visit the try catch block annotations.
	 * @param context                      information about the class being parsed.
	 * @param runtimeTypeAnnotationsOffset the start offset of a Runtime[In]VisibleTypeAnnotations
	 *                                     attribute, excluding the attribute_info's attribute_name_index and attribute_length fields.
	 * @param visible                      true if the attribute to parse is a RuntimeVisibleTypeAnnotations attribute,
	 *                                     false it is a RuntimeInvisibleTypeAnnotations attribute.
	 * @return the start offset of each entry of the Runtime[In]VisibleTypeAnnotations_attribute's
	 * 'annotations' array field.
	 */
	private int[] readTypeAnnotations(
			final MethodVisitor methodVisitor,
			final Context context,
			final int runtimeTypeAnnotationsOffset,
			final boolean visible) {
		char[] charBuffer = context.charBuffer;
		int currentOffset = runtimeTypeAnnotationsOffset;
		// Read the num_annotations field and create an array to store the type_annotation offsets.
		int[] typeAnnotationsOffsets = new int[readUnsignedShort(currentOffset)];
		currentOffset += 2;
		// Parse the 'annotations' array field.
		for (int i = 0; i < typeAnnotationsOffsets.length; ++i) {
			typeAnnotationsOffsets[i] = currentOffset;
			// Parse the type_annotation's target_type and the target_info fields. The size of the
			// target_info field depends on the value of target_type.
			int targetType = readInt(currentOffset);
			switch (targetType >>> 24) {
				case TypeReference.LOCAL_VARIABLE:
				case TypeReference.RESOURCE_VARIABLE:
					// A localvar_target has a variable size, which depends on the value of their table_length
					// field. It also references bytecode offsets, for which we need labels.
					int tableLength = readUnsignedShort(currentOffset + 1);
					currentOffset += 3;
					while (tableLength-- > 0) {
						int startPc = readUnsignedShort(currentOffset);
						int length = readUnsignedShort(currentOffset + 2);
						// Skip the index field (2 bytes).
						currentOffset += 6;
						createLabel(startPc, context.currentMethodLabels);
						createLabel(startPc + length, context.currentMethodLabels);
					}
					break;
				case TypeReference.CAST:
				case TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
				case TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT:
				case TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
				case TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT:
					currentOffset += 4;
					break;
				case TypeReference.CLASS_EXTENDS:
				case TypeReference.CLASS_TYPE_PARAMETER_BOUND:
				case TypeReference.METHOD_TYPE_PARAMETER_BOUND:
				case TypeReference.THROWS:
				case TypeReference.EXCEPTION_PARAMETER:
				case TypeReference.INSTANCEOF:
				case TypeReference.NEW:
				case TypeReference.CONSTRUCTOR_REFERENCE:
				case TypeReference.METHOD_REFERENCE:
					currentOffset += 3;
					break;
				case TypeReference.CLASS_TYPE_PARAMETER:
				case TypeReference.METHOD_TYPE_PARAMETER:
				case TypeReference.METHOD_FORMAL_PARAMETER:
				case TypeReference.FIELD:
				case TypeReference.METHOD_RETURN:
				case TypeReference.METHOD_RECEIVER:
				default:
					// TypeReference type which can't be used in Code attribute, or which is unknown.
					throw new IllegalArgumentException();
			}
			// Parse the rest of the type_annotation structure, starting with the target_path structure
			// (whose size depends on its path_length field).
			int pathLength = readByte(currentOffset);
			if ((targetType >>> 24) == TypeReference.EXCEPTION_PARAMETER) {
				// Parse the target_path structure and create a corresponding TypePath.
				TypePath path = pathLength == 0 ? null : new TypePath(classFileBuffer, currentOffset);
				currentOffset += 1 + 2 * pathLength;
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentOffset, charBuffer);
				currentOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentOffset =
						readElementValues(
								methodVisitor.visitTryCatchAnnotation(
										targetType & 0xFFFFFF00, path, annotationDescriptor, visible),
								currentOffset,
								/* named = */ true,
								charBuffer);
			} else {
				// We don't want to visit the other target_type annotations, so we just skip them (which
				// requires some parsing because the element_value_pairs array has a variable size). First,
				// skip the target_path structure:
				currentOffset += 3 + 2 * pathLength;
				// Then skip the num_element_value_pairs and element_value_pairs fields (by reading them
				// with a null AnnotationVisitor).
				currentOffset =
						readElementValues(
								/* annotationVisitor = */ null, currentOffset, /* named = */ true, charBuffer);
			}
		}
		return typeAnnotationsOffsets;
	}

	/**
	 * Returns the bytecode offset corresponding to the specified JVMS 'type_annotation' structure, or
	 * -1 if there is no such type_annotation of if it does not have a bytecode offset.
	 *
	 * @param typeAnnotationOffsets the offset of each 'type_annotation' entry in a
	 *                              Runtime[In]VisibleTypeAnnotations attribute, or {@literal null}.
	 * @param typeAnnotationIndex   the index a 'type_annotation' entry in typeAnnotationOffsets.
	 * @return bytecode offset corresponding to the specified JVMS 'type_annotation' structure, or -1
	 * if there is no such type_annotation of if it does not have a bytecode offset.
	 */
	private int getTypeAnnotationBytecodeOffset(
			final int[] typeAnnotationOffsets, final int typeAnnotationIndex) {
		if (typeAnnotationOffsets == null
				|| typeAnnotationIndex >= typeAnnotationOffsets.length
				|| readByte(typeAnnotationOffsets[typeAnnotationIndex]) < TypeReference.INSTANCEOF) {
			return -1;
		}
		return readUnsignedShort(typeAnnotationOffsets[typeAnnotationIndex] + 1);
	}

	/**
	 * Parses the header of a JVMS type_annotation structure to extract its target_type, target_info
	 * and target_path (the result is stored in the given context), and returns the start offset of
	 * the rest of the type_annotation structure.
	 *
	 * @param context              information about the class being parsed. This is where the extracted
	 *                             target_type and target_path must be stored.
	 * @param typeAnnotationOffset the start offset of a type_annotation structure.
	 * @return the start offset of the rest of the type_annotation structure.
	 */
	private int readTypeAnnotationTarget(final Context context, final int typeAnnotationOffset) {
		int currentOffset = typeAnnotationOffset;
		// Parse and store the target_type structure.
		int targetType = readInt(typeAnnotationOffset);
		switch (targetType >>> 24) {
			case TypeReference.CLASS_TYPE_PARAMETER:
			case TypeReference.METHOD_TYPE_PARAMETER:
			case TypeReference.METHOD_FORMAL_PARAMETER:
				targetType &= 0xFFFF0000;
				currentOffset += 2;
				break;
			case TypeReference.FIELD:
			case TypeReference.METHOD_RETURN:
			case TypeReference.METHOD_RECEIVER:
				targetType &= 0xFF000000;
				currentOffset += 1;
				break;
			case TypeReference.LOCAL_VARIABLE:
			case TypeReference.RESOURCE_VARIABLE:
				targetType &= 0xFF000000;
				int tableLength = readUnsignedShort(currentOffset + 1);
				currentOffset += 3;
				context.currentLocalVariableAnnotationRangeStarts = new Label[tableLength];
				context.currentLocalVariableAnnotationRangeEnds = new Label[tableLength];
				context.currentLocalVariableAnnotationRangeIndices = new int[tableLength];
				for (int i = 0; i < tableLength; ++i) {
					int startPc = readUnsignedShort(currentOffset);
					int length = readUnsignedShort(currentOffset + 2);
					int index = readUnsignedShort(currentOffset + 4);
					currentOffset += 6;
					context.currentLocalVariableAnnotationRangeStarts[i] =
							createLabel(startPc, context.currentMethodLabels);
					context.currentLocalVariableAnnotationRangeEnds[i] =
							createLabel(startPc + length, context.currentMethodLabels);
					context.currentLocalVariableAnnotationRangeIndices[i] = index;
				}
				break;
			case TypeReference.CAST:
			case TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
			case TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT:
			case TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
			case TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT:
				targetType &= 0xFF0000FF;
				currentOffset += 4;
				break;
			case TypeReference.CLASS_EXTENDS:
			case TypeReference.CLASS_TYPE_PARAMETER_BOUND:
			case TypeReference.METHOD_TYPE_PARAMETER_BOUND:
			case TypeReference.THROWS:
			case TypeReference.EXCEPTION_PARAMETER:
				targetType &= 0xFFFFFF00;
				currentOffset += 3;
				break;
			case TypeReference.INSTANCEOF:
			case TypeReference.NEW:
			case TypeReference.CONSTRUCTOR_REFERENCE:
			case TypeReference.METHOD_REFERENCE:
				targetType &= 0xFF000000;
				currentOffset += 3;
				break;
			default:
				throw new IllegalArgumentException();
		}
		context.currentTypeAnnotationTarget = targetType;
		// Parse and store the target_path structure.
		int pathLength = readByte(currentOffset);
		context.currentTypeAnnotationTargetPath =
				pathLength == 0 ? null : new TypePath(classFileBuffer, currentOffset);
		// Return the start offset of the rest of the type_annotation structure.
		return currentOffset + 1 + 2 * pathLength;
	}

	/**
	 * Reads a Runtime[In]VisibleParameterAnnotations attribute and makes the given visitor visit it.
	 *
	 * @param methodVisitor                     the visitor that must visit the parameter annotations.
	 * @param context                           information about the class being parsed.
	 * @param runtimeParameterAnnotationsOffset the start offset of a
	 *                                          Runtime[In]VisibleParameterAnnotations attribute, excluding the attribute_info's
	 *                                          attribute_name_index and attribute_length fields.
	 * @param visible                           true if the attribute to parse is a RuntimeVisibleParameterAnnotations
	 *                                          attribute, false it is a RuntimeInvisibleParameterAnnotations attribute.
	 */
	private void readParameterAnnotations(
			final MethodVisitor methodVisitor,
			final Context context,
			final int runtimeParameterAnnotationsOffset,
			final boolean visible) {
		int currentOffset = runtimeParameterAnnotationsOffset;
		int numParameters = classFileBuffer[currentOffset++] & 0xFF;
		methodVisitor.visitAnnotableParameterCount(numParameters, visible);
		char[] charBuffer = context.charBuffer;
		for (int i = 0; i < numParameters; ++i) {
			int numAnnotations = readUnsignedShort(currentOffset);
			currentOffset += 2;
			while (numAnnotations-- > 0) {
				// Parse the type_index field.
				String annotationDescriptor = readUTF8(currentOffset, charBuffer);
				currentOffset += 2;
				// Parse num_element_value_pairs and element_value_pairs and visit these values.
				currentOffset =
						readElementValues(
								methodVisitor.visitParameterAnnotation(i, annotationDescriptor, visible),
								currentOffset,
								/* named = */ true,
								charBuffer);
			}
		}
	}


	/**
	 * 解析指定注解的所有元素名称和元素值
	 * @param annotationVisitor  注解访问器
	 * @param annotationOffset 当前需要解析的注解的偏移量
	 * @param named  是否要解析注解元素和其值
	 * @param charBuffer 缓冲区
	 * @return 当前解析注解结束位置的偏移量
	 */
	private int readElementValues(
			final AnnotationVisitor annotationVisitor,
			final int annotationOffset,
			final boolean named,
			final char[] charBuffer) {

		int currentOffset = annotationOffset;
		// 注解中元素（属性）个数（num_element_value_pairs）
		int numElementValuePairs = readUnsignedShort(currentOffset);
		currentOffset += 2;

		if (named) {
			// 遍历注解中所有元素（element_value_pairs）
			while (numElementValuePairs-- > 0) {
				//获取注解元素名称（element_name_index）
				String elementName = readUTF8(currentOffset, charBuffer);
				// 获取注解元素名称对应的值（element_value）
				currentOffset =
						readElementValue(annotationVisitor, currentOffset + 2, elementName, charBuffer);
			}
		} else {
			// Parse the array_value array.
			while (numElementValuePairs-- > 0) {
				currentOffset =
						readElementValue(annotationVisitor, currentOffset, /* elementName= */ null, charBuffer);
			}
		}
		if (annotationVisitor != null) {
			annotationVisitor.visitEnd();
		}
		return currentOffset;
	}

	/**
	 * 读取对应元素名称的元素值
	 * @param annotationVisitor 注解访问器
	 * @param elementValueOffset 当前offset指向tag
	 * @param elementName 元素名称
	 * @param charBuffer 缓冲区
	 * @return 读取完后最后位置的偏移量
	 */
	private int readElementValue(
			final AnnotationVisitor annotationVisitor,
			final int elementValueOffset,
			final String elementName,
			final char[] charBuffer) {

		//当前offset指向tag
		int currentOffset = elementValueOffset;
		//没有访问器，根据类型跳过注解信息
		if (annotationVisitor == null) {
			switch (classFileBuffer[currentOffset] & 0xFF) {
				case 'e': // 注解元素类型为枚举，枚举类型占4个字节（2bytes的枚举类型，2bytes的枚举值），tag占一个字节
					return currentOffset + 5;
				case '@': // 注解元素类型为注解，解析注解值
					return readElementValues(null, currentOffset + 3, /* named = */ true, charBuffer);
				case '[': // 注解元素为数组，解析数组值
					return readElementValues(null, currentOffset + 1, /* named = */ false, charBuffer);
				default:
					return currentOffset + 3;
			}
		}

		//有访问器，调用访问器解析注解解析注解元素值：
		switch (classFileBuffer[currentOffset++] & 0xFF) {//根据tag判断元素值类型
			case 'B':// 字节类型，指向CONSTANT_Integer_info
				annotationVisitor.visit(
						elementName, (byte) readInt(cpInfoOffsets[readUnsignedShort(currentOffset)]));
				currentOffset += 2;
				break;
			case 'C'://字符类型，指向指向CONSTANT_Integer_info
				annotationVisitor.visit(
						elementName, (char) readInt(cpInfoOffsets[readUnsignedShort(currentOffset)]));
				currentOffset += 2;
				break;
			case 'D': //双精度浮点类型，指向CONSTANT_Double_info
			case 'F': //单精度浮点类型，指向CONSTANT_Float_info
			case 'I': //整型类型，指向CONSTANT_Integer_info
			case 'J': //长整型类型，指向CONSTANT_Long_info
				annotationVisitor.visit(
						elementName, readConst(readUnsignedShort(currentOffset), charBuffer));
				currentOffset += 2;
				break;
			case 'S': //短整型类型，指向CONSTANT_Integer_info
				annotationVisitor.visit(
						elementName, (short) readInt(cpInfoOffsets[readUnsignedShort(currentOffset)]));
				currentOffset += 2;
				break;

			case 'Z': //布尔类型，指向CONSTANT_Integer_info
				annotationVisitor.visit(
						elementName,
						readInt(cpInfoOffsets[readUnsignedShort(currentOffset)]) == 0
								? Boolean.FALSE
								: Boolean.TRUE);
				currentOffset += 2;
				break;
			case 's': //字符串类型，指向CONSTANT_Utf8_info
				annotationVisitor.visit(elementName, readUTF8(currentOffset, charBuffer));
				currentOffset += 2;
				break;
			case 'e': //枚举类型，获取枚举类型（2bytes）和枚举值（2bytes）
				annotationVisitor.visitEnum(
						elementName,
						readUTF8(currentOffset, charBuffer),
						readUTF8(currentOffset + 2, charBuffer));
				currentOffset += 4;
				break;
			case 'c': // 引用类型，指向CONSTANT_Class_info，获取class的全限定类名
				annotationVisitor.visit(elementName, Type.getType(readUTF8(currentOffset, charBuffer)));
				currentOffset += 2;
				break;
			case '@': // 注解类型，解析注解
				currentOffset =
						readElementValues(
								annotationVisitor.visitAnnotation(elementName, readUTF8(currentOffset, charBuffer)),
								currentOffset + 2,
								true,
								charBuffer);
				break;
			case '[': // 数组类型，
				//获取数组长度
				int numValues = readUnsignedShort(currentOffset);
				currentOffset += 2;
				if (numValues == 0) {
					return readElementValues(
							annotationVisitor.visitArray(elementName),
							currentOffset - 2,
							/* named = */ false,
							charBuffer);
				}
				//获取每个数组元素
				switch (classFileBuffer[currentOffset] & 0xFF) {
					case 'B':
						byte[] byteValues = new byte[numValues];
						for (int i = 0; i < numValues; i++) {
							byteValues[i] = (byte) readInt(cpInfoOffsets[readUnsignedShort(currentOffset + 1)]);
							currentOffset += 3;
						}
						annotationVisitor.visit(elementName, byteValues);
						break;
					case 'Z':
						boolean[] booleanValues = new boolean[numValues];
						for (int i = 0; i < numValues; i++) {
							booleanValues[i] = readInt(cpInfoOffsets[readUnsignedShort(currentOffset + 1)]) != 0;
							currentOffset += 3;
						}
						annotationVisitor.visit(elementName, booleanValues);
						break;
					case 'S':
						short[] shortValues = new short[numValues];
						for (int i = 0; i < numValues; i++) {
							shortValues[i] = (short) readInt(cpInfoOffsets[readUnsignedShort(currentOffset + 1)]);
							currentOffset += 3;
						}
						annotationVisitor.visit(elementName, shortValues);
						break;
					case 'C':
						char[] charValues = new char[numValues];
						for (int i = 0; i < numValues; i++) {
							charValues[i] = (char) readInt(cpInfoOffsets[readUnsignedShort(currentOffset + 1)]);
							currentOffset += 3;
						}
						annotationVisitor.visit(elementName, charValues);
						break;
					case 'I':
						int[] intValues = new int[numValues];
						for (int i = 0; i < numValues; i++) {
							intValues[i] = readInt(cpInfoOffsets[readUnsignedShort(currentOffset + 1)]);
							currentOffset += 3;
						}
						annotationVisitor.visit(elementName, intValues);
						break;
					case 'J':
						long[] longValues = new long[numValues];
						for (int i = 0; i < numValues; i++) {
							longValues[i] = readLong(cpInfoOffsets[readUnsignedShort(currentOffset + 1)]);
							currentOffset += 3;
						}
						annotationVisitor.visit(elementName, longValues);
						break;
					case 'F':
						float[] floatValues = new float[numValues];
						for (int i = 0; i < numValues; i++) {
							floatValues[i] =
									Float.intBitsToFloat(
											readInt(cpInfoOffsets[readUnsignedShort(currentOffset + 1)]));
							currentOffset += 3;
						}
						annotationVisitor.visit(elementName, floatValues);
						break;
					case 'D':
						double[] doubleValues = new double[numValues];
						for (int i = 0; i < numValues; i++) {
							doubleValues[i] =
									Double.longBitsToDouble(
											readLong(cpInfoOffsets[readUnsignedShort(currentOffset + 1)]));
							currentOffset += 3;
						}
						annotationVisitor.visit(elementName, doubleValues);
						break;
					default:
						currentOffset =
								readElementValues(
										annotationVisitor.visitArray(elementName),
										currentOffset - 2,
										/* named = */ false,
										charBuffer);
						break;
				}
				break;
			default:
				throw new IllegalArgumentException();
		}
		return currentOffset;
	}

	// ----------------------------------------------------------------------------------------------
	// Methods to parse stack map frames
	// ----------------------------------------------------------------------------------------------

	/**
	 * Computes the implicit frame of the method currently being parsed (as defined in the given
	 * {@link Context}) and stores it in the given context.
	 *
	 * @param context information about the class being parsed.
	 */
	private void computeImplicitFrame(final Context context) {
		String methodDescriptor = context.currentMethodDescriptor;
		Object[] locals = context.currentFrameLocalTypes;
		int numLocal = 0;
		if ((context.currentMethodAccessFlags & Opcodes.ACC_STATIC) == 0) {
			if ("<init>".equals(context.currentMethodName)) {
				locals[numLocal++] = Opcodes.UNINITIALIZED_THIS;
			} else {
				locals[numLocal++] = readClass(header + 2, context.charBuffer);
			}
		}
		// Parse the method descriptor, one argument type descriptor at each iteration. Start by
		// skipping the first method descriptor character, which is always '('.
		int currentMethodDescritorOffset = 1;
		while (true) {
			int currentArgumentDescriptorStartOffset = currentMethodDescritorOffset;
			switch (methodDescriptor.charAt(currentMethodDescritorOffset++)) {
				case 'Z':
				case 'C':
				case 'B':
				case 'S':
				case 'I':
					locals[numLocal++] = Opcodes.INTEGER;
					break;
				case 'F':
					locals[numLocal++] = Opcodes.FLOAT;
					break;
				case 'J':
					locals[numLocal++] = Opcodes.LONG;
					break;
				case 'D':
					locals[numLocal++] = Opcodes.DOUBLE;
					break;
				case '[':
					while (methodDescriptor.charAt(currentMethodDescritorOffset) == '[') {
						++currentMethodDescritorOffset;
					}
					if (methodDescriptor.charAt(currentMethodDescritorOffset) == 'L') {
						++currentMethodDescritorOffset;
						while (methodDescriptor.charAt(currentMethodDescritorOffset) != ';') {
							++currentMethodDescritorOffset;
						}
					}
					locals[numLocal++] =
							methodDescriptor.substring(
									currentArgumentDescriptorStartOffset, ++currentMethodDescritorOffset);
					break;
				case 'L':
					while (methodDescriptor.charAt(currentMethodDescritorOffset) != ';') {
						++currentMethodDescritorOffset;
					}
					locals[numLocal++] =
							methodDescriptor.substring(
									currentArgumentDescriptorStartOffset + 1, currentMethodDescritorOffset++);
					break;
				default:
					context.currentFrameLocalCount = numLocal;
					return;
			}
		}
	}

	/**
	 * Reads a JVMS 'stack_map_frame' structure and stores the result in the given {@link Context}
	 * object. This method can also be used to read a full_frame structure, excluding its frame_type
	 * field (this is used to parse the legacy StackMap attributes).
	 *
	 * @param stackMapFrameOffset the start offset in {@link #classFileBuffer} of the
	 *                            stack_map_frame_value structure to be read, or the start offset of a full_frame structure
	 *                            (excluding its frame_type field).
	 * @param compressed          true to read a 'stack_map_frame' structure, false to read a 'full_frame'
	 *                            structure without its frame_type field.
	 * @param expand              if the stack map frame must be expanded. See {@link #EXPAND_FRAMES}.
	 * @param context             where the parsed stack map frame must be stored.
	 * @return the end offset of the JVMS 'stack_map_frame' or 'full_frame' structure.
	 */
	private int readStackMapFrame(
			final int stackMapFrameOffset,
			final boolean compressed,
			final boolean expand,
			final Context context) {
		int currentOffset = stackMapFrameOffset;
		final char[] charBuffer = context.charBuffer;
		final Label[] labels = context.currentMethodLabels;
		int frameType;
		if (compressed) {
			// Read the frame_type field.
			frameType = classFileBuffer[currentOffset++] & 0xFF;
		} else {
			frameType = Frame.FULL_FRAME;
			context.currentFrameOffset = -1;
		}
		int offsetDelta;
		context.currentFrameLocalCountDelta = 0;
		if (frameType < Frame.SAME_LOCALS_1_STACK_ITEM_FRAME) {
			offsetDelta = frameType;
			context.currentFrameType = Opcodes.F_SAME;
			context.currentFrameStackCount = 0;
		} else if (frameType < Frame.RESERVED) {
			offsetDelta = frameType - Frame.SAME_LOCALS_1_STACK_ITEM_FRAME;
			currentOffset =
					readVerificationTypeInfo(
							currentOffset, context.currentFrameStackTypes, 0, charBuffer, labels);
			context.currentFrameType = Opcodes.F_SAME1;
			context.currentFrameStackCount = 1;
		} else if (frameType >= Frame.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
			offsetDelta = readUnsignedShort(currentOffset);
			currentOffset += 2;
			if (frameType == Frame.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
				currentOffset =
						readVerificationTypeInfo(
								currentOffset, context.currentFrameStackTypes, 0, charBuffer, labels);
				context.currentFrameType = Opcodes.F_SAME1;
				context.currentFrameStackCount = 1;
			} else if (frameType >= Frame.CHOP_FRAME && frameType < Frame.SAME_FRAME_EXTENDED) {
				context.currentFrameType = Opcodes.F_CHOP;
				context.currentFrameLocalCountDelta = Frame.SAME_FRAME_EXTENDED - frameType;
				context.currentFrameLocalCount -= context.currentFrameLocalCountDelta;
				context.currentFrameStackCount = 0;
			} else if (frameType == Frame.SAME_FRAME_EXTENDED) {
				context.currentFrameType = Opcodes.F_SAME;
				context.currentFrameStackCount = 0;
			} else if (frameType < Frame.FULL_FRAME) {
				int local = expand ? context.currentFrameLocalCount : 0;
				for (int k = frameType - Frame.SAME_FRAME_EXTENDED; k > 0; k--) {
					currentOffset =
							readVerificationTypeInfo(
									currentOffset, context.currentFrameLocalTypes, local++, charBuffer, labels);
				}
				context.currentFrameType = Opcodes.F_APPEND;
				context.currentFrameLocalCountDelta = frameType - Frame.SAME_FRAME_EXTENDED;
				context.currentFrameLocalCount += context.currentFrameLocalCountDelta;
				context.currentFrameStackCount = 0;
			} else {
				final int numberOfLocals = readUnsignedShort(currentOffset);
				currentOffset += 2;
				context.currentFrameType = Opcodes.F_FULL;
				context.currentFrameLocalCountDelta = numberOfLocals;
				context.currentFrameLocalCount = numberOfLocals;
				for (int local = 0; local < numberOfLocals; ++local) {
					currentOffset =
							readVerificationTypeInfo(
									currentOffset, context.currentFrameLocalTypes, local, charBuffer, labels);
				}
				final int numberOfStackItems = readUnsignedShort(currentOffset);
				currentOffset += 2;
				context.currentFrameStackCount = numberOfStackItems;
				for (int stack = 0; stack < numberOfStackItems; ++stack) {
					currentOffset =
							readVerificationTypeInfo(
									currentOffset, context.currentFrameStackTypes, stack, charBuffer, labels);
				}
			}
		} else {
			throw new IllegalArgumentException();
		}
		context.currentFrameOffset += offsetDelta + 1;
		createLabel(context.currentFrameOffset, labels);
		return currentOffset;
	}

	/**
	 * Reads a JVMS 'verification_type_info' structure and stores it at the given index in the given
	 * array.
	 *
	 * @param verificationTypeInfoOffset the start offset of the 'verification_type_info' structure to
	 *                                   read.
	 * @param frame                      the array where the parsed type must be stored.
	 * @param index                      the index in 'frame' where the parsed type must be stored.
	 * @param charBuffer                 the buffer used to read strings in the constant pool.
	 * @param labels                     the labels of the method currently being parsed, indexed by their offset. If the
	 *                                   parsed type is an ITEM_Uninitialized, a new label for the corresponding NEW instruction is
	 *                                   stored in this array if it does not already exist.
	 * @return the end offset of the JVMS 'verification_type_info' structure.
	 */
	private int readVerificationTypeInfo(
			final int verificationTypeInfoOffset,
			final Object[] frame,
			final int index,
			final char[] charBuffer,
			final Label[] labels) {
		int currentOffset = verificationTypeInfoOffset;
		int tag = classFileBuffer[currentOffset++] & 0xFF;
		switch (tag) {
			case Frame.ITEM_TOP:
				frame[index] = Opcodes.TOP;
				break;
			case Frame.ITEM_INTEGER:
				frame[index] = Opcodes.INTEGER;
				break;
			case Frame.ITEM_FLOAT:
				frame[index] = Opcodes.FLOAT;
				break;
			case Frame.ITEM_DOUBLE:
				frame[index] = Opcodes.DOUBLE;
				break;
			case Frame.ITEM_LONG:
				frame[index] = Opcodes.LONG;
				break;
			case Frame.ITEM_NULL:
				frame[index] = Opcodes.NULL;
				break;
			case Frame.ITEM_UNINITIALIZED_THIS:
				frame[index] = Opcodes.UNINITIALIZED_THIS;
				break;
			case Frame.ITEM_OBJECT:
				frame[index] = readClass(currentOffset, charBuffer);
				currentOffset += 2;
				break;
			case Frame.ITEM_UNINITIALIZED:
				frame[index] = createLabel(readUnsignedShort(currentOffset), labels);
				currentOffset += 2;
				break;
			default:
				throw new IllegalArgumentException();
		}
		return currentOffset;
	}

	// ----------------------------------------------------------------------------------------------
	// Methods to parse attributes
	// ----------------------------------------------------------------------------------------------


	/**
	 * 返回类的attribute_info数组的开始的偏移量
	 *  ** 跳过了access_flags、this_class、super_class、interfaces_count、interfaces[interfaces_count]、
	 *  fields_count、fields[fields_count]、methods_count、methods[methods_count]、attributes_count **
	 * @return attribute_info的偏移量
	 */
	final int getFirstAttributeOffset() {

		// currentOffset：当前指向fields_count
		//  header ： access_flags数据的开始偏移量
		//  8 ： access_flags(2bytes) + this_class(2bytes) + super_class(2bytes) + interfaces_count(2bytes)
		//  readUnsignedShort(header + 6) ：读取interface的个数值
		//  readUnsignedShort(header + 6) * 2 ：interfaces的占的字节数（每个interface占2bytes）
		int currentOffset = header + 8 + readUnsignedShort(header + 6) * 2;

		//读取fields_count值，占2个字节
		int fieldsCount = readUnsignedShort(currentOffset);

		//currentOffset：当前指向field_info
		currentOffset += 2;

		//跳过field_info数组
		while (fieldsCount-- > 0) {
			/*
			field_info {
    			u2    access_flags;   字段的访问标志
    			u2    name_index;     常量池索引值，指向CONSTANT_Utf8_info（字段的名称）
    			u2    descriptor_index;    常量池索引值，指向CONSTANT_Utf8_info（字段类型，field descriptor）
    			u2    attributes_count;    字段属性个数
    			attribute_info  attributes[attributes_count];   字段属性数组
			 }
			 */
			// 跳过6bytes - 每个field_info包含access_flags(2bytes)、name_index(2bytes)、descriptor_index(2bytes)
			// 读取字段包含的attribute_info个数(2bytes)
			int attributesCount = readUnsignedShort(currentOffset + 6);
			// 跳过8bytes - access_flags(2bytes) + name_index(2bytes) + descriptor_index(2bytes) + attributes_count(2bytes)
			//currentOffset：指向第一个attribute_info
			currentOffset += 8;

			/*
			attribute_info {
    			u2  attribute_name_index;     常量池索引值，指向CONSTANT_Utf8_info (属性名称，如：Code、ConstantValue)
   				u4  attribute_length;         info数组长度
    			u1  info[attribute_length];   不同属性的具体结构值
			}
			 */
			//跳过filed_info中的所有attribute_info
			while (attributesCount-- > 0) {
				//遍历每个attribute_info
				// 2 - attribute_name_index(2bytes)
				// currentOffset + 2  - attribute_length(4bytes)的偏移量，
				// readInt(currentOffset + 2)：读取attribute_length(4bytes)值，表示info[attribute_length]长度
				// 6 - attribute_name_index(2bytes) + attribute_length(4bytes)
				currentOffset += 6 + readInt(currentOffset + 2);
			}
		}

		//读取methods_count值，占2个字节
		int methodsCount = readUnsignedShort(currentOffset);
		//currentOffset：当前指向methods_count
		currentOffset += 2;

		//跳过method_info数组
		while (methodsCount-- > 0) {
			/*
			method_info {
    			u2   access_flags;   方法访问标志
    			u2   name_index;     常量池索引值，指向CONSTANT_Utf8_info（方法名称）
    			u2   descriptor_index;   常量池索引值，指向CONSTANT_Utf8_info（方法参数、返回类型，method descriptor）
    			u2   attributes_count;   方法属性个数
    			attribute_info   attributes[attributes_count];   方法属性数组
			 }
			 */

			// 跳过6bytes - 每个method_info包含access_flags(2bytes)、name_index(2bytes)、descriptor_index(2bytes)
			// 读取方法包含的attribute_info个数(2bytes)
			int attributesCount = readUnsignedShort(currentOffset + 6);
			// 跳过8bytes - access_flags(2bytes) + name_index(2bytes) + descriptor_index(2bytes) + attributes_count(2bytes)
			//currentOffset：当前指向第一个attribute_info
			currentOffset += 8;

			/*
			attribute_info {
    			u2  attribute_name_index;    // 常量池索引值，指向CONSTANT_Utf8_info (属性名称，如：Code、ConstantValue)
   				u4  attribute_length;        // info数组长度
    			u1  info[attribute_length]; // 不同属性的具体结构值
			}
			 */
			//跳过method_info中的所有attribute_info
			while (attributesCount-- > 0) {
				currentOffset += 6 + readInt(currentOffset + 2);
			}
		}

		// 最后跳过attributes_count(2bytes)
		return currentOffset + 2;
	}


	/**
	 * 从二进制字节码文件中读取attribute_info（这里是BootstrapMethods）中每个bootstrap method的偏移量，以数组的形式返回
	 * @param maxStringLength 给定最大数组长度
	 * @return BootstrapMethods每个bootstrap method的偏移量
	 */
	private int[] readBootstrapMethodsAttribute(final int maxStringLength) {
		char[] charBuffer = new char[maxStringLength];

		//获取attribute_info数组的开始偏移量
		int currentAttributeOffset = getFirstAttributeOffset();
		// readUnsignedShort(currentAttributeOffset - 2) - 读取attribute_info数组容器值
		for (int i = readUnsignedShort(currentAttributeOffset - 2); i > 0; --i) {
			// 读取attribute_info的attribute_name_index
			String attributeName = readUTF8(currentAttributeOffset, charBuffer);
			// 读取attribute_info的attribute_length
			int attributeLength = readInt(currentAttributeOffset + 2);

			//attribute_name_index(2bytes) + attribute_length(4bytes)
			currentAttributeOffset += 6;

			//找到类型为BootstrapMethods的attribute_info
			if (Constants.BOOTSTRAP_METHODS.equals(attributeName)) {

				/*
				BootstrapMethods_attribute {
    				u2 attribute_name_index;
    				u4 attribute_length;
    				u2 num_bootstrap_methods;    数组长度
    				{  u2 bootstrap_method_ref;
        			   u2 num_bootstrap_arguments;
        			   u2 bootstrap_arguments[num_bootstrap_arguments];
    				} bootstrap_methods[num_bootstrap_methods]; 数组
				}
				 */

				//读取num_bootstrap_methods值
				int[] result = new int[readUnsignedShort(currentAttributeOffset)];

				//bootstrap_methods数组的开始偏移量
				int currentBootstrapMethodOffset = currentAttributeOffset + 2;

				//遍历bootstrap_methods数组
				for (int j = 0; j < result.length; ++j) {
					result[j] = currentBootstrapMethodOffset;
					//跳过bootstrap_method_ref(2bytes)、num_bootstrap_arguments(2bytes)、bootstrap_arguments(num_bootstrap_arguments * 2)
					currentBootstrapMethodOffset +=
							4 + readUnsignedShort(currentBootstrapMethodOffset + 2) * 2;
				}

				//
				return result;
			}
			currentAttributeOffset += attributeLength;
		}
		throw new IllegalArgumentException();
	}

	/**
	 * Reads a non standard JVMS 'attribute' structure in {@link #classFileBuffer}.
	 *
	 * @param attributePrototypes prototypes of the attributes that must be parsed during the visit of
	 *                            the class. Any attribute whose type is not equal to the type of one the prototypes will not
	 *                            be parsed: its byte array value will be passed unchanged to the ClassWriter.
	 * @param type                the type of the attribute.
	 * @param offset              the start offset of the JVMS 'attribute' structure in {@link #classFileBuffer}.
	 *                            The 6 attribute header bytes (attribute_name_index and attribute_length) are not taken into
	 *                            account here.
	 * @param length              the length of the attribute's content (excluding the 6 attribute header bytes).
	 * @param charBuffer          the buffer to be used to read strings in the constant pool.
	 * @param codeAttributeOffset the start offset of the enclosing Code attribute in {@link
	 *                            #classFileBuffer}, or -1 if the attribute to be read is not a code attribute. The 6
	 *                            attribute header bytes (attribute_name_index and attribute_length) are not taken into
	 *                            account here.
	 * @param labels              the labels of the method's code, or {@literal null} if the attribute to be read
	 *                            is not a code attribute.
	 * @return the attribute that has been read.
	 */
	private Attribute readAttribute(
			final Attribute[] attributePrototypes,
			final String type,
			final int offset,
			final int length,
			final char[] charBuffer,
			final int codeAttributeOffset,
			final Label[] labels) {
		for (Attribute attributePrototype : attributePrototypes) {
			if (attributePrototype.type.equals(type)) {
				return attributePrototype.read(
						this, offset, length, charBuffer, codeAttributeOffset, labels);
			}
		}
		return new Attribute(type).read(this, offset, length, null, -1, null);
	}

	// -----------------------------------------------------------------------------------------------
	// Utility methods: low level parsing
	// -----------------------------------------------------------------------------------------------

	/**
	 * Returns the number of entries in the class's constant pool table.
	 *
	 * @return the number of entries in the class's constant pool table.
	 */
	public int getItemCount() {
		return cpInfoOffsets.length;
	}

	/**
	 * Returns the start offset in this {@link ClassReader} of a JVMS 'cp_info' structure (i.e. a
	 * constant pool entry), plus one. <i>This method is intended for {@link Attribute} sub classes,
	 * and is normally not needed by class generators or adapters.</i>
	 *
	 * @param constantPoolEntryIndex the index a constant pool entry in the class's constant pool
	 *                               table.
	 * @return the start offset in this {@link ClassReader} of the corresponding JVMS 'cp_info'
	 * structure, plus one.
	 */
	public int getItem(final int constantPoolEntryIndex) {
		return cpInfoOffsets[constantPoolEntryIndex];
	}

	/**
	 * Returns a conservative estimate of the maximum length of the strings contained in the class's
	 * constant pool table.
	 *
	 * @return a conservative estimate of the maximum length of the strings contained in the class's
	 * constant pool table.
	 */
	public int getMaxStringLength() {
		return maxStringLength;
	}

	/**
	 * Reads a byte value in this {@link ClassReader}. <i>This method is intended for {@link
	 * Attribute} sub classes, and is normally not needed by class generators or adapters.</i>
	 *
	 * @param offset the start offset of the value to be read in this {@link ClassReader}.
	 * @return the read value.
	 */
	public int readByte(final int offset) {
		return classFileBuffer[offset] & 0xFF;
	}

	/**
	 * 根据偏移量位置读取2个字节，作为short值
	 * @param offset 偏移量
	 * @return short值
	 */
	public int readUnsignedShort(final int offset) {
		byte[] classBuffer = classFileBuffer;
		return ((classBuffer[offset] & 0xFF) << 8) | (classBuffer[offset + 1] & 0xFF);
	}

	/**
	 * Reads a signed short value in this {@link ClassReader}. <i>This method is intended for {@link
	 * Attribute} sub classes, and is normally not needed by class generators or adapters.</i>
	 *
	 * @param offset the start offset of the value to be read in this {@link ClassReader}.
	 * @return the read value.
	 */
	public short readShort(final int offset) {
		byte[] classBuffer = classFileBuffer;
		return (short) (((classBuffer[offset] & 0xFF) << 8) | (classBuffer[offset + 1] & 0xFF));
	}

	/**
	 * 读取指定偏移的整型值（4bytes）
	 * @param offset 偏移量
	 * @return 整型值
	 */
	public int readInt(final int offset) {
		byte[] classBuffer = classFileBuffer;
		return ((classBuffer[offset] & 0xFF) << 24)
				| ((classBuffer[offset + 1] & 0xFF) << 16)
				| ((classBuffer[offset + 2] & 0xFF) << 8)
				| (classBuffer[offset + 3] & 0xFF);
	}

	/**
	 * Reads a signed long value in this {@link ClassReader}. <i>This method is intended for {@link
	 * Attribute} sub classes, and is normally not needed by class generators or adapters.</i>
	 *
	 * @param offset the start offset of the value to be read in this {@link ClassReader}.
	 * @return the read value.
	 */
	public long readLong(final int offset) {
		long l1 = readInt(offset);
		long l0 = readInt(offset + 4) & 0xFFFFFFFFL;
		return (l1 << 32) | l0;
	}


	/**
	 * 根据偏移量获取常量池中CONSTANT_Utf8_info类型的值
	 * @param offset  常量池中指定cp_info的起始偏移量（不含tag）
	 * @param charBuffer 字面量值的utf-8的unicode编码
	 * @return 字面量值
	 */
	public String readUTF8(final int offset, final char[] charBuffer) {
		// 根据给定偏移量（某个cp_info的偏移量）获取常量池索引
		int constantPoolEntryIndex = readUnsignedShort(offset);
		if (offset == 0 || constantPoolEntryIndex == 0) {
			return null;
		}
		//此索引一定指向CONSTANT_Utf8_info类型的值（含字符串字面量值）
		return readUtf(constantPoolEntryIndex, charBuffer);
	}


	/**
	 * 获取常量池中指定位置的CONSTANT_Utf8_info值
	 * @param constantPoolEntryIndex 常量池索引
	 * @param charBuffer 字面量CONSTANT_Utf8_info值的unicode编码
	 * @return 字符串字面量值
	 */
	final String readUtf(final int constantPoolEntryIndex, final char[] charBuffer) {
		//首先尝试从缓冲中读取，若没有则从字节码二进制中读取并放入缓存中
		String value = constantUtf8Values[constantPoolEntryIndex];
		if (value != null) {
			return value;
		}
		int cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex];
		// cpInfoOffset + 2  - 跳过字符串字面量长度
		// readUnsignedShort(cpInfoOffset) - 去读字符串字面量值
		return constantUtf8Values[constantPoolEntryIndex] =
				readUtf(cpInfoOffset + 2, readUnsignedShort(cpInfoOffset), charBuffer);
	}


	/**
	 * 读取一个字符串字面量(utf-8编码)
	 * @param utfOffset 字符串字面量数组开始位置
	 * @param utfLength 字符串字面量数组结束位置
	 * @param charBuffer  字符串字面量unicode码值
	 * @return 字符串字面量数组编码后的值
	 */
	private String readUtf(final int utfOffset, final int utfLength, final char[] charBuffer) {
		int currentOffset = utfOffset;
		int endOffset = currentOffset + utfLength;
		int strLength = 0;
		byte[] classBuffer = classFileBuffer;
		while (currentOffset < endOffset) {
			int currentByte = classBuffer[currentOffset++];
			if ((currentByte & 0x80) == 0) {
				charBuffer[strLength++] = (char) (currentByte & 0x7F);
			} else if ((currentByte & 0xE0) == 0xC0) {
				charBuffer[strLength++] =
						(char) (((currentByte & 0x1F) << 6) + (classBuffer[currentOffset++] & 0x3F));
			} else {
				charBuffer[strLength++] =
						(char)
								(((currentByte & 0xF) << 12)
										+ ((classBuffer[currentOffset++] & 0x3F) << 6)
										+ (classBuffer[currentOffset++] & 0x3F));
			}
		}
		return new String(charBuffer, 0, strLength);
	}


	/**
	 * 根据offset获取常量池指定位置的偏移量，根据偏移量读取常量池指定索引的CONSTANT_Class_info、CONSTANT_Module_info、
	 * CONSTANT_String_info，CONSTANT_Package_info、CONSTANT_MethodType_info
	 * 这几个数据结构存储相似：
	 *    {
	 *        u1  tag   - 结构标识
	 *        u2  index - 指向常量池中字符串字面量
	 *    }
	 * @param offset 当前偏移量
	 * @param charBuffer  从常量池读取的内容放入此缓冲中
	 * @return 常量池中指定位置的字符串字面量值
	 */
	private String readStringish(final int offset, final char[] charBuffer) {
		// readUnsignedShort(offset)  - 获取常量池中cp_info（不含tag）的偏移位置
		return readUTF8(cpInfoOffsets[readUnsignedShort(offset)], charBuffer);
	}


	/**
	 * 读取常量池中指定位置的CONSTANT_Class_info
	 * @param offset 当前偏移量
	 * @param charBuffer 读取结果存放在此处
	 * @return 类名
	 */
	public String readClass(final int offset, final char[] charBuffer) {
		return readStringish(offset, charBuffer);
	}

	/**
	 * Reads a CONSTANT_Module constant pool entry in this {@link ClassReader}. <i>This method is
	 * intended for {@link Attribute} sub classes, and is normally not needed by class generators or
	 * adapters.</i>
	 *
	 * @param offset     the start offset of an unsigned short value in this {@link ClassReader}, whose
	 *                   value is the index of a CONSTANT_Module entry in class's constant pool table.
	 * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
	 *                   large. It is not automatically resized.
	 * @return the String corresponding to the specified CONSTANT_Module entry.
	 */
	public String readModule(final int offset, final char[] charBuffer) {
		return readStringish(offset, charBuffer);
	}

	/**
	 * Reads a CONSTANT_Package constant pool entry in this {@link ClassReader}. <i>This method is
	 * intended for {@link Attribute} sub classes, and is normally not needed by class generators or
	 * adapters.</i>
	 *
	 * @param offset     the start offset of an unsigned short value in this {@link ClassReader}, whose
	 *                   value is the index of a CONSTANT_Package entry in class's constant pool table.
	 * @param charBuffer the buffer to be used to read the item. This buffer must be sufficiently
	 *                   large. It is not automatically resized.
	 * @return the String corresponding to the specified CONSTANT_Package entry.
	 */
	public String readPackage(final int offset, final char[] charBuffer) {
		return readStringish(offset, charBuffer);
	}

	/**
	 * Reads a CONSTANT_Dynamic constant pool entry in {@link #classFileBuffer}.
	 *
	 * @param constantPoolEntryIndex the index of a CONSTANT_Dynamic entry in the class's constant
	 *                               pool table.
	 * @param charBuffer             the buffer to be used to read the string. This buffer must be sufficiently
	 *                               large. It is not automatically resized.
	 * @return the ConstantDynamic corresponding to the specified CONSTANT_Dynamic entry.
	 */
	private ConstantDynamic readConstantDynamic(
			final int constantPoolEntryIndex, final char[] charBuffer) {
		ConstantDynamic constantDynamic = constantDynamicValues[constantPoolEntryIndex];
		if (constantDynamic != null) {
			return constantDynamic;
		}
		int cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex];
		int nameAndTypeCpInfoOffset = cpInfoOffsets[readUnsignedShort(cpInfoOffset + 2)];
		String name = readUTF8(nameAndTypeCpInfoOffset, charBuffer);
		String descriptor = readUTF8(nameAndTypeCpInfoOffset + 2, charBuffer);
		int bootstrapMethodOffset = bootstrapMethodOffsets[readUnsignedShort(cpInfoOffset)];
		Handle handle = (Handle) readConst(readUnsignedShort(bootstrapMethodOffset), charBuffer);
		Object[] bootstrapMethodArguments = new Object[readUnsignedShort(bootstrapMethodOffset + 2)];
		bootstrapMethodOffset += 4;
		for (int i = 0; i < bootstrapMethodArguments.length; i++) {
			bootstrapMethodArguments[i] = readConst(readUnsignedShort(bootstrapMethodOffset), charBuffer);
			bootstrapMethodOffset += 2;
		}
		return constantDynamicValues[constantPoolEntryIndex] =
				new ConstantDynamic(name, descriptor, handle, bootstrapMethodArguments);
	}



	/**
	 * 根据常量池索引读取指定位置的值
	 * @param constantPoolEntryIndex CONSTANT_Integer_info、CONSTANT_Float_info、CONSTANT_Long_info、CONSTANT_Double_info - 数值
	 *                               CONSTANT_Class_info、CONSTANT_String_info、CONSTANT_MethodType_info - 字符串
	 *                               CONSTANT_MethodHandle_info、CONSTANT_InvokeDynamic_info - 动态调用
	 * @param charBuffer 缓冲区
	 * @return  指定常量池cp_info的解析值
	 */
	public Object readConst(final int constantPoolEntryIndex, final char[] charBuffer) {
		int cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex];
		switch (classFileBuffer[cpInfoOffset - 1]) {
			case Symbol.CONSTANT_INTEGER_TAG:
				return readInt(cpInfoOffset);
			case Symbol.CONSTANT_FLOAT_TAG:
				return Float.intBitsToFloat(readInt(cpInfoOffset));
			case Symbol.CONSTANT_LONG_TAG:
				return readLong(cpInfoOffset);
			case Symbol.CONSTANT_DOUBLE_TAG:
				return Double.longBitsToDouble(readLong(cpInfoOffset));
			case Symbol.CONSTANT_CLASS_TAG:
				return Type.getObjectType(readUTF8(cpInfoOffset, charBuffer));
			case Symbol.CONSTANT_STRING_TAG:
				return readUTF8(cpInfoOffset, charBuffer);
			case Symbol.CONSTANT_METHOD_TYPE_TAG:
				return Type.getMethodType(readUTF8(cpInfoOffset, charBuffer));
			case Symbol.CONSTANT_METHOD_HANDLE_TAG:
				int referenceKind = readByte(cpInfoOffset);
				int referenceCpInfoOffset = cpInfoOffsets[readUnsignedShort(cpInfoOffset + 1)];
				int nameAndTypeCpInfoOffset = cpInfoOffsets[readUnsignedShort(referenceCpInfoOffset + 2)];
				String owner = readClass(referenceCpInfoOffset, charBuffer);
				String name = readUTF8(nameAndTypeCpInfoOffset, charBuffer);
				String descriptor = readUTF8(nameAndTypeCpInfoOffset + 2, charBuffer);
				boolean isInterface =
						classFileBuffer[referenceCpInfoOffset - 1] == Symbol.CONSTANT_INTERFACE_METHODREF_TAG;
				return new Handle(referenceKind, owner, name, descriptor, isInterface);
			case Symbol.CONSTANT_DYNAMIC_TAG:
				return readConstantDynamic(constantPoolEntryIndex, charBuffer);
			default:
				throw new IllegalArgumentException();
		}
	}
}
