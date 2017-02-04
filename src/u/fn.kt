package u

inline fun<T> returning(value: T, fn: (T) -> Unit): T {
	fn(value)
	return value
}

fun<T> never(): Nothing =
	throw Error("This should never happen")

fun<T, U, V> ((T, U) -> V).partial(t: T): (U) -> V =
	{ u -> this(t, u) }
