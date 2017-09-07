#!/usr/bin/make

prepos:
	@echo "Installing OS dependencies ..."
	@sudo apt-get install --no-install-recommends -y bundler puppet
	@echo "Done"

prep:
	@echo "Installing dependencies ..."
	@bundle install
	@echo "Done"

init: clean
	@echo -n "Checking for region setting ... "
	@test -n "${region}" || (echo "no region setting" && exit 1)
	@echo OK
	@echo -n "Checking for bucket setting ... "
	@test -n "${bucket}" || (echo "no bucket setting" && exit 1)
	@echo OK
	@echo -n "Checking for service setting ... "
	@test -n "${service}" || (echo "no service setting" && exit 1)
	@echo OK
	@echo -n "Checking for environment setting ... "
	@test -n "${environment}" || (echo "no environment setting" && exit 1)
	@echo OK
	@echo "terraform {\n backend \"s3\" {\n  region = \"${region}\"\n  bucket = \"${bucket}\"\n  key = \"${environment}/${region}/${service}.tfstate\"\n  lock_table = \"terraform_statelock\"\n }\n}" > backend_terraformscaffold.tf || (echo "Failed to write backend config to backend_terraformscaffold.tf" && exit 1)
	@echo "Setting up S3 remote state from s3://${bucket}/${environment}/${region}/${service}.tfstate";
	@terraform init || (echo "Terraform init failed" && exit 1)
	@terraform get -update=true

clean:
	@if test -e backend_terraformscaffold.tf; then \
		rm -f backend_terraformscaffold.tf; \
	fi

validate: init
	@echo -n "Checking for region setting ... "
	@test -n "${region}" || (echo "no region setting" && exit 1)
	@echo OK
	@echo -n "Checking for environment setting ... "
	@test -n "${environment}" || (echo "no environment setting" && exit 1)
	@echo OK
	@echo "Running a validate"
	terraform validate
	@echo "Validation passed"

plan: init
	@echo -n "Checking for region setting ... "
	@test -n "${region}" || (echo "no region setting" && exit 1)
	@echo OK
	@echo -n "Checking for environment setting ... "
	@test -n "${environment}" || (echo "no environment setting" && exit 1)
	@echo OK
	@echo "Running a plan"
	@terraform plan -var-file=etc/env_${region}_${environment}.tfvars

apply: init
	@echo -n "Checking for region setting ... "
	@test -n "${region}" || (echo "no region setting" && exit 1)
	@echo OK
	@echo -n "Checking for environment setting ... "
	@test -n "${environment}" || (echo "no environment setting" && exit 1)
	@echo OK
	@echo "Applying..."
	@terraform apply -var-file=etc/env_${region}_${environment}.tfvars

test: prep
	bundle exec rake spec


.PHONY: prepos prep init clean plan apply test
