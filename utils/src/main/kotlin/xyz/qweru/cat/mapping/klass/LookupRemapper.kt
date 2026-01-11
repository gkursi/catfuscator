package xyz.qweru.cat.mapping.klass

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.commons.Remapper
import xyz.qweru.cat.ASM
import xyz.qweru.cat.mapping.MappingLookup

private val logger = KotlinLogging.logger {}

class LookupRemapper(private val lookup: MappingLookup) : Remapper(ASM) {
    override fun map(internalName: String) =
        lookup.getMapping(internalName)?.let {
            logger.info { "Mapped $internalName -> $it" }
            it
        } ?: internalName
}