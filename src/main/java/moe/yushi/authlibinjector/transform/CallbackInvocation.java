/*
 * Copyright (C) 2019  Haowei Wen <yushijinhun@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package moe.yushi.authlibinjector.transform;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.objectweb.asm.MethodVisitor;

public class CallbackInvocation {

	private static Method findCallbackMethod(Class<?> owner, String methodName) {
		for (Method method : owner.getDeclaredMethods()) {
			int modifiers = method.getModifiers();
			if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) &&
					methodName.equals(method.getName()) &&
					method.getAnnotation(CallbackMethod.class) != null) {
				return method;
			}
		}
		throw new IllegalArgumentException("No such method: " + methodName);
	}

	public static CallbackInvocation push(MethodVisitor mv, Class<?> owner, String methodName) {
		String descriptor;
		try {
			descriptor = MethodHandles.publicLookup().unreflect(findCallbackMethod(owner, methodName)).type().toMethodDescriptorString();
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException(e);
		}

		mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "publicLookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
		mv.visitMethodInsn(INVOKESTATIC, "java/lang/ClassLoader", "getSystemClassLoader", "()Ljava/lang/ClassLoader;", false);
		mv.visitLdcInsn(owner.getName());
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false);
		mv.visitLdcInsn(methodName);
		// we cannot use CONSTANT_MethodType_info here because the class file version may be lower than 51
		mv.visitLdcInsn(descriptor);
		mv.visitInsn(ACONST_NULL);
		mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);

		return new CallbackInvocation(mv, descriptor);
	}

	private MethodVisitor mv;
	private String descriptor;

	private CallbackInvocation(MethodVisitor mv, String descriptor) {
		this.mv = mv;
		this.descriptor = descriptor;
	}

	public void invoke() {
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact", descriptor, false);
	}
}