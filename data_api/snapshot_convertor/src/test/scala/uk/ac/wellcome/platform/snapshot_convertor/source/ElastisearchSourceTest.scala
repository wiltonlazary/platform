package uk.ac.wellcome.platform.snapshot_convertor.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.http.HttpClient
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.display.models.WorksUtil
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.IdentifiedWork
import uk.ac.wellcome.test.fixtures.Akka
import uk.ac.wellcome.test.utils.ExtendedPatience
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.search.SearchHit
import uk.ac.wellcome.utils.JsonUtil._

class ElastisearchSourceTest extends FunSpec
  with Matchers
  with ScalaFutures
  with ExtendedPatience
  with Akka with ElasticsearchFixtures with WorksUtil {

  it("outputs the entire content of the index") {
    val itemType = "work"
    withActorSystem { actorSystem =>
      withMaterializer(actorSystem) { actorMaterialiser =>
        withLocalElasticsearchIndex(itemType = itemType) { indexName =>
          implicit val materialiser = actorMaterialiser
          val works = (1 to 10).map { i =>
            workWith(canonicalId = s"$i-id", title = "woah! a wise wizard with walnuts!")
          }
          insertIntoElasticsearch(indexName, itemType, works: _*)

          val future = ElasticsearchSource(elasticClient, indexName, itemType)(actorSystem).runWith(Sink.seq)

          whenReady(future) { result =>
            result should contain theSameElementsAs works
          }
        }
      }
    }
  }

  it("filters non visible works") {
    val itemType = "work"
    withActorSystem { actorSystem =>
      withMaterializer(actorSystem) { actorMaterialiser =>
        withLocalElasticsearchIndex(itemType = itemType) { indexName =>
          implicit val materialiser = actorMaterialiser
          val visibleWorks = createWorks(count = 10, visible = true)
          val invisibleWorks = createWorks(count = 3, start = 11, visible = false)

          val works = visibleWorks ++ invisibleWorks
          insertIntoElasticsearch(indexName, itemType, works: _*)

          val future = ElasticsearchSource(elasticClient, indexName, itemType)(actorSystem).runWith(Sink.seq)

          whenReady(future) { result =>
            result should contain theSameElementsAs visibleWorks
          }
        }
      }
    }
  }

}

object ElasticsearchSource {
  def apply(elasticClient: HttpClient, indexName: String, itemType: String)(implicit actorSystem: ActorSystem): Source[IdentifiedWork, NotUsed] = {
    Source.fromPublisher(
    elasticClient.publisher(search(s"$indexName/$itemType").query(termQuery("visible", true)).scroll("10m"))).map{searchHit: SearchHit => fromJson[IdentifiedWork](searchHit.sourceAsString).get}
  }
}
