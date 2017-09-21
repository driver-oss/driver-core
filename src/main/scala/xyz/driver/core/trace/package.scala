package xyz.driver.core

package object trace {
  // this happens to be the same as the name google uses, but can be anything we want
  val TracingHeaderKey: String = "X-Cloud-Trace-Context"
}
