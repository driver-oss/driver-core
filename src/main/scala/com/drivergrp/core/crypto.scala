package com.drivergrp.core

import com.drivergrp.core.auth.AuthToken

object crypto {

  final case class EncryptionKey(value: String)

  final case class DecryptionKey(value: String)

  trait Crypto {

    def keyForToken(authToken: AuthToken): EncryptionKey

    def encrypt(encryptionKey: EncryptionKey)(message: Array[Byte]): Array[Byte]

    def decrypt(decryptionKey: EncryptionKey)(message: Array[Byte]): Array[Byte]
  }
}
