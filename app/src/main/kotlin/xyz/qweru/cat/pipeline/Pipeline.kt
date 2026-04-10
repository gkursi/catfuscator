package xyz.qweru.cat.pipeline

import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer

class Pipeline {
    internal val stages = arrayListOf<Stage<*>>()
    internal val pre = Stage<Transformer>()
    internal val post = Stage<Transformer>()

    fun <T: Transformer> stage(configurator: Stage<T>.() -> Unit) {
        val stage = Stage<T>()
        configurator(stage)
        stages.add(stage)
    }

    /**
     * This transformer will run before all other transformers
     */
    fun addPreTransformer(transformer: Transformer) =
        pre.transformer(transformer)

    /**
     * This transformer will run after all other transformers
     */
    fun addPostTransformer(transformer: Transformer) =
        post.transformer(transformer)

    fun apply(
        jar: JarContainer,
        opts: Configuration
    ) {
        pre.apply(jar, opts)

        stages.forEach {
            it.apply(jar, opts)
        }

        post.apply(jar, opts)
    }
}