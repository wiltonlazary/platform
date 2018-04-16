# -*- encoding: utf-8 -*-
"""
Publish a new Snapshot Schedule to SNS.
"""

import datetime as dt
import os

import attr
import boto3

from wellcome_aws_utils.sns_utils import publish_sns_message


# This class is duplicated in the elasticdump app
# Changes here will need to be reflected there.
@attr.s
class SnapshotRequest(object):
    time = attr.ib()
    private_bucket_name = attr.ib()
    public_bucket_name = attr.ib()
    public_object_key = attr.ib()
    es_index = attr.ib()


def main(event=None, _ctxt=None, sns_client=None):
    print(f'event = {event!r}')
    sns_client = sns_client or boto3.client('sns')

    topic_arn = os.environ['TOPIC_ARN']
    print(f'topic_arn={topic_arn}')

    bucket_name = os.environ['TARGET_BUCKET_NAME']
    print(f'bucket_name={bucket_name}')

    es_index = os.environ['ES_INDEX']
    print(f'es_index={es_index}')

    snapshot_request_message = SnapshotRequest(
        time=dt.datetime.utcnow().isoformat(),
        private_bucket_name=bucket_name,
        public_bucket_name='wellcomecollection-data-private',
        public_object_key='catalogue/v1/works.json.gz'
        es_index=es_index
    )

    publish_sns_message(
        sns_client=sns_client,
        topic_arn=topic_arn,
        message=attr.asdict(snapshot_request_message),
        subject='source: snapshot_generator.main'
    )
