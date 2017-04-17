package org.rooftrellen.blog.actions

import org.rooftrellen.blog.controllers.BlogController
import play.api.mvc.Security.AuthenticatedBuilder

object MemberAction extends AuthenticatedBuilder(_.session.get(BlogController.NameSession), BlogController.DefaultUnauthorized)
