package xyz.qweru.cat.config

import xyz.qweru.cat.transform.Transformer
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class ConfigValue<T>(val owner: Transformer, val name: String, val description: String, open var value: T) : ReadWriteProperty<Transformer, T> {
    override fun getValue(thisRef: Transformer, property: KProperty<*>): T = value

    override fun setValue(thisRef: Transformer, property: KProperty<*>, value: T) {
        this.value = value
    }
}