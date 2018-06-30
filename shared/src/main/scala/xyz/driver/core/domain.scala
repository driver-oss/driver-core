package xyz.driver.core

import scalaz.Equal

package domain {

  final case class Email(username: String, domain: String) {
    override def toString: String = username + "@" + domain
  }

  object Email {
    implicit val emailEqual: Equal[Email] = Equal.equal {
      case (left, right) => left.toString.toLowerCase == right.toString.toLowerCase
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

}
