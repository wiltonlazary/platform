package uk.ac.wellcome.platform.snapshot_convertor.flow

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.display.models.DisplayWork
import uk.ac.wellcome.exceptions.GracefulFailureException
import uk.ac.wellcome.models.{
  IdentifiedWork,
  IdentifierSchemes,
  SourceIdentifier,
  WorksIncludes
}
import uk.ac.wellcome.test.fixtures.Akka
import uk.ac.wellcome.test.utils.ExtendedPatience
import uk.ac.wellcome.utils.JsonUtil._

import scala.concurrent.{ExecutionContextExecutor, Future}

class ElasticsearchHitToDisplayWorkFlowTest
    extends FunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with ExtendedPatience {

  val includes = WorksIncludes(
    identifiers = true,
    thumbnail = true,
    items = true
  )

  it("creates a DisplayWork from a single hit") {
    withActorSystem { actorSystem =>
      implicit val executionContext: ExecutionContextExecutor =
        actorSystem.dispatcher
      implicit val system: ActorSystem = actorSystem
      implicit val materializer: Materializer =
        ActorMaterializer()(actorSystem)

      val flow = ElasticsearchHitToDisplayWorkFlow()

      val work = IdentifiedWork(
        canonicalId = "t83tggem",
        title = Some("Tired of troubling tests"),
        sourceIdentifier = SourceIdentifier(
          identifierScheme = IdentifierSchemes.miroImageNumber,
          ontologyType = "work",
          value = "T0083000"
        ),
        version = 1
      )

      val elasticsearchHitJson = s"""{
        "_index": "zagbdjgf",
        "_type": "work",
        "_id": "${work.canonicalId}",
        "_score": 1,
        "_source": ${toJson(work).get}
      }"""

      val futureDisplayWork: Future[DisplayWork] = Source
        .single(elasticsearchHitJson)
        .via(flow)
        .runWith(Sink.head)

      whenReady(futureDisplayWork) { displayWork =>
        displayWork shouldBe DisplayWork(work, includes = includes)
      }
    }
  }

  it("returns a failed Future if it gets invalid JSON") {
    withActorSystem { actorSystem =>
      implicit val executionContext: ExecutionContextExecutor =
        actorSystem.dispatcher
      implicit val system: ActorSystem = actorSystem
      implicit val materializer: Materializer =
        ActorMaterializer()(actorSystem)

      val flow = ElasticsearchHitToDisplayWorkFlow()

      val elasticsearchHitJson = s"""MARC?XML RAARGH NOTJSON"""

      val future = Source
        .single(elasticsearchHitJson)
        .via(flow)
        .runWith(Sink.head)

      whenReady(future.failed) { result =>
        result shouldBe a[GracefulFailureException]
      }
    }
  }

  it("returns a failed Future if it gets valid JSON but _source isn't a Work") {
    withActorSystem { actorSystem =>
      implicit val executionContext: ExecutionContextExecutor =
        actorSystem.dispatcher
      implicit val system: ActorSystem = actorSystem
      implicit val materializer: Materializer =
        ActorMaterializer()(actorSystem)

      val flow = ElasticsearchHitToDisplayWorkFlow()

      val elasticsearchHitJson = s"""{
        "_index": "rd8a35zw",
        "_type": "work",
        "_id": "ndpwrqer",
        "_score": 1,
        "_source": {"foo": "bar", "baz": "bat"}
      }"""

      val future = Source
        .single(elasticsearchHitJson)
        .via(flow)
        .runWith(Sink.head)

      whenReady(future.failed) { result =>
        result shouldBe a[GracefulFailureException]
      }
    }
  }
}