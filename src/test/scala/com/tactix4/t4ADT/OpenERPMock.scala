package com.tactix4.t4ADT

import org.apache.camel.test.junit4.CamelTestSupport
import org.apache.camel.scala.dsl.builder.{RouteBuilder, RouteBuilderSupport}
import org.apache.camel.Exchange

/**
 * Created with IntelliJ IDEA.
 * User: max
 * Date: 08/10/13
 * Time: 10:52
 * To change this template use File | Settings | File Templates.
 */
class OpenERPMock extends RouteBuilder {

  val faultMessage =  "<methodResponse> <fault> <value> <struct> <member> <name>faultCode</name> <value>failzors</value> </member> <member> <name>faultString</name> <value><string>Sun Spots</string></value> </member> </struct> </value> </fault> </methodResponse>"


  "mockOpenERPServerCommon" ==> {
    process((exchange: Exchange) => {

      val inbound = exchange.in[String]
      if(inbound contains "login") exchange.out = "<methodResponse><params><param><value><int>1</int></value></param></params></methodResponse>"
      else if(inbound contains "context_get") exchange.out = "<methodResponse><params><param><value><struct><member><name>lang</name><value><string>en_GB</string></value></member><member><name>tz</name><value><string>Europe/Brussels</string></value></member></struct></value></param></params></methodResponse>"
      else exchange.out = faultMessage

    })
    to("log:out")
  }
  "mockOpenERPServerObject" ==> {
    process((exchange: Exchange) => {

      val inbound = exchange.in[String]
      if(inbound contains "login") exchange.out = "<methodResponse><params><param><value><int>1</int></value></param></params></methodResponse>"
      else if(inbound contains "context_get") exchange.out = "<methodResponse><params><param><value><struct><member><name>lang</name><value><string>en_GB</string></value></member><member><name>tz</name><value><string>Europe/Brussels</string></value></member></struct></value></param></params></methodResponse>"
      else if(inbound contains "patientNew") exchange.out = "<methodResponse><params><param><value><int>38</int></value></param></params></methodResponse>"
      else if(inbound contains "patientUpdate") exchange.out = "<methodResponse><params><param><value><boolean>true</boolean></value></param></params></methodResponse>"
      else if(inbound contains "patientMerge") exchange.out = "<methodResponse><params><param><value><boolean>true</boolean></value></param></params></methodResponse>"
      else if(inbound contains "patientDischarge") exchange.out = "<methodResponse><params><param><value><boolean>true</boolean></value></param></params></methodResponse>"
      else exchange.out = faultMessage

    })
    to("log:out")
  }

}
