package uk.co.neovahealth.nhADT

/**
 * Tests the main route functionality
 * @author max@tactix4.com
 * Date: 26/09/13
 */


import org.junit.Test

class MiscTest extends ADTTest {

  val patientMergeADT = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A99^ADT_A05|201|T|2.4\rPID|1||123456789|0123456789^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"

  val patientMergeADTNo_Identifier = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A28^ADT_A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"

  def visitNew(ward:String) = "MSH|^~\\&|iPM|iIE|Wardware|Wardware|20120105105702||ADT^A01|607639|P|2.4|||AL|AL\r" +
    "EVN|A01|20120105105702||||20120105105702\r" +
    "PID|1|^^^^PAS||pid1|Jones^Jimbo^^^Badman||19740613000000|F||||||||||||||B^White Irish|||||||\"\"|N\r" +
    s"PV1|1|I|$ward^^^^^^^^Cobham Clinic|11||^^^^^^^^|^^^^^|C6035630^Ahmed^R^^^Dr|C5205403^Abdunabi^M^^^Mr|110|||||||C5205403^Abdunabi^M^^^Mr|01|vid1|||||||||||||||||||||||||20120103090000\r" +
    "AL1|1|CLIN|ISP^MRSA Positive (P)|||20111214"

  @Test
  def testUnsupportedMessage() ={
    sendMessageAndExpectError(patientMergeADT, "Unsupported")
  }

  @Test
  def testWardMap() = {
    sendMessageAndExpectModifiedMessage(visitNew("101"),visitNew("100"))
  }



}
