/*
 * Copyright (c) 2022-present Snowplow Analytics Ltd.
 * All rights reserved.
 *
 * This software is made available by Snowplow Analytics, Ltd.,
 * under the terms of the Snowplow Limited Use License Agreement, Version 1.0
 * located at https://docs.snowplow.io/limited-use-license-1.0
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING OR DISTRIBUTING ANY PORTION
 * OF THE SOFTWARE, YOU AGREE TO THE TERMS OF SUCH LICENSE AGREEMENT.
 */
package com.snowplowanalytics.snowplow.enrich.kinesis

import java.util.UUID

import scala.concurrent.duration._

import cats.effect.IO

import cats.effect.testing.specs2.CatsEffect

import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import com.snowplowanalytics.snowplow.enrich.kinesis.enrichments._
import com.snowplowanalytics.snowplow.enrich.common.fs2.test.CollectorPayloadGen

class EnrichKinesisSpec extends Specification with AfterAll with CatsEffect {

  override protected val Timeout = 10.minutes

  def afterAll: Unit = Containers.localstack.stop()

  "enrich-kinesis" should {
    "be able to parse the minimal config" in {
      Containers.enrich(
        configPath = "config/config.kinesis.minimal.hocon",
        testName = "minimal",
        needsLocalstack = false,
        enrichments = Nil
      ).use { e =>
        IO(e.getLogs must contain("Running Enrich"))
      }
    }

    "emit the correct number of enriched events and bad rows" in {
      import utils._

      val testName = "count"
      val nbGood = 1000l
      val nbBad = 100l
      val uuid = UUID.randomUUID().toString

      val resources = for {
        _ <- Containers.enrich(
          configPath = "modules/kinesis/src/it/resources/enrich/enrich-localstack.hocon",
          testName = "count",
          needsLocalstack = true,
          enrichments = Nil,
          uuid = uuid
        )
        enrichPipe <- mkEnrichPipe(Containers.localstackMappedPort, uuid)
      } yield enrichPipe

      val input = CollectorPayloadGen.generate[IO](nbGood, nbBad)

      resources.use { enrich =>
        for {
          output <- enrich(input).compile.toList
          (good, bad) = parseOutput(output, testName)
        } yield {
          good.size.toLong must beEqualTo(nbGood)
          bad.size.toLong must beEqualTo(nbBad)
        }
      }
    }

    "run the enrichments and attach their context" in {
      import utils._

      val testName = "enrichments"
      val nbGood = 1000l
      val uuid = UUID.randomUUID().toString

      val enrichments = List(
        ApiRequest,
        Javascript,
        SqlQuery,
        Yauaa
      )

      val enrichmentsContexts = enrichments.map(_.outputSchema)

      val resources = for {
        _ <- Containers.mysqlServer
        _ <- Containers.httpServer
        _ <- Containers.enrich(
          configPath = "modules/kinesis/src/it/resources/enrich/enrich-localstack.hocon",
          testName = "enrichments",
          needsLocalstack = true,
          enrichments = enrichments,
          uuid = uuid
        )
        enrichPipe <- mkEnrichPipe(Containers.localstackMappedPort, uuid)
      } yield enrichPipe

      val input = CollectorPayloadGen.generate[IO](nbGood)

      resources.use { enrich =>
        for {
          output <- enrich(input).compile.toList
          (good, bad) = parseOutput(output, testName)
        } yield {
          good.size.toLong must beEqualTo(nbGood)
          good.map { enriched =>
            enriched.derived_contexts.data.map(_.schema) must containTheSameElementsAs(enrichmentsContexts)
          }
          bad.size.toLong must beEqualTo(0l)
        }
      }
    }

    "shutdown when it receives a SIGTERM" in {
      Containers.enrich(
        configPath = "modules/kinesis/src/it/resources/enrich/enrich-localstack.hocon",
        testName = "stop",
        needsLocalstack = true,
        enrichments = Nil,
        waitLogMessage = "enrich.metrics"
      ).use { enrich =>
        for {
          _ <- IO(println("stop - Sending signal"))
          _ <- IO(enrich.getDockerClient().killContainerCmd(enrich.getContainerId()).withSignal("TERM").exec())
          _ <- Containers.waitUntilStopped(enrich)
        } yield {
          enrich.isRunning() must beFalse
          enrich.getLogs() must contain("Enrich stopped")
        }
      }
    }
  }
}
