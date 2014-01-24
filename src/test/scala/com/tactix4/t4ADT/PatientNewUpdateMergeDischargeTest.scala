package com.tactix4.t4ADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */

import org.junit.Test
import scala.util.Random

class PatientNewUpdateMergeDischargeTest extends ADTTest{

  println("TOP OF TEST")

  val patientOneId = Random.nextInt().abs
  val PID1 = "PID|1|^^^^PAS||" + patientOneId + "|DUMMY^PATIENT HEIDI^^^Miss||19740613000000|F||||||||||||||B^White Irish|||||||\"\"|N"
  val patientTwoId = Random.nextInt().abs
  val PID2 = "PID|1|^^^^PAS||" + patientTwoId + "|DUMMY^PATIENT HEIDI^^^Ms||19740613000000|F||||||||||||||B^White Irish|||||||\"\"|N"


  val patientNewADT28     = "MSH|^~\\&|iPM|iIE|Wardware|Wardware|20120124135111||ADT^A28|609299|P|2.4|||AL|AL\r" +
                            "EVN|A28|20120124135111\r"+
                             PID1 + "\r" +
                            "PD1|||Dummy Test Practice^GPPRC^TEST1X|G0000001^Dummy^Leigh^^^Dr\r" +
                            "PV1|1|R|^^^^^^^^|||^^^^^^^^||||||||||||||||||||||||||||||||||||||"

  val patientNewADT05     = "MSH|^~\\&|iPM|iIE|Wardware|Wardware|20131007152356.695+0100||ADT^A05|609299|T|2.4\r" +
                             PID2 + "\r"+
                            "PD1|||Dummy Test Practice^GPPRC^TEST1X|G0000001^Dummy^Leigh^^^Dr\r" +
                            "PV1|1|R|^^^^^^^^|||^^^^^^^^||||||||||||||||||||||||||||||||||||||"
  val patientUpdateADT_31 = "MSH|^~\\&|iPM|iIE|Wardware|Wardware|20120120143457||ADT^A31|609190|P|2.4|||AL|AL\r" +
                            "EVN|A31|20120120143457\r"+
                            PID1 + "\r" +
                            "PD1|||Branch Practice, Gooseberry Hill HC, Luton^GPPRC^E81046B|G3403352B^Glaze^M E^^^Dr\r"+
                            "PV1|1|I|W005^^^^^^^^Ward 05 Isolation|22||^^^^^^^^|^^^^^|G0000001^Dummy^Leigh^^^Dr|C2724623^Adler^B^^^Dr|420|||||||C2724623^Adler^B^^^Dr|01|1063668|||||||||||||||||||||||||20120110150000"+
                            "AL1|1|CLIN|ISP^MRSA Positive (P)|||20101124"
  val patientUpdateADT_08 = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A08|201|T|2.4\r" +
                            "PID|1|||" + patientTwoId + "^^^^PAS|DUMMY^PATIENT HEIDI^^^Mrs||19740615000000|F||||||||||||||B^White Irish|||||||\"\"|N"
  val patientMergeAlt     = "MSH|^~\\&|OTHER_IBM_BRIDGE_TLS|IBM|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|20090224104210-0600||ADT^A40^ADT_A39|4143361005927619863|P|2.4\r"+
                            "EVN||20090224104210-0600\r"+
                            "PID|1|||" + patientTwoId+"||OTHER_IBM_BRIDGE^MARION||19661109|F\r"+
                            "MRG|"+patientOneId+"^^^IBOT&1.3.6.1.4.1.21367.2009.1.2.370&ISO\r"+
                            "PV1||O"

  val patientDischarge = "MSH|^~\\&|iPM|iIE|Wardware|Wardware|20120122151427||ADT^A03|608741|P|2.4|||AL|AL\r" +
                            "EVN|A03|20120122151427\r" +
                            PID2 + "\r" +
                            "PD1|||Branch Practice, Gooseberry Hill HC, Luton^GPPRC^E81046B|G3403352B^Glaze^M E^^^Dr\r"+
                            "PV1|1|I|W005^^^^^^^^Ward 05 Isolation|22||^^^^^^^^|^^^^^|G0000001^Dummy^Leigh^^^Dr|C2782056^Ahmad^T^^^Mr|160|||||||C2782056^Ahmad^T^^^Mr|01|1063663|||||||||||||||||||||||||20120115111800|20120115151300"


  val patientMergeADTNo_Identifier = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A40^ADT_A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientNewADT05No_Identity = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientNewADT28No_Identity = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A28^ADT_A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientUpdateADTNo_Identity_31 = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A31|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientUpdateADTNo_Identity_08 = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A08|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"

  @Test
  def testAddUpdateMerge(){

    log.info("sending patientNew with ADT_A28 : id: " + patientOneId)
    sendMessageAndExpectResponse(patientNewADT28, "MSA|AA|")
    log.info("sending patientNew with ADT_A05 : id: " + patientTwoId)
    sendMessageAndExpectResponse(patientNewADT05, "MSA|AA|")
    log.info("updating patientTwo")
    sendMessageAndExpectResponse(patientUpdateADT_08, "MSA|AA|")
    log.info("updating patientOne")
    sendMessageAndExpectResponse(patientUpdateADT_31, "MSA|AA|")
    log.info("merge patientOne into patientTwo")
    sendMessageAndExpectResponse(patientMergeAlt, "MSA|AA|")
    log.info("discharge patientTwo")
    sendMessageAndExpectResponse(patientDischarge, "MSA|AA|")
  }

  @Test
  def testFailAddUpdateMerge(){
    sendMessageAndExpectError(patientNewADT28No_Identity, "Required field missing")
    sendMessageAndExpectError(patientNewADT05No_Identity, "Required field missing")
    sendMessageAndExpectError(patientUpdateADTNo_Identity_08, "Required field missing")
    sendMessageAndExpectError(patientUpdateADTNo_Identity_31, "Required field missing")
    sendMessageAndExpectError(patientMergeADTNo_Identifier, "Required field missing")
  }

}
