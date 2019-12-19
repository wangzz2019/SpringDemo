package com.example.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        return s1 + ", " + s2;
    }
}
