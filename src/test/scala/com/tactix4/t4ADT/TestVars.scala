package com.tactix4.t4ADT

import scala.util.Random

/**
 * @author max@tactix4.com
 *         10/03/2014
 */
object TestVars {

  def randomId = Random.shuffle("abcdefghijklmnopqrstuvwxyz1234567890".toList).take(Random.nextInt(30) + 6).mkString

  final val patientOneId = randomId
  val PID1 = "PID|1|^^^^PAS||"+ patientOneId + "^\"\"^^RDD^HOSP~652 639 8685^NSTS01^^NHS^NHS~QD1320147-1^^^RDD^EVO|DUMMY^PATIENT HEIDI^^^Miss||19740613000000|F||||||||||||||B^White Irish|||||||\"\"|N"
  final val patientTwoId = randomId
  val PID2 = "PID|1|^^^^PAS||" + patientTwoId + "|DUMMY^PATIENT HEIDI^^^Ms||19740613000000|F||||||||||||||B^White Irish|||||||\"\"|N"

  val WARD = "E8"

  val visitID = randomId
  val visitID2 = randomId

  val patientNewADT28     = "MSH|^~\\&|iPM|iIE|T4skr|T4skr|20120124135111||ADT^A28|609299|P|2.4|||AL|AL\r" +
                            "EVN|A28|20120124135111\r"+
                             PID1 + "\r" +
                            "PD1|||Dummy Test Practice^GPPRC^TEST1X|G0000001^Dummy^Leigh^^^Dr"

  val patientNewADT05     = "MSH|^~\\&|iPM|iIE|T4skr|T4skr|20131007152356.695+0100||ADT^A05|609299|T|2.4\r" +
                             PID2 + "\r"+
                            "PD1|||Dummy Test Practice^GPPRC^TEST1X|G0000001^Dummy^Leigh^^^Dr"

  val patientUpdateADT_31 = "MSH|^~\\&|iPM|iIE|T4skr|T4skr|20120120143457||ADT^A31|609190|P|2.4|||AL|AL\r" +
                            "EVN|A31|20120120143457\r"+
                            PID1 + "\r" +
                            "PD1|||Branch Practice, Gooseberry Hill HC, Luton^GPPRC^E81046B|G3403352B^Glaze^M E^^^Dr\r"+
//                            s"PV1|1|I|$WARD^^^^^^^^Ward 05 Isolation|22||^^^^^^^^|^^^^^|G0000001^Dummy^Leigh^^^Dr|C2724623^Adler^B^^^Dr|420|||||||C2724623^Adler^B^^^Dr|01|" + visitID2+ "|||||||||||||||||||||||||20120110150000\r"+
                            "AL1|1|CLIN|ISP^MRSA Positive (P)|||20101124"

  val patientUpdateADT_08 = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A08|201|T|2.4\r" +
                            PID2
//                            "PV1|1|I|"+WARD+"^^^^^^^^Cobham Clinic|11||^^^^^^^^|^^^^^|C6035630^Ahmed^R^^^Dr|C5205403^He-Man^M^^^Mr|110|||||||C5205403^Skeletor^M^^^Mr|01|"+ visitID +"|||||||||||||||||||||||||20120103090000"

  val patientUpdateADT_08H = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A08|201|T|2.4\r" +
                               "PID|1|^^^^PAS||"+ patientOneId + "^\"\"^^RDD^HOSP~652 639 8685^NSTS01^^NHS^NHS~QD1320147-1^^^RDD^EVO|DUMMY^PATIENT BERYL^^^Miss||19740613000000|F||||||||||||||B^White Irish|||||||\"\"|N\r" +
                            "PV1|1|I|"+WARD+"^^^^^^^^Cobham Clinic|11||^^^^^^^^|^^^^^|C6035630^Ahmed^R^^^Dr|C5205403^He-Man^M^^^Mr|110|||||||C5205403^Skeletor^M^^^Mr|01|"+ visitID +"|||||||||||||||||||||||||20120103090000|20120203090000"

  val patientMerge     = "MSH|^~\\&|OTHER_IBM_BRIDGE_TLS|IBM|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|20090224104210-0600||ADT^A40^ADT_A39|4143361005927619863|P|2.4\r"+
                            "EVN||20090224104210-0600\r"+
                            "PID|1|||" + patientTwoId+"||OTHER_IBM_BRIDGE^MARION||19661109|F\r"+
                            "MRG|"+patientOneId+"^^^IBOT&1.3.6.1.4.1.21367.2009.1.2.370&ISO"

  val visitNew        = "MSH|^~\\&|iPM|iIE|Wardware|Wardware|20120105105702||ADT^A01|607639|P|2.4|||AL|AL\r" +
                        "EVN|A01|20120105105702\r" +
                        PID2 +"\r" +
                        s"PV1|1|I|$WARD^^^^^^^^Cobham Clinic|11||^^^^^^^^|^^^^^|C6035630^Ahmed^R^^^Dr|C5205403^Abdunabi^M^^^Mr|110|||||||C5205403^Abdunabi^M^^^Mr|01|$visitID |||||||||||||||||||||||||20120103090000\r" +
                        "AL1|1|CLIN|ISP^MRSA Positive (P)|||20111214"

  val visitNewBroken        = "MSH|^~\\&|iPM|iIE|Wardware|Wardware|20120105105702||ADT^A01|607639|P|2.4|||AL|AL\r" +
                        "EVN|A01|20120105105702\r" +
                        s"PV1|1|I|$WARD^^^^^^^^Cobham Clinic|11||^^^^^^^^|^^^^^|C6035630^Ahmed^R^^^Dr|C5205403^Abdunabi^M^^^Mr|110|||||||C5205403^Abdunabi^M^^^Mr|01|$visitID |||||||||||||||||||||||||20120103090000\r" +
                        "AL1|1|CLIN|ISP^MRSA Positive (P)|||20111214"

  val patientDischarge = "MSH|^~\\&|iPM|iIE|T4skr|T4skr|20120122151427||ADT^A03|608741|P|2.4|||AL|AL\r" +
                            "EVN|A03|20120122151427\r" +
                            PID2 + "\r" +
                            "PD1|||Branch Practice, Gooseberry Hill HC, Luton^GPPRC^E81046B|G3403352B^Glaze^M E^^^Dr\r"+
                            s"PV1|1|I|$WARD^^^^^^^^Ward 05 Isolation|22||^^^^^^^^|^^^^^|G0000001^Dummy^Leigh^^^Dr|C2782056^Ahmad^T^^^Mr|160|||||||C2782056^Ahmad^T^^^Mr|01|1063663|||||||||||||||||||||||||20120115111800|20120115151300"


  val patientMergeADTNo_Identifier = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A40^ADT_A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientNewADT05No_Identity = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientNewADT28No_Identity = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A28^ADT_A05|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientUpdateADTNo_Identity_31 = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A31|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"
  val patientUpdateADTNo_Identity_08 = "MSH|^~\\&|||||20131007152356.695+0100||ADT^A08|201|T|2.4\rPID|1|||^AA^^JP|BROS^MARIO^^^^||19850101000000|M|||123 FAKE STREET^MARIO \\T\\ LUIGI BROS PLACE^TOADSTOOL KINGDOM^NES^A1B2C3^JP^HOME^^1234|1234|(555)555-0123^HOME^JP:1234567|||S|MSH|12345678|||||||0|||||N"

}
