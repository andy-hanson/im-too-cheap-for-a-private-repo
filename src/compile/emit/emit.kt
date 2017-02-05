package compile.emit

import n.*
import u.*
import org.objectweb.asm.*

//MOVE


private val JAVA_VERSION = Opcodes.V1_8
private val OBJECT = "java/lang/Object"
private val PUBLIC_FINAL = Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL

fun classToBytecode(klass: Klass, lineColumnGetter: LineColumnGetter): ByteArray =
	ClassWriter(ClassWriter.COMPUTE_FRAMES).run {
		// Concrete class
		visit(JAVA_VERSION, PUBLIC_FINAL, klass.javaTypeName, /*signature*/null, /*superClass*/OBJECT, /*superInterfaces*/emptyArray())

		val slots = (klass.head as Klass.Head.Slots).slots
		for (slot in slots) {
			visitField(Opcodes.ACC_PUBLIC, slot.javaName, slot.ty.javaTypeDescriptor, /*signature*/null, /*value*/null)
		}

		for (method in klass.methods) {
			writeMethod(method, lineColumnGetter)
		}

		visitEnd()
		toByteArray()
	}

private fun ClassWriter.writeMethod(method: MethodWithBody, lineColumnGetter: LineColumnGetter) {
	val descriptor = method.descriptor()
	val opcode = PUBLIC_FINAL + (if (method.isStatic) Opcodes.ACC_STATIC else 0)
	val mv = visitMethod(opcode, method.javaName, descriptor, /*signature*/null, /*exceptions*/emptyArray())
	mv.visitCode();
	CodeWriter(method, mv, lineColumnGetter).writeBody(method.body)
	mv.visitMaxs(0, 0)
	mv.visitEnd()
}

private fun NzMethod.descriptor() =
	Type.getMethodDescriptor(
		Type.getType(returnTy.javaTypeDescriptor),
		*parameters.mapToArray { Type.getType(it.ty.javaTypeDescriptor) })

private class CodeWriter(method: NzMethod, private var mv: MethodVisitor, private val lineColumnGetter: LineColumnGetter) {
	//private var stackDepth = 0
	// Param 0 is 'this'
	private var minDepth = if (method.isStatic) 0 else 1
	private var paramDepths = Lookup.fromKeys(method.parameters) { i, _ -> minDepth + i }
	// For N parameters, last parameter is at index N
	private var nextLocalDepth = minDepth + method.parameters.size
	private var localDepths = HashMap<Pattern.Single, Int>()

	private fun labelHere(loc: Loc) {
		val label = Label()
		mv.visitLineNumber(lineColumnGetter.lineAtPos(loc.start), label)
	}

	fun writeBody(body: Expr) {
		labelHere(body.loc)
		writeExpr(body)
		mv.visitInsn(Opcodes.ARETURN)
	}

	private fun writeExpr(expr: Expr) {
		return when (expr) {
			is Access.Local -> {
				val local = expr.local
				mv.visitVarInsn(Opcodes.ALOAD, localDepths[local]!!)
			}

			is Access.Parameter -> {
				val param = expr.param
				mv.visitVarInsn(Opcodes.ALOAD, paramDepths[param]!!)
			}

			is MethodCall -> {
				val (_, target, method, args) = expr
				writeExpr(target)
				for (arg in args)
					writeExpr(arg)
				invokeVirtual(method.klass.javaTypeName, method.javaName, method.descriptor())
			}

			is GetSlot -> {
				val (_, target, slot) = expr
				writeExpr(target)
				getField(target.ty.javaTypeName, slot.javaName, slot.ty.javaTypeDescriptor)
				TODO("Use loc")
			}

			is Let -> {
				val (loc, assigned, value, then) = expr
				writeExpr(value)
				addPattern(assigned)
				labelHere(loc)
				writeExpr(then)
			}

			is Seq -> {
				val (loc, action, then) = expr
				writeExpr(action)
				labelHere(loc)
				writeExpr(then)
			}
		}
	}

	private fun addPattern(pattern: Pattern) {
		when (pattern) {
			is Pattern.Single -> {
				localDepths[pattern] = nextLocalDepth
				nextLocalDepth += 1
			}
			else -> TODO()
		}
	}

	private fun getField(typeName: String, fieldName: String, fieldType: String) {
		mv.visitFieldInsn(Opcodes.GETFIELD, typeName, fieldName, fieldType)
	}

	private fun putField(typeName: String, fieldName: String, fieldType: String) {
		mv.visitFieldInsn(Opcodes.PUTFIELD, typeName, fieldName, fieldType)
	}

	private fun invokeVirtual(invokedType: String, methodName: String, descriptor: String) {
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, invokedType, methodName, descriptor, /*isInterface*/false)
	}

	private fun ldc(value: Any) {
		mv.visitLdcInsn(value)
	}

	private fun aload0() {
		mv.visitVarInsn(Opcodes.ALOAD, 0)
	}

	private fun dup() {
		mv.visitInsn(Opcodes.DUP)
	}

	private fun pop() {
		mv.visitInsn(Opcodes.POP)
	}
}
