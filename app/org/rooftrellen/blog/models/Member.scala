package org.rooftrellen.blog.models

import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.Constraints

import scala.util.matching.Regex

case class Member(name: String, password: Option[String] = None)

object Member {

  val NameField = "name"

  val PasswordField = "password"

  val PasswordConfirmField = "password-confirm"

  val NameMinLength = 1

  val NameMaxLength = 20

  val NameRegex: Regex = "^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$".r

  val NameMapping: Mapping[String] = text(NameMinLength, NameMaxLength).verifying(Constraints.pattern(NameRegex))

  val PasswordMinLength = 8

  val PasswordMaxLength = 64

  val PasswordRegex: Regex = "^[!-~]+$".r

  val PasswordMapping: Mapping[Option[String]] = optional(text(PasswordMinLength, PasswordMaxLength).verifying(Constraints.pattern(PasswordRegex)))

  val LoginForm = Form(
    mapping(
      NameField -> NameMapping,
      PasswordField -> PasswordMapping
    )(Member.apply)(
      member => Some(member.name, None)
    )
  )

  val RegisterForm = Form(
    mapping(
      NameField -> NameMapping,
      PasswordField -> PasswordMapping,
      PasswordConfirmField -> PasswordMapping
    )(
      (name, password, passwordConfirm) => if (password == passwordConfirm) Member(name, password) else Member(name, None)
    )(
      member => Some((member.name, None, None))
    )
  )

  val AdminName = "admin"

  val AdminPasswordConfig = "admin.password"

}
