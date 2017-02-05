package u

import java.util.HashMap

//TODO: move to its own module
object Hm {
	fun<K, V> createWithSize(size: Int) =
		HashMap<K, V>(size)

	fun<K, V> buildWithSize(size: Int, builder: Action<(K, V) -> V?>): HashMap<K, V> =
		returning (createWithSize(size)) { map ->
			//TODO:revokable
			val tryAdd = { k: K, v: V -> map.tryAdd(k, v) } //TODO:map::tryAdd
			builder(tryAdd)
			//TODO:revoke
		}

	fun<A, K, V> buildFrom(inputs: Collection<A>, getPair: (Int, A) -> Pair<K, V>): HashMap<K, V> =
		buildWithSize(inputs.size) { add ->
			for ((index, element) in inputs.withIndex()) {
				val (k, v) = getPair(index, element)
				val alreadyPresent = add(k, v)
				if (alreadyPresent != null)
					throw Exception("Key already in map: $k")
			}
		}

	fun<K, V> buildFromKeys(keys: Collection<K>, getValue: (Int, K) -> V): HashMap<K, V> =
		buildFrom(keys) { i, key ->
			key to getValue(i, key)
		}

	fun<K, V> buildFromValues(values: Collection<V>, getKey: (V) -> K): HashMap<K, V> =
		buildFrom(values) { _, value ->
			getKey(value) to value
		}
}

fun<K, V> HashMap<K, V>.addOrFail(key: K, value: V, fail: () -> Error) {
	if (key in this)
		throw fail()
	this[key] = value
}

fun<K, V> HashMap<K, V>.add(key: K, value: V) {
	if (key in this) {
		throw Error("Already have key $key. Current keys: ${this.keys}")
	}
	this[key] = value
}

fun<K, V> HashMap<K, V>.addOr(key: K, value: V, f: Action<V>) {
	val old = tryAdd(key, value)
	if (old != null)
		f(old)
}

fun<K, V> HashMap<K, V>.tryAdd(key: K, value: V): V? =
	returning(this[key]) { alreadyPresent ->
		if (alreadyPresent == null)
			this[key] = value
	}

fun<K, V> HashMap<K, V>.mustRemove(key: K) {
	require(key in this)
	remove(key)
}


/** Immutable hash map. */
class Lookup<K, out V> private constructor(private val data: HashMap<K, V>) : Iterable<Pair<K, V>> {
	companion object {
		fun<K, V> of(vararg pairs: Pair<K, V>) =
			Lookup(hashMapOf(*pairs))

		fun<K, V> empty(): Lookup<K, V> =
			Lookup(HashMap())

		fun<K, V> beeld(action: HashMap<K, V>.() -> Unit): Lookup<K, V> {
			val l = HashMap<K, V>()
			l.action()
			return fromHashMap(l)
		}

		fun<K, V> buildWithSize(size: Int, builder: Action<(K, V) -> V?>) =
			Lookup(Hm.buildWithSize(size, builder))

		fun<K, V> build(builder: Action<(K, V) -> V?>) =
			buildWithSize(0, builder)

		fun<K1, V1, K2, V2> build2(builder: ((K1, V1) -> V1?, (K2, V2) -> V2?) -> Unit): Pair<Lookup<K1, V1>, Lookup<K2, V2>> {
			var b: Lookup<K2, V2>? = null
			val a = build<K1, V1> { tryAdd1 ->
				b = build { tryAdd2 ->
					builder(tryAdd1, tryAdd2)
				}
			}
			return Pair(a, b!!)
		}

		fun<K, V> fromValues(values: Collection<V>, getKey: (V) -> K) =
			Lookup(Hm.buildFromValues(values, getKey))

		fun<K, V> fromKeys(keys: Collection<K>, getValue: (Int, K) -> V) =
			Lookup(Hm.buildFromKeys(keys, getValue))

		fun<A, K, V> buildFrom(inputs: Collection<A>, getPair: (A) -> Pair<K, V>): Lookup<K, V> =
			buildFromWithIndex(inputs) { _, input -> getPair(input) }

		fun<A, K, V> buildFromWithIndex(inputs: Collection<A>, getPair: (Int, A) -> Pair<K, V>): Lookup<K, V> =
			Lookup(Hm.buildFrom(inputs, getPair))

		fun<K, V> buildFromKeysWithIndex(keys: Arr<K>, getValue: (Int, K) -> V): Lookup<K, V> =
			buildFromWithIndex(keys) { i, key -> key to getValue(i, key) }

		fun<K, V> ofKeysAndValues(keys: Arr<K>, values: Arr<V>): Lookup<K, V> {
			require(keys.sameSize(values))
			return buildFromWithIndex(keys) { i, key -> key to values[i] }
		}

		fun<K, V> fromHashMap(hm: HashMap<K, V>): Lookup<K, V> =
			fromHashMapMapped(hm) { k, v -> Pair(k, v) }

		fun<K1, V1, K2, V2> fromHashMapMapped(hm: HashMap<K1, V1>, map: (K1, V1) -> Pair<K2, V2>): Lookup<K2, V2> =
			buildWithSize(hm.size) { tryAdd ->
				for ((k, v) in hm) {
					val (mappedK, mappedV) = map(k, v)
					val old = tryAdd(mappedK, mappedV)
					assert(old == null)
				}
			}
	}

	operator fun get(key: K): V? =
		data[key]

	operator fun contains(key: K): Bool =
		key in data

	fun keys(): Iterable<K> =
		data.map { it.key }

	fun values(): Iterable<V> =
		data.map { it.value }

	override fun iterator(): Iterator<Pair<K, V>> =
		data.iterator().map { Pair(it.key, it.value) }
}


fun<K, V> Map<K, V>.reverse(): Lookup<@UnsafeVariance V, K> =
	Lookup.beeld<V, K> {
		for ((key, value) in this@reverse) {
			add(value, key)
		}
	}

fun<T, U> Iterator<T>.map(f: (T) -> U): Iterator<U> {
	val iter = this
	return object : Iterator<U> {
		override fun hasNext() =
			iter.hasNext()
		override fun next(): U {
			val a = iter.next()
			return f(a)
		}
	}
}
