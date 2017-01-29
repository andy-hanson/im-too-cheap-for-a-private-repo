package u

infix fun Int.hasFlag(flag: Int): Bool =
	(this and flag) != 0
