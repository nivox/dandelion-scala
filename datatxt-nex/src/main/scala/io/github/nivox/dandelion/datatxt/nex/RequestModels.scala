package io.github.nivox.dandelion.datatxt.nex

sealed abstract class ExtraInfo(val repr: String)
object ExtraInfo {
  case object Types extends ExtraInfo("types")
  case object Categories extends ExtraInfo("categories")
  case object Abstract extends ExtraInfo("abstract")
  case object Image extends ExtraInfo("image")
  case object Lod extends ExtraInfo("lod")
  case object AlternateLabels extends ExtraInfo("alternate_labels")
}

sealed abstract class ExtraTypes(val repr: String)
object ExtraTypes {
  case object Phone extends ExtraTypes("phone")
  case object Vat extends ExtraTypes("vat")
}
