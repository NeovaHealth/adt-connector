package com.tactix4.t4ADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */

import org.junit.Test

class PatientNewTest extends ADTTest{

  val patientNewADT28 = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A28^ADT_A05|201|T|2.4\rPID|1||123456789|0123456789^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientNewADT05 = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A05|201|T|2.4\rPID|1||987654321|9876543210^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientNewADT05No_Identity = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientNewADT28No_Identity = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A28^ADT_A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"

  @Test
  def testPatientNew_A28(){
    sendMessageAndExpectResponse(patientNewADT28, "MSA|AA|201")
  }
  @Test
  def testPatientNew_A05(){
    sendMessageAndExpectResponse(patientNewADT05, "MSA|AA|201")
  }
  @Test
  def testPatientNewNoIdentity_A28(){
    sendMessageAndExpectError(patientNewADT28No_Identity, "Required field missing")
  }
  @Test
  def testPatientNewNoIdentity_A05(){
    sendMessageAndExpectError(patientNewADT05No_Identity, "Required field missing")
  }
}
