Camel Router with Scala DSL Project
===================================

To build this project use.

    `mvn install`

You can run this project from source.

    `mvn exec:java`

But the preferred way is to run the latest Docker image.

    `cd docker/`
    `make run`

Then you can use **HAPI TestPanel** to send messages to the ADT connector.

    * Create a connection that connects to localhost:8888.
    * Create a message and send it.
    * Go back to the connection and check the *Activity* tab or tail the karaf logs at /opt/karaf/data/log/karaf.log on the ADT connector.

For more help see the Apache Camel documentation.

    http://camel.apache.org/
