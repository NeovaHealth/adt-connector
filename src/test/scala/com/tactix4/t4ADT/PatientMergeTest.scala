package com.tactix4.t4ADT

/**
 * Tests the main route functionality
 * @author max@tactix4.com
 * Date: 26/09/13
 */


import org.scalatest.matchers.ShouldMatchers
import org.junit.Test
import org.apache.camel.component.hl7.HL7MLLPCodec
import org.apache.camel.ExchangePattern
import org.apache.camel.test.spring.CamelSpringTestSupport
import org.springframework.context.support.{ClassPathXmlApplicationContext, AbstractApplicationContext}

class PatientMergeTest extends ADTTest {

  val patientMergeADT = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A40^ADT_A05|201|T|2.4\rPID|1||123456789|0123456789^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientMergeADTNo_Identifier = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A40^ADT_A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"


  @Test
  def testPatientMerge() ={
    sendMessageAndExpectResponse(patientMergeADT, "MSA|AA|201")
  }

  @Test
  def testPatientMergeNo_Identifier() = {
    sendMessageAndExpectError(patientMergeADTNo_Identifier, "Required field missing")
  }



}
