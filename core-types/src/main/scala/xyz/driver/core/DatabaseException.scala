package xyz.driver.core

final case class DatabaseException(message: String = "Database access error") extends RuntimeException(message)
