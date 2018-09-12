package xyz.driver.core

import scala.collection.generic.CanBuildFrom
import scala.language.{higherKinds, implicitConversions}

/**
  * @author sergey
  * @since 9/11/18
  */
package object tagging {

  implicit class Taggable[V <: Any](val v: V) extends AnyVal {
    def tagged[Tag]: V @@ Tag = v.asInstanceOf[V @@ Tag]
  }

}

package tagging {

  sealed trait Tagged[+V, +Tag]

  object Tagged {
    implicit class TaggedOps[V, Tag](val v: V @@ Tag) extends AnyVal {
      def tagless: V = v
    }

    implicit def orderingMagnet[V, Tag](implicit ord: Ordering[V]): Ordering[V @@ Tag] =
      ord.asInstanceOf[Ordering[V @@ Tag]]

  }

  sealed trait Trimmed

  object Trimmed {

    implicit def apply[V](trimmable: V)(implicit ev: CanBeTrimmed[V]): V @@ Trimmed = {
      ev.trim(trimmable).tagged[Trimmed]
    }

    sealed trait CanBeTrimmed[T] {
      def trim(trimmable: T): T
    }

    implicit object StringCanBeTrimmed extends CanBeTrimmed[String] {
      def trim(str: String): String = str.trim()
    }

    implicit def nameCanBeTrimmed[T]: CanBeTrimmed[Name[T]] = new CanBeTrimmed[Name[T]] {
      def trim(name: Name[T]): Name[T] = Name[T](name.value.trim())
    }

    implicit def option2Trimmed[V: CanBeTrimmed](option: Option[V]): Option[V @@ Trimmed] =
      option.map(Trimmed(_))

    implicit def coll2Trimmed[T, C[_] <: Traversable[_]](coll: C[T])(
        implicit ev: C[T] <:< Traversable[T],
        tr: CanBeTrimmed[T],
        bf: CanBuildFrom[Nothing, T @@ Trimmed, C[T @@ Trimmed]]): C[T @@ Trimmed] =
      ev(coll).map(Trimmed(_)(tr)).to[C]
  }

}
