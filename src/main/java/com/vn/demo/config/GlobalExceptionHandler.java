package com.vn.demo.config;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.exceptions.TemplateProcessingException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleAllExceptions(Exception ex) {
        ModelAndView modelAndView = new ModelAndView("error");
        
        modelAndView.addObject("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        modelAndView.addObject("status", "500");
        modelAndView.addObject("message", ex.getMessage());
        modelAndView.addObject("path", "N/A");
        modelAndView.addObject("exception", stackTraceToString(ex));
        
        return modelAndView;
    }
    
    @ExceptionHandler({TemplateInputException.class, TemplateProcessingException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleTemplateExceptions(Exception ex) {
        ModelAndView modelAndView = new ModelAndView("error");
        
        modelAndView.addObject("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        modelAndView.addObject("status", "500");
        modelAndView.addObject("message", "Lỗi khi xử lý template: " + ex.getMessage());
        modelAndView.addObject("path", "N/A");
        modelAndView.addObject("exception", stackTraceToString(ex));
        
        return modelAndView;
    }
    
    // Helper to get the stack trace as a string
    private String stackTraceToString(Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.toString()).append("\n");
        
        for (StackTraceElement element : ex.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        
        return sb.toString();
    }
} 