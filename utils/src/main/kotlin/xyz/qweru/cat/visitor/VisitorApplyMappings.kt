package xyz.qweru.cat.visitor

import org.objectweb.asm.ClassVisitor
import xyz.qweru.cat.ASM
import xyz.qweru.cat.mapping.MappingLookup

/**
 * @param lookup Root lookup to use
 */
class VisitorApplyMappings(private val lookup: MappingLookup, delegate: ClassVisitor?) : ClassVisitor(ASM, delegate) {

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String,
        superName: String,
        interfaces: Array<out String>
    ) {
        val name = lookup.getMapping(name)

        super.visit(version, access, name, signature, superName, interfaces)
    }

}