package example

import xyz.driver.core.init

object Main extends init.SimpleHttpApp {

  lazy val fs = this.storage("data")

  override def applicationRoute = path("data" / Segment) { key =>
    post {
      entity(as[Array[Byte]]) { value =>
        complete(fs.uploadContent(key, value))
      }
    } ~ get {
      rejectEmptyResponse{
        complete(fs.content(key))
      }
    }
  }

}
