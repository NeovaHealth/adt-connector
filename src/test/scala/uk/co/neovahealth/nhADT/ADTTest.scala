package uk.co.neovahealth.nhADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 *         Date: 26/09/13
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

  val URI: String = "mina2:tcp://localhost:31337?sync=true&codec=#hl7codec"

  override def createRegistry() = {

    val jndi = super.createRegistry()
    val codec = new HL7MLLPCodec()
    codec.setCharset("iso-8859-1")
    jndi.bind("hl7codec", codec)
    jndi
  }

  val msg1 = "<?xml version='1.0'?><methodCall><methodName>execute</methodName><params><param><value><string>nhclinical</string></value></param><param><value><int>1</int></value></param><param><value><string>adt</string></value></param><param><value><string>nh.clinical.patient</string></value></param><param><value><string>search</string></value></param><param><value><array><data><value><array><data><value><string>other_identifier</string></value><value><string>=</string></value><value><string>pid1</string></value></data></array></value></data></array></value></param><param><value><int>0</int></value></param><param><value><int>0</int></value></param><param><value><string></string></value></param><param><value><struct><member><name>activeTest</name><value><boolean>1</boolean></value></member><member><name>lang</name><value><string>en_GB</string></value></member><member><name>tz</name><value><string>Europe/London</string></value></member></struct></value></param></params></methodCall>"

  val msg2 = "<?xml version='1.0'?><methodCall><methodName>execute</methodName><params><param><value><string>nhclinical</string></value></param><param><value><int>1</int></value></param><param><value><string>adt</string></value></param><param><value><string>nh.eobs.api</string></value></param><param><value><string>update</string></value></param><param><value><string>pid1</string></value></param><param><value><struct><member><name>location</name><value><string>101</string></value></member><member><name>dob</name><value><string>1974-06-13 00:00:00</string></value></member><member><name>family_name</name><value><string>Jones</string></value></member><member><name>sex</name><value><string>F</string></value></member><member><name>title</name><value><string>Badman</string></value></member><member><name>given_name</name><value><string>Jimbo</string></value></member></struct></value></param><param><value><struct><member><name>activeTest</name><value><boolean>1</boolean></value></member><member><name>lang</name><value><string>en_GB</string></value></member><member><name>tz</name><value><string>Europe/London</string></value></member></struct></value></param></params></methodCall>"

  val msg3 = "<?xml version='1.0'?><methodCall><methodName>execute</methodName><params><param><value><string>nhclinical</string></value></param><param><value><int>1</int></value></param><param><value><string>adt</string></value></param><param><value><string>nh.eobs.api</string></value></param><param><value><string>admit</string></value></param><param><value><string>pid1</string></value></param><param><value><struct><member><name>location</name><value><string>100</string></value></member><member><name>start_date</name><value><string>2012-01-03 09:00:00</string></value></member><member><name>code</name><value><string>vid1</string></value></member><member><name>doctors</name><value><array><data><value><struct><member><name>family_name</name><value><string>Abdunabi</string></value></member><member><name>code</name><value><string>C5205403</string></value></member><member><name>title</name><value><string>Mr</string></value></member><member><name>type</name><value><string>c</string></value></member><member><name>given_name</name><value><string>M</string></value></member></struct></value><value><struct><member><name>type</name><value><string>r</string></value></member></struct></value></data></array></value></member></struct></value></param><param><value><struct><member><name>activeTest</name><value><boolean>1</boolean></value></member><member><name>lang</name><value><string>en_GB</string></value></member><member><name>tz</name><value><string>Europe/London</string></value></member></struct></value></param></params></methodCall>"

  def sendMessageAndExpectModifiedMessage(message: String, expectedModification: String) = {
    val resultEndpoint = getMockEndpoint("mock:odooTestMock")
    resultEndpoint.setExpectedMessageCount(3)
    resultEndpoint.expectedBodiesReceived(msg1,msg2,msg3)
    template.sendBody(URI, ExchangePattern.InOut, message).toString
    assertMockEndpointsSatisfied()
    resetMocks()
  }

  def sendMessageAndExpectResponse(message: String, expectedResult: String) = {
    val resultEndpoint = getMockEndpoint("msgHistory")
    val failEndpoint = getMockEndpoint("failMsgHistory")
    resultEndpoint.setExpectedMessageCount(1)
    failEndpoint.setExpectedMessageCount(0)
    resultEndpoint.message(0).body.equals(message) //checks that rabbitmq recieves the original adt message
    val result = template.sendBody(URI, ExchangePattern.InOut, message).toString
    assertMockEndpointsSatisfied()
    assert(result contains expectedResult)
    resetMocks()
  }

  def sendMessageAndExpectError(message: String, expectedResult: String) = {
    val resultEndpoint = getMockEndpoint("msgHistory")
    val failEndpoint = getMockEndpoint("failMsgHistory")
    resultEndpoint.setExpectedMessageCount(0)
    failEndpoint.setMinimumExpectedMessageCount(1)
    failEndpoint.message(0).body.equals(message) //checks that rabbitmq recieves the original adt message
    val result = template.sendBody(URI, ExchangePattern.InOut, message).toString
    assertMockEndpointsSatisfied()
    assert(result contains expectedResult)
    resetMocks()
    failEndpoint.reset()
    resultEndpoint.reset()
  }
}
