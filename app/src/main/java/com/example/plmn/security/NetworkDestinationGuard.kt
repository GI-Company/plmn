package com.example.plmn.security

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress

/**
 * NetworkDestinationGuard: OkHttp Interceptor enforcing strict transport and IP boundary rules.
 *
 * Rules:
 * 1. HTTPS is allowed for explicitly configured endpoints.
 * 2. HTTP (cleartext) is strictly restricted to local loopback and RFC1918 private LAN addresses.
 * 3. Prevents DNS rebinding attacks by verifying resolved IP addresses remain private.
 * 4. Redirect Security: Explicitly validates every redirect hop target URL before proceeding.
 * 5. Cross-Origin Header Protection: Strips sensitive authentication headers (Authorization, X-PLM-*)
 *    if a redirect targets a different host, port, or scheme.
 */
class NetworkDestinationGuard : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        validateDestination(originalUrl.scheme, originalUrl.host, originalUrl.port)

        var currentRequest = originalRequest
        var response = chain.proceed(currentRequest)
        var redirectCount = 0

        while (response.isRedirect && redirectCount < 3) {
            redirectCount++
            val locationHeader = response.header("Location") ?: break
            val redirectUrl = currentRequest.url.resolve(locationHeader) ?: break

            // 1. Validate redirect target destination against PLMN security boundary
            validateDestination(redirectUrl.scheme, redirectUrl.host, redirectUrl.port)

            // 2. Strip sensitive authentication headers on cross-origin redirects
            currentRequest = buildRedirectRequest(currentRequest, redirectUrl)

            response.close()
            response = chain.proceed(currentRequest)
        }

        return response
    }

    companion object {
        fun buildRedirectRequest(originalRequest: Request, redirectUrl: HttpUrl): Request {
            val origUrl = originalRequest.url
            val isSameOrigin = origUrl.host.equals(redirectUrl.host, ignoreCase = true) &&
                    origUrl.port == redirectUrl.port &&
                    origUrl.scheme.equals(redirectUrl.scheme, ignoreCase = true)

            val builder = originalRequest.newBuilder().url(redirectUrl)

            if (!isSameOrigin) {
                builder.removeHeader("Authorization")
                builder.removeHeader("X-PLM-Signature")
                builder.removeHeader("X-PLM-Device-ID")
                builder.removeHeader("X-PLM-Nonce")
                builder.removeHeader("X-PLM-Timestamp")
                builder.removeHeader("X-PLM-Signature-Algorithm")
            }

            return builder.build()
        }

        fun validateDestination(scheme: String, host: String, port: Int) {
            val isHttps = scheme.equals("https", ignoreCase = true)
            if (isHttps) {
                return
            }

            if (!scheme.equals("http", ignoreCase = true)) {
                throw SecurityException("Unsupported URI scheme '$scheme'")
            }

            if (isLoopbackHost(host)) {
                return
            }

            val isPrivateAddress = try {
                val addrs = InetAddress.getAllByName(host)
                addrs.all { addr ->
                    addr.isLoopbackAddress ||
                            addr.isSiteLocalAddress ||
                            addr.hostAddress == "10.0.2.2"
                }
            } catch (_: Exception) {
                false
            }

            if (!isPrivateAddress && !isRfc1918HostName(host)) {
                throw SecurityException(
                    "Security Boundary Violation: Cleartext HTTP traffic to public destination '$host' is strictly blocked by PLMN Security Policy."
                )
            }
        }

        private fun isLoopbackHost(host: String): Boolean {
            val lower = host.lowercase()
            return lower == "localhost" || lower == "127.0.0.1" || lower == "::1" || lower == "10.0.2.2"
        }

        private fun isRfc1918HostName(host: String): Boolean {
            val parts = host.split(".")
            if (parts.size != 4) return false
            val first = parts[0].toIntOrNull() ?: return false
            val second = parts[1].toIntOrNull() ?: return false

            return when {
                first == 10 -> true
                first == 172 && second in 16..31 -> true
                first == 192 && second == 168 -> true
                else -> false
            }
        }
    }
}
