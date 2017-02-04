package u

class Loc(val start: Pos, val end: Pos) : HasSexpr {
	companion object {
		fun singleChar(start: Pos) =
			Loc(start, start + 1)

		val zero = Loc(0, 0)
		val nil = Loc(-1, -1)
	}

	override fun toSexpr() =
		sexprTuple(Sexpr(start), Sexpr(end))
}

typealias Pos = Int

fun Pos.incr(): Pos =
	this + 1

class LcPos(val line: Int, val column: Int) {
	override fun toString(): String =
		"$line:$column"
}
class LcLoc(val start: LcPos, val end: LcPos) {
	companion object {
		fun from(source: String, loc: Loc): LcLoc {
			val start = walkTo(source, startLc, loc.start)
			val end = walkTo(source, start, loc.end- loc.start)
			return LcLoc(start, end)
		}
	}

	override fun toString(): String =
		"$start-$end"
}

private fun walkTo(source: String, startPos: LcPos, distanceToWalk: Int): LcPos {
	var line = startPos.line
	var column = startPos.column
	repeat(distanceToWalk) { i ->
		when (source[i]) {
			'\n' -> {
				line++
				column = 1
			}
			else ->
				column++
		}
	}
	return LcPos(line, column)
}

private val startLc = LcPos(1, 1)
