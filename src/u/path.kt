package u

class Path(private val parts: Arr<Sym>) : HasSexpr {
	companion object {
		val empty = Path(Arr.empty())

		fun resolveWithRoot(root: Path, path: Path): Path =
			root.resolve(RelPath(0, path))

		fun from(vararg elements: String): Path =
			Path(Arr.fromMappedArray(elements, String::sym))
	}

	override fun toSexpr() =
		Sexpr.S(toString().sym)

	override fun toString() =
		parts.joinToString("/")

	fun resolve(rel: RelPath): Path {
		val (nParents, relToParent) = rel
		val nPartsToKeep = parts.size - nParents
		if (nPartsToKeep < 0)
			throw Exception("Can't resolve: $rel\nRelative to: $this")
		val parent = parts.slice(0, nPartsToKeep)
		return Path(parent.concat(relToParent.parts))
	}

	fun add(next: Sym): Path =
		Path(Arr.rcons(parts, next))

	fun parent(): Path =
		Path(parts.rtail())

	val last: Sym
		get() = parts.last

	fun addExtension(extension: String): Path =
		parent().add(last.mod { it + extension })

	val isEmpty: Boolean
		get() = parts.isEmpty()

	fun directory(): Path =
		Path(parts.rtail())

	fun directoryAndBasename(): Pair<Path, Sym> =
		Pair(directory(), last)

	override fun equals(other: Any?) =
		other is Path && parts.equals(other.parts)

	override fun hashCode() =
		4//parts.hashCode()
}

// # of parents, then a path relative to the ancestor
data class RelPath(val nParents: Int, val relToParent: Path) : HasSexpr {
	init {
		assert(nParents > 0)
	}

	override fun toSexpr() =
		Sexpr.S(toString().sym)

	override fun toString(): String {
		val start =
			when (nParents) {
				0 -> "/"
				1 -> "./"
				else -> "../".repeat(nParents - 1)
			}
		return start + relToParent.toString()
	}

	val isParentsOnly: Boolean
		get() = relToParent.isEmpty

	val last: Sym
		get() = relToParent.last
}
