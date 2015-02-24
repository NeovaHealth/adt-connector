package co.uk.neovahealth.nhADT

import co.uk.neovahealth.nhADT.utils.ConfigHelper
import org.apache.camel.{Header, RoutingSlip}

/**
 * Created by max on 06/08/14.
 */
case class RoutingSlipBean() {

  @RoutingSlip
  def route(@Header("CamelHL7TriggerEvent") msgType:String ): String = {
    ConfigHelper.getRecipientLists.getOrElse(msgType, throw new Exception(s"Config error: $msgType not found in Recipient List Map")).mkString(",")
  }
}
