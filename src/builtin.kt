import u.*
import n.*
import java.lang.reflect.Modifier

private annotation class Name(val name: String)
private annotation class Hid()

object Builtins {
	@Name("Int")
	class NzInt(private val value: Int) {
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

	@Name("Float")
	class NzFloat(private val value: Double) {
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
	class NzString(private val value: String) {
		fun _add(other: NzString) =
			NzString(value + other.value)

		@Hid
		override fun toString() = value
	}
}

private fun toBuiltin(jClass: Class<*>): BuiltinClass {
	val name = jClass.getDeclaredAnnotation(Name::class.java).name.sym//jClass.annotations.asIterable().findInstance<Annotation, Name>()!!.name.sym
	val klass = BuiltinClass(name, jClass)

	fun cnvTy(jTy: Class<*>): Ty =
		if (jTy == jClass)
			klass
		else {
			TODO()
		}

	val methods = jClass.declaredMethods.mapNotNull { method ->
		val methodName = unescapeName(method.name)
		val mods = assertModifiers(method.modifiers)
		if (mods.isPrivate || method.getDeclaredAnnotation(Hid::class.java) != null)
			return@mapNotNull null

		val parameters = Arr.fromMapped<java.lang.reflect.Parameter, NzMethod.Parameter>(method.parameters) {
			NzMethod.Parameter(Loc.zero, cnvTy(it.type), it.name.sym)
		}
		BuiltinMethod(klass, Loc.zero, mods.isStatic, cnvTy(method.returnType), methodName, parameters)
	}
	klass.members = mapFromValues(methods, BuiltinMethod::name)
	return klass
}

inline fun<T, reified U : T> Iterable<T>.findInstance(): U? {
	for (value in this) {
		if (value is U)
			return value
	}
	return null
}

class Modifiers(val isStatic: Boolean, val isPrivate: Boolean)

fun assertModifiers(modifiers: Int): Modifiers {
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

internal object Builtin {
	val all: Map<Sym, BuiltinClass> =
		mapFrom(Builtins::class.java.classes.asList()) { jClass ->
			val b = toBuiltin(jClass)
			b.name to b
		}
}
