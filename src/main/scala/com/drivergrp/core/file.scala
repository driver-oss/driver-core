package com.drivergrp.core

import akka.http.scaladsl.model.Uri
import com.drivergrp.core.time.Time

object file {

  final case class File(id: Id[File])

  final case class FileLink(
      id: Id[File],
      name: Name[File],
      location: Uri,
      additionDate: Time
  )

  trait FileService {

    def getFileLink(id: Id[File]): FileLink

    def getFile(fileLink: FileLink): File
  }
}
