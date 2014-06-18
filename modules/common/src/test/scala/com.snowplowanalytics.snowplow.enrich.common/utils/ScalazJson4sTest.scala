/*
 * Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.enrich.common
package utils

// Java
import java.lang.{Byte => JByte}

// Scalaz
import scalaz._
import Scalaz._

// json4s
import org.json4s.scalaz.JsonScalaz._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._


// Specs2
import org.specs2.mutable.Specification

/**
 * Tests ScalazJson4sUtils
 */
class JsonExtractionTest extends Specification {

  val testJson = parse("""{
    "outer": "1",
    "inner": {
      "value": 2
      }
    }""")

  "Applying extractString" should {
    "successfully access an outer string field" in {

      val result = ScalazJson4sUtils.extractString(testJson, NonEmptyList("outer"))
      result must_== "1".success
    }
  }

  "Applying extractInt" should {

    "successfully access an inner string field" in {

      val result = ScalazJson4sUtils.extractInt(testJson, NonEmptyList("inner", "value"))
      result must_== 2.success
    }
  }

}
