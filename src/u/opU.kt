package u

//TODO: stdlib helper for this?
inline infix fun<T, U> T?.opMap(f: (T) -> U): U? =
	when (this) {
		null -> null
		else -> f(this)
	}

inline fun<T> opIf(condition: Bool, makeSome: Thunk<T>): T? =
	if (condition)
		makeSome()
	else
		null
