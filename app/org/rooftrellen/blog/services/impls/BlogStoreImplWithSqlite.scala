package org.rooftrellen.blog.services.impls

import java.sql.Connection
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.{Inject, Singleton}
import javax.xml.bind.DatatypeConverter

import anorm.SqlParser._
import anorm._
import org.rooftrellen.blog.models.{Blog, Member, Tag}
import org.rooftrellen.blog.services.BlogStore
import play.api.{Configuration, Environment, Mode}
import play.api.db.Database

import scala.language.implicitConversions
import scala.util.Random

@Singleton
class BlogStoreImplWithSqlite @Inject()(val configuration: Configuration, val environment: Environment, val database: Database) extends BlogStore with MemberTable with BlogTable with TagTable

private[rooftrellen] trait MemberTable extends BlogStore {

  import MemberTable._

  def configuration: Configuration

  def environment: Environment

  def database: Database

  override def insertMember(member: Member): Option[Member] = {
    member.name match {
      case Member.AdminName => None
      case name => member.password.flatMap { password =>
        database.withConnection { implicit connection =>
          val (passwordSalt, passwordHash) = salt(password)
          InsertSql.on(Columns(1) -> name, Columns(2) -> passwordSalt, Columns(3) -> passwordHash).executeUpdate() match {
            case 1 => Some(member.copy(password = None))
            case _ => None
          }
        }
      }
    }
  }

  override def selectMember(member: Member): Option[Member] = {
    member.name match {
      case Member.AdminName => member.password.filter(password => configuration.getString(Member.AdminPasswordConfig) match {
        case Some("changeme") => environment.mode != Mode.Prod && "changeme" == password
        case Some(p) => p == password
        case None => false
      }).map(_ => member.copy(password = None))
      case name => member.password.flatMap { password =>
        database.withConnection { implicit connection =>
          SelectSql.on(Columns(1) -> name).as(SelectParser.singleOpt).filter { passwordTuple =>
            hash(password, DatatypeConverter.parseHexBinary(passwordTuple._1)).sameElements(DatatypeConverter.parseHexBinary(passwordTuple._2))
          }.map(_ => member.copy(password = None))
        }
      }
    }
  }

  override def selectMembers(): Seq[Member] = database.withConnection { implicit connection =>
    SelectMultipleSql.as(SelectMultipleParser.*)
  }

  private def salt(password: String): (String, String) = {
    val passwordSalt = Array.ofDim[Byte](SaltArrayLength)
    Random.nextBytes(passwordSalt)
    (DatatypeConverter.printHexBinary(passwordSalt), DatatypeConverter.printHexBinary(hash(password, passwordSalt)))
  }

  private def hash(password: String, passwordSalt: Array[Byte]): Array[Byte] =
    SecretKeyFactory.getInstance(KeyAlgorithm).generateSecret(new PBEKeySpec(password.toCharArray, passwordSalt, IterationCount, KeyLength)).getEncoded

}

private[rooftrellen] object MemberTable {

  final val Name = "Member"

  final val Columns = Array("ID", "Name", "PasswordSalt", "PasswordHash")

  final val InsertSql = SQL(s"INSERT INTO $Name (${Columns(1)},${Columns(2)},${Columns(3)}) VALUES ({${Columns(1)}},{${Columns(2)}},{${Columns(3)}})")

  final val SelectSql = SQL(s"SELECT ${Columns(2)},${Columns(3)} FROM $Name WHERE ${Columns(1)}={${Columns(1)}}")

  final val SelectParser: RowParser[(String, String)] = str(Columns(2)) ~ str(Columns(3)) map {
    case passwordSalt ~ passwordHash => (passwordSalt, passwordHash)
  }

  final val SelectMultipleSql = SQL(s"SELECT ${Columns(1)} FROM $Name")

  final val SelectMultipleParser: RowParser[Member] = str(Columns(1)) map (Member(_))

  final val KeyAlgorithm = "PBKDF2WithHmacSHA256"

  final val IterationCount = 65536

  final val KeyLength = 256

  final val SaltArrayLength = 32

}

