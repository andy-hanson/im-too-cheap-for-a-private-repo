package u

import java.util.concurrent.ConcurrentHashMap

class Sym private constructor(val str: String) : HasSexpr {
	companion object {
		private val table = ConcurrentHashMap<String, Sym>()

		fun ofString(s: String): Sym {
			val entry = table[s]
			if (entry != null)
				return entry

			val sym = Sym(s)
			// In case another thread added the same symbol, use putIfAbsent.
			// This returns null when the put succeeds, so return `sym` in that case.
			return table.putIfAbsent(s, sym) ?: sym
		}
	}

	override fun toString() =
		str

	override fun toSexpr() =
		Sexpr.S(this)

	fun mod(f: (String) -> String) =
		ofString(f(str))
}

val String.sym: Sym
	get() = Sym.ofString(this)
