package com.tactix4.t4ADT;

import ca.uhn.hl7v2.validation.ValidationContext;
import ca.uhn.hl7v2.validation.impl.DefaultValidation;
import org.apache.camel.builder.RouteBuilder;

/**
 * @author max@tactix4.com
 *         06/09/2013
 */
public class ADTInJRoute extends RouteBuilder{
    @Override
    public void configure() throws Exception {       // Use standard or define your own validation rules
   ValidationContext defaultContext = new DefaultValidation();

//      .onException(Exception.class)
//         .handled(true)
//         .transform(ack()) // auto-generates negative ack because of exception in Exchange
//         .end()
//      .validate(messageConformsTo(defaultContext))
      // do something meaningful here
      // acknowledgement
//      .transform(ack());
    }
}
