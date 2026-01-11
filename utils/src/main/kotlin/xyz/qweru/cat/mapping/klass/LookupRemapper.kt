package xyz.qweru.cat.mapping.klass

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.commons.Remapper
import xyz.qweru.cat.ASM
import xyz.qweru.cat.mapping.lookup.JarMappingLookup
import kotlin.math.log

private val logger = KotlinLogging.logger {}

class LookupRemapper(private val lookup: JarMappingLookup) : Remapper(ASM) {
    override fun map(internalName: String) =
        lookup.get(internalName)?.also {
            logger.info { "Mapped class  : $internalName -> $it" }
        } ?: internalName

    override fun mapMethodName(owner: String, name: String, descriptor: String?): String {
        println("$owner$$name")
        val lookup = lookup.getLookup(owner) ?: return name.also {
            if (!name.startsWith("xyz") || name.endsWith("<init>")) return@also
            logger.warn { "No lookup for class $owner (field $name)" }
        }
        val methods = lookup.methods
        return methods.get(name)?.also {
            logger.info { "Mapped method : $owner$$name -> $it" }
        } ?: name.also {
            logger.warn { "No value for $name in $owner method lookup." }
            logger.warn { "Lookup contains: " }
            methods.dump()
            logger.warn { "Root lookup: " }
            this.lookup.dump()
        }
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String?): String {
        val lookup = lookup.getLookup(owner) ?: return name
        val fields = lookup.fields
        return fields.get(name)?.also {
            logger.info { "Mapped field  : $owner.$name -> $it" }
        } ?: name
    }

    fun mapLocalFieldName(owner: String, method: String, name: String): String {
        val lookup = lookup.getLookup(owner) ?: return name
        val localFields = lookup.methods.getLookup(method)  ?: return name
        return localFields.get(name)?.also {
            logger.info { "Mapped mfield : $owner#$method..$name -> $it" }
        } ?: name
    }
}