private[rooftrellen] trait BlogTable extends BlogStore {

  import BlogTable._

  def database: Database

  override def insertBlog(blog: Blog): Option[Blog] = {
    blog match {
      case Blog(path, Some(title), content, tags, Some(createdYear), Some(createdMonth), Some(createdDay), Some(author), _, _) => database.withConnection { implicit connection =>
        InsertSql.on(Columns(1) -> path, Columns(2) -> title, Columns(3) -> content, Columns(4) -> createdYear, Columns(5) -> createdMonth, Columns(6) -> createdDay, MemberTable.Columns(1) -> author).executeUpdate() match {
          case 1 =>
            insertTags(tags, path, createdYear, createdMonth, createdDay, author)
            Some(blog)
          case _ => None
        }
      }
      case _ => None
    }
  }

  override def selectBlog(blog: Blog): Option[Blog] = {
    blog match {
      case Blog(path, _, _, _, Some(createdYear), Some(createdMonth), Some(createdDay), Some(author), _, _) => database.withConnection { implicit connection =>
        SelectSql.on(Columns(1) -> path, Columns(4) -> createdYear, Columns(5) -> createdMonth, Columns(6) -> createdDay, MemberTable.Columns(1) -> author).as(SelectParser.singleOpt) match {
          case Some((title, content, id, authorId)) =>
            Some(blog.copy(title = Some(title), content = content, tags = TagTable.SelectSql.on(BlogTagTable.Columns(1) -> id).as(str(TagTable.Columns(1)).*).map(Tag), id = Some(id), authorId = Some(authorId)))
          case None => None
        }
      }
      case _ => None
    }
  }

  override def selectPreviousBlog(blog: Blog): Option[Blog] = {
    blog match {
      case Blog(_, _, _, _, _, _, _, _, Some(id), Some(authorId)) => database.withConnection { implicit connection =>
        SelectPreviousSql.on(Columns(0) -> id, Columns(7) -> authorId).as(SelectAdjacentParser.singleOpt) match {
          case Some((path, createdYear, createdMonth, createdDay)) => Some(Blog(path, createdYear, createdMonth, createdDay))
          case None => None
        }
      }
      case _ => None
    }
  }

  override def selectNextBlog(blog: Blog): Option[Blog] = {
    blog match {
      case Blog(_, _, _, _, _, _, _, _, Some(id), Some(authorId)) => database.withConnection { implicit connection =>
        SelectNextSql.on(Columns(0) -> id, Columns(7) -> authorId).as(SelectAdjacentParser.singleOpt) match {
          case Some((path, createdYear, createdMonth, createdDay)) => Some(Blog(path, createdYear, createdMonth, createdDay))
          case None => None
        }
      }
      case _ => None
    }
  }

  override def selectBlogs(author: String): Seq[Blog] = database.withConnection { implicit connection =>
    SelectAllSql.on(MemberTable.Columns(1) -> author).as(SelectMultipleParser.*)
  }

  override def selectBlogsByYear(author: String, createdYear: Int): Seq[Blog] = database.withConnection { implicit connection =>
    SelectYearSql.on(MemberTable.Columns(1) -> author, Columns(4) -> createdYear).as(SelectMultipleParser.*)
  }

  override def selectBlogsByMonth(author: String, createdYear: Int, createdMonth: Int): Seq[Blog] = database.withConnection { implicit connection =>
    SelectMonthSql.on(MemberTable.Columns(1) -> author, Columns(4) -> createdYear, Columns(5) -> createdMonth).as(SelectMultipleParser.*)
  }

  override def selectBlogsByDay(author: String, createdYear: Int, createdMonth: Int, createdDay: Int): Seq[Blog] = database.withConnection { implicit connection =>
    SelectDaySql.on(MemberTable.Columns(1) -> author, Columns(4) -> createdYear, Columns(5) -> createdMonth, Columns(6) -> createdDay).as(SelectMultipleParser.*)
  }

  override def selectBlogsByTag(author: String, tag: String): Seq[Blog] = database.withConnection { implicit connection =>
    SelectTagSql.on(s"${MemberTable.Name}.${MemberTable.Columns(1)}" -> author, s"${TagTable.Name}.${TagTable.Columns(1)}" -> tag).as(SelectMultipleParser.*)
  }

  override def selectRecentBlogs(limit: Int): Seq[Blog] = database.withConnection { implicit connection =>
    SelectRecentSql.on(Limit -> limit).as(SelectRecentParser.*)
  }

  override def selectLatestBlog(author: String): Option[Blog] = database.withConnection { implicit connection =>
    SelectLatestSql.on(MemberTable.Columns(1) -> author).as(SelectLatestParser.singleOpt)
  }

  override def updateBlog(blog: Blog, oldPath: String): Option[Blog] = {
    blog match {
      case Blog(path, Some(title), content, tags, Some(createdYear), Some(createdMonth), Some(createdDay), Some(author), _, _) => database.withConnection { implicit connection =>
        UpdateSql.on(Columns(1) -> path, Columns(2) -> title, Columns(3) -> content, OldPath -> oldPath, Columns(4) -> createdYear, Columns(5) -> createdMonth, Columns(6) -> createdDay, MemberTable.Columns(1) -> author).executeUpdate() match {
          case 1 =>
            BlogTagTable.DeleteSql.on(Columns(1) -> path, Columns(4) -> createdYear, Columns(5) -> createdMonth, Columns(6) -> createdDay, MemberTable.Columns(1) -> author).executeUpdate()
            insertTags(tags, path, createdYear, createdMonth, createdDay, author)
            Some(blog)
          case _ => None
        }
      }
      case _ => None
    }
  }

  private def insertTags(tags: Seq[Tag], path: String, createdYear: Int, createdMonth: Int, createdDay: Int, author: String)(implicit connection: Connection) = {
    tags.headOption.map(_ => {
      TagTable.InsertSql.on(TagTable.TagNames -> SeqParameter(tags.map(_.name), ",", "(", ")")).executeUpdate()
      BlogTagTable.InsertSql.on(BlogTable.Columns(1) -> path, BlogTable.Columns(4) -> createdYear, BlogTable.Columns(5) -> createdMonth, BlogTable.Columns(6) -> createdDay, s"${MemberTable.Name}.${MemberTable.Columns(1)}" -> author, s"${TagTable.Name}.${TagTable.Columns(1)}" -> SeqParameter(tags.map(_.name))).executeUpdate()
    })
  }

}

