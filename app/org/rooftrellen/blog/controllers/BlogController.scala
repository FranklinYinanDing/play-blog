package org.rooftrellen.blog.controllers

import java.time.LocalDate
import javax.inject.Inject

import org.rooftrellen.blog.actions.{AdminAction, AuthorAction, MemberAction}
import org.rooftrellen.blog.models.{Blog, Member, Tag}
import org.rooftrellen.blog.services.{BlogRenderer, BlogStore}
import org.rooftrellen.blog.views.html._
import play.api.data.Form
import play.api.http.HeaderNames
import play.api.mvc._
import play.twirl.api.Html

class BlogController @Inject()(blogStore: BlogStore, blogRenderer: BlogRenderer) extends Controller {

  import BlogController._

  def viewHome: Action[AnyContent] = Action { implicit request =>
    val blogs = blogStore.selectRecentBlogs(Blog.RecentBlogs)
    Ok(homePage(blogs, blogStore.selectMembers().filterNot(_ == Member(Member.AdminName)).diff(blogs.filter(_.author.isDefined).map(blog => Member(blog.author.get)))))
  }

  def viewBlog(author: String, year: Int, month: Int, day: Int, path: String) = Action { implicit request =>
    blogStore.selectBlog(Blog(path, year, month, day, author)) match {
      case Some(blog) =>
        Ok(blogPage(blog, request.session.get(NameSession).filter(_ == author).map(_ => Seq(("Edit", Some(routes.BlogController.viewBlogDraft(author, year, month, day, path).url)))).getOrElse(Seq.empty)
          ++ Seq(("Previous", blogStore.selectPreviousBlog(blog) match {
          case Some(Blog(prevPath, _, _, _, Some(prevYear), Some(prevMonth), Some(prevDay), _, _, _)) => Some(routes.BlogController.viewBlog(author, prevYear, prevMonth, prevDay, prevPath).url)
          case _ => None
        })) ++ Seq(("Next", blogStore.selectNextBlog(blog) match {
          case Some(Blog(nextPath, _, _, _, Some(nextYear), Some(nextMonth), Some(nextDay), _, _, _)) => Some(routes.BlogController.viewBlog(author, nextYear, nextMonth, nextDay, nextPath).url)
          case _ => None
        }))))
      case _ => DefaultNotFound(request)
    }
  }

  def viewNewDraft(author: String): Action[AnyContent] = (MemberAction andThen AuthorAction) { implicit request =>
    Ok(draftPage(links = Seq(("Cancel", Some(routes.BlogController.viewBlogs(author).url)))))
  }

  def viewBlogDraft(author: String, year: Int, month: Int, day: Int, path: String): Action[AnyContent] = (MemberAction andThen AuthorAction) { implicit request =>
    blogStore.selectBlog(Blog(path, year, month, day, author)) match {
      case Some(blog) => Ok(draftPage(Some(Blog.DraftForm.fillAndValidate(blog)), Seq(("Cancel", Some(routes.BlogController.viewBlog(author, year, month, day, path).url)))))
      case _ => DefaultNotFound(request)
    }
  }

  def viewRegister: Action[AnyContent] = (MemberAction andThen AdminAction) { implicit request =>
    Ok(registerPage())
  }

  def viewBlogs(author: String) = Action { implicit request =>
    Ok(archivePage(s"Author: $author", author, blogStore.selectBlogs(author)))
  }

  def viewBlogsByYear(author: String, year: Int) = Action { implicit request =>
    Ok(archivePage(s"Author: $author on $year", author, blogStore.selectBlogsByYear(author, year)))
  }

  def viewBlogsByMonth(author: String, year: Int, month: Int) = Action { implicit request =>
    Ok(archivePage(s"Author: $author on $year/$month", author, blogStore.selectBlogsByMonth(author, year, month)))
  }

  def viewBlogsByDay(author: String, year: Int, month: Int, day: Int) = Action { implicit request =>
    Ok(archivePage(s"Author: $author on $year/$month/$day", author, blogStore.selectBlogsByDay(author, year, month, day)))
  }

  def viewLatest(author: String) = Action { implicit request =>
    Redirect(blogStore.selectLatestBlog(author) match {
      case Some(Blog(path, _, _, _, Some(createdYear), Some(createdMonth), Some(createdDay), _, _, _)) => routes.BlogController.viewBlog(author, createdYear, createdMonth, createdDay, path)
      case _ => routes.BlogController.viewBlogs(author)
    })
  }

  def viewTags(author: String) = Action { implicit request =>
    Ok(tagsPage(s"Author: $author", author, blogStore.selectTags(author)))
  }

  def viewTag(author: String, tag: String) = Action { implicit request =>
    Ok(archivePage(s"Author: $author on $tag", author, blogStore.selectBlogsByTag(author, tag)))
  }

