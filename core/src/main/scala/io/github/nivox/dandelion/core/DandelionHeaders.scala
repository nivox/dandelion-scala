package io.github.nivox.dandelion.core

import java.text.SimpleDateFormat
import java.util.Date

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion, RawHeader}

import scala.util.Try

object DandelionHeaders {

  val UNITS_HEADER = "X-Dl-Units"
  val UNITS_LEFT_HEADER = "X-Dl-Units-Left"
  val UNITS_RESET_HEADER = "X-Dl-Units-Reset"

  def decodeDanelionHeaders(headers: collection.immutable.Seq[HttpHeader]): collection.immutable.Seq[HttpHeader] =
    headers.map {
      case h: RawHeader => h.name match {
        case UNITS_HEADER => DandelionUnits.parse(h.value).getOrElse(h)
        case UNITS_LEFT_HEADER => DandelionUnitsLeft.parse(h.value).getOrElse(h)
        case UNITS_RESET_HEADER => DandelionUnitsReset.parse(h.value).getOrElse(h)
        case _ => h
      }

      case h => h
    }

  final class DandelionUnits(val units: Int) extends ModeledCustomHeader[DandelionUnits] {
    override def companion: ModeledCustomHeaderCompanion[DandelionUnits] = DandelionUnits
    override def value(): String = units.toString
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }

  object DandelionUnits extends ModeledCustomHeaderCompanion[DandelionUnits] {
    override def name: String = UNITS_HEADER
    override def parse(value: String): Try[DandelionUnits] = Try {
      new DandelionUnits(Integer.parseInt(value))
    }
  }


  final class DandelionUnitsLeft(val unitsLeft: Int) extends ModeledCustomHeader[DandelionUnitsLeft] {
    override def companion: ModeledCustomHeaderCompanion[DandelionUnitsLeft] = DandelionUnitsLeft
    override def value(): String = unitsLeft.toString
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }

  object DandelionUnitsLeft extends ModeledCustomHeaderCompanion[DandelionUnitsLeft] {
    override def name: String = UNITS_LEFT_HEADER
    override def parse(value: String): Try[DandelionUnitsLeft] = Try {
      new DandelionUnitsLeft(Integer.parseInt(value))
    }
  }

  final class DandelionUnitsReset(val date: Date) extends ModeledCustomHeader[DandelionUnitsReset] {
    override def companion: ModeledCustomHeaderCompanion[DandelionUnitsReset] = DandelionUnitsReset
    override def value(): String = DandelionUnitsReset.resetDateFormat.format(date)
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }

  object DandelionUnitsReset extends ModeledCustomHeaderCompanion[DandelionUnitsReset] {
    val resetDateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss Z")

    override def name: String = UNITS_RESET_HEADER
    override def parse(value: String): Try[DandelionUnitsReset] = Try {
      new DandelionUnitsReset(resetDateFormat.parse(value))
    }
  }
}
