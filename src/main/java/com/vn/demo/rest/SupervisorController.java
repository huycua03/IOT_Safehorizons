package com.vn.demo.rest;

import com.vn.demo.dao.DeviceRepository;
import com.vn.demo.dao.SessionRepository;
import com.vn.demo.dao.UserRepository;
import com.vn.demo.entity.Device;
import com.vn.demo.entity.DeviceStatusHistory;
import com.vn.demo.entity.Log;
import com.vn.demo.entity.Session;
import com.vn.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequestMapping("/supervisor")
@PreAuthorize("hasAuthority('ROLE_SUPERVISOR')")
public class SupervisorController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        try {
            // Get current logged in supervisor
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Optional<User> supervisorOpt = userRepository.findByUser_name(username);
            
            if (supervisorOpt.isPresent()) {
                User supervisor = supervisorOpt.get();
                model.addAttribute("supervisor", supervisor);
                
                // Lấy danh sách phiên đang hoạt động từ cơ sở dữ liệu và bộ nhớ
                List<Map<String, Object>> activeSessions = getAllActiveSessions();
                model.addAttribute("activeSessions", activeSessions);
                
                // Đếm tổng số thiết bị
                long deviceCount = deviceRepository.count();
                model.addAttribute("deviceCount", deviceCount);
                
                // Đếm operators đang hoạt động (có phiên SSH)
                Set<String> activeOperators = new HashSet<>();
                // Lấy danh sách operators từ database có phiên ACTIVE
                List<User> dbActiveOperators = userRepository.findOperatorsWithActiveSessions();
                for (User operator : dbActiveOperators) {
                    if (operator.getUser_name() != null) {
                        activeOperators.add(operator.getUser_name());
                    }
                }
                
                // Thêm operators từ phiên memory
                for (Map<String, Object> session : activeSessions) {
                    if (session.get("operatorName") != null && !"N/A".equals(session.get("operatorName"))) {
                        activeOperators.add((String) session.get("operatorName"));
                    }
                }
                
                System.out.println("Active operators: " + activeOperators);
                model.addAttribute("activeOperatorsCount", activeOperators.size());
                
                // Đếm lệnh đã thực thi (giả định)
                model.addAttribute("commandsExecutedToday", activeSessions.size() + 15);
            } else {
                model.addAttribute("errorMessage", "Không tìm thấy thông tin supervisor!");
            }
            
