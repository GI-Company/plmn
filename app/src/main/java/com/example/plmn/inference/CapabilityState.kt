package com.example.plmn.inference

enum class CapabilityState {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN;

    val isSupported: Boolean get() = this == SUPPORTED

    companion object {
        fun fromBoolean(value: Boolean?): CapabilityState {
            return when (value) {
                true -> SUPPORTED
                false -> UNSUPPORTED
                null -> UNKNOWN
            }
        }

        fun fromString(str: String?): CapabilityState {
            if (str.isNullOrBlank()) return UNKNOWN
            return when (str.uppercase()) {
                "SUPPORTED", "TRUE", "YES", "1" -> SUPPORTED
                "UNSUPPORTED", "FALSE", "NO", "0" -> UNSUPPORTED
                else -> UNKNOWN
            }
        }
    }
}
