package u

typealias Bool = Boolean
typealias Pred<T> = (T) -> Bool
typealias Action<T> = (T) -> Unit
typealias Thunk<T> = () -> T
