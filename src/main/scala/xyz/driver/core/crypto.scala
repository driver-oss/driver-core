package xyz.driver.core

import xyz.driver.core.auth.AuthToken

object crypto {

  final case class EncryptionKey(value: String)

  final case class DecryptionKey(value: String)

  trait Crypto {

    def keyForToken(authToken: AuthToken): EncryptionKey

    def encrypt(encryptionKey: EncryptionKey)(message: Array[Byte]): Array[Byte]

    def decrypt(decryptionKey: EncryptionKey)(message: Array[Byte]): Array[Byte]
  }

  object NoCrypto extends Crypto {

    override def keyForToken(authToken: AuthToken): EncryptionKey = EncryptionKey(authToken.value.value)

    override def decrypt(decryptionKey: EncryptionKey)(message: Array[Byte]): Array[Byte] = message
    override def encrypt(encryptionKey: EncryptionKey)(message: Array[Byte]): Array[Byte] = message
  }
}
