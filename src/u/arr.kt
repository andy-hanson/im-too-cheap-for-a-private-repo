package u

import java.util.Arrays

/**
Immutable array.
Do *not* call the constructor!
 */
class Arr<out T> constructor(private val data: Array<out T>) : Collection<T> {
	companion object {
		inline fun<reified T> from(c: Collection<T>) = Arr(c.toTypedArray())
		inline fun<reified T> of(vararg elements: T) = Arr(elements)

		inline fun<reified T> empty(): Arr<T> = Arr(arrayOf())

		inline fun<reified T> init(size: Int, noinline initialize: (Int) -> T) =
			Arr(Array<T>(size, initialize))

		inline fun<reified T, reified U> fromMapped(c: Array<T>, crossinline map: (T) -> U): Arr<U> =
			init(c.size) { i -> map(c[i]) }
		inline fun<T, reified U> fromMapped(c: List<T>, crossinline map: (T) -> U): Arr<U> =
			init(c.size) { i -> map(c[i]) }

		inline fun<reified T, reified U> fromMappedArray(a: Array<out T>, crossinline map: (T) -> U): Arr<U> =
			fromMapped(a.asList(), map)

		inline fun<reified T> tail(l: MutableList<T>): Arr<T> =
			init(l.size - 1) { i ->
				l[i + 1]
			}

		inline fun<reified T> nil(): Arr<T> = empty()

		inline fun<reified T> cons(first: T, arr: Arr<T>): Arr<T> =
			Arr.init(arr.size + 1) { i ->
				if (i == 0)
					first
				else
					arr[i - 1]
			}

		inline fun<reified T> rcons(arr: Arr<T>, last: T): Arr<T> =
			Arr.init(arr.size + 1) { i ->
				if (i < arr.size)
					arr[i]
				else
					last
			}
	}

	operator fun get(idx: Int): T =
		data[idx]

	override fun toString() =
		"[${joinToString(" ")}]"

	override fun equals(other: Any?): Boolean {
		if (!(other is Arr<*>))
			return false
		if (other.size != size)
			return false
		for (i in indices) {
			if (this[i] != other[i])
				return false
		}
		return true
	}

	override fun hashCode(): Int =
		Arrays.hashCode(data)

	override val size: Int
		get() = data.size

	override fun isEmpty() = size == 0

	override fun iterator() = data.iterator()

	val indices: Iterable<Int>
		get() = 0..size -  1

	val first: T
		get() = this[0]

	val last: T
		get() = this[size - 1]

	inline fun<reified U> map(crossinline f: (T) -> U): Arr<U> =
		Arr.init<U>(size) { i ->
			f(this[i])
		}

	inline fun<reified U> mapToArray(crossinline f: (T) -> U): Array<U> =
		Array<U>(size) { i ->
			f(this[i])
		}

	fun<U> sameSize(other: Arr<U>): Boolean =
		size == other.size

	inline fun<reified U, reified Res> zip(other: Arr<U>, crossinline f: (T, U) -> Res): Arr<Res> {
		require(sameSize(other))
		return Arr.init(size) { i ->
			f(this[i], other[i])
		}
	}

	inline fun <reified U, reified Res> partialZip(other: Arr<U>, crossinline f: (T, U) -> Res): Arr<Res> {
		val nRemaining = size - other.size
		require(nRemaining >= 0)
		return Arr.init(other.size) { i ->
			f(this[nRemaining + i], other[i])
		}
	}

	override operator fun contains(element: @UnsafeVariance T) =
		this.some { it === element }

	override fun containsAll(elements: Collection<@UnsafeVariance T>) =
		TODO()
}

inline fun<T> Iterable<T>.some(predicate: (T) -> Boolean): Boolean {
	for (element in this)
		if (predicate(element))
			return true
	return false
}

inline fun<reified T> Arr<T>.concat(other: Arr<T>) =
	Arr.init(size + other.size) { i ->
		if (i < size)
			this[i]
		else
			other[i - size]
	}

inline fun<reified T> Arr<T>.slice(start: Int, end: Int): Arr<T> {
	require(start >= 0)
	require(start <= end)
	require(end <= size)
	return Arr.init(end - start) { this[start + it] }
}

inline fun<reified T> Arr<T>.rtail(): Arr<T> =
	rtailN(1)

inline fun <reified T> Arr<T>.rtailN(n: Int): Arr<T> =
	Arr.init(size - n) { this[it] }

inline fun<reified T> build(action: ArrayBuilder<T>.() -> Unit): Arr<T> {
	val l = mutableListOf<T>()
	ArrayBuilder(l).action()
	return Arr.from(l)
}

class ArrayBuilder<T>(private val l: MutableList<T>) {
	fun add(t: T) {
		l.add(t)
	}

	fun addMany(many: Iterable<T>) {
		for (element in many)
			add(element)
	}
}

inline fun<reified T> ArrayBuilder<T>.buildUntilNullWorker(f: () -> T?): Unit {
	while (true) {
		val x = f()
		when (x) {
			null -> return
			else -> add(x)
		}
	}
}

inline fun<reified T> buildUntilNull(f: () -> T?): Arr<T> =
	build {
		buildUntilNullWorker(f)
	}

inline fun<reified T> buildUntilNullWithFirst(first: T, f: () -> T?): Arr<T> =
	build {
		add(first)
		buildUntilNullWorker(f)
	}
