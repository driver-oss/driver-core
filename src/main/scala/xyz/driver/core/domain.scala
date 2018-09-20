package xyz.driver.core

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import scalaz.Equal
import scalaz.std.string._
import scalaz.syntax.equal._

import scala.util.Try
import scala.util.control.NonFatal

object domain {

  final case class Email(username: String, domain: String) {

    val value: String = toString

    override def toString: String = username + "@" + domain
  }

  object Email {
    implicit val emailEqual: Equal[Email] = Equal.equal {
      case (left, right) => left.toString.toLowerCase === right.toString.toLowerCase
    }

    def parse(emailString: String): Option[Email] = {
      Some(emailString.split("@")) collect {
        case Array(username, domain) => Email(username, domain)
      }
    }
  }

  final case class PhoneNumber(countryCode: String, number: String) {

    /** This is a more human-friendly alias for #toE164String() */
    def toCompactString: String = s"+$countryCode$number"

    /** Outputs the phone number in a E.164-compliant way, e.g. +14151234567 */
    def toE164String: String = toCompactString

    /**
      * Outputs the phone number in a "readable" way, e.g. "+1 415-123-45-67 ext. 1234"
      * @throws IllegalStateException if the contents of this object is not a valid phone number
      */
    @throws[IllegalStateException]
    def toHumanReadableString: String =
      try {
        val phoneNumber = PhoneNumber.phoneUtil.parse(toE164String, "US")
        PhoneNumber.phoneUtil.format(phoneNumber, PhoneNumberFormat.INTERNATIONAL)
      } catch {
        case NonFatal(e) => throw new IllegalStateException(s"$toString is not a valid number", e)
      }

    override def toString: String = s"+$countryCode $number"
  }

  object PhoneNumber {

    private[PhoneNumber] val phoneUtil = PhoneNumberUtil.getInstance()

    def parse(phoneNumber: String): Option[PhoneNumber] = {
      val validated = Try(phoneUtil.parseAndKeepRawInput(phoneNumber, "US")).toOption.filter(phoneUtil.isValidNumber)
      validated.map { pn =>
        PhoneNumber(pn.getCountryCode.toString, pn.getNationalNumber.toString)
      }
    }
  }
}
