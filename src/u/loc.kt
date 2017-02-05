package u

class Loc(val start: Pos, val end: Pos) : HasSexpr {
	companion object {
		fun singleChar(start: Pos) =
			Loc(start, start + 1)

		//Eventually get rid of this
		val zero = Loc(0, 0)
	}

	override fun toSexpr() =
		sexprTuple(Sexpr(start), Sexpr(end))
}

/** Character position in a source file. */
typealias Pos = Int

data class LineAndColumn(val line: Int, val column: Int) {
	override fun toString() =
		"$line:$column"
}

data class LineAndColumnLoc(val start: LineAndColumn, val end: LineAndColumn) {
	override fun toString() =
		"$start-$end"
}

class LineColumnGetter(text: String) {
	// Maps a line number to the position of the first character of that line.
	private val lineToPos = build<Int> {
		add(0)
		for (pos in 0..text.length - 1) {
			val ch = text[pos]
			if (ch == '\n') {
				add(pos + 1)
			}
		}
	}

	fun lineAndColumnAtLoc(loc: Loc): LineAndColumnLoc =
		LineAndColumnLoc(lineAndColumnAtPos(loc.start), lineAndColumnAtPos(loc.end))

	fun lineAtPos(pos: Pos): Int =
		lineAndColumnAtPos(pos).line

	fun lineAndColumnAtPos(pos: Pos): LineAndColumn {
		//binary search
		var lowLine = 0
		var highLine = lineToPos.size - 1

		//Invariant:
		//start of lowLineNumber comes before pos
		//end of line highLineNumber comes after pos
		while (lowLine <= highLine) {
			val middleLine = mid(lowLine, highLine)
			val middlePos = lineToPos[middleLine]

			if (middlePos == pos)
				return LineAndColumn(middleLine, 0)
			else if (pos < middlePos)
				highLine = middleLine - 1
			else // pos > middlePos
				lowLine = middleLine + 1
		}

		val line = lowLine - 1
		return LineAndColumn(line, pos - lineToPos[line])
	}
}

private fun mid(a: Int, b: Int) =
	(a + b) / 2
