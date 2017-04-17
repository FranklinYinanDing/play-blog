package org.rooftrellen.blog.models

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints

import scala.util.matching.Regex

case class Blog(path: String, title: Option[String] = None, content: Option[String] = None, tags: Seq[Tag] = Seq.empty, createdYear: Option[Int] = None, createdMonth: Option[Int] = None, createdDay: Option[Int] = None, author: Option[String] = None, id: Option[Int] = None, authorId: Option[Int] = None)

object Blog {

  val RecentBlogs = 10

  val PathField = "path"

  val TitleField = "title"

  val ContentField = "content"

  val TagsField = "tags"

  val PathMinLength = 1

  val PathMaxLength = 255

  val PathRegex: Regex = "^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$".r

  val TitleMinLength = 3

  val TitleMaxLength = 255

  val TitleRegex: Regex = "^# .*\\S$".r

  val TagsMaxLength = 255

  val TagsRegex: Regex = "^[A-Za-z0-9]+(-[A-Za-z0-9]+)*(,[A-Za-z0-9]+(-[A-Za-z0-9]+)*)*$".r

  val DraftForm = Form(
    mapping(
      PathField -> text(PathMinLength, PathMaxLength).verifying(Constraints.pattern(PathRegex)),
      TitleField -> optional(text(TitleMinLength, TitleMaxLength).verifying(Constraints.pattern(TitleRegex))),
      ContentField -> optional(text),
      TagsField -> optional(text(maxLength = TagsMaxLength).verifying(Constraints.pattern(TagsRegex)))
    )(
      (path, title, content, tags) => Blog(path, title, content, tags.map(_.split(",").toSeq.distinct.filterNot(_.isEmpty).map(Tag)).getOrElse(Seq.empty))
    )(
      blog => Some((blog.path, blog.title, blog.content, Some(blog.tags.map(_.name).mkString(","))))
    )
  )

  def apply(path: String, createdYear: Int, createdMonth: Int, createdDay: Int, author: String): Blog = Blog(path, None, None, Seq.empty, Some(createdYear), Some(createdMonth), Some(createdDay), Some(author))

  def apply(path: String, createdYear: Int, createdMonth: Int, createdDay: Int): Blog = Blog(path, None, None, Seq.empty, Some(createdYear), Some(createdMonth), Some(createdDay))

}
