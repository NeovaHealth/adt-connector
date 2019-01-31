# adt-connector

Provides an HL7 interface to open-eObs.

A Camel Router with Scala DSL Project. For more help see the [Apache Camel](http://camel.apache.org/) documentation.


## Build

To build this project use:

```sh
mvn install
```

You can run this project from source.

```sh
mvn exec:java
```

## Docker
To build the connector and run with Docker:

```sh
make -C docker integration-test
mvn install
make -C docker integration-test-clean
registry=openeobs make -C docker run
```

## Test with HAPI
You can use [HAPI TestPanel](https://hapifhir.github.io/hapi-hl7v2/hapi-testpanel/) to send messages to the ADT connector.

  * Create a connection that connects to localhost:8888.
  * Create a message and send it.
  * Go back to the connection and check the *Activity* tab
  * Tail the karaf logs at on the ADT connector.
