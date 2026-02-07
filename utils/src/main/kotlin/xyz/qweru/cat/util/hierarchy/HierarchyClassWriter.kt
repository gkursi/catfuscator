package xyz.qweru.cat.util.hierarchy

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassWriter

private val logger = KotlinLogging.logger {  }

class HierarchyClassWriter(
    val hierarchy: ClassHierarchy,
    flags: Int
) : ClassWriter(flags) {

    override fun getCommonSuperClass(type1: String, type2: String): String {
        return hierarchy.getCommonType(type1, type2).also {
            logger.warn { "$type1 + $type2 = $it (super = ${super.getCommonSuperClass(type1, type2)})" }
        }
    }
}