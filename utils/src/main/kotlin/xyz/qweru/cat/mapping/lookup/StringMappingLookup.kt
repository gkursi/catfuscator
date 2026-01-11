package xyz.qweru.cat.mapping.lookup

import java.util.concurrent.ConcurrentHashMap

open class StringMappingLookup : MappingLookup {
    val classes by lazy { ConcurrentHashMap<String, String>() }

    override fun get(string: String) = classes[string]

    override fun put(original: String, new: String) {
        classes[original] = new
    }
}