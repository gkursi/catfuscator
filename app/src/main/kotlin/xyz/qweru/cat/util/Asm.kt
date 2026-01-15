package xyz.qweru.cat.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Attribute
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import xyz.qweru.cat.jar.JarContainer

fun newClass(
    name: String,
    version: Int,
    access: Int,
    superClass: String = "java/lang/Object",
    signature: String = "L$superClass;",
    interfaces: Array<String> = arrayOf(),
    configurator: ClassBuilder.() -> Unit
): ClassNode {
    val node = ClassNode()
    node.visit(version, access, name, signature, superClass, interfaces)

    val builder = ClassBuilder(node)
    configurator.invoke(builder)

    return node
}

fun transformClass(classNode: ClassNode, configurator: ClassBuilder.() -> Unit) =
    ClassBuilder(classNode).configurator()

fun transformMethod(methodNode: MethodNode, configurator: MethodTransformer.() -> Unit) =
    MethodTransformer(methodNode).configurator()

/**
 * This requires the jar to contain at least one class entry
 */
fun versionFromJar(target: JarContainer) =
    target.classes.entries.first().value.version

class MethodTransformer(methodNode: MethodNode) {
    val instructions: InsnList = methodNode.instructions

    fun replace(list: InsnList, predicate: (AbstractInsnNode) -> Boolean) {
        for (node in instructions.toArray()) {
            if (!predicate.invoke(node)) continue
            instructions.insert(node, list)
            instructions.remove(node)
        }
    }

    fun replace(listProvider: (AbstractInsnNode) -> InsnList, predicate: (AbstractInsnNode) -> Boolean) {
        for (node in instructions.toArray()) {
            if (!predicate.invoke(node)) continue
            instructions.insert(node, listProvider(node))
            instructions.remove(node)
        }
    }
}

@CatDsl
class ClassBuilder(val classNode: ClassNode) {

    /**
     * @param annotationDefault Default annotation value
     */
    fun method(
        name: String,
        access: Int,
        descriptor: String,
        signature: String? = null,
        exceptions: List<String> = listOf(),
        parameters: List<CatMethodParameter> = listOf(),
        runtimeAnnotations: List<AnnotationNode> = listOf(),
        annotations: List<AnnotationNode> = listOf(),
        runtimeTypeAnnotations: List<TypeAnnotationNode> = listOf(),
        typeAnnotations: List<TypeAnnotationNode> = listOf(),
        attributes: List<Attribute> = listOf(),
        annotationDefault: Any? = null,
        visibleAnnotableParameterCount: Int = 0,
        runtimeParameterAnnotations: Array<List<AnnotationNode>> = arrayOf(),
        parameterAnnotations: Array<List<AnnotationNode>> = arrayOf(),
        configurator: InsnBuilder.() -> Unit
    ) {
        val node = MethodNode()
        node.name = name
        node.access = access
        node.desc = descriptor
        node.signature = signature
        node.parameters = ArrayList()
        node.exceptions = exceptions
        node.visibleAnnotations = runtimeAnnotations
        node.invisibleAnnotations = annotations
        node.visibleTypeAnnotations = runtimeTypeAnnotations
        node.invisibleTypeAnnotations = typeAnnotations
        node.attrs = attributes
        node.annotationDefault = annotationDefault
        node.visibleAnnotableParameterCount = visibleAnnotableParameterCount
        node.visibleParameterAnnotations = runtimeParameterAnnotations
        node.invisibleParameterAnnotations = parameterAnnotations

        node.tryCatchBlocks = listOf() // todo

        // this is recomputed
        node.maxStack = 0
        node.maxLocals = 0

        // this is set by the builder
        node.localVariables = arrayListOf()

        // no
        node.visibleLocalVariableAnnotations = listOf()
        node.invisibleLocalVariableAnnotations = listOf()

        val builder = InsnBuilder(node, parameters, "L${classNode.name};")
        configurator.invoke(builder)
        builder.runPost()

        classNode.methods.add(node)
    }
}

fun instructions(methodNode: MethodNode, builder: InsnBuilder.() -> Unit) =
    instructions(localVariableOffset(methodNode), builder)

fun instructions(localOffset: Int, builder: InsnBuilder.() -> Unit) =
    InsnBuilder(InsnList(), arrayListOf(), localOffset)
        .apply {
            builder()
            runPost()
        }
        .instructions

private val logger = KotlinLogging.logger {}

@CatDsl
class InsnBuilder(val instructions: InsnList, val locals: MutableList<LocalVariableNode>, localOffset: Int) {
    private var variableIndex = localOffset

    // do not call `setLabel` with these, asm will explode
    val startLabel = label()
    val endLabel = label()

