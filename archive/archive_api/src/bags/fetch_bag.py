# -*- encoding: utf-8

import json


def fetch_bag(dynamodb_resource, table_name, s3_client, bucket_name, id):
    """
    Fetch the contents of a Bag.
    """

    table = dynamodb_resource.Table(table_name)
    item_response = table.get_item(Key={'id': id})

    try:
        response = s3_client.get_object(
            Bucket=item_response['Item']['location']['namespace'],
            Key=item_response['Item']['location']['key']
        )
    except KeyError:
        raise ValueError(f'No bag found for id={id!r}')

    return json.loads(response['Body'].read())