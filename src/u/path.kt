package u

class Path(private val parts: Arr<Sym>) : HasSexpr {
	companion object {
		val empty = Path(Arr.empty())
		fun resolveWithRoot(root: Path, path: Path): Path =
			root.resolve(RelPath(0, path))

		fun from(vararg elements: String): Path =
			Path(Arr.fromMappedArray<String, Sym>(elements) { it.sym }) //TODO: Sym::ofString
	}

	override fun toSexpr() =
		Sexpr(parts)

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

	val isEmpty: Bool
		get() = parts.isEmpty

	fun directory(): Path =
		Path(parts.rtail())

	fun directoryAndBasename(): Pair<Path, Sym> =
		Pair(directory(), last)

	override fun equals(other: Any?) =
		other is Path && parts.equals(other.parts)

	override fun hashCode() =
		parts.hashCode()
}

// # of parents, then a path relative to the ancestor
data class RelPath(val nParents: Int, val relToParent: Path) : HasSexpr {
	override fun toString(): String {
		val start =
			when (nParents) {
				0 -> "/"
				1 -> "./"
				else -> "../".repeat(nParents - 1)
			}
		return start + relToParent.toString()
	}

	override fun toSexpr() = sexprTuple(Sexpr(nParents), relToParent)

	val isParentsOnly: Bool
		get() = relToParent.isEmpty
}
