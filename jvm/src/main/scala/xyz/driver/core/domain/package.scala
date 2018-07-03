package xyz.driver.core

import com.google.i18n.phonenumbers.PhoneNumberUtil

package object domain {

  private val phoneUtil = PhoneNumberUtil.getInstance()

  /** Enhances the PhoneNumber companion object with methods only available on the JVM. */
  implicit class JvmPhoneNumber(val number: PhoneNumber.type) extends AnyVal {
    def parse(phoneNumber: String): Option[PhoneNumber] = {
      val phone = scala.util.Try(phoneUtil.parseAndKeepRawInput(phoneNumber, "US")).toOption

      val validated = phone match {
        case None => None
        case Some(pn) =>
          if (!phoneUtil.isValidNumber(pn)) None
          else Some(pn)
      }
      validated.map(pn => PhoneNumber(pn.getCountryCode.toString, pn.getNationalNumber.toString))
    }
  }

}
