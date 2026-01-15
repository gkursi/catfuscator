package xyz.qweru.cat.mapping.lookup

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * todo: clean up whatever the fuck is happening in this package
 */
class JarMappingLookup : MappingLookup {
    val classes by lazy { ConcurrentHashMap<String, ClassMappingLookup>() }

    override fun get(string: String) = classes[string]?.name

    fun getLookup(string: String) = classes[string]

    fun getOrCreateLookup(string: String): ClassMappingLookup {
        if (!classes.containsKey(string)) {
            classes[string] = ClassMappingLookup(string)
        }

        return classes[string]!!
    }

    override fun put(original: String, new: String) {
        if (!classes.containsKey(original)) {
            classes[original] = ClassMappingLookup(original)
        }
        classes[original]!!.name = new
    }

}