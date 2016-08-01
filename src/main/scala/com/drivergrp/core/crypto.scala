package com.drivergrp.core

object crypto {

  final case class Macaroon(value: String)

  final case class Base64[T](value: String)

  final case class AuthToken(value: Base64[Macaroon])

  final case class EncryptionKey(value: String)

  final case class DecryptionKey(value: String)

  trait Crypto {

    def keyForToken(authToken: AuthToken): EncryptionKey

    def encrypt(encryptionKey: EncryptionKey)(message: Array[Byte]): Array[Byte]

    def decrypt(decryptionKey: EncryptionKey)(message: Array[Byte]): Array[Byte]
  }
}