private[rooftrellen] object BlogTable {

  final val Name = "Blog"

  final val Columns = Array("ID", "Path", "Title", "Content", "CreatedYear", "CreatedMonth", "CreatedDay", "AuthorID")

  final val OldPath = "OldPath"

  final val Limit = "Limit"

  final val InsertSql = SQL(s"INSERT INTO $Name (${Columns(1)},${Columns(2)},${Columns(3)},${Columns(4)},${Columns(5)},${Columns(6)},${Columns(7)}) SELECT {${Columns(1)}},{${Columns(2)}},{${Columns(3)}},{${Columns(4)}},{${Columns(5)}},{${Columns(6)}},${MemberTable.Columns(0)} FROM ${MemberTable.Name} WHERE ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}}")

  final val SelectSql = SQL(s"SELECT ${Columns(2)},${Columns(3)},$Name.${Columns(0)},$Name.${Columns(7)} FROM $Name JOIN ${MemberTable.Name} ON $Name.${Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${Columns(1)}={${Columns(1)}} AND ${Columns(4)}={${Columns(4)}} AND ${Columns(5)}={${Columns(5)}} AND ${Columns(6)}={${Columns(6)}} AND ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}}")

  final val SelectParser: RowParser[(String, Option[String], Int, Int)] = str(Columns(2)) ~ str(Columns(3)).? ~ int(s"$Name.${Columns(0)}") ~ int(s"$Name.${Columns(7)}") map flatten

  final val SelectPreviousSql = SQL(s"SELECT ${Columns(1)},${Columns(4)},${Columns(5)},${Columns(6)} FROM $Name WHERE ${Columns(0)}<{${Columns(0)}} AND ${Columns(7)}={${Columns(7)}} ORDER BY ${Columns(0)} DESC LIMIT 1")

  final val SelectNextSql = SQL(s"SELECT ${Columns(1)},${Columns(4)},${Columns(5)},${Columns(6)} FROM $Name WHERE ${Columns(0)}>{${Columns(0)}} AND ${Columns(7)}={${Columns(7)}} ORDER BY ${Columns(0)} LIMIT 1")

  final val SelectAdjacentParser: RowParser[(String, Int, Int, Int)] = str(Columns(1)) ~ int(Columns(4)) ~ int(Columns(5)) ~ int(Columns(6)) map flatten

  final val SelectAllSql = SQL(s"SELECT ${Columns(1)},${Columns(2)},${Columns(4)},${Columns(5)},${Columns(6)} FROM $Name JOIN ${MemberTable.Name} ON $Name.${Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}} ORDER BY $Name.${Columns(0)} DESC")

  final val SelectYearSql = SQL(s"SELECT ${Columns(1)},${Columns(2)},{${Columns(4)}},${Columns(5)},${Columns(6)} FROM $Name JOIN ${MemberTable.Name} ON $Name.${Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}} AND ${Columns(4)}={${Columns(4)}} ORDER BY $Name.${Columns(0)} DESC")

  final val SelectMonthSql = SQL(s"SELECT ${Columns(1)},${Columns(2)},{${Columns(4)}},{${Columns(5)}},${Columns(6)} FROM $Name JOIN ${MemberTable.Name} ON $Name.${Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}} AND ${Columns(4)}={${Columns(4)}} AND ${Columns(5)}={${Columns(5)}} ORDER BY $Name.${Columns(0)} DESC")

  final val SelectDaySql = SQL(s"SELECT ${Columns(1)},${Columns(2)},{${Columns(4)}},{${Columns(5)}},{${Columns(6)}} FROM $Name JOIN ${MemberTable.Name} ON $Name.${Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}} AND ${Columns(4)}={${Columns(4)}} AND ${Columns(5)}={${Columns(5)}} AND ${Columns(6)}={${Columns(6)}} ORDER BY $Name.${Columns(0)} DESC")

  final val SelectTagSql = SQL(s"SELECT ${Columns(1)},${Columns(2)},${Columns(4)},${Columns(5)},${Columns(6)} FROM $Name JOIN ${MemberTable.Name} ON $Name.${Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${MemberTable.Name}.${MemberTable.Columns(1)}={${MemberTable.Name}.${MemberTable.Columns(1)}} JOIN ${BlogTagTable.Name} ON $Name.${Columns(0)}=${BlogTagTable.Name}.${BlogTagTable.Columns(1)} JOIN ${TagTable.Name} ON ${BlogTagTable.Name}.${BlogTagTable.Columns(2)}=${TagTable.Name}.${TagTable.Columns(0)} AND ${TagTable.Name}.${TagTable.Columns(1)}={${TagTable.Name}.${TagTable.Columns(1)}} ORDER BY $Name.${Columns(0)} DESC")

  final val SelectRecentSql = SQL(s"SELECT ${Columns(1)},${Columns(2)},${Columns(4)},${Columns(5)},${Columns(6)},${MemberTable.Columns(1)} FROM $Name JOIN ${MemberTable.Name} ON $Name.${Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} ORDER BY $Name.${Columns(0)} DESC LIMIT {$Limit}")

  final val SelectMultipleParser: RowParser[Blog] = str(Columns(1)) ~ str(Columns(2)) ~ int(3) ~ int(4) ~ int(5) map {
    case path ~ title ~ createdYear ~ createdMonth ~ createdDay => Blog(path = path, title = Some(title), createdYear = Some(createdYear), createdMonth = Some(createdMonth), createdDay = Some(createdDay))
  }

  final val SelectRecentParser: RowParser[Blog] = str(Columns(1)) ~ str(Columns(2)) ~ int(Columns(4)) ~ int(Columns(5)) ~ int(Columns(6)) ~ str(MemberTable.Columns(1)) map {
    case path ~ title ~ createdYear ~ createdMonth ~ createdDay ~ author => Blog(path = path, title = Some(title), createdYear = Some(createdYear), createdMonth = Some(createdMonth), createdDay = Some(createdDay), author = Some(author))
  }

  final val SelectLatestSql = SQL(s"SELECT ${Columns(1)},${Columns(4)},${Columns(5)},${Columns(6)} FROM $Name JOIN ${MemberTable.Name} ON $Name.${Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}} ORDER BY $Name.${Columns(0)} DESC LIMIT 1")

  final val SelectLatestParser: RowParser[Blog] = str(Columns(1)) ~ int(Columns(4)) ~ int(Columns(5)) ~ int(Columns(6)) map {
    case path ~ createdYear ~ createdMonth ~ createdDay => Blog(path = path, createdYear = Some(createdYear), createdMonth = Some(createdMonth), createdDay = Some(createdDay))
  }

  final val UpdateSql = SQL(s"UPDATE $Name SET ${Columns(1)}={${Columns(1)}},${Columns(2)}={${Columns(2)}},${Columns(3)}={${Columns(3)}} WHERE ${Columns(1)}={$OldPath} AND ${Columns(4)}={${Columns(4)}} AND ${Columns(5)}={${Columns(5)}} AND ${Columns(6)}={${Columns(6)}} AND ${Columns(7)}=(SELECT ${MemberTable.Columns(0)} FROM ${MemberTable.Name} WHERE ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}})")

}

