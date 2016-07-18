package com.drivergrp

import scalaz.Equal


package object core {
  import scala.language.reflectiveCalls

  def make[T](v: => T)(f: T => Unit): T = {
    val value = v; f(value); value
  }

  def using[R <: { def close() }, P](r: => R)(f: R => P): P = {
    val resource = r
    try {
      f(resource)
    } finally {
      resource.close()
    }
  }


  private[core] trait Tagged[+V, +Tag]
  type @@[+V, +Tag] = V with Tagged[V, Tag]

  type Id[+Tag] = Long @@ Tag
  object Id {
    def apply[Tag](value: Long) = value.asInstanceOf[Id[Tag]]
  }

  implicit def idEqual[T]: Equal[Id[T]] = Equal.equal[Id[T]](_ == _)

  type Name[+Tag] = String @@ Tag
  object Name {
    def apply[Tag](value: String) = value.asInstanceOf[Name[Tag]]
  }

  implicit def nameEqual[T]: Equal[Name[T]] = Equal.equal[Name[T]](_ == _)
}
