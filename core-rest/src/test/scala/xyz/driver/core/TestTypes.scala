package xyz.driver.core

object TestTypes {

  sealed trait CustomGADT {
    val field: String
  }

  object CustomGADT {
    final case class GadtCase1(field: String) extends CustomGADT
    final case class GadtCase2(field: String) extends CustomGADT
    final case class GadtCase3(field: String) extends CustomGADT
  }
}
