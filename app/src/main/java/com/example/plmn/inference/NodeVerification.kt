package com.example.plmn.inference

enum class VerificationFailureCode {
    DNS_LOOKUP_FAILED,
    CONNECT_TIMEOUT,
    CONNECTION_REFUSED,
    TLS_HANDSHAKE_FAILED,
    HTTP_ERROR,
    UNAUTHORIZED_401,
    FORBIDDEN_403,
    MALFORMED_RESPONSE,
    MODEL_NOT_FOUND,
    STREAM_INTERRUPTED,
    REDIRECT_BLOCKED_SECURITY,
    UNKNOWN;

    companion object {
        fun fromException(e: Throwable): VerificationFailureCode {
            val msg = e.localizedMessage.orEmpty().lowercase()
            return when {
                e is java.net.UnknownHostException || msg.contains("unknownhost") || msg.contains("unable to resolve host") -> DNS_LOOKUP_FAILED
                e is java.net.SocketTimeoutException || msg.contains("timeout") -> CONNECT_TIMEOUT
                e is java.net.ConnectException || msg.contains("refused") -> CONNECTION_REFUSED
                e is javax.net.ssl.SSLException || msg.contains("ssl") || msg.contains("tls") -> TLS_HANDSHAKE_FAILED
                e is SecurityException || msg.contains("security") -> REDIRECT_BLOCKED_SECURITY
                else -> UNKNOWN
            }
        }

        fun fromHttpStatus(code: Int): VerificationFailureCode {
            return when (code) {
                401 -> UNAUTHORIZED_401
                403 -> FORBIDDEN_403
                in 400..499 -> HTTP_ERROR
                in 500..599 -> HTTP_ERROR
                else -> UNKNOWN
            }
        }
    }
}

data class NodeVerification(
    val level: NodeVerificationLevel,
    val resolvedAddresses: List<String> = emptyList(),
    val tcpReachable: Boolean = false,
    val protocolStatus: Int? = null,
    val authenticated: Boolean = false,
    val selectedModelPresent: Boolean = false,
    val streamVerifiedAt: Long? = null,
    val checkedAt: Long = System.currentTimeMillis(),
    val failureCode: VerificationFailureCode? = null,
    val failureMessage: String? = null
)
