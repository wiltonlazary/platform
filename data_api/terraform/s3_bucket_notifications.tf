# Note: I was unable to actually create this resource using Terraform.
# Whenever I ran an apply step, I'd get an error:
#
#     Error putting S3 notification configuration: InvalidArgument: Unable to
#     validate the following destination configurations
#
# Instead, I created the notification through the AWS Console, then used
# 'terraform import' to add it to the Terraform state.  Now it exists, TF
# doesn't try to modify it, but this may cause problems if it's ever deleted.
#
resource "aws_s3_bucket_notification" "private_data_bucket_notification" {
  bucket = "${aws_s3_bucket.private_data.id}"

  lambda_function {
    lambda_function_arn = "${module.snapshot_convertor_job_generator.function_arn}"
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "elasticdump/"
  }
}