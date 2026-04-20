package com.github.tartaricacid.mcshelper.options

enum class PlayerPermissionLevel {
    VISITOR,
    MEMBER,
    OPERATOR;

    val code: Int
        get() = when (this) {
            VISITOR -> 0
            MEMBER -> 1
            OPERATOR -> 2
        }

    val displayName: String
        get() = when (this) {
            VISITOR -> "访客"
            MEMBER -> "成员"
            OPERATOR -> "操作员"
        }
}
