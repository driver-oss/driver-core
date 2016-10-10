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

  object tagging {
    private[core] trait Tagged[+V, +Tag]
  }
  type @@[+V, +Tag] = V with tagging.Tagged[V, Tag]

  type Id[+Tag] = Long @@ Tag
  object Id {
    implicit def idEqual[T]: Equal[Id[T]]       = Equal.equal[Id[T]](_ == _)
    implicit def idOrdering[T]: Ordering[Id[T]] = Ordering.by(i => i: Long)

    def apply[Tag](value: Long) = value.asInstanceOf[Id[Tag]]
  }

  type Name[+Tag] = String @@ Tag
  object Name {
    implicit def nameEqual[T]: Equal[Name[T]]       = Equal.equal[Name[T]](_ == _)
    implicit def nameOrdering[T]: Ordering[Name[T]] = Ordering.by(n => n: String)

    def apply[Tag](value: String) = value.asInstanceOf[Name[Tag]]
  }


  object revision {
    final case class Revision[T](id: String)

    implicit def revisionEqual[T]: Equal[Revision[T]] = Equal.equal[Revision[T]](_.id == _.id)
  }
}