private[rooftrellen] trait TagTable extends BlogStore {

  import TagTable._

  def database: Database

  override def selectTags(author: String): Seq[Tag] = database.withConnection { implicit connection =>
    SelectMultipleSql.on(s"${MemberTable.Name}.${MemberTable.Columns(1)}" -> author).as(str(s"$Name.${Columns(1)}").*).map(Tag)
  }

}

private[rooftrellen] object TagTable {

  final val Name = "Tag"

  final val Columns = Array("ID", "Name")

  final val TagNames = "TagNames"

  final val InsertSql = SQL(s"INSERT INTO $Name (${Columns(1)}) VALUES {$TagNames}")

  final val SelectSql = SQL(s"SELECT ${Columns(1)} FROM $Name JOIN ${BlogTagTable.Name} ON $Name.${Columns(0)}=${BlogTagTable.Columns(2)} AND ${BlogTagTable.Columns(1)}={${BlogTagTable.Columns(1)}} ORDER BY ${Columns(1)}")

  final val SelectMultipleSql = SQL(s"SELECT DISTINCT $Name.${Columns(1)} FROM ${BlogTable.Name} JOIN ${MemberTable.Name} ON ${BlogTable.Name}.${BlogTable.Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${MemberTable.Name}.${MemberTable.Columns(1)}={${MemberTable.Name}.${MemberTable.Columns(1)}} JOIN ${BlogTagTable.Name} ON ${BlogTable.Name}.${BlogTable.Columns(0)}=${BlogTagTable.Name}.${BlogTagTable.Columns(1)} JOIN $Name ON ${BlogTagTable.Name}.${BlogTagTable.Columns(2)}=$Name.${Columns(0)} ORDER BY $Name.${Columns(1)}")

}

