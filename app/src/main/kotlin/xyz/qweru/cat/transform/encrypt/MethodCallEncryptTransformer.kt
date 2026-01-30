package xyz.qweru.cat.transform.encrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import xyz.qweru.cat.util.asm.InsnBuilder
import xyz.qweru.cat.util.asm.PUBLIC_STATIC
import xyz.qweru.cat.util.asm.createArrayFromStack
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.isEnum
import xyz.qweru.cat.util.asm.isEnumMethod
import xyz.qweru.cat.util.asm.newClass
import xyz.qweru.cat.util.asm.pushType
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.asm.unboxType
import xyz.qweru.cat.util.asm.versionFromJar
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.thread.createExecutorFrom
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {  }

private const val invokeDesc = "(Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
private const val poolName = "cat/MethodPool"
private const val invokeMethod = "call"

private const val clazz = "java/lang/Class"
private const val mhandle = "java/lang/invoke/MethodHandle"
private const val mtype = "java/lang/invoke/MethodType"
private const val mhandles = "java/lang/invoke/MethodHandles"
private const val mhandlesLookup = $$"$$mhandles$Lookup"

/**
 * todo:  shuffled method args
 * todo:  constructor support(?)
 */
class MethodCallEncryptTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("MethodCallEncrypt", "Encrypt method calls", target, opts) {

    init {
        target.apply {
            val parallel = createExecutorFrom(opts)
            val targets = CopyOnWriteArrayList<Method>()

            for (entry in classes) {
                if (!canTarget(entry)) continue
                val klass = entry.value
                parallel {
                    for (method in klass.methods) {
                        if (klass.isEnum && method.isEnumMethod) continue
                        transformMethod(method) {
                            createPass().replace({ it is MethodInsnNode && it.opcode != Opcodes.INVOKEINTERFACE && it.name != "<init>" }) { invoke, _, _ ->
                                instructionsFor(method) {
                                    invoke as MethodInsnNode

                                    val mInvoke = Method(
                                        invoke.owner,
                                        invoke.name,
                                        invoke.desc,
                                        mapTag(invoke.name, invoke.opcode)
                                    )
                                    val types = Type.getArgumentTypes(invoke.desc)

                                    targets.add(mInvoke)
                                    logger.info { "Types ${types.joinToString(",")}" }

                                    createArrayFromStack(types)
                                    if (mInvoke.isStatic) {
                                        constantNull()
                                        swap()
                                    }
                                    ldc(mInvoke.hash())
                                    invokeStatic(poolName, invokeMethod, invokeDesc)
                                    unboxType(Type.getReturnType(invoke.desc))
                                }
                            }
                        }
                    }
                }
            }

            parallel.await()

            val poolClass = newClass(
                poolName,
                versionFromJar(this),
                Opcodes.ACC_PUBLIC
            ) {
                field("map", PUBLIC_STATIC, "Ljava/util/concurrent/ConcurrentHashMap;", null)

                method(invokeMethod, PUBLIC_STATIC, invokeDesc) {
                    // method params
                    val instance = 0 // Ljava/lang/Object;
                    val args = 1 // [Ljava/lang/Object;
                    val target = 2 // Ljava/lang/String;
                    getStaticField(poolName, "map", "Ljava/util/concurrent/ConcurrentHashMap;")
                    loadLocalObject(target)
                    invokeVirtual(
                        "java/util/concurrent/ConcurrentHashMap",
                        "get",
                        "(Ljava/lang/Object;)Ljava/lang/Object;"
                    )
                    checkCast("java/lang/invoke/MethodHandle")

                    val static = label()
                    loadLocalObject(instance)
                    jumpIfNull(static)

                    loadLocalObject(instance)
                    invokeVirtual(
                        "java/lang/invoke/MethodHandle",
                        "bindTo",
                        "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;"
                    ) // L

                    +static // mh
                    loadLocalObject(args) // mh, [L
                    invokeVirtual(
                        "java/lang/invoke/MethodHandle",
                        "invoke",
                        "([Ljava/lang/Object;)Ljava/lang/Object;"
                    ) // L
                    returnInstance() // Ljava/lang/Object;
                }

                method("<clinit>", PUBLIC_STATIC, "()V") {
                    newObject("java/util/concurrent/ConcurrentHashMap", "(I)V") { ldc(targets.size) }
                    dup()
                    storeStaticField(poolName, "map", "Ljava/util/concurrent/ConcurrentHashMap;")

                    for (method in targets.shuffled()) {
                        dup()
                        ldc(method.hash())
                        getHandle(method)
                        invokeVirtual(
                            "java/util/concurrent/ConcurrentHashMap",
                            "put",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                        )
                        pop()
                    }

                    pop()
                    returnVoid()
                }
            }

            put(poolClass)
        }
    }

    private fun mapTag(name: String, op: Int): Int = when(op) {
        Opcodes.INVOKESTATIC -> Opcodes.H_INVOKESTATIC
        Opcodes.INVOKEVIRTUAL -> Opcodes.H_INVOKEVIRTUAL
        Opcodes.INVOKESPECIAL -> if (name == "<init>") Opcodes.H_NEWINVOKESPECIAL else Opcodes.H_INVOKESPECIAL
        else -> throw IllegalArgumentException("unknown tag")
    }

