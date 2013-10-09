package com.tactix4.t4ADT

/**
 * Tests the tersermap and associated validation functionality
 * @author max@tactix4.com
 * Date: 26/09/13
 */


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.{TestContextManager, ContextConfiguration}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.scalatest.matchers.ShouldMatchers
import ca.uhn.hl7v2.model.v24.message.{ADT_A15, ADT_A01}
import ca.uhn.hl7v2.util.Terser


@ContextConfiguration(locations=Array("classpath:META-INF/spring/testBeans.xml"))
class TerserMapTest extends FunSuite with ShouldMatchers{

  val configPath = "src/test/resources/ADT_A01.properties"

  @Autowired val route :ADTInRoute  = null

  new TestContextManager(this.getClass).prepareTestInstance(this)

  val testMessage = new ADT_A01()
  testMessage.initQuickstart("ADT", "A28", "P")
  val terser = new Terser(testMessage)
  terser.set("PID-5-1", "Bobkins")
  terser.set("PID-5-2", "Bob")
  terser.set("PID-5-3", null)
  terser.set("PID-7-1", "19850101000000")
  terser.set("PID-8", "M")
  val mappings = route.getMappings(terser)


  val testFailMessage = new ADT_A15()
  testFailMessage.initQuickstart("ADT", "A15", "P")
  val failTerser = new Terser(testFailMessage)
  val failMappings = route.getMappings(failTerser)

  test("read from the terserMap"){
    assert(mappings.flatMap(m => route.checkAttribute(m,"firstName",terser)).isSuccess)
  }
  test("generate error on non existent message type in terserMap"){
    failMappings.flatMap(m => route.checkAttribute(m,"firstName",failTerser)).fold(
      l => l.head should equal ("Could not find terser configuration for messages of type: A15"),
      x => fail("did not fail: " + x))
  }
  test("generate error on non existent message attribute in terserMap"){
    mappings.flatMap(m => route.checkAttribute(m,"middleName",terser)).fold(
      l => l.head should equal ("Could not find attribute: middleName in terser configuration"),
      _ => fail("did not fail"))
  }
  test("generate error on empty message attribute in terserMap"){
    mappings.flatMap(m => route.checkAttribute(m,"middleName",terser)).fold(
      l => l.head should equal ("Could not find attribute: middleName in terser configuration"),
      _ => fail("did not fail"))
  }
  test("generate a failure on an invalid date") {
    val invalidDate = "0o8ijasdf"
    route.checkDate(invalidDate).fold(
    l => l.head should startWith ("Invalid format: \"" + invalidDate +"\""),
    _ => fail("did not fail")
    )
  }
  test("parse a valid date") {
    val validDate = "20130801001000"
    assert(route.checkDate(validDate).isSuccess)
   }

  test("generate a failure on an invalid terserPath") {
    mappings.flatMap(m => route.checkAttribute(m, "terserFail",terser)).fold(
    l => l.head should include regex "The pattern .* is not valid",
    _ => fail("did not fail")
    )
  }

}