private[rooftrellen] object BlogTagTable {

  final val Name = "BlogTag"

  final val Columns = Array("ID", "BlogID", "TagID")

  final val InsertSql = SQL(s"INSERT INTO $Name (${Columns(1)},${Columns(2)}) SELECT ${BlogTable.Name}.${BlogTable.Columns(0)},${TagTable.Name}.${TagTable.Columns(0)} FROM ${BlogTable.Name} CROSS JOIN ${TagTable.Name} WHERE ${BlogTable.Columns(1)}={${BlogTable.Columns(1)}} AND ${BlogTable.Columns(4)}={${BlogTable.Columns(4)}} AND ${BlogTable.Columns(5)}={${BlogTable.Columns(5)}} AND ${BlogTable.Columns(6)}={${BlogTable.Columns(6)}} AND ${BlogTable.Columns(7)}=(SELECT ${MemberTable.Columns(0)} FROM ${MemberTable.Name} WHERE ${MemberTable.Columns(1)}={${MemberTable.Name}.${MemberTable.Columns(1)}}) AND ${TagTable.Columns(1)} IN ({${TagTable.Name}.${TagTable.Columns(1)}})")

  final val DeleteSql = SQL(s"DELETE FROM $Name WHERE ${Columns(1)}=(SELECT ${BlogTable.Name}.${BlogTable.Columns(0)} FROM ${BlogTable.Name} JOIN ${MemberTable.Name} ON ${BlogTable.Name}.${BlogTable.Columns(7)}=${MemberTable.Name}.${MemberTable.Columns(0)} AND ${BlogTable.Columns(1)}={${BlogTable.Columns(1)}} AND ${BlogTable.Columns(4)}={${BlogTable.Columns(4)}} AND ${BlogTable.Columns(5)}={${BlogTable.Columns(5)}} AND ${BlogTable.Columns(6)}={${BlogTable.Columns(6)}} AND ${MemberTable.Columns(1)}={${MemberTable.Columns(1)}})")

}
