package xyz.qweru.cat.util.mapping.klass

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import xyz.qweru.cat.util.ASM

class LocalFieldLookupRemapper(
    private val remapper: LookupRemapper,
    private val klass: String,
    private val method: String,
    delegate: MethodVisitor
) : MethodVisitor(ASM, delegate) {
    override fun visitLocalVariable(name: String, descriptor: String?, signature: String?, start: Label?, end: Label?, index: Int) =
        super.visitLocalVariable(remapper.mapLocalFieldName(klass, method, name), descriptor, signature, start, end, index)
}