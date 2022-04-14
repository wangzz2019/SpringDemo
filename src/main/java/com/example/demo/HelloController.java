package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Controller;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;

import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.header.internals.*;

import datadog.trace.api.DDTags;
import datadog.opentracing.DDTracer;
import io.opentracing.Tracer;
import io.opentracing.*;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.GlobalTracer;
import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.Trace;
import datadog.opentracing.DDTracer;

// Imports the Google Cloud client library
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.Statement;


@Controller
public class HelloController {
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(this.getClass());
    @Autowired
    private KafkaTemplate<String,Object> kafkaTemplate;

    @RequestMapping(value = "/hello",method = RequestMethod.GET)
    @ResponseBody
    public String sayHello() {
        return CombineString();
    }
    private String CombineString(){
        String s1="Hello";
        String s2="This is Jack's Web created by Spring boot";
        logger.debug("this is a debug message");
        logger.info("there is the console message from GET /hello");
        //System.out.println("there is the console message from GET /hello");
        return s1 + ", " + s2;
    }

    @RequestMapping(value = "/spanner",method = RequestMethod.GET)
    @ResponseBody
    //@Trace(resourceName="/spanner")
    public String callspanner() {
        Tracer tracer = GlobalTracer.get();
        Span span=tracer.buildSpan("callspanner").withTag(DDTags.SERVICE_NAME,"GoogleSpanner")
                .withTag(DDTags.RESOURCE_NAME,"/spanner")
                .start();
        try (Scope scope= tracer.activateSpan(span)){
            span.setTag("my.tag","value");
            return spanner();
        }
        catch(Exception e){}
        finally {
            span.finish();
        }
        return "";
    }
    private String spanner(){
        // Instantiates a client
        SpannerOptions options = SpannerOptions.newBuilder().build();
        Spanner spanner = options.getService();

        String instanceId = "jacktest";
        String databaseId = "testdb";
        try {
            // Creates a database client
            DatabaseClient dbClient =
                    spanner.getDatabaseClient(DatabaseId.of(options.getProjectId(), instanceId, databaseId));
            // Queries the database
            ResultSet resultSet = dbClient.singleUse().executeQuery(Statement.of("SELECT * from testtb"));

            System.out.println("\n\nResults:");
            // Prints the results
            while (resultSet.next()) {
                System.out.printf("%d\n\n", resultSet.getLong(0));
            }
        } finally {
            // Closes the client which will free up the resources used
            spanner.close();
        }
        return "call spanner successfully";
    }


    @GetMapping("/user/{id}")
    @ResponseBody
    public String getUserid(@PathVariable("id") String id){
        String s1= "user id is: ";
        String s2=id;
        logger.info("We have the user id: " + id);
        return s1+s2;
    }
    //call kafka
    @GetMapping("/message/send")
    @ResponseBody
    public boolean kafkatest(@RequestParam String message){
        //print trace_id, span_id
        Tracer tracer=GlobalTracer.get();
//        System.out.println("traceid is :" + CorrelationIdentifier.getTraceId());
//        System.out.println("spanid is :" + CorrelationIdentifier.getSpanId());
        String tid=CorrelationIdentifier.getTraceId();
        String sid=CorrelationIdentifier.getSpanId();
        String keystring=tid + "," + sid;
        kafkaTemplate.send("topic-name",keystring,message);
        return true;
    }

    //call kafka
    @GetMapping("/messageandheader/send")
    @ResponseBody
    public String kafkaheader(@RequestParam String message,String header){
        ProducerRecord<String, Object> record = new ProducerRecord<String, Object>("topic-name", null, message);
        record.headers().add(new RecordHeader("headerkey", header.getBytes()));
//        record.headers().add(new RecordHeader("type", "record_created".getBytes()));
//        producer.send(record);
        kafkaTemplate.send(record);
        return "send message with header";
    }

    @RequestMapping(value = "/callothers",method = RequestMethod.GET)
    @ResponseBody
    public String callothers(){
        String endpoint="http://18.180.59.191:8081/test";
        URI uri= UriComponentsBuilder.fromHttpUrl(endpoint).build().encode().toUri();
        RestTemplate restTemplate=new RestTemplate();
        String data=restTemplate.getForObject(uri,String.class);
        System.out.println(data);
        return "test";
    }
}
