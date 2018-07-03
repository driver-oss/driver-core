package xyz.driver.core.rest

object ContextHeaders {
  val AuthenticationTokenHeader: String  = "Authorization"
  val PermissionsTokenHeader: String     = "Permissions"
  val AuthenticationHeaderPrefix: String = "Bearer"
  val ClientFingerprintHeader: String    = "X-Client-Fingerprint"
  val TrackingIdHeader: String           = "X-Trace"
  val StacktraceHeader: String           = "X-Stacktrace"
  val OriginatingIpHeader: String        = "X-Forwarded-For"
  val ResourceCount: String              = "X-Resource-Count"
  val PageCount: String                  = "X-Page-Count"
  val TraceHeaderName                    = "Tracing-Trace-Id"
  val SpanHeaderName                     = "Tracing-Span-Id"
}
