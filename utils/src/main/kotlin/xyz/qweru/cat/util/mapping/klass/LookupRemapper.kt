package xyz.qweru.cat.util.mapping.klass

import org.objectweb.asm.commons.Remapper
import xyz.qweru.cat.util.ASM
import xyz.qweru.cat.util.mapping.lookup.JarMappingLookup

class LookupRemapper(private val lookup: JarMappingLookup) : Remapper(ASM) {
    override fun map(internalName: String) =
        lookup.get(internalName) ?: internalName

    override fun mapMethodName(owner: String, name: String, descriptor: String?): String {
        val lookup = lookup.getLookup(owner) ?: return name
        val methods = lookup.methods
        return methods.get(name) ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String?): String {
        val lookup = lookup.getLookup(owner) ?: return name
        val fields = lookup.fields
        return fields.get(name) ?: name
    }

    fun mapLocalFieldName(owner: String, method: String, name: String): String {
        val lookup = lookup.getLookup(owner) ?: return name
        val localFields = lookup.methods.getLookup(method) ?: return name
        return localFields.get(name) ?: name
    }
}