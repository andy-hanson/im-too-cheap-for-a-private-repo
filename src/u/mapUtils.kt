package u

import java.util.HashMap

fun<K, V> HashMap<K, V>.add(key: K, value: V) {
	if (key in this)
		throw Error("Already have key $key. Current keys: ${this.keys}")
	this[key] = value
}

fun<K, V : Any> HashMap<K, V>.addOr(key: K, value: V, f: (V) -> Unit) {
	val old = this[key]
	if (old == null)
		this[key] = value
	else
		f(old)
}

fun<K, V> HashMap<K, V>.mustRemove(key: K) {
	require(key in this)
	remove(key)
}

fun<K0, V0, K1, V1> Map<K0, V0>.toMap(getPair: (K0, V0) -> Pair<K1, V1>): Map<K1, V1> =
	HashMap<K1, V1>().also {
		for ((key, value) in this) {
			val (newKey, newValue) = getPair(key, value)
			it.add(newKey, newValue)
		}
	}

fun<A, K, V> Iterable<A>.toMap(getPair: (A) -> Pair<K, V>): Map<K, V> =
	HashMap<K, V>().also { it.fill(this, getPair) }

fun<A, K, V> Collection<A>.toMap(getPair: (A) -> Pair<K, V>): Map<K, V> =
	HashMap<K, V>(size).also { it.fill(this, getPair) }

private fun<A, K, V> HashMap<K, V>.fill(iter: Iterable<A>, getPair: (A) -> Pair<K, V>) {
	for (element in iter) {
		val (key, value) = getPair(element)
		add(key, value)
	}
}

fun<V> Map<*, V>.valuesIter(): Iterable<V> =
	map { it.value }

fun<K, V> Map<K, V>.reverse(): Map<V, K> =
	HashMap<V, K>().apply {
		for ((key, value) in this@reverse) {
			add(value, key)
		}
	}
