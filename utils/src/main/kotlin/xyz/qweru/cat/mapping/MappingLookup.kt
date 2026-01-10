package xyz.qweru.cat.mapping

import java.util.concurrent.ConcurrentHashMap

class MappingLookup(private val mapping: String? = null) {
    val values by lazy { ConcurrentHashMap<String, MappingLookup>() }

    fun getLookup(string: String) = values[string]
    fun getMapping(string: String) = values[string]?.mapping

    fun put(original: String, new: String) {
        if (values.contains(original)) throw IllegalStateException("Duplicate element: $original (mapped to $new)")
        values[original] = MappingLookup(new)
    }
}