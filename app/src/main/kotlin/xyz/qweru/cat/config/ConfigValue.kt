package xyz.qweru.cat.config

import xyz.qweru.cat.transform.Transformer
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ConfigValue<T>(val owner: Transformer, val name: String, var value: T) : ReadOnlyProperty<Transformer, T> {
    override fun getValue(thisRef: Transformer, property: KProperty<*>): T = value
}