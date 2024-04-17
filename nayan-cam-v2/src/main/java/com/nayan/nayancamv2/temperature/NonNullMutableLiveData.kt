package com.nayan.nayancamv2.temperature

import androidx.lifecycle.MutableLiveData

class NonNullMutableLiveData<T>(defaultValue: T) : MutableLiveData<T>() {

    init {
        value = defaultValue
    }

    override fun getValue(): T {
        return super.getValue()!!
    }

    override fun setValue(value: T) {
        value ?: throw NullPointerException("Cannot set null value")
        super.setValue(value)
    }
}