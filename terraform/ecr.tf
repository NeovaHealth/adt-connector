resource "aws_ecr_repository" "adtconnector" {
  name = "adtconnector"
}

output "adtconnector-ecr_url" {
  value = "${aws_ecr_repository.adtconnector.repository_url}"
}