    private fun InsnBuilder.getHandle(method: Method) {
        val args = Type.getArgumentTypes(method.desc)

        // obtain the base methodhandle
        // (the constant pool is used
        // for methods in closed named
        // packages instead of private
        // lookups)
        if (!method.owner.startsWith("java") && !method.owner.startsWith("[")) {
            // todo: dedupe lookups

            ldc(Type.getType("L${method.owner};"))
            dup()
            invokeStatic(mhandles, "lookup", "()L$mhandlesLookup;")
            invokeStatic(mhandles, "privateLookupIn", "(L$clazz;L$mhandlesLookup;)L$mhandlesLookup;")
            swap()

            ldc(method.name)

            // methodtype
            pushType(Type.getReturnType(method.desc))
            ldc(args.size)
            newObjectArray("java/lang/Class")
            for ((i, type) in args.withIndex()) {
                dup()
                ldc(i)
                pushType(type)
                storeObjectInArray()
                logger.info { "Push type for method ${method.name} : $type " }
            }

            invokeStatic(mtype, "methodType", "(L$clazz;[L$clazz;)L$mtype;") // lookup, cl, name, mtype

            when (method.tag) {
                Opcodes.H_INVOKESTATIC -> invokeVirtual(
                    mhandlesLookup, "findStatic",
                    "(L$clazz;Ljava/lang/String;L$mtype;)L$mhandle;"
                )
                Opcodes.H_INVOKEVIRTUAL -> invokeVirtual(
                    mhandlesLookup, "findVirtual",
                    "(L$clazz;Ljava/lang/String;L$mtype;)L$mhandle;"
                )
                Opcodes.H_INVOKESPECIAL -> {
                    ldc(Type.getType("L${method.owner};")) // lookup, cl, name, mtype, caller
                    invokeVirtual(
                        mhandlesLookup, "findSpecial",
                        "(L$clazz;Ljava/lang/String;L$mtype;L$clazz;)L$mhandle;"
                    )
                }
            }
        } else {
            ldc(Handle(
                method.tag,
                method.owner,
                method.name,
                method.desc,
                method.tag == Opcodes.H_INVOKEINTERFACE
            ))
        }

        // adapt to use with an array

        invokeVirtual(mhandle, "asFixedArity", "()L$mhandle;")
        pushType(Type.getType("[Ljava/lang/Object;"))
        ldc(args.size)
        invokeVirtual(mhandle, "asSpreader", "(Ljava/lang/Class;I)L$mhandle;")
    }

    private data class Method(val owner: String, val name: String, val desc: String, val tag: Int) {
        val isStatic = tag == Opcodes.H_INVOKESTATIC

        fun hash(): String =
             xyz.qweru.cat.util.crypto.hash("$tag: $owner#$name$desc $isStatic")
    }

    // Workaround for my weird remapping pipeline.
    // Todo: find a better solution

    class Post(
        target: JarContainer,
        opts: Configuration
    ) : Transformer("PostMCET", "Post transformer for method call encryption, required when using method renaming", target, opts) {
        init {
            target.apply {
                transformMethod(classes[poolName]!!.methods.first { it.name == "<clinit>" }) {
                    createPass()
                        .onlyTake { it !is LabelNode && it !is LineNumberNode && it !is FrameNode }
                        .find({ it is LdcInsnNode && it.cst is String }) { ldc, insns, i ->
                            ldc as LdcInsnNode
                            if (i < 6 || insns[i - 1].opcode != Opcodes.SWAP) {
                                return@find
                            }

                            val lookup = insns[i - 3]
                            if (lookup !is MethodInsnNode || lookup.owner != mhandles || lookup.name != "lookup") {
                                return@find
                            }

                            val privateLookupIn = insns[i - 2]
                            if (privateLookupIn !is MethodInsnNode || privateLookupIn.owner != mhandles || privateLookupIn.name != "privateLookupIn") {
                                privateLookupIn as MethodInsnNode
                                logger.warn { "owner=${privateLookupIn.owner} (expected $mhandlesLookup)" }
                                logger.warn { "name=${privateLookupIn.name} (expected privateLookupIn)" }
                                return@find
                            }

                            val typeInsn = insns[i - 5]
                            if (typeInsn !is LdcInsnNode || typeInsn.cst !is Type) {
                                return@find
                            }
                            val type = typeInsn.cst as Type

                            val klass = mappings.getLookup(type.internalName)
                            if (klass == null) {
                                logger.warn { "No mapping for type $type" }
                                return@find
                            }

                            val method = klass.methods.get(ldc.cst as String)
                            if (method == null) {
                                logger.warn { "No mapping for method ${ldc.cst} of type $type" }
                                return@find
                            }

                            logger.info { "Post processor mapped ${ldc.cst} to $method (type $type)" }
                            ldc.cst = method
                        }
                }
            }
        }
    }
}