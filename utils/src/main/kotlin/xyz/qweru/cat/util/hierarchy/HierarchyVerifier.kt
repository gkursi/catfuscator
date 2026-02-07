package xyz.qweru.cat.util.hierarchy

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Type
import org.objectweb.asm.tree.analysis.SimpleVerifier
import xyz.qweru.cat.util.ASM

private val logger = KotlinLogging.logger {  }

class HierarchyVerifier(
    val hierarchy: ClassHierarchy,
) : SimpleVerifier(ASM, null, null, null, false) {

    override fun isAssignableFrom(type1: Type, type2: Type): Boolean {
        return hierarchy.isAssignableFrom(type2.internalName, type1.internalName).also {
            logger.warn { "is $type1 assignable from $type2: $it" }
        }
    }

}