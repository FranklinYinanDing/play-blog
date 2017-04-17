package org.rooftrellen.blog.modules

import com.google.inject.AbstractModule
import org.pegdown.PegDownProcessor
import org.rooftrellen.blog.services.{BlogRenderer, BlogStore}
import org.rooftrellen.blog.services.impls.{BlogRendererImplWithPegDown, BlogStoreImplWithSqlite}

class BlogModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[BlogStore]).to(classOf[BlogStoreImplWithSqlite])
    bind(classOf[BlogRenderer]).to(classOf[BlogRendererImplWithPegDown])
    bind(classOf[PegDownProcessor]).toInstance(new PegDownProcessor())
  }

}
