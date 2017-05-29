import u.*
import n.*
import java.lang.reflect.*

private annotation class Name(val name: String)
annotation class Hid() //TODO:PRIVATE

object Builtins {
	@Name(NzInt.name)
	class NzInt(@JvmField val value: Int) {
		companion object {
			@Hid
			const val name = "Int"

			//TODO: find a way for @Hid to work with a getter...
			@Hid
			fun ty() = BuiltinClass(name.sym, NzInt::class.java)

			@JvmStatic fun parse(s: NzString): NzInt =
				NzInt(s.value.toInt())
		}

		fun _add(other: NzInt) =
			NzInt(Math.addExact(value, other.value))

		fun _sub(other: NzInt) =
			NzInt(Math.subtractExact(value, other.value))

		fun _mul(other: NzInt) =
			NzInt(Math.multiplyExact(value, other.value))

		fun _div(other: NzInt) =
			NzInt(value / other.value)

		@Hid
		override fun toString() = value.toString()
	}

	@Name(NzFloat.name)
	class NzFloat(@JvmField val value: Double) {
		companion object {
			@Hid
			const val name = "Float"

			@Hid
			fun ty() = BuiltinClass(name.sym, NzFloat::class.java)

			@JvmStatic fun parse(s: NzString): NzFloat =
				NzFloat(s.value.toDouble())
		}

		fun _add(other: NzFloat) =
			NzFloat(value + other.value)

		fun _sub(other: NzFloat) =
			NzFloat(value - other.value)

		fun _mul(other: NzFloat) =
			NzFloat(value * other.value)

		fun _div(other: NzFloat) =
			NzFloat(value / other.value)

		@Hid
		override fun toString() = value.toString()
	}

	@Name("String")
	class NzString(@JvmField val value: String) {
		companion object {
			@Hid
			const val name = "String"

			@Hid
			fun ty() = BuiltinClass(name.sym, NzString::class.java)
		}

		fun _add(other: NzString) =
			NzString(value + other.value)

		@Hid
		override fun toString() = value
	}
}

class Modifiers(val isStatic: Boolean, val isPrivate: Boolean)


internal object Builtin {
	val all: Map<Sym, BuiltinClass> = run {
		val jClassToBuiltinClass = mutableMapOf<Class<*>, BuiltinClass>()

		for (jClass in Builtins::class.java.classes) {
			jClassToBuiltinClass[jClass] = run {
				val name = jClass.getDeclaredAnnotation(Name::class.java).name.sym
				BuiltinClass(name, jClass)
			}
		}

		fun convertType(jTy: Class<*>): Ty =
			jClassToBuiltinClass[jTy] ?: throw Error("Builtin references non-builtin type $jTy")

		jClassToBuiltinClass.toMap { jClass, klass ->
			klass.members = jClass.declaredMethods.mapNotNull {
				method(klass, it, ::convertType)
			}.toMap { it.name to it }
			klass.name to klass
		}
	}
}

private fun method(klass: BuiltinClass, method: Method, convertType: (Class<*>) -> Ty): BuiltinMethod? {
	val methodName = unescapeName(method.name)
	val mods = assertModifiers(method.modifiers)
	if (mods.isPrivate || method.getDeclaredAnnotation(Hid::class.java) != null)
		return null

	val parameters = Arr.fromMapped<java.lang.reflect.Parameter, NzMethod.Parameter>(method.parameters) {
		NzMethod.Parameter(Loc.zero, convertType(it.type), it.name.sym)
	}

	return BuiltinMethod(klass, Loc.zero, mods.isStatic, convertType(method.returnType), methodName, parameters)
}

private fun assertModifiers(modifiers: Int): Modifiers {
	fun m(modifier: Int) = modifiers.hasFlag(modifier)

	forbid(m(Modifier.ABSTRACT))
	assert(m(Modifier.FINAL))
	forbid(m(Modifier.INTERFACE))
	forbid(m(Modifier.NATIVE))
	forbid(m(Modifier.PRIVATE))
	forbid(m(Modifier.PROTECTED))
	assert(m(Modifier.PUBLIC))
	forbid(m(Modifier.STRICT))
	forbid(m(Modifier.SYNCHRONIZED))
	forbid(m(Modifier.TRANSIENT))
	forbid(m(Modifier.VOLATILE))

	return Modifiers(m(Modifier.STATIC), m(Modifier.PRIVATE))
}
