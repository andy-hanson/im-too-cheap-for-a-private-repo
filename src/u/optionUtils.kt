package u

//TODO: stdlib helper for this?
inline infix fun<T, U> T?.opMap(f: (T) -> U): U? =
	if (this == null) null else f(this)

inline fun<T> opIf(condition: Boolean, makeSome: () -> T): T? =
	if (condition)
		makeSome()
	else
		null

