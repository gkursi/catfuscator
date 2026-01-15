package xyz.qweru.cat.mapping.lookup

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class MethodMappingLookup : MappingLookup {
    val methodNames by lazy { ConcurrentHashMap<String, NamedStringMappingLookup>() }

    override fun get(string: String) = methodNames[string]?.name

    fun getLookup(string: String) = methodNames[string]

    fun getOrCreateLookup(string: String): NamedStringMappingLookup {
        if (!methodNames.containsKey(string)) {
            methodNames[string] = NamedStringMappingLookup(string)
        }
        return methodNames[string]!!
    }

    override fun put(original: String, new: String) {
        if (!methodNames.containsKey(original)) {
            methodNames[original] = NamedStringMappingLookup(new)
        } else {
            methodNames[original]!!.name = new
        }
    }
}