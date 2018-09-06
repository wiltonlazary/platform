output "private_subnets" {
  value = ["${aws_subnet.private.*.id}"]
}

output "public_subnets" {
  value = ["${aws_subnet.public.*.id}"]
}

output "vpc_id" {
  value = "${aws_vpc.vpc.id}"
}

output "num_subnets" {
  value = "${var.az_count}"
}

output "route_table_id" {
  value = "${aws_route_table.private_route_table.id}"
}
