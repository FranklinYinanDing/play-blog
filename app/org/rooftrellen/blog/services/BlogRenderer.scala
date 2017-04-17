package org.rooftrellen.blog.services

import org.rooftrellen.blog.models.Blog

trait BlogRenderer {

  def render(blog: Blog): String

  def renderTitle(title: String): String

}
