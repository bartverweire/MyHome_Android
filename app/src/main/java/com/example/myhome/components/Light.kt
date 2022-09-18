package com.example.myhome.components

data class Light(
    val id: Int,
    val name: String,
    val dimmable: Boolean = false,
    var state: Int = 0,
)