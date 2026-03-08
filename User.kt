package com.example.smartwasteclassification

data class User(
    val id: Long = 0,
    val username: String,
    val password: String,
    val nickname: String = ""
)
