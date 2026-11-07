package com.seen.film.domain

enum class MetadataLanguage(val apiCode: String, val displayName: String) {
    ZH_CN("zh-CN", "简体中文"), EN_US("en-US", "English");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.apiCode == code } ?: ZH_CN
    }
}
