package com.tactix4.t4ADT;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v23.message.ADR_A19;
import ca.uhn.hl7v2.model.v23.message.ADT_A01;
import ca.uhn.hl7v2.model.v23.segment.MSA;
import ca.uhn.hl7v2.model.v23.segment.MSH;
import ca.uhn.hl7v2.model.v23.segment.QRD;

// START SNIPPET: e2
public class MyHL7BusinessLogic {

// This is a plain POJO that has NO imports whatsoever on Apache Camel.
// its a plain POJO only importing the HAPI library so we can much easier work with the HL7 format.

    public Message handleA19(Message msg) throws Exception {
// here you can have your business logic for A19 messages
// just return the same dummy response
        return createADR19Message();
    }

    public Message handleA01(Message msg) throws Exception {
// here you can have your business logic for A01 messages
// just return the same dummy response
        return createADT01Message();
    }


// END SNIPPET: e2

    private static Message createADR19Message() throws Exception {
        ADR_A19 adr = new ADR_A19();

// Populate the MSH Segment
        MSH mshSegment = adr.getMSH();
        mshSegment.getFieldSeparator().setValue("|");
        mshSegment.getEncodingCharacters().setValue("^~\\&");
        mshSegment.getDateTimeOfMessage().getTimeOfAnEvent().setValue("200701011539");
        mshSegment.getSendingApplication().getNamespaceID().setValue("MYSENDER");
        mshSegment.getSequenceNumber().setValue("123");
        mshSegment.getMessageType().getMessageType().setValue("ADR");
        mshSegment.getMessageType().getTriggerEvent().setValue("A19");

// Populate the PID Segment
        MSA msa = adr.getMSA();
        msa.getAcknowledgementCode().setValue("AA");
        msa.getMessageControlID().setValue("123");

        QRD qrd = adr.getQRD();
        qrd.getQueryDateTime().getTimeOfAnEvent().setValue("20080805120000");

        return adr.getMessage();
    }

    private static Message createADT01Message() throws Exception {
        ADT_A01 adt = new ADT_A01();

// Populate the MSH Segment
        MSH mshSegment = adt.getMSH();
        mshSegment.getFieldSeparator().setValue("|");
        mshSegment.getEncodingCharacters().setValue("^~\\&");
        mshSegment.getDateTimeOfMessage().getTimeOfAnEvent().setValue("200701011539");
        mshSegment.getSendingApplication().getNamespaceID().setValue("MYSENDER");
        mshSegment.getSequenceNumber().setValue("123");
        mshSegment.getMessageType().getMessageType().setValue("ADT");
        mshSegment.getMessageType().getTriggerEvent().setValue("A01");


        return adt;
    }

}
