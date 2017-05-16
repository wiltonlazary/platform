package uk.ac.wellcome.transformer.modules

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.dynamodbv2.streamsadapter.AmazonDynamoDBStreamsAdapterClient
import com.amazonaws.services.kinesis.AmazonKinesis
import com.google.inject.{Provides, Singleton}
import com.twitter.inject.TwitterModule
import uk.ac.wellcome.models.aws.AWSConfig

object AmazonKinesisModule extends TwitterModule {

  @Provides
  @Singleton
  def providesAmazonKinesis(awsConfig: AWSConfig): AmazonKinesis = {
    val adapter = new AmazonDynamoDBStreamsAdapterClient(
      new DefaultAWSCredentialsProviderChain()
    )
    adapter.setRegion(RegionUtils.getRegion(awsConfig.region))
    adapter
  }
}
