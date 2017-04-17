package org.rooftrellen.blog.actions

import org.rooftrellen.blog.controllers.BlogController
import org.rooftrellen.blog.models.Member
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc.{ActionFilter, Result}

import scala.concurrent.Future
import scala.language.reflectiveCalls

object AdminAction extends ActionFilter[({type R[A] = AuthenticatedRequest[A, String]})#R] {

  override protected def filter[A](request: AuthenticatedRequest[A, String]): Future[Option[Result]] = Future.successful {
    if (Member.AdminName == request.user) None else Some(BlogController.DefaultUnauthorized(request))
  }

}
