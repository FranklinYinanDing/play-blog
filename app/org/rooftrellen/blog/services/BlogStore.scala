package org.rooftrellen.blog.services

import org.rooftrellen.blog.models.{Blog, Member, Tag}

trait BlogStore {

  def insertMember(member: Member): Option[Member]

  def selectMember(member: Member): Option[Member]

  def selectMembers(): Seq[Member]

  def insertBlog(blog: Blog): Option[Blog]

  def selectBlog(blog: Blog): Option[Blog]

  def selectLatestBlog(author: String): Option[Blog]

  def selectPreviousBlog(blog: Blog): Option[Blog]

  def selectNextBlog(blog: Blog): Option[Blog]

  def selectBlogs(author: String): Seq[Blog]

  def selectBlogsByYear(author: String, createdYear: Int): Seq[Blog]

  def selectBlogsByMonth(author: String, createdYear: Int, createdMonth: Int): Seq[Blog]

  def selectBlogsByDay(author: String, createdYear: Int, createdMonth: Int, createdDay: Int): Seq[Blog]

  def selectBlogsByTag(author: String, tag: String): Seq[Blog]

  def selectRecentBlogs(limit: Int): Seq[Blog]

  def updateBlog(blog: Blog, oldPath: String): Option[Blog]

  def selectTags(author: String): Seq[Tag]

}