            return "supervisor-dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Lỗi khi tải dashboard: " + e.getMessage());
            return "supervisor-dashboard";
        }
    }
    
    @PostMapping("/terminate-session")
    public String terminateSession(@RequestParam String sessionId, RedirectAttributes redirectAttributes) {
        try {
            // Gọi phương thức closeSSHSession của OperatorController
            callCloseSSHSession(sessionId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã kết thúc phiên " + sessionId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi kết thúc phiên: " + e.getMessage());
        }
        return "redirect:/supervisor/dashboard";
    }
    
    @GetMapping("/session-detail/{sessionId}")
    public String getSessionDetail(@PathVariable String sessionId, Model model) {
        try {
            System.out.println("Getting details for session: " + sessionId);
            
            // Enable debug mode (for testing only)
            boolean debugMode = true;
            model.addAttribute("debug", debugMode);
            
            // Tìm thông tin phiên từ ConcurrentHashMap của OperatorController
            Map<String, Object> sessionInfo = getSessionInfoFromOperatorController(sessionId);
            
            if (sessionInfo != null) {
                System.out.println("Session info found: " + sessionInfo);
                
                // Add session info to model
                model.addAttribute("sessionInfo", sessionInfo);
                
                if (sessionInfo.containsKey("dbSessionId")) {
                    Integer dbSessionId = (Integer) sessionInfo.get("dbSessionId");
                    System.out.println("Database session ID: " + dbSessionId);
                    
                    // Nếu có thông tin dbSessionId, lấy chi tiết từ database
                    Optional<Session> dbSessionOpt = sessionRepository.findById(dbSessionId);
                    
                    if (dbSessionOpt.isPresent()) {
                        Session dbSession = dbSessionOpt.get();
                        model.addAttribute("dbSession", dbSession);
                        
                        // Lấy logs từ repository để tránh LazyInitializationException
                        List<Log> logs = sessionRepository.findLogsById(dbSessionId);
                        if (logs != null && !logs.isEmpty()) {
                            model.addAttribute("logs", logs);
                            System.out.println("Found " + logs.size() + " logs");
                            
                            // Print first log for debugging
                            if (logs.size() > 0) {
                                Log firstLog = logs.get(0);
                                System.out.println("Sample log: action=" + firstLog.getAction() + 
                                                 ", timestamp=" + firstLog.getTimestamp() + 
                                                 ", detail=" + firstLog.getDetail());
                            }
                        } else {
                            System.out.println("No logs found for this session");
                        }
                        
                        // Lấy device status history từ repository
                        List<DeviceStatusHistory> history = sessionRepository.findDeviceStatusHistoryById(dbSessionId);
                        if (history != null && !history.isEmpty()) {
                            model.addAttribute("deviceStatusHistory", history);
                            System.out.println("Found " + history.size() + " device status entries");
                            
                            // Print first status for debugging
                            if (history.size() > 0) {
                                DeviceStatusHistory firstStatus = history.get(0);
                                System.out.println("Sample status: status=" + firstStatus.getStatus() + 
                                                 ", timestamp=" + firstStatus.getTimestamp() + 
                                                 ", notes=" + firstStatus.getNotes());
                            }
                        } else {
                            System.out.println("No device status history found for this session");
                        }
                    } else {
                        System.out.println("No database session found with ID: " + dbSessionId);
                    }
                } else {
                    System.out.println("No dbSessionId found in session info");
                }
            } else {
                System.out.println("No session info found for ID: " + sessionId);
                model.addAttribute("errorMessage", "Không tìm thấy thông tin phiên");
            }
            
            // Debug print of all attributes in the model
            System.out.println("Model attributes:");
            for (String attributeName : model.asMap().keySet()) {
                Object value = model.asMap().get(attributeName);
                System.out.println("  " + attributeName + ": " + (value != null ? value.getClass().getSimpleName() : "null"));
            }
            
            // Thêm sessionId vào model
            model.addAttribute("sessionId", sessionId);
            
            return "session-detail";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Lỗi khi lấy chi tiết phiên: " + e.getMessage());
            return "redirect:/supervisor/dashboard";
        }
    }
    
    /**
     * Lấy danh sách tất cả các phiên đang hoạt động, kết hợp từ cả database và memory
     */
    private List<Map<String, Object>> getAllActiveSessions() {
        List<Map<String, Object>> sessionsList = new ArrayList<>();
        Map<Integer, Map<String, Object>> deviceSessionMap = new HashMap<>(); // Map để theo dõi session theo deviceId
        
        try {
            // 1. Lấy các phiên từ database trước
            List<Session> dbSessions = sessionRepository.findByStatus("ACTIVE");
            System.out.println("Found " + dbSessions.size() + " active sessions in database");
            
            // Thêm các phiên từ database vào map theo deviceId
            for (Session dbSession : dbSessions) {
                Map<String, Object> sessionInfo = new HashMap<>();
                sessionInfo.put("sessionId", "db-" + dbSession.getSessionId());
                sessionInfo.put("dbSessionId", dbSession.getSessionId());
                sessionInfo.put("operatorName", dbSession.getOperator().getUser_name());
                sessionInfo.put("deviceName", dbSession.getDevice().getDeviceName());
                sessionInfo.put("deviceId", dbSession.getDevice().getDeviceId());
                sessionInfo.put("startTime", Date.from(dbSession.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
                sessionInfo.put("status", dbSession.getStatus());
                
                // Thêm thời gian kết thúc nếu có
                if (dbSession.getEndTime() != null) {
                    sessionInfo.put("endTime", Date.from(dbSession.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
                }
                
                // Lưu vào map theo deviceId
                deviceSessionMap.put(dbSession.getDevice().getDeviceId(), sessionInfo);
            }

            // 2. Lấy các phiên từ bộ nhớ (SSH_SESSIONS)
            Map<String, Object> sessionsFromMemory = getActiveSessionsFromMemory();
            List<Map<String, Object>> memSessions = sessionsFromMemory.values().stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .filter(session -> "ACTIVE".equals(session.get("status"))) // Chỉ lấy các phiên ACTIVE
                .collect(java.util.stream.Collectors.toList());
            
            System.out.println("Found " + memSessions.size() + " active sessions in memory");
            
            // Xử lý các phiên từ bộ nhớ
            for (Map<String, Object> memSession : memSessions) {
                Integer deviceId = null;
                
                // Lấy deviceId
                if (memSession.containsKey("deviceId")) {
                    Object deviceIdObj = memSession.get("deviceId");
                    if (deviceIdObj instanceof Integer) {
                        deviceId = (Integer) deviceIdObj;
                    } else if (deviceIdObj instanceof String) {
                        try {
                            deviceId = Integer.parseInt((String) deviceIdObj);
                        } catch (NumberFormatException e) {
                            // Bỏ qua nếu không parse được
                        }
                    }
                }
                
                // Xử lý case phiên từ memory với deviceId đã có trong database
                if (deviceId != null && deviceSessionMap.containsKey(deviceId)) {
                    Map<String, Object> existingSession = deviceSessionMap.get(deviceId);
                    
                    // Bổ sung thông tin từ memory mà database không có
                    if (!existingSession.containsKey("sshSession") && memSession.containsKey("sessionId")) {
                        existingSession.put("sshSession", memSession.get("sessionId"));
                    }
                    
                    // Nếu database không có operatorName nhưng memory có
                    if (("N/A".equals(existingSession.get("operatorName")) || existingSession.get("operatorName") == null) 
                        && memSession.containsKey("operatorName") && memSession.get("operatorName") != null 
                        && !"N/A".equals(memSession.get("operatorName"))) {
                        existingSession.put("operatorName", memSession.get("operatorName"));
                    }
                }
                // Xử lý case phiên chỉ có trong memory
                else if (deviceId != null) {
                    // Đảm bảo có operatorName
                    if (!memSession.containsKey("operatorName") || memSession.get("operatorName") == null) {
                        memSession.put("operatorName", "N/A");
                    }
                    
                    deviceSessionMap.put(deviceId, memSession);
                }
            }
            
            // Chuyển tất cả từ map sang list
            sessionsList.addAll(deviceSessionMap.values());
            System.out.println("Combined active sessions: " + sessionsList.size());
            
        } catch (Exception e) {
            e.printStackTrace();
            // Xử lý khi không thể truy cập trường hoặc có lỗi khác
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("sessionId", "error");
            errorInfo.put("deviceName", "Lỗi khi lấy dữ liệu");
            errorInfo.put("deviceId", "");
            errorInfo.put("operatorName", "");
            errorInfo.put("startTime", new Date());
            errorInfo.put("status", "Lỗi: " + e.getMessage());
            sessionsList.add(errorInfo);
        }
        
        return sessionsList;
    }
    
    /**
     * Lấy thông tin chi tiết một phiên
     */
    private Map<String, Object> getSessionInfoFromOperatorController(String sessionId) {
        try {
            System.out.println("Looking for session info with ID: " + sessionId);
            
            // 1. First try to get from SSH_SESSIONS map via reflection
            try {
                Field sshSessionsField = OperatorController.class.getDeclaredField("SSH_SESSIONS");
                sshSessionsField.setAccessible(true);
                Object fieldValue = sshSessionsField.get(null);
                
                if (fieldValue != null && fieldValue instanceof ConcurrentHashMap) {
                    ConcurrentHashMap<String, Map<String, Object>> sshSessions = 
                        (ConcurrentHashMap<String, Map<String, Object>>) fieldValue;
                    
                    if (sshSessions != null) {
                        if (sshSessions.containsKey(sessionId)) {
                            Map<String, Object> sessionInfo = new HashMap<>(sshSessions.get(sessionId));
                            System.out.println("Found session in SSH_SESSIONS: " + sessionInfo);
                            
                            // Make sure sessionId is included
                            if (!sessionInfo.containsKey("sessionId")) {
                                sessionInfo.put("sessionId", sessionId);
                            }
                            
                            return sessionInfo;
                        } else {
                            // Nếu không tìm thấy trong SSH_SESSIONS, có thể phiên đã kết thúc
                            System.out.println("Session ID not found in SSH_SESSIONS. Available keys: " + sshSessions.keySet());
                        }
                    } else {
                        System.out.println("SSH_SESSIONS map is null");
                    }
                } else {
                    System.out.println("SSH_SESSIONS field value is null or not a ConcurrentHashMap");
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                System.out.println("Error accessing SSH_SESSIONS via reflection: " + e.getMessage());
            }
            
            // 2. If not found or error, try the database approach for "db-" prefixed IDs
            if (sessionId != null && sessionId.startsWith("db-")) {
                try {
                    int dbSessionId = Integer.parseInt(sessionId.substring(3));
                    System.out.println("Looking up database session with ID: " + dbSessionId);
                    
                    Optional<Session> dbSessionOpt = sessionRepository.findById(dbSessionId);
                    
                    if (dbSessionOpt.isPresent()) {
                        Session dbSession = dbSessionOpt.get();
                        Map<String, Object> sessionInfo = new HashMap<>();
                        sessionInfo.put("sessionId", sessionId);
                        sessionInfo.put("dbSessionId", dbSession.getSessionId());
                        
                        // Add null checks for all fields
                        if (dbSession.getOperator() != null) {
                            sessionInfo.put("operatorName", dbSession.getOperator().getUser_name());
                        } else {
                            sessionInfo.put("operatorName", "N/A");
                        }
                        
                        if (dbSession.getDevice() != null) {
                            sessionInfo.put("deviceName", dbSession.getDevice().getDeviceName());
                            sessionInfo.put("deviceId", dbSession.getDevice().getDeviceId());
                        } else {
                            sessionInfo.put("deviceName", "N/A");
                            sessionInfo.put("deviceId", "N/A");
                        }
                        
                        if (dbSession.getStartTime() != null) {
                            sessionInfo.put("startTime", Date.from(dbSession.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
                        } else {
                            sessionInfo.put("startTime", new Date());
                        }
                        
                        if (dbSession.getEndTime() != null) {
                            sessionInfo.put("endTime", Date.from(dbSession.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
                        }
                        
                        sessionInfo.put("status", dbSession.getStatus() != null ? dbSession.getStatus() : "UNKNOWN");
                        
                        System.out.println("Found session in database: " + sessionInfo);
                        return sessionInfo;
                    } else {
                        System.out.println("No database session found with ID: " + dbSessionId);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error parsing database session ID: " + e.getMessage());
                }
            }
            
            // 3. Special handling for SSH session format (ssh-timestamp-deviceId)
            else if (sessionId != null && sessionId.startsWith("ssh-")) {
                String[] parts = sessionId.split("-");
                if (parts.length >= 3) {
                    try {
                        String timestamp = parts[1];
                        String deviceId = parts[2];
                        
                        System.out.println("Parsed SSH session ID: timestamp=" + timestamp + ", deviceId=" + deviceId);
                        
                        // Try to find device info
                        Integer deviceIdInt = Integer.parseInt(deviceId);
                        Optional<Device> deviceOpt = deviceRepository.findById(deviceIdInt);
                        
                        // Check if this session is linked in the database to get the correct status
                        List<Session> sessions = sessionRepository.findByDeviceIdAndActiveTimestamp(
                            deviceIdInt, 
                            java.time.LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(Long.parseLong(timestamp)),
                                java.time.ZoneId.systemDefault()
                            )
                        );
                        
                        Map<String, Object> sessionInfo = new HashMap<>();
                        sessionInfo.put("sessionId", sessionId);
                        sessionInfo.put("deviceId", deviceIdInt);
                        sessionInfo.put("deviceName", deviceOpt.isPresent() ? deviceOpt.get().getDeviceName() : "Device " + deviceId);
                        sessionInfo.put("startTime", new Date(Long.parseLong(timestamp)));
                        
                        // Nếu tìm thấy phiên trong database, sử dụng thông tin từ database
                        if (!sessions.isEmpty()) {
                            Session dbSession = sessions.get(0);
                            sessionInfo.put("dbSessionId", dbSession.getSessionId());
                            sessionInfo.put("status", dbSession.getStatus());
                            sessionInfo.put("operatorName", dbSession.getOperator().getUser_name());
                            
                            if (dbSession.getEndTime() != null) {
                                sessionInfo.put("endTime", Date.from(dbSession.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
                            }
                        } else {
                            // Nếu không tìm thấy trong SSH_SESSIONS, có thể phiên đã kết thúc
                            sessionInfo.put("status", "CLOSED");
                            sessionInfo.put("operatorName", "N/A");
                        }
                        
                        System.out.println("Created info for SSH session: " + sessionInfo);
                        return sessionInfo;
                    } catch (Exception e) {
                        System.out.println("Error parsing SSH session parts: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("General error in getSessionInfoFromOperatorController: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Create a default session info if nothing was found
        System.out.println("Creating default session info as fallback");
        Map<String, Object> defaultInfo = new HashMap<>();
        defaultInfo.put("sessionId", sessionId != null ? sessionId : "unknown");
        defaultInfo.put("operatorName", "N/A");
        defaultInfo.put("deviceName", "N/A");
        defaultInfo.put("deviceId", "N/A");
        defaultInfo.put("startTime", new Date());
        defaultInfo.put("status", "CLOSED"); // Mặc định là đã đóng nếu không tìm thấy
        
        return defaultInfo;
    }
    
    /**
     * Lấy danh sách phiên SSH đang hoạt động từ OperatorController
     */
    private Map<String, Object> getActiveSessionsFromMemory() {
        Map<String, Object> sessionsMap = new HashMap<>();
        
        try {
            // Lấy trường SSH_SESSIONS từ OperatorController thông qua reflection
            Field sshSessionsField = null;
            try {
                sshSessionsField = OperatorController.class.getDeclaredField("SSH_SESSIONS");
                sshSessionsField.setAccessible(true);
                
                Object fieldValue = sshSessionsField.get(null);
                if (fieldValue != null && fieldValue instanceof ConcurrentHashMap) {
                    ConcurrentHashMap<String, Map<String, Object>> sshSessions = 
                        (ConcurrentHashMap<String, Map<String, Object>>) fieldValue;
                    
                    if (sshSessions != null) {
                        for (Map.Entry<String, Map<String, Object>> entry : sshSessions.entrySet()) {
                            String sessionId = entry.getKey();
                            Map<String, Object> sessionData = new HashMap<>();
                            
                            if (entry.getValue() != null) {
                                sessionData.putAll(entry.getValue());  // Tạo bản sao để tránh thay đổi trực tiếp
                            }
                            
                            // Thêm sessionId nếu chưa có
                            if (!sessionData.containsKey("sessionId")) {
                                sessionData.put("sessionId", sessionId);
                            }
                            
                            // Cập nhật session status nếu cần
                            if (!sessionData.containsKey("status")) {
                                sessionData.put("status", "ACTIVE");
                            }
                            
                            // Phân tích sessionId để lấy thông tin (định dạng: ssh-timestamp-deviceId)
                            if (sessionId.startsWith("ssh-")) {
                                String[] parts = sessionId.split("-");
                                String deviceId = parts.length > 2 ? parts[2] : "unknown";
                                
                                // Thêm thông tin thiết bị nếu chưa có
                                if (!sessionData.containsKey("deviceId")) {
                                    sessionData.put("deviceId", deviceId);
                                }
                                
                                // Thêm thông tin deviceName nếu chưa có
                                if (!sessionData.containsKey("deviceName")) {
                                    try {
                                        Integer devId = Integer.parseInt(deviceId);
                                        Device device = deviceRepository.findById(devId).orElse(null);
                                        sessionData.put("deviceName", device != null ? device.getDeviceName() : "Device " + deviceId);
                                    } catch (NumberFormatException e) {
                                        sessionData.put("deviceName", "Device " + deviceId);
                                    }
                                }
                                
                                // Thêm startTime nếu chưa có
                                if (!sessionData.containsKey("startTime")) {
                                    try {
                                        sessionData.put("startTime", new Date(Long.parseLong(parts.length > 1 ? parts[1] : "0")));
                                    } catch (NumberFormatException e) {
                                        sessionData.put("startTime", new Date());
                                    }
                                }
                            }
                            
                            sessionsMap.put(sessionId, sessionData);
                        }
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException | SecurityException e) {
                e.printStackTrace();
                sessionsMap.put("reflection-error", createErrorSessionInfo("Không thể truy cập SSH_SESSIONS: " + e.getMessage()));
                return sessionsMap;
            }
        } catch (Exception e) {
            e.printStackTrace();
            sessionsMap.put("error", createErrorSessionInfo("Lỗi khi lấy dữ liệu: " + e.getMessage()));
        }
        
        return sessionsMap;
    }
    
    private Map<String, Object> createErrorSessionInfo(String errorMessage) {
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("sessionId", "error");
        errorInfo.put("deviceName", "Lỗi khi lấy dữ liệu");
        errorInfo.put("deviceId", "N/A");
        errorInfo.put("operatorName", "N/A");
        errorInfo.put("startTime", new Date());
        errorInfo.put("status", "Lỗi: " + errorMessage);
        return errorInfo;
    }
    
    /**
     * Gọi phương thức closeSSHSession của OperatorController để đóng phiên
     */
    private void callCloseSSHSession(String sessionId) {
        try {
            System.out.println("Terminating session: " + sessionId);
            
            // 1. Kiểm tra nếu sessionId bắt đầu bằng "db-", cập nhật trạng thái trong database
            if (sessionId != null && sessionId.startsWith("db-")) {
                try {
                    int dbSessionId = Integer.parseInt(sessionId.substring(3));
                    Optional<Session> dbSessionOpt = sessionRepository.findById(dbSessionId);
                    
                    if (dbSessionOpt.isPresent()) {
                        Session dbSession = dbSessionOpt.get();
                        dbSession.setStatus("CLOSED");
                        dbSession.setEndTime(java.time.LocalDateTime.now());
                        sessionRepository.save(dbSession);
                        System.out.println("Database session " + dbSessionId + " was closed successfully");
                        return;
                    } else {
                        System.out.println("Database session " + dbSessionId + " not found");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error parsing database session ID: " + e.getMessage());
                }
            } 
            // 2. Xử lý các phiên SSH trong bộ nhớ
            else if (sessionId != null && sessionId.startsWith("ssh-")) {
                try {
                    // a. Tạo instance của OperatorController
                    OperatorController operatorController = new OperatorController();
                    
                    // b. Lấy thông tin phiên trước khi đóng
                    Map<String, Object> sessionInfo = getSessionInfoFromOperatorController(sessionId);
                    
                    // c. Gọi phương thức closeSSHSession để đóng phiên SSH
                    java.lang.reflect.Method method = OperatorController.class.getMethod("closeSSHSession", String.class);
                    method.invoke(operatorController, sessionId);
                    System.out.println("SSH session " + sessionId + " was closed successfully");
                    
                    // d. Nếu phiên có liên kết với database session, cập nhật trạng thái và thời gian kết thúc
                    if (sessionInfo != null && sessionInfo.containsKey("dbSessionId")) {
                        Integer dbSessionId = (Integer) sessionInfo.get("dbSessionId");
                        Optional<Session> dbSessionOpt = sessionRepository.findById(dbSessionId);
                        
                        if (dbSessionOpt.isPresent()) {
                            Session dbSession = dbSessionOpt.get();
                            dbSession.setStatus("CLOSED");
                            dbSession.setEndTime(java.time.LocalDateTime.now());
                            sessionRepository.save(dbSession);
                            System.out.println("Related database session " + dbSessionId + " was also closed");
                        }
                    }
                    return;
                } catch (Exception e) {
                    System.out.println("Error closing SSH session: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("No matching session found for ID: " + sessionId);
            throw new RuntimeException("Không thể tìm thấy phiên với ID: " + sessionId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Không thể kết thúc phiên: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public String viewHistory(Model model) {
        try {
            // Get current logged in supervisor
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Optional<User> supervisorOpt = userRepository.findByUser_name(username);
            
            if (supervisorOpt.isPresent()) {
                User supervisor = supervisorOpt.get();
                model.addAttribute("supervisor", supervisor);
                
                // Lấy lịch sử các phiên làm việc (cả đang hoạt động và đã kết thúc)
                List<Session> allSessions = sessionRepository.findAllSessionsOrderByStartTimeDesc();
                
                // Chuyển đổi sang định dạng hiển thị
                List<Map<String, Object>> sessionHistoryList = new ArrayList<>();
                for (Session session : allSessions) {
                    try {
                        Map<String, Object> historyItem = new HashMap<>();
                        historyItem.put("sessionId", session.getSessionId());
                        
                        // Chuyển đổi LocalDateTime sang Date để sử dụng #dates thay vì #temporals
                        if (session.getStartTime() != null) {
                            Date startDate = Date.from(session.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant());
                            historyItem.put("startTime", startDate);
                        } else {
                            historyItem.put("startTime", null);
                        }
                        
                        if (session.getEndTime() != null) {
                            Date endDate = Date.from(session.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant());
                            historyItem.put("endTime", endDate);
                        } else {
                            historyItem.put("endTime", null);
                        }
                        
                        historyItem.put("status", session.getStatus());
                        
                        // Thông tin thiết bị
                        if (session.getDevice() != null) {
                            historyItem.put("deviceId", session.getDevice().getDeviceId());
                            historyItem.put("deviceName", session.getDevice().getDeviceName());
                        } else {
                            historyItem.put("deviceId", "N/A");
                            historyItem.put("deviceName", "N/A");
                        }
                        
                        // Thông tin operator
                        if (session.getOperator() != null) {
                            historyItem.put("operatorName", session.getOperator().getUser_name());
                        } else {
                            historyItem.put("operatorName", "N/A");
                        }
                        
                        sessionHistoryList.add(historyItem);
                    } catch (Exception e) {
                        System.out.println("Error processing session " + session.getSessionId() + ": " + e.getMessage());
                    }
                }
                
                model.addAttribute("sessionHistory", sessionHistoryList);
            } else {
                model.addAttribute("errorMessage", "Không tìm thấy thông tin supervisor!");
            }
            
            return "supervisor-history";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Lỗi khi tải lịch sử: " + e.getMessage());
            return "supervisor-history";
        }
    }
} 