package io.github.nivox.dandelion.datatxt

import argonaut.Json
import org.scalatest.FunSuite

class CommonCodecsSuite extends FunSuite {

  def jsonTimestamp(timestamp: String): Json = {
    import argonaut._, Argonaut._
    ("timestamp" := timestamp) ->: jEmptyObject
  }

  test("parsing timestamp with millis") {
    val json = jsonTimestamp("2016-05-18T08:39:26.211")

    assert(CommonCodecs.getTimestamp(json.hcursor).result.isRight)
  }

  test("parsing timestamp without millis") {
    val json = jsonTimestamp("2016-05-18T08:39:26")

    assert(CommonCodecs.getTimestamp(json.hcursor).result.isRight)
  }
}
