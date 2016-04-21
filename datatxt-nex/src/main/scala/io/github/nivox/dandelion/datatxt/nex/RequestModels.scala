package io.github.nivox.dandelion.datatxt.nex

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

trait ExtraInfo
object ExtraInfo {
  object Types extends ExtraInfo
  object Categories extends ExtraInfo
  object Abstract extends ExtraInfo
  object Image extends ExtraInfo
  object Lod extends ExtraInfo
  object AlternateLabels extends ExtraInfo
}

trait ExtraTypes
object ExtraTypes {
  object Phone extends ExtraTypes
  object Vat extends ExtraTypes
}
