package org.rooftrellen.blog.services.impls

import javax.inject.{Inject, Singleton}

import org.pegdown.PegDownProcessor
import org.rooftrellen.blog.models.Blog
import org.rooftrellen.blog.services.BlogRenderer

import scala.util.matching.Regex

@Singleton
class BlogRendererImplWithPegDown @Inject()(pegDownProcessor: PegDownProcessor) extends BlogRenderer {

  private final val TitleRegex: Regex = "^<h1>(.+)</h1>.*$".r

  override def render(blog: Blog): String = blog match {
    case Blog(_, Some(title), content, _, _, _, _, _, _, _) => pegDownProcessor.markdownToHtml(s"$title${content.map(c => s"  \n$c").getOrElse("")}")
  }

  override def renderTitle(title: String): String = pegDownProcessor.markdownToHtml(title) match {
    case TitleRegex(t) => s"<p>$t</p>".replaceAll("""<a href="[^"]*">""", "").replaceAll("""</a>""", "")
  }

}
