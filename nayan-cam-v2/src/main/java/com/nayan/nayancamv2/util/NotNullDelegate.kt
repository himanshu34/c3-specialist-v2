package com.nayan.nayancamv2.util

import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

class NotNullDelegate<T> : ObservableProperty<T?>(null) {
    override fun afterChange(property: KProperty<*>, oldValue: T?, newValue: T?) {
        if (oldValue == null && newValue != null) {
            println("${property.name} has become not null: $newValue")
            // You can perform any desired actions here when the property becomes not null
        }
    }
}