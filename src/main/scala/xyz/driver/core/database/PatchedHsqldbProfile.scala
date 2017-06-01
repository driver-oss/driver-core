package xyz.driver.core.database

import slick.driver.HsqldbDriver
import slick.jdbc.JdbcType
import slick.ast.FieldSymbol
import slick.profile.RelationalProfile

trait PatchedHsqldbProfile extends HsqldbDriver {
  override def defaultSqlTypeName(tmd: JdbcType[_], sym: Option[FieldSymbol]): String = tmd.sqlType match {
    case java.sql.Types.VARCHAR =>
      val size = sym.flatMap(_.findColumnOption[RelationalProfile.ColumnOption.Length])
      size.fold("LONGVARCHAR")(l => if (l.varying) s"VARCHAR(${l.length})" else s"CHAR(${l.length})")
    case _ => super.defaultSqlTypeName(tmd, sym)
  }
}

object PatchedHsqldbProfile extends PatchedHsqldbProfile
