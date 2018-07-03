package xyz.driver.core.rest

object AuthProvider {
  val AuthenticationTokenHeader: String    = ContextHeaders.AuthenticationTokenHeader
  val PermissionsTokenHeader: String       = ContextHeaders.PermissionsTokenHeader
  val SetAuthenticationTokenHeader: String = "set-authorization"
  val SetPermissionsTokenHeader: String    = "set-permissions"
}
