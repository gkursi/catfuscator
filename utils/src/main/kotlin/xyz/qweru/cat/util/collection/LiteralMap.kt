package xyz.qweru.cat.util.collection

class LiteralMap<T> : Map<T, T> {
    override val size: Int
        get() = throw NotImplementedError()
    override val keys: Set<T>
        get() = throw NotImplementedError()
    override val values: Collection<T>
        get() = throw NotImplementedError()
    override val entries: Set<Map.Entry<T, T>>
        get() = throw NotImplementedError()

    override fun isEmpty(): Boolean = false

    override fun containsKey(key: T): Boolean = true

    override fun containsValue(value: T): Boolean = true

    override fun get(key: T): T = key
}