#!/usr/bin/make

REGION = $(shell echo ${registry} | awk -F. '{print $$4}')
VERSION = $(shell echo $${GO_PIPELINE_LABEL:-1}-$${GO_STAGE_COUNTER:-1})

all: build test publish

clean:
	@echo -n "Checking for registry setting ... "
	@test -n "${registry}" || (echo "no registry setting" && exit 1)
	@echo OK
	docker rmi ${registry}/adtconnector:${VERSION} || true
	docker rmi ${registry}/adtconnector:latest || true
	docker rmi adtconnector:${VERSION} || true

login:
	@echo -n "Checking for registry setting ... "
	@test -n "${registry}" || (echo "no registry setting" && exit 1)
	@echo OK
	@echo -n "Logging into ECR..."
	@`aws ecr get-login --no-include-email --region "${REGION}"`

build: clean login
	docker build -f docker/Dockerfile --pull --no-cache -t ${registry}/adtconnector:${VERSION} ..
	docker tag ${registry}/adtconnector:${VERSION} adtconnector:${VERSION}
	@cd ..

test:
	@bundle install
	@bundle exec rake spec
	@rm -rf vendor

publish: login
	@echo -n "Checking for registry setting ... "
	@test -n "${registry}" || (echo "no registry setting" && exit 1)
	@echo OK
	@docker tag ${registry}/adtconnector:${VERSION} ${registry}/adtconnector:latest
	@docker push ${registry}/adtconnector:${VERSION}
	@docker push ${registry}/adtconnector:latest

.PHONY: clean login build test publish
