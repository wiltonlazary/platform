# miro_preprocessor

This stack is responsible for sorting Miro metadata and assets into one of three piles:

*   Cold store / Amazon Glacier
*   Tandem Vault
*   The Catalogue API

## xml_to_json_converter

An ECS Task with inputs:

- S3 Source location (a Miro formatted XML dump)
- S3 Target location

This task downloads and converts a Miro formatted XML dump to JSON and puts it at the S3 target location specified.

## xml_to_json_run_task

A lambda which is triggered by an S3 upload of a new Miro xml dump.

The default location monitored is the miro data bucket at `/source/*.xml`.

This lambda starts the xml_to_json_converter task and hands it the correct command.

# image_sorter

This is an AWS Lambda that divides the images into one of three piles:

*   Cold store -- images that we expect to delete
*   Tandem Vault -- images that we want to keep, but restrict to staff access
*   Catalogue API -- images that we want to make publicly available

It receives an S3 event trigger from xml_to_json_converter, and pushes the JSON blob to the corresponding SNS topic.