package io.github.nivox.dandelion.datatxt

import akka.http.scaladsl.model.Uri

trait DandelionSource
object DandelionSource {
  case class Text(text: String) extends DandelionSource
  case class Url(url: Uri) extends DandelionSource
  case class Html(html: String) extends DandelionSource
  case class HtmlFrag(html: String) extends DandelionSource

  def name(s: DandelionSource): String = s match {
    case _: Text => "text"
    case _: Url => "url"
    case _: Html => "html"
    case _: HtmlFrag => "html_fragment"
  }

  def value(s: DandelionSource): String = s match {
    case Text(text) => text
    case Url(url) => url.toString
    case Html(html) => html
    case HtmlFrag(html) => html
  }
}

trait DandelionLang{
  val lang: String
}
object DandelionLang {
  object Auto extends DandelionLang {
    val lang = "auto"
  }

  case class LangCode(lang: String) extends DandelionLang
}