package uk.co.neovahealth.nhADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */


import org.apache.camel.ExchangePattern
import org.apache.camel.component.hl7.HL7MLLPCodec
import org.apache.camel.test.spring.CamelSpringTestSupport
import org.scalatest.matchers.ShouldMatchers
import org.springframework.context.support.{AbstractApplicationContext, ClassPathXmlApplicationContext}

class ADTTest extends CamelSpringTestSupport with ShouldMatchers{

  def createApplicationContext(): AbstractApplicationContext = {
    new ClassPathXmlApplicationContext("META-INF/spring/testBeans.xml")
  }

  val URI:String = "mina2:tcp://localhost:31337?sync=true&codec=#hl7codec"

  override def createRegistry() ={

    val jndi = super.createRegistry()
    val codec = new HL7MLLPCodec()
    codec.setCharset("iso-8859-1")
    jndi.bind("hl7codec", codec)
    jndi
  }


 def sendMessageAndExpectResponse(message: String, expectedResult: String)= {

 val resultEndpoint = getMockEndpoint("msgHistory")
 val failEndpoint = getMockEndpoint("failMsgHistory")
   resultEndpoint.setExpectedMessageCount(1)
   failEndpoint.setExpectedMessageCount(0)
   resultEndpoint.message(0).body.equals(message) //checks that rabbitmq recieves the original adt message
   val result = template.sendBody(URI, ExchangePattern.InOut, message).toString
   println(result)
   assertMockEndpointsSatisfied()
   assert(result contains expectedResult)
   resetMocks()
 }
  def sendMessageAndExpectError(message: String, expectedResult: String)= {
    val resultEndpoint = getMockEndpoint("msgHistory")
    val failEndpoint = getMockEndpoint("failMsgHistory")
    resultEndpoint.setExpectedMessageCount(0)
    failEndpoint.setMinimumExpectedMessageCount(1)
    failEndpoint.message(0).body.equals(message) //checks that rabbitmq recieves the original adt message
    val result = template.sendBody(URI, ExchangePattern.InOut, message).toString
    println(result)
    assertMockEndpointsSatisfied()
    assert(result contains expectedResult)
    resetMocks()
    failEndpoint.reset()
    resultEndpoint.reset()
   }
}
