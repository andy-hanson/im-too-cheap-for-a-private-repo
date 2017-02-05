package u

sealed class Sexpr : HasSexpr {
	companion object {
		operator fun invoke(x: Arr<HasSexpr>) =
			Compound.List(x.map { it.toSexpr() })

		operator fun invoke(n: Int) =
			N(n.toLong())
	}

	override fun toString(): String {
		val sb = StringBuilder()
		show(0, sb)
		return sb.toString()
	}

	abstract fun show(indent: Int, builder: StringBuilder): Unit

	class S(val content: String) : Sexpr() {
		companion object {
			operator fun invoke(content: Sym) =
				S(content.str)
		}

		override fun show(indent: Int, builder: StringBuilder) {
			builder.append(content)
		}
	}

	sealed class Compound(val content: Arr<Sexpr>) : Sexpr() {
		class NamedTuple(val name: String, content: Arr<Sexpr>) : Compound(content) {
			override fun show(indent: Int, builder: StringBuilder) {
				builder.append(name)
				builder.append('(')
				showParts(indent, builder)
				builder.append(')')
			}
		}

		class Tuple(content: Arr<Sexpr>): Compound(content) {
			override fun show(indent: Int, builder: StringBuilder) {
				builder.append('(')
				showParts(indent, builder)
				builder.append(')')
			}
		}

		class List(content: Arr<Sexpr>) : Compound(content) {
			override fun show(indent: Int, builder: StringBuilder) {
				builder.append('[')
				showParts(indent, builder)
				builder.append(']')
			}
		}

		protected fun showParts(indent: Int, builder: StringBuilder) {
			if (content.any { it is Compound }) {
				fun addIndent(amount: Int) {
					builder.append('\n')
					repeat(amount) {
						builder.append('\t')
					}
				}
				for (part in content) {
					addIndent(indent + 1)
					part.show(indent + 1, builder)
				}
				//addIndent(indent)
			} else {
				for ((index, part) in content.withIndex()) {
					part.show(indent, builder)
					if (index != content.size - 1)
						builder.append(' ')
				}
			}
		}
	}


	class N(val value: Long) : Sexpr() {
		override fun show(indent: Int, builder: StringBuilder) {
			builder.append(value)
		}
	}

	class F(val value: Double) : Sexpr() {
		override fun show(indent: Int, builder: StringBuilder) {
			builder.append(value)
		}
	}

	class Str(val value: String) : Sexpr() {
		override fun show(indent: Int, builder: StringBuilder) {
			builder.append("\"$value\"")
		}
	}

	override fun toSexpr() =
		this
}

interface HasSexpr {
	fun toSexpr(): Sexpr
}

fun sexpr(name: String, parts: Arr<HasSexpr>) =
	sexpr(name) {
		for (part in parts)
			s(part)
	}

fun sexpr(name: String, vararg parts: HasSexpr) =
	sexpr(name) {
		for (part in parts)
			s(part)
	}

fun sexprTuple(parts: Arr<HasSexpr>) =
	Sexpr.Compound.Tuple(parts.map { it.toSexpr() })

fun sexprTuple(vararg parts: HasSexpr) =
	Sexpr.Compound.Tuple(Arr.fromMappedArray(parts) { it.toSexpr() })

fun sexpr(parts: Arr<HasSexpr>) =
	Sexpr.Compound.List(parts.map { it.toSexpr() })

inline fun sexpr(name: String, f: SexprBuilder.() -> Unit): Sexpr {
	val builder = SexprBuilder(name)
	builder.f()
	return builder.finish()
}
inline fun sexpr(name: Sym, f: SexprBuilder.() -> Unit): Sexpr =
	sexpr(name.str, f)


class SexprBuilder(private val name: String) {
	val parts = mutableListOf<Sexpr>()

	fun s(sexpr: Sexpr) {
		parts.add(sexpr)
	}

	fun s(n: Long) {
		s(Sexpr.N(n))
	}

	fun s(name: Sym) {
		parts.add(Sexpr.S(name))
	}
	fun s(name: String) { s(name.sym) }

	fun s(obj: HasSexpr) {
		parts.add(obj.toSexpr())
	}

	fun s(arr: Arr<HasSexpr>) {
		parts.add(Sexpr.Compound.List(arr.map { it.toSexpr() }))
	}

	fun finish() =
		Sexpr.Compound.NamedTuple(name, Arr.from(parts))
}
