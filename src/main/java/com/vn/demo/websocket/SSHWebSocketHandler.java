package com.vn.demo.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import com.vn.demo.rest.OperatorController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vn.demo.dao.DeviceRepository;

@Component
public class SSHWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SSHWebSocketHandler.class);
    
    @Autowired
    private OperatorController operatorController;
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToSSHSessionMap = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Bọc session với decorator đồng thời để bảo đảm thread-safe
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session, 1000, 8192);
        sessions.put(session.getId(), concurrentSession);
        
        // Gửi thông báo chào mừng
        concurrentSession.sendMessage(new TextMessage(
                "{\"type\":\"connected\",\"message\":\"Kết nối WebSocket đã được thiết lập\"}"));
        
        logger.info("Phiên WebSocket đã được thiết lập: {}", session.getId());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // Phân tích thông điệp nhận được
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");
            
            WebSocketSession concurrentSession = sessions.get(session.getId());
            if (concurrentSession == null) {
                logger.error("Không tìm thấy phiên: {}", session.getId());
                return;
            }
            
            logger.info("Nhận thông điệp loại: {}", type);
            
            if ("connect".equals(type)) {
                // Bắt đầu phiên SSH mới
                String deviceId = (String) payload.get("deviceId");
                String host = (String) payload.get("host");
                String username = (String) payload.get("username");
                String password = (String) payload.get("password");
                String sshSessionId = (String) payload.get("sessionId");
                
                logger.info("Đang kết nối đến: {}@{} với ID phiên: {}", username, host, sshSessionId);
                
                // Ghi log thông tin cấu hình cổng để gỡ lỗi
                logger.info("Sử dụng cổng SSH 2222 (ánh xạ từ cổng 22 của container)");
                try {
                    concurrentSession.sendMessage(new TextMessage(
                            "{\"type\":\"info\",\"message\":\"Sử dụng cổng SSH 2222 (được ánh xạ từ cổng 22 của container)\"}"));
                } catch (Exception e) {
                    logger.error("Lỗi khi gửi thông tin cổng", e);
                }
                
                // Chuyển đổi deviceId sang số nguyên nếu có
                Integer deviceIdInt = null;
                if (deviceId != null && !deviceId.isEmpty()) {
                    try {
                        deviceIdInt = Integer.parseInt(deviceId);
                        // Thử lấy địa chỉ IP từ thiết bị nếu host không phải là IP
                        if (deviceIdInt != null && (host == null || !host.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
                            deviceRepository.findById(deviceIdInt).ifPresent(device -> {
                                if (device.getIpAddress() != null && !device.getIpAddress().isEmpty()) {
                                    try {
                                        logger.info("Sử dụng IP thiết bị: {} thay vì hostname: {}", device.getIpAddress(), host);
                                        concurrentSession.sendMessage(new TextMessage(
                                                "{\"type\":\"info\",\"message\":\"Sử dụng IP: " + device.getIpAddress() + "\"}"));
                                    } catch (Exception e) {
                                        logger.error("Lỗi khi gửi thông điệp", e);
                                    }
                                }
                            });
                        }
                    } catch (NumberFormatException e) {
                        logger.error("ID thiết bị không hợp lệ: {}", deviceId, e);
                    }
                }
                
                // Lưu ánh xạ giữa phiên websocket và phiên SSH
                sessionToSSHSessionMap.put(session.getId(), sshSessionId);
                
                // Xác định cổng SSH
                int sshPort = 2222; // Sử dụng cổng ánh xạ 2222 thay vì cổng mặc định 22
                
                // Thông báo về cổng SSH đang sử dụng
                try {
                    concurrentSession.sendMessage(new TextMessage(
                            "{\"type\":\"info\",\"message\":\"Đang kết nối đến cổng SSH: " + sshPort + "\"}"));
                    logger.info("Thông báo cho người dùng về cổng SSH: {}", sshPort);
                } catch (Exception e) {
                    logger.error("Lỗi khi gửi thông tin cổng SSH", e);
                }
                
                // Thiết lập kết nối SSH với cổng được chỉ định
                operatorController.setupSSHShellConnection(sshSessionId, host, username, password, concurrentSession, sshPort);
                
                // Gửi xác nhận
                concurrentSession.sendMessage(new TextMessage(
                        "{\"type\":\"connected\",\"message\":\"Đang kết nối SSH...\"}"));
                
            } else if ("command".equals(type)) {
                // Gửi lệnh đến phiên SSH hiện có
                String command = (String) payload.get("command");
                String sshSessionId = sessionToSSHSessionMap.get(session.getId());
                
                logger.debug("Nhận lệnh: '{}' cho phiên: {}", command, sshSessionId);
                
                if (sshSessionId != null) {
                    // Chuyển tiếp lệnh đến phiên SSH qua controller
                    try {
                        operatorController.sendCommandToSSHSession(sshSessionId, command);
                        logger.debug("Đã gửi lệnh đến phiên SSH: {}", sshSessionId);
                    } catch (Exception e) {
                        logger.error("Lỗi khi gửi lệnh đến phiên SSH: {}", sshSessionId, e);
                        concurrentSession.sendMessage(new TextMessage(
                                "{\"type\":\"error\",\"message\":\"Lỗi khi gửi lệnh: " + e.getMessage() + "\"}"));
                    }
                } else {
                    logger.warn("Không tìm thấy phiên SSH cho phiên WebSocket: {}", session.getId());
                    concurrentSession.sendMessage(new TextMessage(
                            "{\"type\":\"error\",\"message\":\"Không tìm thấy phiên SSH\"}"));
                }
            }
        } catch (Exception e) {
            logger.error("Lỗi xử lý thông điệp WebSocket", e);
            WebSocketSession concurrentSession = sessions.get(session.getId());
            if (concurrentSession != null && concurrentSession.isOpen()) {
                concurrentSession.sendMessage(new TextMessage(
                        "{\"type\":\"error\",\"message\":\"Lỗi: " + e.getMessage() + "\"}"));
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("Phiên WebSocket đã đóng: {} với trạng thái: {}", session.getId(), status);
        
        String sshSessionId = sessionToSSHSessionMap.get(session.getId());
        if (sshSessionId != null) {
            // Dọn dẹp phiên SSH
            operatorController.closeSSHSession(sshSessionId);
            sessionToSSHSessionMap.remove(session.getId());
        }
        sessions.remove(session.getId());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Lỗi kết nối trong phiên WebSocket: {}", session.getId(), exception);
        
        WebSocketSession concurrentSession = sessions.get(session.getId());
        if (concurrentSession != null && concurrentSession.isOpen()) {
            concurrentSession.sendMessage(new TextMessage(
                    "{\"type\":\"error\",\"message\":\"Lỗi kết nối: " + exception.getMessage() + "\"}"));
        }
        
        super.handleTransportError(session, exception);
    }
} 