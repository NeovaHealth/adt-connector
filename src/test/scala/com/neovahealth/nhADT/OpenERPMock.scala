package com.neovahealth.nhADT

import org.apache.camel.Exchange
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * Created with IntelliJ IDEA.
 * User: max
 * Date: 08/10/13
 * Time: 10:52
 * To change this template use File | Settings | File Templates.
 */
class OpenERPMock extends RouteBuilder {

  "jetty:http://localhost:8069/xmlrpc/common" ==> {
    transform(_.in = "<methodResponse><params><param><value><int>1</int></value></param></params></methodResponse>")
    to("log:out")
  }
  "jetty:http://localhost:8069/xmlrpc/object" ==> {
    transform(_.in = "<methodResponse><params><param><value><int>1</int></value></param></params></methodResponse>")
    to("log:out")
  }

}
