package com.vn.demo.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/test")
public class TestController {

    @GetMapping("/redirect")
    public String testRedirect() {
        System.out.println("Thực hiện test redirect đến /dashboard");
        return "redirect:/dashboard";
    }
    
    @GetMapping("/info")
    @ResponseBody
    public String getSessionInfo(HttpServletRequest request, HttpServletResponse response) {
        StringBuilder info = new StringBuilder();
        info.append("Session ID: ").append(request.getSession().getId()).append("<br>");
        info.append("Is new session: ").append(request.getSession().isNew()).append("<br>");
        info.append("Server name: ").append(request.getServerName()).append("<br>");
        info.append("Context path: ").append(request.getContextPath()).append("<br>");
        
        return info.toString();
    }
}
