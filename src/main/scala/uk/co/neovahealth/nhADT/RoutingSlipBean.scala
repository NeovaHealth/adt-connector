package uk.co.neovahealth.nhADT

import org.apache.camel.{Header, RoutingSlip}
import uk.co.neovahealth.nhADT.utils.ConfigHelper

/**
 * Created by max on 06/08/14.
 */
case class RoutingSlipBean() {

  @RoutingSlip
  def route(@Header("CamelHL7TriggerEvent") msgType:String ): String = {
    ConfigHelper.getRoutingSlips.getOrElse(msgType, throw new Exception(s"Config error: $msgType not found in Recipient List Map")).mkString(",")
  }
}
