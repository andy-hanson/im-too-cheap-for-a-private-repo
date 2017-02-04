package u

import kotlin.reflect.KProperty

/**
A value that may be assigned to only once.
Getting the value will fail if it has never been assigned.
This is useful for implementing immutable data with late initialization.
Unlike `lateinit`, this is not mutable once it's been assigned.
 */
class Late<T>() {
	var value: T? = null

	operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
		return value ?: throw Exception("Property ${property.name} not initialized")
	}

	operator fun setValue(thisRef: Any?, property: KProperty<*>, v: T): Unit {
		if (value != null)
			throw Exception("Property ${property.name} already initialized to $value")
		value = v
	}
}

//INITIALIZE TO NULL
class SingleAssign<T>(var value: T) {
	operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
		return value
	}

	operator fun setValue(thisRef: Any?, property: KProperty<*>, v: T): Unit {
		if (value != null)
			throw Exception("Property ${property.name} already initialized to $value")
		value = v
	}
}
