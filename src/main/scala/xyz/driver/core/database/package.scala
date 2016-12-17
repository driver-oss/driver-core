import slick.dbio.{DBIOAction, NoStream}

package object database {
  type Schema = {
    def create: DBIOAction[Unit, NoStream, slick.dbio.Effect.Schema]
    def drop: DBIOAction[Unit, NoStream, slick.dbio.Effect.Schema]
  }
}
