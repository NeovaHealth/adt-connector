package com.tactix4.t4ADT

/**
 * Tests the tersermap and associated validation functionality
 * @author max@tactix4.com
 * Date: 26/09/13
 */


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.{TestContextManager, ContextConfiguration}
import org.scalatest.matchers.ShouldMatchers
import ca.uhn.hl7v2.model.v24.message.{ADT_A15, ADT_A01}
import ca.uhn.hl7v2.util.Terser
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Test
import org.scalatest.{FunSuiteLike, FunSpec}
import org.apache.camel.component.hl7.HL7MLLPCodec
import java.util.concurrent.TimeUnit
import org.apache.camel.{CamelContext, ExchangePattern}
import org.apache.camel.scala.dsl.builder.{RouteBuilder, RouteBuilderSupport}


@ContextConfiguration(locations=Array("classpath:META-INF/spring/testBeans.xml"))
class ADTRouteInTest extends CamelTestSupport with ShouldMatchers{

  override def createCamelContext() = {
    route.getContext
  }
  override def createRegistry() ={

    val jndi = super.createRegistry()
    val codec = new HL7MLLPCodec()
    codec.setCharset("iso-8859-1")
    jndi.bind("hl7codec", codec)
    jndi
  }

  val configPath = "src/test/resources/ADT_A01.properties"

  @Autowired val route :ADTInRoute  = null

  new TestContextManager(this.getClass).prepareTestInstance(this)


  val testMessage = new ADT_A01()
  testMessage.initQuickstart("ADT", "A01", "P")
  val terser = new Terser(testMessage)
  terser.set("PID-5-1", "Bobkins")
  terser.set("PID-5-2", "Bob")
  terser.set("PID-5-3", null)
  terser.set("PID-7-1", "19850101000000")
  terser.set("PID-8", "M")
  val mappings = route.getMappings(terser)


  val testFailMessage = new ADT_A15()
  testFailMessage.initQuickstart("ADT", "A28", "P")
  val failTerser = new Terser(testFailMessage)
  val failMappings = route.getMappings(failTerser)

  val patientNewADT = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A28^ADT_A05|201|T|2.4\rPID|1||123456789|0123456789^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"




  @Test
  def testRoute(){
    getMockEndpoint("mock:rabbitmq").setExpectedMessageCount(1)
    template.sendBody("mina2:tcp://0.0.0.0:8888?sync=true&codec=#hl7codec", ExchangePattern.InOut, patientNewADT)
    getMockEndpoint("mock:rabbitmq").assertIsSatisfied()
    println("###########################" + getMockEndpoint("mock:rabbitmq").getReceivedCounter)
  }


}
