module "harrison_pim_notebook" {
  source = "notebooks"

  namespace = "${var.namespace}-notebook"

  s3_bucket_name = "${var.notebook_bucket_name}"
  s3_bucket_arn  = "${var.notebook_bucket_arn}"

  key_name = "${var.key_name}"

  aws_region = "${var.aws_region}"

  vpc_cidr_block = "${var.vpc_cidr_block}"
  subnets        = "${var.public_subnets}"
  vpc_id         = "${var.vpc_id}"

  controlled_access_cidr_ingress = ["${var.admin_cidr_ingress}"]

  efs_id                = "${var.efs_id}"
  efs_security_group_id = "${var.efs_security_group_id}"
}

module "labs" {
  source = "labs"

  namespace = "${var.namespace}-datalabs"

  vpc_cidr_block = "${var.vpc_cidr_block}"

  vpc_id = "${var.vpc_id}"

  private_subnets = "${var.private_subnets}"
  public_subnets  = "${var.public_subnets}"
}