package u

inline fun<T> buildStringFromChars(f: (Action<Char>) -> T): Pair<String, T> {
	val buffer = StringBuilder()
	//TODO: f(buffer::append)
	val returned = f { buffer.append(it) }
	return Pair(buffer.toString(), returned)
}
