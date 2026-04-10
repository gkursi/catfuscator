package xyz.qweru.cat.pipeline

import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer

class Stage<T : Transformer> {
    internal val transformers = arrayListOf<T>()

    fun transformer(transformer: T) {
        transformers.add(transformer)
    }

    fun apply(jar: JarContainer, opts: Configuration) {
        for (t in transformers) {
            t.apply(jar, opts)
        }
    }
}