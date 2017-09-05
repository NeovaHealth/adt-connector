#!/usr/bin/make

build:
	docker build https://github.com/bjss/BJSS_liveobs_adt_connector/

run:
	docker-compose -f docker-compose-integration-run.yml up

test:
	docker-compose -f docker-compose-integration-test.yml run -d db-adt
	mvn test

clean:
	docker-compose -f docker-compose-integration-test.yml rm
	docker-compose -f docker-compose-run.yml rm
