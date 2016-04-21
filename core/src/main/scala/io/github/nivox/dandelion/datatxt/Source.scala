package io.github.nivox.dandelion.datatxt

import akka.http.scaladsl.model.Uri

trait Source
object Source {
  case class Text(text: String) extends Source
  case class Url(url: Uri) extends Source
  case class Html(html: String) extends Source
  case class HtmlFrag(html: String) extends Source

  def name(s: Source): String = s match {
    case _: Text => "text"
    case _: Url => "url"
    case _: Html => "html"
    case _: HtmlFrag => "html_fragment"
  }

  def value(s: Source): String = s match {
    case Text(text) => text
    case Url(url) => url.toString
    case Html(html) => html
    case HtmlFrag(html) => html
  }
}

trait Lang{
  val lang: String
}
object Lang {
  object Auto extends Lang {
    val lang = "auto"
  }

  case class LangCode(lang: String) extends Lang
}