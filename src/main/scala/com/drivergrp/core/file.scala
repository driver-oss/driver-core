package com.drivergrp.core

import com.drivergrp.core.time.Time

object file {

  final case class Document(
      id: Id[Document],
      name: Name[Document],
      additionDate: Time
  )
}
