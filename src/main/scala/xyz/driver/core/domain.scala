package xyz.driver.core

import com.google.i18n.phonenumbers.PhoneNumberUtil
import scalaz.Equal
import scalaz.std.string._
import scalaz.syntax.equal._

object domain {

  final case class Email(username: String, domain: String) {
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

  final case class PhoneNumber(countryCode: String = "1", number: String) {
    override def toString: String = s"+$countryCode $number"
  }

  object PhoneNumber {

    private val phoneUtil = PhoneNumberUtil.getInstance()

    def parse(phoneNumber: String): Option[PhoneNumber] = {
      val validated =
        util.Try(phoneUtil.parseAndKeepRawInput(phoneNumber, "US")).toOption.filter(phoneUtil.isValidNumber)
      validated.map(pn => PhoneNumber(pn.getCountryCode.toString, pn.getNationalNumber.toString))
    }
  }
}
