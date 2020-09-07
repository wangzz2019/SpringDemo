package com.example.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Controller
public class HelloController {
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(this.getClass());
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
    @GetMapping("/user/{id}")
    @ResponseBody
    public String getUserid(@PathVariable("id") String id){
        String s1= "user id is: ";
        String s2=id;
        logger.info("We have the user id: " + id);
        return s1+s2;
    }
    @RequestMapping(value = "/callothers",method = RequestMethod.GET)
    @ResponseBody
    public String callothers(){
        String endpoint="http://52.196.214.170:5123/test";
        URI uri= UriComponentsBuilder.fromHttpUrl(endpoint).build().encode().toUri();
        RestTemplate restTemplate=new RestTemplate();
        String data=restTemplate.getForObject(uri,String.class);
        System.out.println(data);
        return "test";
    }
}
