package xyz.qweru.cat.mapping.klass

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import xyz.qweru.cat.ASM

class LocalFieldLookupRemapper(
    private val remapper: LookupRemapper,
    private val klass: String,
    private val method: String,
    delegate: MethodVisitor
) : MethodVisitor(ASM, delegate) {
    override fun visitLocalVariable(
        name: String,
        descriptor: String?,
        signature: String?,
        start: Label?,
        end: Label?,
        index: Int
    ) {
        println("lvar $klass#$method..$name")
        super.visitLocalVariable(remapper.mapLocalFieldName(klass, method, name), descriptor, signature, start, end, index)
//        super.visitLocalVariable(name, descriptor, signature, start, end, index)
    }
}