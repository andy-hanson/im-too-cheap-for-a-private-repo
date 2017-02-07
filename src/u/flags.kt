package u

infix fun Int.hasFlag(flag: Int): Boolean =
	(this and flag) != 0
