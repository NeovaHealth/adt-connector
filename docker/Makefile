#!/usr/bin/make
IP = $(shell ip r l | awk '/default/ {print $$3}')
REGION = $(shell echo ${registry} | awk -F. '{print $$4}')
VERSION = $(shell ./print-docker-tag)

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
	# @`aws ecr get-login --no-include-email --region "${REGION}"`

build: clean login
	docker build -f Dockerfile --pull --no-cache -t ${registry}/adtconnector:${VERSION} ..
	docker tag ${registry}/adtconnector:${VERSION} adtconnector:${VERSION}
	@cd ..

server-test:
	@bundle install
	@VERSION=${VERSION} bundle exec rake spec
	@rm -rf vendor

integration-test:
	@docker-compose -f docker-compose-integration-test.yml run -d db-adt
	@./set-redis-host-if-on-gocd
	mvn -f ../pom.xml -Dsurefire.useFile=false -DtrimStackTrace=false test

integration-test-clean:
	docker-compose -f docker-compose-integration-test.yml stop
	docker-compose -f docker-compose-integration-test.yml rm --force

test: server-test integration-test integration-test-clean

publish: clean login build test
	@echo -n "Checking for registry setting ... "
	@test -n "${registry}" || (echo "no registry setting" && exit 1)
	@echo OK
	@docker tag ${registry}/adtconnector:${VERSION} ${registry}/adtconnector:latest
	@docker push ${registry}/adtconnector:${VERSION}
	@docker push ${registry}/adtconnector:latest

run:
	docker-compose -f docker-compose-run.yml up --build -d

destroy:
	docker-compose -f docker-compose-run.yml down -v

.PHONY: clean login build test publish
