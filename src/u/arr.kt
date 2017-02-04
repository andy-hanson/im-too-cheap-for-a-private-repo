package u

import java.util.Arrays

/**
Immutable array.
Do *not* call the constructor!
 */
class Arr<out T> constructor(private val data: Array<out T>) : Iterable<T> {
	fun toArray(): Array<out T> = data

	companion object {
		inline fun<reified T> from(c: Collection<T>) = Arr(c.toTypedArray())
		inline fun<reified T> of(vararg elements: T) = Arr(elements)

		inline fun<reified T> empty(): Arr<T> = Arr(arrayOf())

		inline fun<reified T> init(size: Int, noinline initialize: (Int) -> T) =
			Arr(Array<T>(size, initialize))

		inline fun<reified T, reified U> fromMapped(c: List<T>, crossinline map: (T) -> U): Arr<U> =
			init(c.size) { i ->
				map(c[i])
			}

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

	override fun equals(other: Any?): Bool =
		other is Arr<*> && other.size == size && Arrays.equals(data, other.data)

	override fun hashCode(): Int =
		Arrays.hashCode(data)

	val size: Int
		get() = data.size

	val isEmpty: Bool
		get() = size == 0

	override fun iterator(): Iterator<T> =
		data.iterator()

	val first: T
		get() = this[0]

	val last: T
		get() = this[size - 1]

	inline fun<reified U> map(crossinline f: (T) -> U): Arr<U> =
		Arr.init<U>(size) { i ->
			f(this[i])
		}

	val indexes: Iterable<Int> =
		0..(size - 1)

	inline fun<reified U> allZip(other: Arr<U>, f: (T, U) -> Bool): Bool {
		for (i in indexes)
			if (!f(this[i], other[i]))
				return false
		return true
	}

	inline fun findIndex(f: Pred<T>): Int? {
		for (i in indexes)
			if (f(this[i]))
				return i
		return null
	}

	inline fun<U> findMap(f: (T) -> U?): U? {
		for (element in this) {
			val res = f(element)
			if (res != null)
				return res
		}
		return null
	}

	fun<U> sameSize(other: Arr<U>): Bool =
		size == other.size

	inline fun<reified U> eachZip(other: Arr<U>, f: (T, U) -> Unit) {
		require(sameSize(other))
		for (i in indexes)
			f(this[i], other[i])
	}

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

	fun single(): T {
		require(size == 1)
		return first
	}

	fun lazySlice(start: Int, end: Int): Iterator<T> {
		val arr = this
		return object: Iterator<T> {
			var i = start

			override fun hasNext() =
				i != end

			override fun next(): T =
				returning (arr[i]) {
					i++
					assert(i <= end) // Or else someone forgot to call hasNext()
				}
		}
	}

	fun lazyDrop(start: Int) =
		lazySlice(start, size)

	fun lazyTail() =
		lazyDrop(1)

}

inline fun<reified T> Arr<T>.mapIfAnyMaps(crossinline f: (T) -> T?): Arr<T>? {
	var didReplace = false
	val mapped = map { element ->
		val m = f(element)
		if (m != null)
			didReplace = true
		m ?: element
	}
	return opIf(!didReplace) { mapped }
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





//TODO: ArrBuild.kt
inline fun<reified T> build(f: ArrayBuilder<T>.() -> Unit): Arr<T> {
	val l = mutableListOf<T>()
	ArrayBuilder(l).f()
	return Arr.from(l)
}

inline fun<reified T, reified U> build2(f: (Action<T>, Action<U>) -> Unit): Pair<Arr<T>, Arr<U>> {
	val a = mutableListOf<T>()
	val b = mutableListOf<U>()
	f({ a.add(it) }, { b.add(it) })
	return Pair(Arr.from(a), Arr.from(b))
}

inline fun<reified T, U> buildFromFirstAndMapTail(first: T, arr: Arr<U>, map: (U) -> T): Arr<T> =
	build {
		add(first)
		for (element in arr.lazyTail())
			add(map(element))
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


inline fun<reified Element, Result> buildAndReturn(f: (Action<Element>) -> Result): Pair<Arr<Element>, Result> {
	val l = mutableListOf<Element>()
	val res = f { l.add(it) }
	return Pair(Arr.from(l), res)
}

inline fun<reified T> buildLoop(f: Thunk<Pair<T, Bool>>): Arr<T> =
	build<T> {
		while (true) {
			val (element, shouldContinue) = f()
			add(element)
			if (!shouldContinue)
				break
		}
	}

sealed class Builder<Element, Result> {
	class Continue<Element, Result>(val element: Element) : Builder<Element, Result>()
	class Done<Element, Result>(val result: Result) : Builder<Element, Result>()
}

inline fun<reified Element, Result> buildLoop0WithFirst(first: Element, f: Thunk<Builder<Element, Result>>): Pair<Arr<Element>, Result> =
	buildAndReturn { add ->
		add(first)
		var res: Result
		loop@ while (true) {
			val x = f()
			when (x) {
				is Builder.Continue -> {
					add(x.element)
				}
				is Builder.Done -> {
					res = x.result
					break@loop
				}
			}
		}
		res
	}

inline fun<reified T> ArrayBuilder<T>.buildUntilNullWorker(f: Thunk<T?>): Unit {
	while (true) {
		val x = f()
		when (x) {
			null -> return
			else -> add(x)
		}
	}
}

inline fun<reified T> buildUntilNull(f: Thunk<T?>): Arr<T> =
	build {
		buildUntilNullWorker(f)
	}

inline fun<reified T> buildUntilNullWithFirst(first: T, f: Thunk<T?>): Arr<T> =
	build {
		add(first)
		buildUntilNullWorker(f)
	}
