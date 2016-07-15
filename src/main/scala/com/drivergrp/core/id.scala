package com.drivergrp.core

import scalaz._


object id {

  trait Tagged[+V, +Tag]
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
