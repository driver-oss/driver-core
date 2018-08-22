package xyz.driver.core
package rest
package directives

trait Directives  extends AuthDirectives with CorsDirectives with PathMatchers with Unmarshallers
object Directives extends Directives
