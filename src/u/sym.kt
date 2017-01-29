package u

import java.util.WeakHashMap

// Maps from a string to itself
private val table = WeakHashMap<String, Sym>()

class Sym private constructor(val str: String) : HasSexpr {
	companion object {
		fun symOfString(s: String): Sym {
			val entry = table[s]
			if (entry == null) {
				val sym = Sym(s)
				table[s] = sym
				return sym
			} else
				return entry
		}
	}

	override fun toString() =
		str

	override fun toSexpr() =
		Sexpr.S(this)

	fun mod(f: (String) -> String) =
		Sym(f(str))
}

val String.sym: Sym
	get() = Sym.symOfString(this)
