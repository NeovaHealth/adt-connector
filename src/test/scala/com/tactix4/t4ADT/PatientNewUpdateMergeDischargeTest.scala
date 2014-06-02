package com.tactix4.t4ADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */
import org.junit.{FixMethodOrder, Test}
import scala.util.Random
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PatientNewUpdateMergeDischargeTest extends ADTTest{


  @Test
  def atestA28(){
    log.info("sending patientNew with ADT_A28 : id: " + TestVars.patientOneId)
    sendMessageAndExpectResponse(TestVars.patientNewADT28, "MSA|AA|")
  }
  @Test
  def btestA05{
    log.info("sending patientNew with ADT_A05 : id: " + TestVars.patientTwoId)
    sendMessageAndExpectResponse(TestVars.patientNewADT05, "MSA|AA|")
  }
  @Test
  def ctestA08{
    log.info("updating patientTwo")
    sendMessageAndExpectResponse(TestVars.patientUpdateADT_08, "MSA|AA|")
  }
  @Test
  def dtestA31{
    log.info("updating patientOne")
    sendMessageAndExpectResponse(TestVars.patientUpdateADT_31, "MSA|AA|")
  }
  @Test
  def etestA40{
    log.info("merge patientOne into patientTwo")
    sendMessageAndExpectResponse(TestVars.patientMerge, "MSA|AA|")
  }
  @Test
  def ftestA01{
    log.info("new visit for patientTwo")
    sendMessageAndExpectResponse(TestVars.visitNew, "MSA|AA|")
  }
  @Test
  def fbtestA01{
    log.info("new visit for patientTwo")
    sendMessageAndExpectError(TestVars.visitNewBroken, "Could not locate hospital number")
  }
  @Test
  def gtestA03 {
    log.info("discharge patientTwo")
    sendMessageAndExpectResponse(TestVars.patientDischarge, "MSA|AA|")
  }

  @Test
  def hfail28{
    sendMessageAndExpectError(TestVars.patientNewADT28No_Identity, "Required field missing")
  }
  @Test
  def ifail05{
    sendMessageAndExpectError(TestVars.patientNewADT05No_Identity, "Required field missing")
  }
  @Test
  def jfail08{
    sendMessageAndExpectError(TestVars.patientUpdateADTNo_Identity_08, "Required field missing")
  }
  @Test
  def kfail31{
    sendMessageAndExpectError(TestVars.patientUpdateADTNo_Identity_31, "Required field missing")
  }
  @Test
  def lfail40{
    sendMessageAndExpectError(TestVars.patientMergeADTNo_Identifier, "Patients to merge did not exist")
  }

}
