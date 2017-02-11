package u

fun never(): Nothing =
	throw Error("This should never happen")

fun<T, U, V> ((T, U) -> V).partial(t: T): (U) -> V =
	{ u -> this(t, u) }
