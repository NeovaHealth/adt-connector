package com.tactix4.t4ADT

/**
 * Tests the A28 and A05 patientNew route
 * @author max@tactix4.com
 * Date: 26/09/13
 */

import org.junit.Test
import scala.util.Random

class PatientNewUpdateMergeDischargeTest extends ADTTest{


  @Test
  def testA28(){
    log.info("sending patientNew with ADT_A28 : id: " + TestVars.patientOneId)
    sendMessageAndExpectResponse(TestVars.patientNewADT28, "MSA|AA|")
  }
  @Test
  def testA05{
    log.info("sending patientNew with ADT_A05 : id: " + TestVars.patientTwoId)
    sendMessageAndExpectResponse(TestVars.patientNewADT05, "MSA|AA|")
  }
  @Test
  def testA08{
    log.info("updating patientTwo")
    sendMessageAndExpectResponse(TestVars.patientUpdateADT_08, "MSA|AA|")
  }
  @Test
  def testA31{
    log.info("updating patientOne")
    sendMessageAndExpectResponse(TestVars.patientUpdateADT_31, "MSA|AA|")
  }
  @Test
  def testA40{
    log.info("merge patientOne into patientTwo")
    sendMessageAndExpectResponse(TestVars.patientMerge, "MSA|AA|")
  }
  @Test
  def testA01{
    log.info("new visit for patientTwo")
    sendMessageAndExpectResponse(TestVars.visitNew, "MSA|AA|")
  }
  @Test
  def testA03 {
    log.info("discharge patientTwo")
    sendMessageAndExpectResponse(TestVars.patientDischarge, "MSA|AA|")
  }

  @Test
  def fail28{
    sendMessageAndExpectError(TestVars.patientNewADT28No_Identity, "Required field missing")
  }
  @Test
  def fail05{
    sendMessageAndExpectError(TestVars.patientNewADT05No_Identity, "Required field missing")
  }
  @Test
  def fail08{
    sendMessageAndExpectError(TestVars.patientUpdateADTNo_Identity_08, "Required field missing")
  }
  @Test
  def fail31{
    sendMessageAndExpectError(TestVars.patientUpdateADTNo_Identity_31, "Required field missing")
  }
  @Test
  def fail40{
    sendMessageAndExpectError(TestVars.patientMergeADTNo_Identifier, "Required field missing")
  }

}