    init {
        setLabel(startLabel)
    }

    /**
     * @param ownerDesc required if the method is not static
     */
    constructor(methodNode: MethodNode, parameters: List<CatMethodParameter>, ownerDesc: String?) : this(
        methodNode.instructions ?: InsnList(),
        methodNode.localVariables ?: ArrayList(),
        0
    ) {
        methodNode.instructions = instructions
        methodNode.localVariables = locals

        if ((methodNode.access and Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC) {
            local("this", ownerDesc!!, startLabel, endLabel)
        }

        for (param in parameters) {
            local(
                param.node.name,
                param.descriptor,
                startLabel,
                endLabel,
            )
        }
    }

    private fun instruction(insn: AbstractInsnNode) = instructions.add(insn)
    private fun instruction(insn: Int) = instructions.add(InsnNode(insn))

    fun label() = LabelNode(Label())

    fun setLabel(label: LabelNode) =
        instruction(label)

    /**
     * @return the index
     */
    fun local(
        name: String,
        descriptor: String,
        from: LabelNode,
        to: LabelNode,
        signature: String? = null
    ): Int {
        val node = LocalVariableNode(
            name,
            descriptor,
            signature,
            from,
            to,
            variableIndex++
        )
        locals.add(node)
        return node.index
    }

    fun loadLocalObject(index: Int) =
        useLocal(Opcodes.ALOAD, index)

    fun loadLocalInt(index: Int) =
        useLocal(Opcodes.ILOAD, index)

    fun incrementLocalInt(index: Int, increment: Int) =
        instruction(IincInsnNode(index, increment))

    fun storeLocalObject(index: Int) =
        useLocal(Opcodes.ASTORE, index)

    fun storeLocalInt(index: Int) =
        useLocal(Opcodes.ISTORE, index)

    fun useLocal(op: Int, index: Int) =
        instruction(VarInsnNode(op, index))

    fun loadCharFromArray() {
        instruction(Opcodes.CALOAD)
    }

    fun storeCharInArray() {
        instruction(Opcodes.CASTORE)
    }

    fun returnVoid() =
        instruction(Opcodes.RETURN)

    fun returnInstance() =
        instruction(Opcodes.ARETURN)

    fun dup() = instruction(Opcodes.DUP)

    fun dup2() = instruction(Opcodes.DUP2)

    fun addInts() = instruction(Opcodes.IADD)

    fun xorInts() = instruction(Opcodes.IXOR)

    fun getArraySize() = instruction(Opcodes.ARRAYLENGTH)

    fun jumpIfSmaller(label: LabelNode) = instruction(JumpInsnNode(Opcodes.IF_ICMPLT, label))

    /**
     * @param value the constant to be loaded on the stack. This parameter must be a non-null {@link
     *        Integer}, a {@link Float}, a {@link Long}, a {@link Double}, a {@link String}, a {@link
     *        Type} of OBJECT or ARRAY sort for {@code .class} constants, for classes whose version is
     *        49, a {@link Type} of METHOD sort for MethodType, a {@link Handle} for MethodHandle
     *        constants, for classes whose version is 51 or a {@link ConstantDynamic} for a constant
     *        dynamic for classes whose version is 55.
     */
    fun loadConstant(value: Any) =
        instruction(LdcInsnNode(value))

    fun newObject(type: String, descriptor: String, pushArgs: () -> Unit) {
        instruction(TypeInsnNode(Opcodes.NEW, type))
        instruction(Opcodes.DUP)
        pushArgs()
        invokeSpecial(type, "<init>", descriptor)
    }

    fun invokeStatic(owner: String, name: String, descriptor: String) =
        invoke(owner, name, descriptor, Opcodes.INVOKESTATIC)

    /**
     * this ref + non-static linking
     */
    fun invokeVirtual(owner: String, name: String, descriptor: String) =
        invoke(owner, name, descriptor, Opcodes.INVOKEVIRTUAL)

    /**
     * this ref + static linking
     */
    fun invokeSpecial(owner: String, name: String, descriptor: String) =
        invoke(owner, name, descriptor, Opcodes.INVOKESPECIAL)

    fun invoke(owner: String, name: String, descriptor: String, op: Int) =
        instruction(MethodInsnNode(op, owner, name, descriptor))

    fun runPost() {
        setLabel(endLabel)
    }
}

fun localVariableOffset(methodNode: MethodNode) =
    localVariableOffset(methodNode.access and Opcodes.ACC_STATIC == 0, methodNode.desc)

fun localVariableOffset(isStatic: Boolean, methodDescriptor: String): Int =
    (Type.getArgumentsAndReturnSizes(methodDescriptor) shr 2) - (if (isStatic) 1 else 0)