  def createBlog(author: String): Action[AnyContent] = (MemberAction andThen AuthorAction) { implicit request =>
    val form = Blog.DraftForm.bindFromRequest()
    form.fold(_ => None, blog => {
      val now = LocalDate.now()
      blogStore.insertBlog(blog.copy(createdYear = Some(now.getYear), createdMonth = Some(now.getMonthValue), createdDay = Some(now.getDayOfMonth), author = Some(author)))
    }) match {
      case Some(Blog(path, _, _, _, Some(createdYear), Some(createdMonth), Some(createdDay), _, _, _)) => Redirect(routes.BlogController.viewBlog(author, createdYear, createdMonth, createdDay, path))
      case _ => BadRequest(draftPage(Some(form), Seq(("Cancel", Some(routes.BlogController.viewBlogs(author).url))), Some("Publish failed.")))
    }
  }

  def updateBlog(author: String, year: Int, month: Int, day: Int, oldPath: String): Action[AnyContent] = (MemberAction andThen AuthorAction) { implicit request =>
    val form = Blog.DraftForm.bindFromRequest()
    form.fold(_ => None, blog => blogStore.updateBlog(blog.copy(createdYear = Some(year), createdMonth = Some(month), createdDay = Some(day), author = Some(author)), oldPath)) match {
      case Some(Blog(path, _, _, _, _, _, _, _, _, _)) => Redirect(routes.BlogController.viewBlog(author, year, month, day, path))
      case _ => BadRequest(draftPage(Some(form), Seq(("Cancel", Some(routes.BlogController.viewBlog(author, year, month, day, oldPath).url))), Some("Publish failed.")))
    }
  }

  def login: Action[AnyContent] = Action { implicit request =>
    Member.LoginForm.bindFromRequest().fold(_ => None, member => blogStore.selectMember(member)) match {
      case Some(member) => DefaultRedirect(request).withSession((NameSession, member.name))
      case None => DefaultRedirect(request).withNewSession.flashing(warningFlash("Login failed."))
    }
  }

  def logout: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.BlogController.viewHome().url).withNewSession
  }

  def register: Action[AnyContent] = (MemberAction andThen AdminAction) { implicit request =>
    val form = Member.RegisterForm.bindFromRequest()
    form.fold(_ => None, member => blogStore.insertMember(member)) match {
      case Some(_) => DefaultRedirect(request)
      case None => BadRequest(registerPage(Some(form), Some("Register failed.")))
    }
  }

  private def homePage(blogs: Seq[Blog], members: Seq[Member])(implicit requestHeader: RequestHeader): Html = SkeletonPage("Home", HomeElement(blogs.map {
    case Blog(path, Some(title), _, _, Some(createdYear), Some(createdMonth), Some(createdDay), Some(author), _, _) => (path, blogRenderer.renderTitle(title), createdYear, createdMonth, createdDay, author)
  }, members))

  private def registerPage(memberForm: Option[Form[Member]] = None, warningMessage: Option[String] = None)(implicit requestHeader: RequestHeader): Html = SkeletonPage("Register", RegisterElement(memberForm), Seq.empty, warningMessage)

  private def draftPage(blogForm: Option[Form[Blog]] = None, links: Seq[(String, Option[String])] = Seq.empty, warningMessage: Option[String] = None)(implicit requestHeader: RequestHeader): Html = SkeletonPage("Draft", DraftElement(blogForm), links, warningMessage)

  private def blogPage(blog: Blog, links: Seq[(String, Option[String])] = Seq.empty)(implicit requestHeader: RequestHeader): Html = blog match {
    case Blog(_, Some(_), _, tags, Some(createdYear), Some(createdMonth), Some(createdDay), Some(author), _, _) => SkeletonPage("Blog", BlogElement(blogRenderer.render(blog), tags, createdYear, createdMonth, createdDay, author), links)
  }

  private def archivePage(heading: String, author: String, blogs: Seq[Blog])(implicit requestHeader: RequestHeader): Html = SkeletonPage("Archive", ArchiveElement(heading, author, blogs.map {
    case Blog(path, Some(title), _, _, Some(createdYear), Some(createdMonth), Some(createdDay), _, _, _) => (path, blogRenderer.renderTitle(title), createdYear, createdMonth, createdDay)
  }))

  private def tagsPage(heading: String, author: String, tags: Seq[Tag])(implicit requestHeader: RequestHeader): Html = SkeletonPage("Archive", TagsElement(heading, author, tags.map(_.name)))

}

object BlogController {

  val DefaultNotFound: RequestHeader => Result = { requestHeader =>
    Results.NotFound(views.html.defaultpages.notFound(requestHeader.method, requestHeader.uri))
  }

  val DefaultRedirect: RequestHeader => Result = { requestHeader =>
    Results.Redirect(requestHeader.headers.get(HeaderNames.REFERER).getOrElse(routes.BlogController.viewHome().url))
  }

  val DefaultUnauthorized: RequestHeader => Result = _ => Results.Unauthorized(views.html.defaultpages.unauthorized())

  val WarningFlash: String = "Warning"

  def warningFlash(warning: String): Flash = Flash(Map(WarningFlash -> warning))

  val NameSession: String = Security.username

}
