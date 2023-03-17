package com.example.demo;

import io.opentracing.propagation.TextMap;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Controller;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.header.internals.*;

import datadog.trace.api.DDTags;
import datadog.opentracing.DDTracer;
//import io.opentracing.Tracer;
import io.opentracing.*;
//import io.opentracing.Scope;
//import io.opentracing.util.GlobalTracer;
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

import java.net.http.*;
import java.util.HashMap;
import java.util.Random;
import java.util.List;
import java.util.Map;

//add on 09/29 by wangzz
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;

import javax.servlet.http.HttpServletResponse;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageRequest;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageResponse;
import software.amazon.awssdk.services.comprehend.model.DominantLanguage;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

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

    @RequestMapping(value="/receivesqs",method = RequestMethod.GET)
    @ResponseBody
    public String Receivefromsqs(){
        String retMessage="";
        SqsClient sqsClient=SqsClient.builder().region(Region.AP_NORTHEAST_1).credentialsProvider(ProfileCredentialsProvider.create()).build();
        try{
            GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                    .queueName("jackqueue")
                    .build();
            String queueUrl=sqsClient.getQueueUrl(getQueueRequest).queueUrl();
            ReceiveMessageRequest rmq=ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(5).build();
            ReceiveMessageResponse response = sqsClient.receiveMessage(rmq);
            List<Message> messages=response.messages();

            for(Message s : messages){
                retMessage = s.body();
                DeleteMessageRequest dmr=DeleteMessageRequest.builder().queueUrl("jackqueue").receiptHandle(s.receiptHandle()).build();
                sqsClient.deleteMessage(dmr);
                System.out.println(s.body());
            }

//            response.messages().forEach(message -> {
//                System.out.printf(
//                        "receive message = [%s], message-id = [%s], receipt-handle=[%s]%n",
//                        message.body(),
//                        message.messageId(),
//                        message.receiptHandle()
//                );}
//
//            );
            return retMessage;
        }catch (SqsException e){
            e.getStackTrace();
            e.printStackTrace();
        }
        finally{
            return retMessage;
        }

    }

    private String CombineString(){
        String s1="Hello";
        String s2="This is Jack's Web created by Spring boot";
        logger.debug("this is a debug message");
        logger.info("there is the console message from GET /hello");
        //System.out.println("there is the console message from GET /hello");
        return s1 + ", " + s2;
    }
    @RequestMapping(value = "/sendtosqs/{message}",method = RequestMethod.GET)
    @ResponseBody
    public String sendtosqs(@PathVariable("message") String message){
        //String strMessage=@PathVariable("message");
        SqsClient sqsClient=SqsClient.builder().region(Region.AP_NORTHEAST_1).credentialsProvider(ProfileCredentialsProvider.create()).build();
        try{
            GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                    .queueName("jackqueue")
                    .build();
            String queueUrl=sqsClient.getQueueUrl(getQueueRequest).queueUrl();
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
//                    .messageAttributes(myMap)
//                    .messageGroupId("GroupA_"+lanCode)
//                    .messageDeduplicationId(msg.getId())
//                    .messageBody(msg.getBody())
                    .messageBody(message)
                    .build();

            sqsClient.sendMessage(sendMsgRequest);
            return "message sent to sqs with " + message;
        }catch(SqsException e){
            e.getStackTrace();
            e.printStackTrace();
        }
        finally{
            return "message sent to sqs " + message;
        }
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

    @RequestMapping(value = "/javahttpclient",method = RequestMethod.GET)
    @ResponseBody
    public String javahttpclient() throws Exception{
        String endpoint="http://18.180.59.191:8080/test";
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest.Builder builder=java.net.http.HttpRequest.newBuilder().uri(URI.create(endpoint));
        builder.header("sender","jack");
//        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(URI.create(endpoint)).build();
        java.net.http.HttpRequest request =builder.build();
//        System.out.println(request.headers());
        logger.info("using httpclient to send request");
        HttpHeaders headers=request.headers();
        logger.info(headers.toString());
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        Random r=new java.util.Random();
        int intRandom=r.nextInt(10);
        logger.info("intRandom is " + intRandom);
        if (intRandom < 0) {
            logger.info("log response body");
            return response.body();
        }
        else {
            logger.info("throw new exception");
//            throw new Exception(new RuntimeException());
            final Span span=GlobalTracer.get().activeSpan();
            try {
                tryException();
            }
            catch(Exception e){
                logger.info("I catch the exception");
                String exceptionStackTrace="";
                if (span !=null){
                    StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();
                    for (int i = 0; i < stackTraceElements.length; i++) {
                        StackTraceElement ste=stackTraceElements[i];
                        exceptionStackTrace += ste.getClassName() + ste.getMethodName() + "\n";
                    }
                    span.setTag("exceptionStackTrace",exceptionStackTrace);
                }
            }
            finally{
//                response.headers("httpcode");
                return response.body();
            }
        }
    }

    private void tryException() throws Exception {
        throw new Exception(new RuntimeException());
    }

    @RequestMapping(value = "/javahttpclient2",method = RequestMethod.GET)
    @ResponseBody
    public String javahttpclient2() throws Exception{
        Tracer tracer=GlobalTracer.get();
        Span span=tracer.buildSpan("httpClientSpan").start();
        Scope scope=tracer.activateSpan(span);


        String endpoint="http://35.74.62.42:8080/test";
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest.Builder builder=java.net.http.HttpRequest.newBuilder().uri(URI.create(endpoint));
        builder.header("sender","jack");
//        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(URI.create(endpoint)).build();
//        java.net.http.HttpRequest request =builder.build();
//        System.out.println(request.headers());
        logger.info("using httpclient to send request");


        span.setTag(DDTags.RESOURCE_NAME,"PATH");
        TextMap textMap=new TextMapAdapter(new HashMap<String,String>());
        tracer.inject(span.context(),Format.Builtin.HTTP_HEADERS,textMap);
        textMap.forEach( (header) ->
                builder.header(header.getKey().toString(),header.getValue().toString()));


//        HttpHeaders headers=request.headers();
//        logger.info(headers.toString());
//        Map<String, List<String>> headerMap=new java.util.HashMap<>(headers.map());
        java.net.http.HttpRequest request =builder.build();
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        span.finish();
        return response.body();
    }


    @RequestMapping(value = "/callothers",method = RequestMethod.GET)
    @ResponseBody
    public String callothers(){
        String endpoint="http://35.74.62.42:8080/test";
        URI uri= UriComponentsBuilder.fromHttpUrl(endpoint).build().encode().toUri();
        RestTemplate restTemplate=new RestTemplate();
        String data=restTemplate.getForObject(uri,String.class);
        System.out.println(data);
        return "test";
    }

    @RequestMapping(value = "/errtest",method = RequestMethod.GET)
    @ResponseBody
    public String errtest(HttpServletResponse response) throws IOException {
        int i=10;
        int retInt;
//        retInt=i/0;
        try{
            retInt=i/0;
        }catch (Exception e){
            retInt=11;
            //even the exception is caught, we still want to response 500 code
            response.setStatus(500);
//            response.getWriter().append("divided by 0 error");
        }

        return "error happened and i is " + retInt;
    }

}
