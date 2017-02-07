package u

import java.util.HashMap

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

fun<K, V> HashMap<K, V>.addOr(key: K, value: V, f: (V) -> Unit) {
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

fun<K, V> buildMap(action: HashMap<K, V>.() -> Unit): Map<K, V> =
	HashMap<K, V>().apply(action)

fun <A, K, V> mapFrom(inputs: Collection<A>, getPair: (A) -> Pair<K, V>): Map<K, V> =
	mapFrom(inputs) { _, input -> getPair(input) }

fun<A, K, V> mapFrom(inputs: Collection<A>, getPair: (Int, A) -> Pair<K, V>): Map<K, V> =
	HashMap<K, V>(inputs.size).apply {
		for ((index, element) in inputs.withIndex()) {
			val (k, v) = getPair(index, element)
			val alreadyPresent = tryAdd(k, v)
			if (alreadyPresent != null)
				throw Exception("Key already in map: $k")
		}
	}

fun<K, V> mapFromKeys(keys: Collection<K>, getValue: (Int, K) -> V) =
	mapFrom(keys) { i, key ->
		key to getValue(i, key)
	}

fun<K, V> mapFromValues(values: Collection<V>, getKey: (V) -> K): Map<K, V> =
	mapFrom(values) { _, value ->
		getKey(value) to value
	}

fun<V> Map<*, V>.valuesIter(): Iterable<V> =
	map { it.value }

fun<K, V> Map<K, V>.reverse(): Map<V, K> =
	buildMap {
		for ((key, value) in this@reverse) {
			add(value, key)
		}
	}
