package org.rooftrellen.blog.actions

import org.rooftrellen.blog.controllers.BlogController
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc.{ActionFilter, Result}

import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.util.matching.Regex

object AuthorAction extends ActionFilter[({type R[A] = AuthenticatedRequest[A, String]})#R] {

  private final val AuthorPathRegex: Regex = "^\\/([^\\/]+)\\/(?:.+)$".r

  override protected def filter[A](request: AuthenticatedRequest[A, String]): Future[Option[Result]] = Future.successful {
    request.path match {
      case AuthorPathRegex(request.user) => None
      case _ => Some(BlogController.DefaultUnauthorized(request))
    }
  }

}
