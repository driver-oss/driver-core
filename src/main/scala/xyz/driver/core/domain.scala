package xyz.driver.core

object domain {

  final case class Email(username: String, domain: String) {
    override def toString = username + "@" + domain
  }

  object Email {
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
    def parse(phoneNumberString: String): Option[PhoneNumber] = {
      val onlyDigits = phoneNumberString.replaceAll("[^\\d.]", "")

      if (onlyDigits.length < 10) None
      else {
        val tenDigitNumber = onlyDigits.takeRight(10)
        val countryCode    = Option(onlyDigits.dropRight(10)).filter(_.nonEmpty).getOrElse("1")

        Some(PhoneNumber(countryCode, tenDigitNumber))
      }
    }
  }
}