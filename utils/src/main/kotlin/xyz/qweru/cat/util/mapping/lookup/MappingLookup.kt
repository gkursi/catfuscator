package xyz.qweru.cat.util.mapping.lookup

interface MappingLookup {
    fun get(string: String): String?
    fun put(original: String, new: String)
}