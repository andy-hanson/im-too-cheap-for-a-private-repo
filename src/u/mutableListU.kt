package u

fun<T> MutableList<T>.pop(): T =
	removeAt(size - 1)
