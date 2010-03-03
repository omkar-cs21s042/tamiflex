/*******************************************************************************
 * Copyright (c) 2010 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Eric Bodden - initial API and implementation
 ******************************************************************************/
package de.bodden.tamiflex.playout;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import de.bodden.tamiflex.playout.rt.ReflLogger;

public class ReflectionMonitor implements ClassFileTransformer {
	
	private final static String REFL_LOGGER = ReflLogger.class.getName().replace('.', '/');
	
	public ReflectionMonitor(File logFile, boolean count) {
		ReflLogger.setMustCount(count);
		ReflLogger.setLogFile(logFile);
	}
	
	public byte[] transform(ClassLoader loader, final String className,
		Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
		byte[] classfileBuffer) throws IllegalClassFormatException {
		if(!className.equals("java/lang/Class") && !className.equals("java/lang/reflect/Method") && !className.equals("java/lang/reflect/Constructor")) return null;		
		
        try {
        	// scan class binary format to find fields for toString() method
        	ClassReader creader = new ClassReader(classfileBuffer);
        	ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        	
            ClassVisitor visitor = new ClassAdapter(writer) {
            	
            	public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
            		//delegate
            		MethodVisitor mv = cv.visitMethod(access, methodName, desc, signature, exceptions);
            		if(className.equals("java/lang/Class") && methodName.equals("forName")) {
            			mv = new ClassForNameAdapter(mv);            			
            		} else if(className.equals("java/lang/Class") && methodName.equals("newInstance0")) {
            			mv = new ClassNewInstanceAdapter(mv);            			
            		} else if(className.equals("java/lang/reflect/Method") && methodName.equals("invoke")) {
            			mv = new MethodInvokeAdapter(mv);            			
            		} else if(className.equals("java/lang/reflect/Constructor") && methodName.equals("newInstance")) {
            			mv = new ConstructorNewInstanceAdapter(mv);            			
            		}

            		return mv;
            	};
            	
            };
            creader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return writer.toByteArray();
        } catch (IllegalStateException e) {
            throw new IllegalClassFormatException("Error: " + e.getMessage() +
                " on class " + className);
		}
	}
	
	static class ClassForNameAdapter extends MethodAdapter {

		public ClassForNameAdapter(MethodVisitor mv) {
			super(mv);
		}
		
		@Override
		public void visitInsn(int opcode) {
			if(opcode==Opcodes.ARETURN) {
    			//load first argument on stack, i.e. the name of the class to be loaded
    			mv.visitVarInsn(ALOAD, 0);
    			//call logging method with that Class object as argument
				mv.visitMethodInsn(INVOKESTATIC, "REFL_LOGGER", "classForName", "(Ljava/lang/String;)V");
			}
			super.visitInsn(opcode);
		}
		
	}

	static class ClassNewInstanceAdapter extends MethodAdapter {

		public ClassNewInstanceAdapter(MethodVisitor mv) {
			super(mv);
		}
		
		@Override
		public void visitInsn(int opcode) {
			if(opcode==Opcodes.ARETURN) {
    			//load "this" on stack, i.e. the Class object
    			mv.visitVarInsn(ALOAD, 0);
    			//call logging method with that Class object as argument
				mv.visitMethodInsn(INVOKESTATIC, REFL_LOGGER, "classNewInstance", "(Ljava/lang/Class;)V");
			}
			super.visitInsn(opcode);
		}
		
	}

	static class MethodInvokeAdapter extends MethodAdapter {

		public MethodInvokeAdapter(MethodVisitor mv) {
			super(mv);
		}
		
		@Override
		public void visitInsn(int opcode) {
			if(opcode==Opcodes.ARETURN) {
    			//load first parameter on the stack, i.e. the designated receiver object
    			mv.visitVarInsn(ALOAD, 1);
    			//load "this" on stack, i.e. the Method object
    			mv.visitVarInsn(ALOAD, 0);
    			//call logging method with that Method object as argument
				mv.visitMethodInsn(INVOKESTATIC, REFL_LOGGER, "methodInvoke", "(Ljava/lang/Object;Ljava/lang/reflect/Method;)V");
			}
			super.visitInsn(opcode);
		}
		
	}
	
	static class ConstructorNewInstanceAdapter extends MethodAdapter {

		public ConstructorNewInstanceAdapter(MethodVisitor mv) {
			super(mv);
		}
		
		@Override
		public void visitInsn(int opcode) {
			if(opcode==Opcodes.ARETURN) {
    			//load "this" on stack, i.e. the Constructor object
    			mv.visitVarInsn(ALOAD, 0);
    			//call logging method with that Method object as argument
				mv.visitMethodInsn(INVOKESTATIC, REFL_LOGGER, "constructorNewInstance", "(Ljava/lang/reflect/Constructor;)V");
			}
			super.visitInsn(opcode);
		}
		
	}
}
