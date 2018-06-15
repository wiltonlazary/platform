package uk.ac.wellcome.platform.api.controllers

import javax.inject.Singleton

import com.google.inject.Inject
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.display.models.v2.DisplayWorkV2
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.platform.api.models.ApiConfig
import uk.ac.wellcome.platform.api.services.WorksService

@Singleton
class V2WorksController @Inject()(apiConfig: ApiConfig,
                                  elasticConfig: ElasticConfig,
                                  worksService: WorksService)
    extends WorksController(
      apiConfig = apiConfig,
      indexName = elasticConfig.indexV2name,
      worksService = worksService
    ) {
  implicit protected val swagger = ApiV2Swagger

  prefix(s"${apiConfig.pathPrefix}/${ApiVersions.v2.toString}") {
    setupResultListEndpoint(ApiVersions.v2, "/works", DisplayWorkV2.apply)
    setupSingleWorkEndpoint(ApiVersions.v2, "/works/:id", DisplayWorkV2.apply)
  }
}
